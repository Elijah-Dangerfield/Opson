package com.opson.libraries.ui.qr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * [code] drawn square, black on white with its quiet zone, in any theme: a camera needs the
 * contrast, and a dark-theme code reads worse. The modules are one path, built once a size.
 */
@Composable
fun QrCodeImage(code: QrCode, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
            .drawWithCache {
                val modules = code.size + QrPng.QUIET_ZONE * 2
                val cell = size.minDimension / modules
                val path = Path()
                for (y in 0 until code.size) {
                    for (x in 0 until code.size) {
                        if (code.isDark(x, y)) {
                            val left = (x + QrPng.QUIET_ZONE) * cell
                            val top = (y + QrPng.QUIET_ZONE) * cell
                            // A hair over a module, so neighbours meet with no seam between them.
                            path.addRect(Rect(left, top, left + cell + SEAM, top + cell + SEAM))
                        }
                    }
                }
                onDrawBehind {
                    drawRect(Color.White)
                    drawPath(path, Color.Black)
                }
            },
    )
}

private const val SEAM = 0.5f
