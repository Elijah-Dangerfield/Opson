package com.opson

import android.app.Application
import com.opson.libraries.reminders.ReminderActions
import com.opson.libraries.reminders.impl.ReminderActionsHost

/**
 * [ReminderActionsHost] so the reminder receivers, which the system builds outside DI, reach the
 * coordinator through the graph built here.
 */
class OpsonApplication : Application(), ReminderActionsHost {

    override val reminderActions: ReminderActions get() = appComponent.reminderActions

    lateinit var appComponent: AndroidAppComponent
        private set
    
    override fun onCreate() {
        super.onCreate()
        appComponent = AndroidAppComponent::class.create(this)
        appComponent.telemetry.initialize()
        // Construct every @AutoInit singleton up front (AppEventDispatcher's
        // lifecycle attach, connectivity edge watcher, …). Resolving the set
        // is what forces construction. App.kt does the same when iOS /
        // Compose launches; Android needs it here in Application.onCreate
        // since some warm work (AppLifecycleObserver attachment) wants to
        // fire before the first Activity.
        appComponent.autoInits
        // Eagerly start tracking the foreground Activity so bindings that
        // need it (e.g. AndroidReviewLauncher) work the moment they're called.
        appComponent.activityProvider
    }
}
