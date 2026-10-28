package com.opson.libraries.navigation.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.navigation.WebLinkLauncher
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosWebLinkLauncher @Inject constructor() : WebLinkLauncher {

    override fun open(url: String): Catching<Unit> = Catching {
        // The messages leave the link out: it can be a helpline, and failures get logged.
        val targetUrl = requireNotNull(NSURL.URLWithString(url)) { "Not a URL" }
        val application = UIApplication.sharedApplication
        check(application.canOpenURL(targetUrl)) { "No app can open this link" }
        application.openURL(
            url = targetUrl,
            options = emptyMap<Any?, Any?>(),
            completionHandler = null,
        )
    }
}
