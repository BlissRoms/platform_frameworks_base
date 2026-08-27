/*
 * Copyright (C) 2014-2026 The BlissRoms Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.blissroms.systemui.ambientmusic.engine

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Generates acoustic peak signatures compatible with Shazam's recognition catalog.
 * Implements the exact time-frequency landmark peak extraction & binary TLV format.
 */
object ShazamSignatureGenerator {

    private const val FFT_SIZE = 2048
    private const val STEP_SIZE = 128
    private const val NUM_BINS = 1025

    private val HANNING_WINDOW = FloatArray(FFT_SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * Math.PI * i / (FFT_SIZE - 1))).toFloat()
    }

    class FrequencyPeak(
        val fftPassNumber: Int,
        val peakMagnitude: Int,
        val correctedFrequencyBin: Int,
    )

    fun generateSignature(pcmAudio: ByteArray, sampleRate: Int = 16000): String? {
        val numShorts = pcmAudio.size / 2
        if (numShorts < 2048) return null
        val s16Buffer = ShortArray(numShorts)
        ByteBuffer.wrap(pcmAudio).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s16Buffer)

        val peaks = extractPeaks(s16Buffer, sampleRate)
        val totalPeaks = peaks.sumOf { it.size }
        if (totalPeaks < 15) {
            return null
        }
        val rawSignature = encodeToBinary(peaks, numShorts, sampleRate)
        return Base64.encodeToString(rawSignature, Base64.NO_WRAP)
    }

    private fun extractPeaks(s16Buffer: ShortArray, sampleRate: Int): Array<MutableList<FrequencyPeak>> {
        val bands = Array(4) { mutableListOf<FrequencyPeak>() }

        val ringBuffer = ShortArray(2048)
        var ringBufferIndex = 0

        val reorderedRing = FloatArray(2048)
        val complexOutReal = FloatArray(2048)
        val complexOutImag = FloatArray(2048)

        val fftOutputs = Array(256) { FloatArray(NUM_BINS) }
        var fftOutputsIndex = 0

        val spreadOutputs = Array(256) { FloatArray(NUM_BINS) }
        var spreadOutputsIndex = 0
        var numSpreadDone = 0

        var offset = 0
        while (offset + STEP_SIZE <= s16Buffer.size) {
            // Copy 128 samples to ring buffer
            for (i in 0 until STEP_SIZE) {
                ringBuffer[(ringBufferIndex + i) and 2047] = s16Buffer[offset + i]
            }
            ringBufferIndex = (ringBufferIndex + STEP_SIZE) and 2047

            // Reorder and apply Hanning window
            for (i in 0 until 2048) {
                reorderedRing[i] = ringBuffer[(i + ringBufferIndex) and 2047].toFloat() * HANNING_WINDOW[i]
                complexOutReal[i] = reorderedRing[i]
                complexOutImag[i] = 0f
            }

            fft(complexOutReal, complexOutImag)

            val currentFft = fftOutputs[fftOutputsIndex]
            for (i in 0 until NUM_BINS) {
                val pwr = (complexOutReal[i] * complexOutReal[i] + complexOutImag[i] * complexOutImag[i]) / (1 shl 17).toFloat()
                currentFft[i] = pwr.coerceAtLeast(0.0000000001f)
            }
            fftOutputsIndex = (fftOutputsIndex + 1) and 255

            // Peak spreading
            val lastFft = fftOutputs[(fftOutputsIndex - 1) and 255]
            val currentSpread = spreadOutputs[spreadOutputsIndex]
            System.arraycopy(lastFft, 0, currentSpread, 0, NUM_BINS)

            for (pos in 0..1022) {
                val m = currentSpread[pos].coerceAtLeast(currentSpread[pos + 1]).coerceAtLeast(currentSpread[pos + 2])
                currentSpread[pos] = m
            }

            for (pos in 0..1024) {
                val spreadVal = currentSpread[pos]
                for (former in intArrayOf(1, 3, 6)) {
                    val formerFft = spreadOutputs[(spreadOutputsIndex - former) and 255]
                    formerFft[pos] = formerFft[pos].coerceAtLeast(spreadVal)
                }
            }

            spreadOutputsIndex = (spreadOutputsIndex + 1) and 255
            numSpreadDone++

            if (numSpreadDone >= 46) {
                val fftMinus46 = fftOutputs[(fftOutputsIndex - 46) and 255]
                val fftMinus49 = spreadOutputs[(spreadOutputsIndex - 49) and 255]

                for (bin in 10..1014) {
                    if (fftMinus46[bin] >= (1f / 64f) && fftMinus46[bin] >= fftMinus49[bin - 1]) {
                        var maxNeighbor = 0f
                        for (neighborOffset in intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)) {
                            val neighborBin = bin + neighborOffset
                            if (neighborBin in 0..1024) {
                                maxNeighbor = maxNeighbor.coerceAtLeast(fftMinus49[neighborBin])
                            }
                        }

                        if (fftMinus46[bin] > maxNeighbor) {
                            var maxOther = maxNeighbor
                            for (otherOffset in intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)) {
                                val otherFft = spreadOutputs[(spreadOutputsIndex + otherOffset) and 255]
                                maxOther = maxOther.coerceAtLeast(otherFft[bin - 1])
                            }

                            if (fftMinus46[bin] > maxOther) {
                                val passNum = numSpreadDone - 46
                                val mag = (ln(fftMinus46[bin].coerceAtLeast(1f / 64f).toDouble()) * 1477.3 + 6144.0).toFloat()
                                val magBefore = (ln(fftMinus46[bin - 1].coerceAtLeast(1f / 64f).toDouble()) * 1477.3 + 6144.0).toFloat()
                                val magAfter = (ln(fftMinus46[bin + 1].coerceAtLeast(1f / 64f).toDouble()) * 1477.3 + 6144.0).toFloat()

                                val var1 = mag * 2f - magBefore - magAfter
                                val var2 = if (var1 != 0f) (magAfter - magBefore) * 32f / var1 else 0f
                                val correctedBin = (bin * 64 + var2.toInt()).coerceIn(0, 65535)

                                val freqHz = correctedBin.toFloat() * (sampleRate.toFloat() / 2f / 1024f / 64f)

                                val bandIndex = when (freqHz.toInt()) {
                                    in 250..519 -> 0
                                    in 520..1449 -> 1
                                    in 1450..3499 -> 2
                                    in 3500..5500 -> 3
                                    else -> -1
                                }

                                if (bandIndex != -1) {
                                    bands[bandIndex].add(
                                        FrequencyPeak(
                                            fftPassNumber = passNum,
                                            peakMagnitude = mag.toInt().coerceIn(0, 65535),
                                            correctedFrequencyBin = correctedBin,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            offset += STEP_SIZE
        }

        return bands
    }

    private fun encodeToBinary(bands: Array<MutableList<FrequencyPeak>>, numSamples: Int, sampleRate: Int): ByteArray {
        val peakStream = ByteArrayOutputStream()

        for (bandIndex in 0 until 4) {
            val peaks = bands[bandIndex]
            if (peaks.isEmpty()) continue

            val bandBytes = ByteArrayOutputStream()
            var lastPass = 0

            for (peak in peaks) {
                val delta = peak.fftPassNumber - lastPass
                if (delta >= 255) {
                    bandBytes.write(0xFF)
                    val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(peak.fftPassNumber)
                    bandBytes.write(buf.array())
                    lastPass = peak.fftPassNumber
                }

                val currentDelta = (peak.fftPassNumber - lastPass).coerceIn(0, 254)
                bandBytes.write(currentDelta)

                val shortBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                shortBuf.putShort(peak.peakMagnitude.toShort())
                shortBuf.putShort(peak.correctedFrequencyBin.toShort())
                bandBytes.write(shortBuf.array())

                lastPass = peak.fftPassNumber
            }

            val peakData = bandBytes.toByteArray()
            val chunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            chunkHeader.putInt(0x60030040.toInt() + bandIndex)
            chunkHeader.putInt(peakData.size)
            peakStream.write(chunkHeader.array())
            peakStream.write(peakData)

            val padding = (4 - peakData.size % 4) % 4
            for (p in 0 until padding) {
                peakStream.write(0)
            }
        }

        val peaksPayload = peakStream.toByteArray()
        val totalSize = 48 + 8 + peaksPayload.size
        val sizeMinusHeader = totalSize - 48

        val bodyBuffer = ByteBuffer.allocate(totalSize - 8).order(ByteOrder.LITTLE_ENDIAN)
        bodyBuffer.putInt(sizeMinusHeader)
        bodyBuffer.putInt(0x94119c00.toInt())
        bodyBuffer.putInt(0) // void1
        bodyBuffer.putInt(0)
        bodyBuffer.putInt(0)
        bodyBuffer.putInt(3 shl 27) // 16000Hz rate ID
        bodyBuffer.putInt(0) // void2
        bodyBuffer.putInt(0)
        bodyBuffer.putInt(numSamples + (sampleRate * 0.24f).toInt())
        bodyBuffer.putInt((15 shl 19) + 0x40000) // 0x7c0000
        bodyBuffer.putInt(0x40000000)
        bodyBuffer.putInt(sizeMinusHeader)
        bodyBuffer.put(peaksPayload)

        val bodyBytes = bodyBuffer.array()
        val crc = CRC32()
        crc.update(bodyBytes)
        val crcValue = crc.value.toInt()

        val fullBuffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        fullBuffer.putInt(0xcafe2580.toInt())
        fullBuffer.putInt(crcValue)
        fullBuffer.put(bodyBytes)

        return fullBuffer.array()
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = 2048
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var l = 2
        while (l <= n) {
            val halfL = l shr 1
            val theta = -2.0 * Math.PI / l
            val cosTheta = cos(theta).toFloat()
            val sinTheta = sin(theta).toFloat()

            var i = 0
            while (i < n) {
                var wReal = 1.0f
                var wImag = 0.0f
                for (k in 0 until halfL) {
                    val idx1 = i + k
                    val idx2 = idx1 + halfL
                    val tempR = wReal * real[idx2] - wImag * imag[idx2]
                    val tempI = wReal * imag[idx2] + wImag * real[idx2]

                    real[idx2] = real[idx1] - tempR
                    imag[idx2] = imag[idx1] - tempI
                    real[idx1] += tempR
                    imag[idx1] += tempI

                    val nextWReal = wReal * cosTheta - wImag * sinTheta
                    wImag = wReal * sinTheta + wImag * cosTheta
                    wReal = nextWReal
                }
                i += l
            }
            l = l shl 1
        }
    }
}
