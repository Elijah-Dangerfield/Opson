package com.opson.libraries.widgets.impl

import android.content.Context
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import com.opson.libraries.widgets.WidgetJson
import com.opson.libraries.widgets.WidgetPublisher
import com.opson.libraries.widgets.WidgetSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Android widgets: the snapshot goes to [WidgetSnapshotStore], then every placed [OpsonWidget]
 * redraws from it. With no widget on the home screen (or no receiver in the manifest) the update
 * finds nothing to do.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = WidgetPublisher::class)
@Inject
class AndroidWidgetPublisher(private val context: Context) : WidgetPublisher {

    override suspend fun publish(snapshot: WidgetSnapshot) {
        WidgetSnapshotStore.write(context, WidgetJson.encode(snapshot))
        OpsonWidget().updateAll(context)
    }

    override suspend fun clear() {
        WidgetSnapshotStore.write(context, null)
        OpsonWidget().updateAll(context)
    }
}

/**
 * Where the snapshot lives between the app writing it and Glance drawing it. SharedPreferences
 * because the widget reads it from its own process start, where the app's graph may not exist.
 */
internal object WidgetSnapshotStore {
    private const val FILE = "opson_widgets"
    private const val KEY = "snapshot"

    suspend fun write(context: Context, json: String?) = withContext(Dispatchers.IO) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) {
            if (json == null) remove(KEY) else putString(KEY, json)
        }
    }

    suspend fun read(context: Context): WidgetSnapshot? = withContext(Dispatchers.IO) {
        WidgetJson.decode(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))
    }
}
