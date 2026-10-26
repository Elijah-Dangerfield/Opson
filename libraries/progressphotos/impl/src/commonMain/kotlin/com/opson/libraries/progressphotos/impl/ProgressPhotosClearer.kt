package com.opson.libraries.progressphotos.impl

import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.progressphotos.ProgressPhotoRepository
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * Deletes every progress photo when the user changes: "Delete everything"
 * (the account goes), signing out, or switching accounts. Photos never leave
 * the phone, so this is the only copy going. Runs only on a user change, never
 * at launch, and whatever the release flag says: deleting files is always safe.
 */
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class ProgressPhotosClearer(private val photos: ProgressPhotoRepository) : UserScopedClearer {
    override suspend fun clear(previousUserId: String) {
        photos.deleteAll()
    }
}
