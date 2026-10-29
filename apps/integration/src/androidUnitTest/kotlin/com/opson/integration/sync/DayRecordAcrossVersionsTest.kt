package com.opson.integration.sync

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.written
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.impl.DiarySyncApi
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **A day record across app versions.** The day's check-in (0.2, `release.checkIn`) rides on
 * the day record, a row 0.1 already syncs. A 0.1 phone decodes the record without it and sends the
 * whole row back on its next change to that day, so the server must not read the missing key as
 * "cleared".
 */
class DayRecordAcrossVersionsTest : IntegrationTest() {

    /**
     * The 0.1 wire: no `accepts` (the server tells 0.1 from 0.2 by it) and no `checkIn` on day
     * records, going up or coming down.
     */
    private class V01Wire(private val delegate: DiarySyncApi) : DiarySyncApi {
        override suspend fun sync(request: SyncRequestDto): Catching<SyncResponseDto> {
            val sent = request.copy(
                accepts = emptyList(),
                mutations = request.mutations.map { m -> if (m.entity != "day_record") m else m.copy(row = JsonObject(m.row - "checkIn")) },
            )
            return delegate.sync(sent).map { response ->
                response.copy(changes = response.changes.copy(dayRecords = response.changes.dayRecords.map { it.copy(checkIn = null) }))
            }
        }
    }

    @Test
    fun aV01WaterChange_keepsTheCheckInA02PhoneSaved() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        val day = newPhone.today()
        val checkIn = CheckIn(hunger = HungerLevel.of(4), energy = Energy.Good)
        newPhone.checkIns.setCheckIn(day, checkIn)
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::V01Wire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.diary.addWater(day, 250).written()
        oldPhone.syncNow().getOrThrow()

        val stored = server.query("SELECT check_in, water_ml FROM day_records WHERE user_id = ?::uuid", user).single()
        assertNotNull(stored["check_in"], "the server kept the check-in the 0.1 row did not carry")
        assertEquals(250, stored["water_ml"])

        newPhone.syncNow().getOrThrow()
        val onNewPhone = newPhone.diary.observeDay(day).first()
        assertEquals(250, onNewPhone.waterMl, "the 0.1 change landed")
        assertEquals(checkIn, onNewPhone.checkIn, "and the check-in it never saw is still there")
    }
}
