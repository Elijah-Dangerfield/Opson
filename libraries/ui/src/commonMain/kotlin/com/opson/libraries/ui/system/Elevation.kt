package com.opson.libraries.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
class Elevation internal constructor(val dp: Dp) : Comparable<Elevation> {

    override fun compareTo(other: Elevation): Int = dp.compareTo(other.dp)

    companion object {
        val None = Elevation(0.dp)
        val Button = Elevation(3.dp)
        val Card = Elevation(4.dp)

        val Header = Elevation(5.dp)
        val BottomBar = Elevation(10.dp)
        val Modal = Elevation(100.dp)
    }
}
