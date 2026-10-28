package com.opson.integration.account

import com.opson.integration.helpers.Harness
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import kotlinx.coroutines.flow.first
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * **Changing the display name.** `PATCH /v1/me` with the codes the client turns into outcomes: a
 * name someone else holds is 409 `display_name_taken`, one that breaks the rules is 400
 * `invalid_display_name`. Either way the person keeps the name they had, on the phone and on the
 * server.
 */
class DisplayNameTest : IntegrationTest() {

    @Test
    fun aNewName_isSaved_andComesBackOnAnotherPhone() = integration {
        val user = randomUserId()
        val name = uniqueName()
        val phone = client(user)
        phone.profileRepository.current() // signed in and resolved, as the Settings screen is

        val saved = assertIs<UpdateProfileOutcome.Success>(phone.profileRepository.update(displayName = name))

        assertEquals(name, saved.profile.displayName)
        assertEquals(name, (client(user).profileRepository.current() as Profile.Authenticated).displayName)
    }

    @Test
    fun aTakenName_isRefused_andTheEditRollsBack() = integration {
        val name = uniqueName()
        val holder = client().profileRepository.apply { current() }
        assertIs<UpdateProfileOutcome.Success>(holder.update(displayName = name))
        val user = randomUserId()
        val phone = client(user)
        val before = (phone.profileRepository.current() as Profile.Authenticated).displayName

        assertEquals(UpdateProfileOutcome.DisplayNameTaken, phone.profileRepository.update(displayName = name))

        assertEquals(before, (phone.profileRepository.observe().first() as Profile.Authenticated).displayName)
        assertEquals(before, storedName(user))
    }

    @Test
    fun aNameWithAnEmoji_isRefusedAsInvalid() = integration {
        val user = randomUserId()
        val phone = client(user)
        val before = (phone.profileRepository.current() as Profile.Authenticated).displayName

        assertEquals(UpdateProfileOutcome.InvalidDisplayName, phone.profileRepository.update(displayName = "Sam 🙂"))

        assertEquals(before, storedName(user))
    }

    private fun Harness.storedName(user: String): Any? =
        server.query("SELECT display_name FROM profiles WHERE user_id = ?::uuid", user).single()["display_name"]

    /** Display names are unique across the shared database, so each test makes its own. */
    private fun uniqueName() = "Sam ${Random.nextInt(100_000_000)}"
}
