package com.opson.libraries.health.impl

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity

/**
 * What Health Connect opens when someone asks why Opson wants their data: from the permission
 * prompt's privacy policy link (Android 13 and lower) and from "App permissions" in Health
 * Connect (14 and up, through the activity-alias in the manifest). Health Connect will not
 * list an app without one, and Play reviews that it shows the privacy policy.
 *
 * A plain WebView over the published policy, so it can never say something the policy
 * does not.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(WebView(this).apply { loadUrl(PRIVACY_POLICY_URL) })
    }

    private companion object {
        /** Published from legal/privacy.md by legal-sync (`docs/release-automation.md`). */
        const val PRIVACY_POLICY_URL = "https://nightjarlabs.llc/opson/privacy"
    }
}
