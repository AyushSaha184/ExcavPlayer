package com.excavplayer.player.core

import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser

@UnstableApi
class OffsetSubtitleParserFactory(
    private val delegate: SubtitleParser.Factory = DefaultSubtitleParserFactory(),
    private val getDelayUs: () -> Long
) : SubtitleParser.Factory {

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int = delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser {
        val baseParser = delegate.create(format)
        return object : SubtitleParser {
            override fun parse(
                data: ByteArray,
                offset: Int,
                length: Int,
                outputOptions: SubtitleParser.OutputOptions,
                output: Consumer<CuesWithTiming>
            ) {
                val delayUs = getDelayUs()
                baseParser.parse(data, offset, length, outputOptions) { cuesWithTiming ->
                    val sanitizedCues = cuesWithTiming.cues.map { cue ->
                        val rawText = cue.text
                        if (rawText is android.text.Spanned) {
                            val spannable = android.text.SpannableString(rawText)
                            spannable.getSpans(0, spannable.length, android.text.style.ForegroundColorSpan::class.java)
                                .forEach { spannable.removeSpan(it) }
                            spannable.getSpans(0, spannable.length, android.text.style.BackgroundColorSpan::class.java)
                                .forEach { spannable.removeSpan(it) }
                            spannable.getSpans(0, spannable.length, android.text.style.AbsoluteSizeSpan::class.java)
                                .forEach { spannable.removeSpan(it) }
                            spannable.getSpans(0, spannable.length, android.text.style.RelativeSizeSpan::class.java)
                                .forEach { spannable.removeSpan(it) }
                            cue.buildUpon().setText(spannable).build()
                        } else {
                            cue
                        }
                    }

                    if (delayUs == 0L) {
                        output.accept(
                            CuesWithTiming(
                                sanitizedCues,
                                cuesWithTiming.startTimeUs,
                                cuesWithTiming.durationUs
                            )
                        )
                    } else {
                        val shiftedStartTime = if (cuesWithTiming.startTimeUs >= 0) {
                            (cuesWithTiming.startTimeUs + delayUs).coerceAtLeast(0L)
                        } else {
                            cuesWithTiming.startTimeUs
                        }
                        val shifted = CuesWithTiming(
                            sanitizedCues,
                            shiftedStartTime,
                            cuesWithTiming.durationUs
                        )
                        output.accept(shifted)
                    }
                }
            }

            override fun getCueReplacementBehavior(): Int = baseParser.cueReplacementBehavior

            override fun reset() = baseParser.reset()
        }
    }
}
