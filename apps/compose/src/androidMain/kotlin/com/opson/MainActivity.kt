package com.opson

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.opson.libraries.reminders.impl.ReminderIntents
import com.opson.libraries.telemetry.impl.AndroidJankMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** The system's night mode, which a theme left on System follows. */
    private val systemDark = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen BEFORE super.onCreate()
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)

        followThemeWithSystemBars()

        // Keep the splash screen on until AppViewModel has determined the destination.
        // AppViewModel is a singleton, so this is the same instance used in App composable.
        splashScreen.setKeepOnScreenCondition {
            !appComponent.appViewModel.isReady.value
        }

        // JankStats needs a Window, so it can only be armed once the Activity
        // has one. Attaching after setContent would miss the first frames,
        // which are the ones most likely to be janky.
        (appComponent.jankMonitor as? AndroidJankMonitor)?.attach(window)

        setContent {
            App(appComponent)
        }

        // A recreated activity already handled its intent; only a fresh launch carries a new tap.
        if (savedInstanceState == null) {
            handleReminderTap(intent)
            handleWidgetTap(intent)
        }

        reportStartupWhenReady()
    }

    /**
     * Closes the cold-start measurement at the moment the user can actually use
     * the app, and tells the platform the same thing.
     *
     * The timing is the whole point. [AppViewModel.isReady] is when the start
     * destination is resolved and the splash condition lets go — but the frame
     * behind the splash has not been drawn yet at that instant. `onPreDraw`
     * fires immediately *before* that frame, so posting from inside it lands
     * just after the pixels are up. Measuring at `isReady` instead would
     * under-report by however long the first real composition takes, which is
     * the slowest frame of the launch and the one most worth counting.
     *
     * [reportFullyDrawn] hands that same instant to the platform, which is what
     * Play Console grades "fully drawn" startup on. Without it Play measures to
     * the first frame — the splash — and grades the app on a number no user ever
     * experiences.
     */
    private fun reportStartupWhenReady() {
        lifecycleScope.launch {
            appComponent.appViewModel.isReady.first { it }

            val decorView = window.decorView
            decorView.viewTreeObserver.addOnPreDrawListener(
                object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        decorView.viewTreeObserver.removeOnPreDrawListener(this)
                        decorView.post {
                            reportFullyDrawn()
                            appComponent.startupReporter.onAppReady()
                        }
                        return true
                    }
                },
            )
        }
    }

    /**
     * Edge to edge, with bar icons that read on the theme the app is drawing. That is the user's
     * Look and feel pick, which can differ from the system's, so `SystemBarStyle.auto` is not
     * enough. The manifest keeps uiMode changes in process, hence [onConfigurationChanged].
     */
    private fun followThemeWithSystemBars() {
        systemDark.value = resources.configuration.isNightMode()
        // lifecycleScope is Main.immediate, so the first value applies before the first frame.
        lifecycleScope.launch {
            combine(appComponent.settingsRepository.settings.map { it.theme }, systemDark) { theme, systemDark ->
                theme.isDark(systemDark)
            }
                .distinctUntilChanged()
                .collect(::applySystemBars)
        }
    }

    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDark.value = newConfig.isNightMode()
    }

    private fun Configuration.isNightMode(): Boolean =
        (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderTap(intent)
        handleWidgetTap(intent)
    }

    /**
     * A home screen widget's +: its link goes to WidgetOpens, which asks App.kt for the Logger.
     * Cleared once handled so NavHost, which reads the intent's data as a deep link, never sees it.
     */
    private fun handleWidgetTap(intent: Intent?) {
        val url = intent?.dataString ?: return
        if (appComponent.widgetOpens.opened(url)) intent.data = null
    }

    /** A tapped reminder, or its Log it button: the coordinator asks App.kt for the Logger. */
    private fun handleReminderTap(intent: Intent?) {
        val (reminder, via) = ReminderIntents.opened(intent) ?: return
        ReminderIntents.consume(intent)
        appComponent.reminderActions.opened(reminder, via)
    }

    override fun onStop() {
        super.onStop()
        // Flush whatever this screen accumulated before the process can be
        // killed in the background. A session that never returns still reports
        // the screen it was on, which is the one worth knowing about.
        appComponent.jankMonitor.onBackground()
    }

    override fun onDestroy() {
        (appComponent.jankMonitor as? AndroidJankMonitor)?.detach()
        super.onDestroy()
    }

    private val appComponent get() = (application as OpsonApplication).appComponent
}
