package com.opson

import android.content.Context
import com.opson.libraries.opson.ActivityProvider
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class AndroidAppComponent(
    private val context: Context
) : AppComponent {

    @Provides
    fun context() = context

    /**
     * Eagerly accessed in [OpsonApplication.onCreate] so the
     * activity-lifecycle callback registration happens before the first
     * Activity is created.
     */
    abstract val activityProvider: ActivityProvider
}
