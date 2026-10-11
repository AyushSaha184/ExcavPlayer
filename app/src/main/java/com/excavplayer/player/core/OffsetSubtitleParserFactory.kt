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
                    if (delayUs == 0L) {
                        output.accept(cuesWithTiming)
                    } else {
                        val shiftedStartTime = if (cuesWithTiming.startTimeUs >= 0) {
                            (cuesWithTiming.startTimeUs + delayUs).coerceAtLeast(0L)
                        } else {
                            cuesWithTiming.startTimeUs
                        }
                        val shifted = CuesWithTiming(
                            cuesWithTiming.cues,
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
