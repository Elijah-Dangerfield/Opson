package com.opson.integration.sync

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.diary.impl.DiarySyncApi
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Appearance across app versions.** 0.1 synced a `palette` (Pine, Espresso, Clay or Plum) beside
 * `theme`. 0.2 has one scheme, Slate, so it drops `palette` and keeps `theme` as Appearance. A 0.2
 * save leaves the key out, and the server's shallow merge keeps it, so a 0.1 phone on the same
 * account keeps the palette it picked.
 */
class AppearanceAcrossVersionsTest : IntegrationTest() {

    /** The 0.1 wire for this test's purpose: every settings row it sends carries its palette. */
    private class PaletteWire(private val delegate: DiarySyncApi) : DiarySyncApi {
        override suspend fun sync(request: SyncRequestDto): Catching<SyncResponseDto> = delegate.sync(
            request.copy(
                mutations = request.mutations.map { m ->
                    if (m.entity != "settings") {
                        m
                    } else {
                        val settings = m.row.getValue("settings").jsonObject
                        m.copy(row = JsonObject(m.row + ("settings" to JsonObject(settings + ("palette" to JsonPrimitive("plum"))))))
                    }
                },
            ),
        )
    }

    @Test
    fun aV02Save_keepsTheV01Palette_andReadsItsAppearance() = integration {
        val user = randomUserId()
        val oldPhone = client(user, wrapSync = ::PaletteWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        oldPhone.settings.update { it.copy(theme = ThemeMode.Dark) }
        oldPhone.syncNow().getOrThrow()

        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        assertEquals(ThemeMode.Dark, newPhone.settings.settings.awaitState { it.theme == ThemeMode.Dark }.theme)
        newPhone.settings.update { it.copy(theme = ThemeMode.Light) }
        newPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT settings->>'palette' AS palette, settings->>'theme' AS theme FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals("plum", stored["palette"], "the merge kept 0.1's palette")
        assertEquals("light", stored["theme"], "the 0.2 save landed")
    }
}
