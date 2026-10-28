package com.opson.server.data

import com.opson.server.di.ServerScope
import com.opson.server.domain.LabelReading
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What the model read for each recent parse, so a submission can carry the
 * server's own copy of it into review rather than whatever the app says the
 * model said. Numbers and names only, never the photos. Held for [TTL] and
 * at most [MAX_ENTRIES], in memory: after a deploy a submission simply arrives
 * without the model's reading, and review shows "not available".
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@Inject
class LabelParseCache(private val clock: Clock) {

    class Entry(val userId: UserId, val reading: LabelReading, val model: String, val at: Instant)

    private val entries = LinkedHashMap<UUID, Entry>()

    fun put(userId: UserId, reading: LabelReading, model: String): UUID {
        val id = UUID.randomUUID()
        synchronized(entries) {
            prune()
            entries[id] = Entry(userId, reading, model, clock.now())
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }
        return id
    }

    /** The reading [id] names, only for the person it was read for. */
    fun get(id: UUID, userId: UserId): Entry? = synchronized(entries) {
        prune()
        entries[id]?.takeIf { it.userId == userId }
    }

    private fun prune() {
        val now = clock.now()
        val iterator = entries.values.iterator()
        while (iterator.hasNext() && now - iterator.next().at > TTL) iterator.remove()
    }

    companion object {
        val TTL: Duration = 1.hours
        const val MAX_ENTRIES = 2_000
    }
}
