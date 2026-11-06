package com.excavplayer.ui.icons

import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.*

/**
 * SF Symbols mapped and thickened for settings and player menus, matching BitChord.
 */
object SfIcons {
    // ---- Settings & Navigation ----
    val Settings: ImageVector by lazy { bold(SfSymbols.Monochrome.SFGearshape) }
    val Advanced: ImageVector by lazy { bold(SfSymbols.Monochrome.SFWrenchAndScrewdriver) }
    val ArrowBack: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronBackward) }
    val ChevronRight: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronRight) }
    val Check: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCheckmark) }
    val Folder: ImageVector by lazy { bold(SfSymbols.Monochrome.SFFolder) }
    val Home: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMusicNoteHouse) }
    val Performance: ImageVector by lazy { bold(SfSymbols.Monochrome.SFGaugeWithDotsNeedle67percent) }
    val Playback: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPlayCircle) }
    val Subtitles: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCharacterBubble) }
    val Gestures: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHandTap) }
    val Display: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSunMax) }
    val AutoAwesome: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSparkles) }
    val Volume: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSpeakerWave2) }
    val Brightness: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSunMax) }
    val Speed: ImageVector by lazy { bold(SfSymbols.Monochrome.SFGaugeWithDotsNeedle67percent) }
    val Repeat: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowTrianglehead2Clockwise) }
    val Orientation: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowUpLeftAndArrowDownRight) }
    val Info: ImageVector by lazy { bold(SfSymbols.Monochrome.SFInfoCircle) }
    val Language: ImageVector by lazy { bold(SfSymbols.Monochrome.SFGlobe) }
    val Headphones: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHeadphones) }
    val Search: ImageVector by lazy { bold(SfSymbols.Monochrome.SFEllipsisCircle) }
    val Play: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPlay) }
    val Close: ImageVector by lazy { bold(SfSymbols.Monochrome.SFXmark) }
    val Refresh: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowTrianglehead2Clockwise) }
    val Trash: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTrash) }
    val Pencil: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPencil) }

    internal const val BOLD_STROKE = 0.9f
    internal const val CANVAS = 31f

    internal inline fun canvas(
        name: String,
        width: Float,
        height: Float,
        autoMirror: Boolean = false,
        content: ImageVector.Builder.() -> Unit,
    ): ImageVector {
        val fit = minOf(1f, CANVAS / width, CANVAS / height)
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = CANVAS,
            viewportHeight = CANVAS,
            autoMirror = autoMirror,
        ).apply {
            addGroup(
                translationX = (CANVAS - width * fit) / 2f,
                translationY = (CANVAS - height * fit) / 2f,
                scaleX = fit,
                scaleY = fit,
            )
            content()
            clearGroup()
        }.build()
    }

    private fun bold(regular: ImageVector): ImageVector {
        val pad = BOLD_STROKE / 2f
        return canvas(
            name = regular.name + ".bold",
            width = regular.viewportWidth + pad * 2f,
            height = regular.viewportHeight + pad * 2f,
            autoMirror = regular.autoMirror,
        ) {
            addGroup(translationX = pad, translationY = pad)
            copyInto(this, regular.root)
            clearGroup()
        }
    }

    internal fun copyInto(builder: ImageVector.Builder, group: VectorGroup, extraStroke: Float = BOLD_STROKE) {
        for (node in group) {
            when (node) {
                is VectorGroup -> {
                    builder.addGroup(
                        name = node.name,
                        rotate = node.rotation,
                        pivotX = node.pivotX,
                        pivotY = node.pivotY,
                        scaleX = node.scaleX,
                        scaleY = node.scaleY,
                        translationX = node.translationX,
                        translationY = node.translationY,
                        clipPathData = node.clipPathData,
                    )
                    copyInto(builder, node, extraStroke)
                    builder.clearGroup()
                }
                is VectorPath -> builder.addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = node.fill,
                    fillAlpha = 1f,
                    stroke = node.stroke ?: node.fill,
                    strokeAlpha = 1f,
                    strokeLineWidth = node.strokeLineWidth + extraStroke,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = StrokeJoin.Round,
                    strokeLineMiter = node.strokeLineMiter,
                )
            }
        }
    }
}

