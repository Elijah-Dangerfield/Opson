package com.opson.features.upgrade

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.core.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class AppGuardStateTest {

    @Test
    fun noConfig_isNormal() {
        // The shipped fallback is an empty map: a phone that never reached us is never stopped.
        assertEquals(AppGuardState.Normal, guard(configOf(), version = 1))
    }

    @Test
    fun belowTheFloor_isUpgradeRequired() {
        assertEquals(AppGuardState.UpgradeRequired, guard(upgrade("minSupportedVersionCode" to 42), version = 41))
    }

    @Test
    fun atTheFloor_isNormal() {
        assertEquals(AppGuardState.Normal, guard(upgrade("minSupportedVersionCode" to 42), version = 42))
    }

    @Test
    fun loweringTheFloorAgain_dropsTheStop() {
        assertEquals(AppGuardState.UpgradeRequired, guard(upgrade("minSupportedVersionCode" to 42), version = 41))
        assertEquals(AppGuardState.Normal, guard(upgrade("minSupportedVersionCode" to 1), version = 41))
    }

    @Test
    fun theFloorHoldsOffline() {
        assertEquals(
            AppGuardState.UpgradeRequired,
            guard(upgrade("minSupportedVersionCode" to 42), version = 41, reachable = false),
        )
    }

    @Test
    fun theFloorOutranksMaintenance() {
        val config = upgrade(
            "minSupportedVersionCode" to 99,
            "maintenanceMode" to "blocking",
            "maintenanceMessage" to "Down for a bit",
        )
        assertEquals(AppGuardState.UpgradeRequired, guard(config, version = 1))
    }

    @Test
    fun blockingMaintenance_stopsTheAppWithTheMessage() {
        val config = upgrade("maintenanceMode" to "blocking", "maintenanceMessage" to "Back in 15 minutes.")
        assertEquals(AppGuardState.MaintenanceBlocking("Back in 15 minutes."), guard(config, version = 1))
    }

    @Test
    fun blockingMaintenance_offline_failsOpenToTheBanner() {
        val config = upgrade("maintenanceMode" to "blocking", "maintenanceMessage" to "Back in 15 minutes.")
        assertEquals(
            AppGuardState.MaintenanceBanner("Back in 15 minutes.", loggingContinues = true),
            guard(config, version = 1, reachable = false),
        )
    }

    @Test
    fun bannerMaintenance_isABanner_onlineOrNot() {
        val config = upgrade("maintenanceMode" to "banner", "maintenanceMessage" to "Sync is slow today.")
        val banner = AppGuardState.MaintenanceBanner("Sync is slow today.", loggingContinues = false)
        assertEquals(banner, guard(config, version = 1))
        assertEquals(banner, guard(config, version = 1, reachable = false))
    }

    @Test
    fun maintenanceWithoutAMessage_usesTheShippedOne() {
        assertEquals(
            AppGuardState.MaintenanceBlocking("We're updating the servers, back in a moment."),
            guard(upgrade("maintenanceMode" to "blocking"), version = 1),
        )
    }

    @Test
    fun anUnknownModeOrStrayCase_isReadSafely() {
        assertEquals(AppGuardState.Normal, guard(upgrade("maintenanceMode" to "banana"), version = 1))
        assertEquals(
            AppGuardState.MaintenanceBlocking("x"),
            guard(upgrade("maintenanceMode" to " Blocking ", "maintenanceMessage" to "x"), version = 1),
        )
    }

    @Test
    fun aNewerBuild_offersTheUpdate_untilDismissed() {
        val config = upgrade("latestVersionCode" to 10)
        assertEquals(AppGuardState.UpdateAvailable, guard(config, version = 9))
        assertEquals(AppGuardState.Normal, guard(config, version = 9, dismissed = true))
        assertEquals(AppGuardState.Normal, guard(config, version = 10))
    }

    @Test
    fun maintenanceOutranksTheUpdateOffer() {
        val config = upgrade("latestVersionCode" to 10, "maintenanceMode" to "banner", "maintenanceMessage" to "m")
        assertEquals(AppGuardState.MaintenanceBanner("m", loggingContinues = false), guard(config, version = 9))
    }

    @Test
    fun storeUrls() {
        assertEquals("https://apps.apple.com/app/id6827143148", storeUrl(Platform.iOS, "com.opson.Opson"))
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.opson",
            storeUrl(Platform.Android, "com.opson.debug"),
        )
    }

    private fun guard(
        config: AppConfigMap,
        version: Int,
        reachable: Boolean = true,
        dismissed: Boolean = false,
    ) = AppGuardState.from(config, version, serverReachable = reachable, updateDismissed = dismissed)

    private fun upgrade(vararg entries: Pair<String, Any>) = configOf("upgrade" to entries.toMap())

    private fun configOf(vararg entries: Pair<String, Any>): AppConfigMap = object : AppConfigMap() {
        override val map: Map<String, *> = entries.toMap()
    }
}
