package com.opson.features.today.impl

import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.modes_timeline_done_around_cd
import opson.libraries.resources.generated.resources.modes_timeline_done_at_cd
import opson.libraries.resources.generated.resources.modes_timeline_open_cd
import kotlin.test.Test
import kotlin.test.assertEquals

/** No numbers' meal circles tell a screen reader when, in one sentence per status. */
class TimelineMealSpokenTest {

    private val eight = LocalTime(8, 10)

    @Test
    fun aLoggedMeal_saysTheTimeItWasLogged() {
        assertEquals(SpokenMeal(Res.string.modes_timeline_done_at_cd, eight), TileStatus.Logged(eight).spoken())
    }

    @Test
    fun aMealMarkedEaten_saysAroundWhenItUsuallyIs() {
        assertEquals(SpokenMeal(Res.string.modes_timeline_done_around_cd, eight), TileStatus.AteNoDetails(eight).spoken())
    }

    @Test
    fun aMealNotYetEaten_saysNotYetAndWhenItUsuallyIs() {
        assertEquals(SpokenMeal(Res.string.modes_timeline_open_cd, eight), TileStatus.Empty(eight).spoken())
    }
}
