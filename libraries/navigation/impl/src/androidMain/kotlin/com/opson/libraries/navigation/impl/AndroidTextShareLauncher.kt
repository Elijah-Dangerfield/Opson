package com.opson.libraries.navigation.impl

import android.content.Context
import android.content.Intent
import com.opson.libraries.core.Catching
import com.opson.libraries.navigation.TextShareLauncher
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** The system chooser over `ACTION_SEND` with plain text. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidTextShareLauncher @Inject constructor(
    private val context: Context,
) : TextShareLauncher {

    override fun share(text: String): Catching<Unit> = Catching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}
