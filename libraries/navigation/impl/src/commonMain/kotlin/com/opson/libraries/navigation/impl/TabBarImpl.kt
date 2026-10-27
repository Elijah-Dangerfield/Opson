package com.opson.libraries.navigation.impl

import com.opson.libraries.navigation.TabBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** The owners holding the bar away; it shows again once none is left. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class TabBarImpl : TabBar {
    private val owners = MutableStateFlow(emptySet<String>())
    private val _hidden = MutableStateFlow(false)

    override val hidden: StateFlow<Boolean> = _hidden.asStateFlow()

    override fun hide(owner: String, hidden: Boolean) {
        owners.update { if (hidden) it + owner else it - owner }
        _hidden.value = owners.value.isNotEmpty()
    }
}
