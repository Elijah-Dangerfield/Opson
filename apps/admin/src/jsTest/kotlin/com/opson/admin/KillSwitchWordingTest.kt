package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The kill switches reach 0.2 and later and never 0.1, and the console has to say so wherever an
 * operator decides. It once promised a lockout no build performed; an operator in an incident
 * believed the confirm sheet.
 */
class KillSwitchWordingTest {

    @Test
    fun everyWarningNamesWhichBuildsItReaches() {
        listOf(
            dangerousWarning("upgrade.maintenanceMode", "\"blocking\""),
            dangerousWarning("upgrade.maintenanceMode", "\"banner\""),
            dangerousWarning("upgrade.minSupportedVersionCode", "40"),
        ).forEach { warning ->
            assertNotNull(warning)
            assertTrue("0.2+" in warning, "says 0.2 and later honour it: $warning")
            assertTrue("0.1" in warning, "says 0.1 does not: $warning")
        }
    }

    @Test
    fun blockingSaysOfflinePhonesKeepLogging() {
        val warning = assertNotNull(dangerousWarning("upgrade.maintenanceMode", "\"blocking\""))
        assertTrue("keeps logging" in warning, warning)
    }

    @Test
    fun turningMaintenanceOffAndTheUpdateOfferNeedNoWarning() {
        assertNull(dangerousWarning("upgrade.maintenanceMode", "\"off\""))
        assertNull(dangerousWarning("upgrade.latestVersionCode", "40"))
    }

    @Test
    fun thePanelHeaderNamesBothSides() {
        assertTrue("0.2 and later" in WHO_READS_THESE)
        assertTrue("0.1 does not read them" in WHO_READS_THESE)
    }
}
