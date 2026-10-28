@file:Suppress("MagicNumber")

package com.opson.system

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
class Radius private constructor(val shape: RoundedCornerShape) {
    internal constructor(cornerSize: CornerSize) : this(RoundedCornerShape(cornerSize))

    val cornerSize: CornerSize
        get() = shape.topStart.takeUnless { it == SquareCornerSize }
            ?: shape.topEnd.takeUnless { it == SquareCornerSize }
            ?: shape.bottomEnd.takeUnless { it == SquareCornerSize }
            ?: shape.bottomStart

    override fun equals(other: Any?): Boolean = this === other || other is Radius && shape == other.shape
    override fun hashCode(): Int = shape.hashCode()
    override fun toString(): String = "Radius(cornerSize=$cornerSize)"
}

object Radii {
    val Round = Radius(CornerSize(percent = 50))
    val R300 = Radius(CornerSize(DimensionResource.D300.dp))
    val R400 = Radius(CornerSize(DimensionResource.D400.dp))
    val R600 = Radius(CornerSize(DimensionResource.D600.dp))
    val None = Radius(SquareCornerSize)

    val Default get() = None
    // Handoff values; AppTheme.shapes has the same radii as Shapes for new code.
    val Button get() = Radius(CornerSize(14.dp))
    val IconButton get() = Round
    val Banner get() = Radius(CornerSize(14.dp))
    val Header get() = None
    val Card get() = Radius(CornerSize(22.dp))
}



private val SquareCornerSize = CornerSize(0.dp)


