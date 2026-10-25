package com.opson

import android.app.Application

class OpsonApplication : Application() {
    
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
