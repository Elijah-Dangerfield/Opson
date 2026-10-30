package com.opson.libraries.navigation.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.navigation.FileSharePresenter
import com.opson.libraries.navigation.TextShareLauncher
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** The Swift [FileSharePresenter]'s sheet, with the text as its one item. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosTextShareLauncher @Inject constructor(
    private val presenter: FileSharePresenter,
) : TextShareLauncher {

    override fun share(text: String): Catching<Unit> = Catching { presenter.presentText(text = text) }
}
