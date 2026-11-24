package com.html2mp4.renderer.engine

/**
 * Pure JVM helper object containing audio validation, sequence ordering, gap compensation,
 * and PCM16 resampling logic without Android framework dependencies for direct unit testing.
 */
object AudioPcmMath {

    fun isValidPcmFormat(byteCount: Int, sampleRate: Int, channels: Int): Boolean {
        if (byteCount <= 0) return false
        if (sampleRate !in 8_000..192_000 || channels !in 1..2) return false
        val frameBytes = 2 * channels
        return byteCount >= frameBytes && byteCount % frameBytes == 0
    }

    /**
     * Returns Pair(missingOrOutOfOrderDelta, nextLastSeq).
     */
    fun evaluateSequenceOrder(lastReceivedSeq: Long, incomingSeq: Long): Pair<Long, Long> {
        if (incomingSeq < 0L) return 0L to lastReceivedSeq
        if (lastReceivedSeq < 0L) return 0L to incomingSeq
        val expected = lastReceivedSeq + 1L
        return when {
            incomingSeq > expected -> (incomingSeq - expected) to incomingSeq
            incomingSeq <= lastReceivedSeq -> 1L to lastReceivedSeq
            else -> 0L to incomingSeq
        }
    }

    fun computeGapSilenceMs(
        relSourceMs: Long,
        writtenMs: Long,
        thresholdMs: Long = 45L,
        maxCapMs: Long = 1_000L
    ): Long {
        if (relSourceMs <= writtenMs + thresholdMs) return 0L
        return (relSourceMs - writtenMs).coerceAtMost(maxCapMs)
    }

    fun computeMaxSampleFrames(targetDurationMs: Long, sampleRate: Int): Long {
        if (targetDurationMs !in 1L until 3_600_000_000L) return Long.MAX_VALUE
        val safeRate = sampleRate.coerceIn(8_000, 192_000).toLong()
        return (targetDurationMs * safeRate) / 1_000L
    }

    fun trimPcmToMaxFrames(pcmBytes: ByteArray, channels: Int, maxFrames: Long): ByteArray {
        if (maxFrames <= 0L || pcmBytes.isEmpty()) return ByteArray(0)
        val frameBytes = 2 * channels.coerceIn(1, 2)
        val availableFrames = (pcmBytes.size / frameBytes).toLong()
        if (availableFrames <= maxFrames) return pcmBytes
        return pcmBytes.copyOf((maxFrames * frameBytes).toInt())
    }

    fun adaptPcmToTarget(
        srcBytes: ByteArray,
        srcRate: Int,
        srcChannels: Int,
        targetRate: Int,
        targetChannels: Int
    ): ByteArray {
        val tgtRate = targetRate.coerceIn(8_000, 192_000)
        val tgtCh = targetChannels.coerceIn(1, 2)
        val safeSrcCh = srcChannels.coerceIn(1, 2)
        val safeSrcRate = srcRate.coerceIn(8_000, 192_000)
        val srcFrameBytes = 2 * safeSrcCh
        val srcFrames = srcBytes.size / srcFrameBytes
        if (srcFrames <= 0) return ByteArray(0)
        if (safeSrcRate == tgtRate && safeSrcCh == tgtCh && srcBytes.size % srcFrameBytes == 0) {
            return srcBytes
        }

        val srcShorts = ShortArray(srcFrames * safeSrcCh)
        for (i in srcShorts.indices) {
            val b0 = srcBytes[i * 2].toInt() and 0xFF
            val b1 = srcBytes[i * 2 + 1].toInt()
            srcShorts[i] = ((b1 shl 8) or b0).toShort()
        }

        val outFrames = if (safeSrcRate == tgtRate) {
            srcFrames
        } else {
            ((srcFrames.toDouble() * tgtRate.toDouble()) / safeSrcRate.toDouble()).toInt().coerceAtLeast(1)
        }
        val ratio = safeSrcRate.toDouble() / tgtRate.toDouble()
        val outBytes = ByteArray(outFrames * 2 * tgtCh)

        for (i in 0 until outFrames) {
            val srcPos = if (safeSrcRate == tgtRate) {
                i.toDouble()
            } else {
                (i.toDouble() * ratio).coerceAtMost((srcFrames - 1).toDouble())
            }
            val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val idx1 = (idx0 + 1).coerceAtMost(srcFrames - 1)
            val frac = srcPos - idx0
            for (c in 0 until tgtCh) {
                val sampleFloat = when {
                    safeSrcCh == 1 -> {
                        val s0 = srcShorts[idx0].toDouble()
                        val s1 = srcShorts[idx1].toDouble()
                        s0 * (1.0 - frac) + s1 * frac
                    }
                    tgtCh == 1 -> {
                        val l = srcShorts[idx0 * 2] * (1.0 - frac) + srcShorts[idx1 * 2] * frac
                        val r = srcShorts[idx0 * 2 + 1] * (1.0 - frac) + srcShorts[idx1 * 2 + 1] * frac
                        (l + r) * 0.5
                    }
                    else -> {
                        val s0 = srcShorts[idx0 * 2 + c].toDouble()
                        val s1 = srcShorts[idx1 * 2 + c].toDouble()
                        s0 * (1.0 - frac) + s1 * frac
                    }
                }
                val cl = sampleFloat.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                val byteIdx = (i * tgtCh + c) * 2
                outBytes[byteIdx] = (cl and 0xFF).toByte()
                outBytes[byteIdx + 1] = ((cl shr 8) and 0xFF).toByte()
            }
        }
        return outBytes
    }
}
