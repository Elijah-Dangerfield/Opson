package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SubmittedFoods
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.opson.UserScopedSyncer
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** One submission as the device keeps it. [customFoodId] is null for one sent before the app named its copy. */
@Serializable
data class SubmissionRecord(
    val submissionId: String,
    val foodId: String,
    val customFoodId: String? = null,
    val status: ReviewStatus = ReviewStatus.Pending,
)

@Serializable
data class StoredSubmissions(val records: List<SubmissionRecord> = emptyList())

/** `GET /v1/foods/submissions`, mirrored from the server's `OwnSubmissionsResponse`. */
@Serializable
data class OwnSubmissionsResponse(val submissions: List<OwnSubmissionDto> = emptyList())

@Serializable
data class OwnSubmissionDto(
    val id: String,
    val foodId: String,
    val status: String,
    val createdAt: String,
    val customFoodId: String? = null,
)

/**
 * [SubmittedFoods] on disk, user-scoped: a user change wipes it before the next account's state
 * is emitted, and the sync pulls that account's own list.
 *
 * The server's list is the truth once it answers: a status moves, and a submission it no longer
 * has (the account was deleted elsewhere) goes. A server from before the route answers 404, which
 * changes nothing, so a food sent to it stays marked from the moment it was sent.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = SubmittedFoods::class)
@ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class SubmittedFoodsStore(
    cacheFactory: CacheFactory,
    private val networkClient: NetworkClient,
    appScope: AppCoroutineScope,
) : SubmittedFoods, UserScopedSyncer, UserScopedClearer {

    private val cache: Cache<StoredSubmissions> = cacheFactory.persistent(
        name = "submitted_foods",
        serializer = versionedJsonSerializer(defaultValue = { StoredSubmissions() }),
    )
    private val mutex = Mutex()

    override val reviews: StateFlow<Map<String, ReviewStatus>> = cache.updates
        .map { stored -> byCustomFood(stored.records) }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    override suspend fun sent(customFoodId: String, submissionId: String, foodId: String) {
        mutex.withLock {
            cache.update { stored ->
                val record = SubmissionRecord(submissionId = submissionId, foodId = foodId, customFoodId = customFoodId)
                stored.copy(records = stored.records.filterNot { it.submissionId == submissionId } + record)
            }
        }
    }

    override suspend fun sync(): Result<Unit> = Catching {
        val answer = networkClient.authedCall("foods.submissions_mine") { client ->
            val response = client.get(PATH) { expectSuccess = false }
            when (response.status) {
                HttpStatusCode.OK -> response.body<OwnSubmissionsResponse>().submissions
                // A server from before the route: keep what the device recorded.
                HttpStatusCode.NotFound -> null
                else -> error("submissions answered ${response.status.value}")
            }
        }.getOrThrow() ?: return@Catching
        mutex.withLock { cache.update { stored -> StoredSubmissions(merge(stored.records, answer)) } }
    }

    override suspend fun clear(previousUserId: String) {
        mutex.withLock { cache.set(StoredSubmissions()) }
    }

    internal companion object {
        const val PATH = "/v1/foods/submissions"

        /**
         * The server's list (newest first) as records oldest first, the order [sent] appends in,
         * each keeping the custom food id the device recorded when the server has none for it
         * (sent to a server from before it stored the link).
         */
        fun merge(local: List<SubmissionRecord>, remote: List<OwnSubmissionDto>): List<SubmissionRecord> {
            val known = local.associateBy { it.submissionId }
            return remote.asReversed().map { dto ->
                SubmissionRecord(
                    submissionId = dto.id,
                    foodId = dto.foodId,
                    customFoodId = dto.customFoodId ?: known[dto.id]?.customFoodId,
                    status = ReviewStatus.fromWire(dto.status),
                )
            }
        }

        /** Oldest first, so the newest record wins for a custom food sent twice. */
        fun byCustomFood(records: List<SubmissionRecord>): Map<String, ReviewStatus> =
            records.mapNotNull { record -> record.customFoodId?.let { it to record.status } }.toMap()
    }
}
