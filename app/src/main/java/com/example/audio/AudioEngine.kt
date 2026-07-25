package com.example.audio

import android.content.Context
import android.util.Log
import com.example.data.VocalFileEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object AudioEngine {
    private const val TAG = "AudioEngine"
    private const val TARGET_SAMPLE_RATE = 48000
    private const val CHANNELS_MONO = 1
    private const val CHANNELS_STEREO = 2
    private const val DEFAULT_EXPORT_BITS = 24

    enum class AudioType {
        BEAT,
        VOCAL
    }

    class WavData(
        val floatSamples: FloatArray,
        val numChannels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int
    ) {
        val samples: ShortArray
            get() = ShortArray(floatSamples.size) { i ->
                (floatSamples[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
            }
    }

    /**
     * Parse WAV files supporting 16-bit, 24-bit, and 32-bit (int or float) PCM,
     * arbitrary sample rates, and mono/stereo configurations.
     */
    fun parseWavPCM(file: File): WavData? {
        try {
            val bytes = file.readBytes()
            if (bytes.size < 44) return null

            // Validate RIFF header
            val riffHeader = String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)
            val waveHeader = String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
            if (riffHeader != "RIFF" || waveHeader != "WAVE") {
                Log.e(TAG, "Not a valid RIFF/WAVE file: $file")
                return null
            }

            var offset = 12
            var audioFormat = 1 // 1 = PCM, 3 = IEEE Float
            var numChannels = 2
            var sampleRate = 44100
            var bitsPerSample = 16
            var dataOffset = -1
            var dataSize = -1

            while (offset + 8 <= bytes.size) {
                val chunkId = String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

                if (chunkId == "fmt " && offset + 8 + 16 <= bytes.size) {
                    audioFormat = ByteBuffer.wrap(bytes, offset + 8, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                    numChannels = ByteBuffer.wrap(bytes, offset + 10, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                    sampleRate = ByteBuffer.wrap(bytes, offset + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample = ByteBuffer.wrap(bytes, offset + 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                } else if (chunkId == "data") {
                    dataOffset = offset + 8
                    dataSize = min(chunkSize, bytes.size - dataOffset)
                    break
                }

                val paddedChunkSize = if (chunkSize % 2 != 0) chunkSize + 1 else chunkSize
                offset += 8 + paddedChunkSize
            }

            if (dataOffset < 0 || dataSize <= 0) {
                // Fallback to 44-byte header if data chunk was not explicitly parsed
                dataOffset = 44
                dataSize = bytes.size - 44
                if (dataSize <= 0) return null
            }

            if (numChannels <= 0 || sampleRate <= 0) return null

            val bytesPerSample = bitsPerSample / 8
            if (bytesPerSample <= 0) return null

            val totalSamples = dataSize / bytesPerSample
            val floatSamples = FloatArray(totalSamples)

            val bb = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)

            when {
                bitsPerSample == 16 && (audioFormat == 1 || audioFormat == 65534) -> {
                    for (i in 0 until totalSamples) {
                        if (!bb.hasRemaining()) break
                        floatSamples[i] = bb.short.toFloat() / 32768.0f
                    }
                }
                bitsPerSample == 24 && (audioFormat == 1 || audioFormat == 65534) -> {
                    for (i in 0 until totalSamples) {
                        if (bb.remaining() < 3) break
                        val b0 = bb.get().toInt() and 0xFF
                        val b1 = bb.get().toInt() and 0xFF
                        val b2 = bb.get().toInt()
                        val raw = b0 or (b1 shl 8) or (b2 shl 16)
                        val signed = if ((raw and 0x800000) != 0) raw or 0xFF000000.toInt() else raw
                        floatSamples[i] = signed.toFloat() / 8388608.0f
                    }
                }
                bitsPerSample == 32 && audioFormat == 3 -> { // 32-bit IEEE Float
                    for (i in 0 until totalSamples) {
                        if (bb.remaining() < 4) break
                        floatSamples[i] = bb.float.coerceIn(-1.0f, 1.0f)
                    }
                }
                bitsPerSample == 32 && (audioFormat == 1 || audioFormat == 65534) -> { // 32-bit Int PCM
                    for (i in 0 until totalSamples) {
                        if (bb.remaining() < 4) break
                        floatSamples[i] = bb.int.toFloat() / 2147483648.0f
                    }
                }
                else -> {
                    Log.w(TAG, "Unsupported WAV format: format=$audioFormat, bits=$bitsPerSample")
                    return null
                }
            }

            return WavData(floatSamples, numChannels, sampleRate, bitsPerSample)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WAV file $file", e)
            return null
        }
    }

    /**
     * High-quality linear resampling between any sample rates without changing pitch or tempo
     */
    fun resample(
        input: FloatArray,
        numChannels: Int,
        srcRate: Int,
        targetRate: Int
    ): FloatArray {
        if (srcRate == targetRate || input.isEmpty() || numChannels <= 0) return input

        val inputFrames = input.size / numChannels
        val outputFrames = Math.round(inputFrames.toDouble() * targetRate / srcRate).toInt()
        if (outputFrames <= 0) return FloatArray(0)

        val output = FloatArray(outputFrames * numChannels)
        val ratio = srcRate.toDouble() / targetRate.toDouble()

        for (outFrame in 0 until outputFrames) {
            val srcPos = outFrame * ratio
            val srcIndex = srcPos.toInt()
            val frac = (srcPos - srcIndex).toFloat()

            val idx0 = srcIndex.coerceIn(0, inputFrames - 1)
            val idx1 = (srcIndex + 1).coerceIn(0, inputFrames - 1)

            for (ch in 0 until numChannels) {
                val s0 = input[idx0 * numChannels + ch]
                val s1 = input[idx1 * numChannels + ch]
                output[outFrame * numChannels + ch] = s0 + frac * (s1 - s0)
            }
        }
        return output
    }

    /**
     * Converts any multi-channel or mono float buffer to stereo
     */
    fun toStereo(input: FloatArray, numChannels: Int): FloatArray {
        if (numChannels == 2) return input
        if (numChannels == 1) {
            val output = FloatArray(input.size * 2)
            for (i in input.indices) {
                val s = input[i]
                output[i * 2] = s
                output[i * 2 + 1] = s
            }
            return output
        }
        val frames = input.size / numChannels
        val output = FloatArray(frames * 2)
        for (f in 0 until frames) {
            output[f * 2] = input[f * numChannels]
            output[f * 2 + 1] = input[f * numChannels + 1]
        }
        return output
    }

    /**
     * Converts stereo/multi-channel float buffer to mono by averaging
     */
    fun toMono(input: FloatArray, numChannels: Int): FloatArray {
        if (numChannels == 1) return input
        val frames = input.size / numChannels
        val output = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0.0f
            for (ch in 0 until numChannels) {
                sum += input[f * numChannels + ch]
            }
            output[f] = sum / numChannels
        }
        return output
    }

    fun extractWaveform(file: File, numBars: Int): FloatArray? {
        try {
            val wavData = parseWavPCM(file) ?: return null
            val samples = wavData.floatSamples
            if (samples.isEmpty()) return null

            val bars = FloatArray(numBars)
            val samplesPerBar = samples.size / numBars
            if (samplesPerBar == 0) return bars

            for (b in 0 until numBars) {
                var maxAmp = 0f
                val startIdx = b * samplesPerBar
                val endIdx = min(samples.size, startIdx + samplesPerBar)
                for (i in startIdx until endIdx) {
                    maxAmp = max(maxAmp, abs(samples[i]))
                }
                bars[b] = maxAmp.coerceIn(0.0f, 1.0f)
            }
            return bars
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting waveform", e)
            return null
        }
    }

    /**
     * Synthesizes a high-quality test Beat WAV file (90 BPM)
     */
    fun generateTestBeat(context: Context, outputFile: File): Double {
        val durationSec = 10.0
        val sampleRate = TARGET_SAMPLE_RATE
        val numSamples = (sampleRate * durationSec).toInt()
        val bpm = 90.0
        val beatIntervalSec = 60.0 / bpm
        val samplesPerBeat = (sampleRate * beatIntervalSec).toInt()

        val buffer = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val beatIndex = i / samplesPerBeat
            val sampleInBeat = i % samplesPerBeat

            val kickFreq = max(40.0, 150.0 * 2.0.pow(-sampleInBeat.toDouble() / 2500.0))
            val kickAmplitude = max(0.0, 1.0 - (sampleInBeat.toDouble() / 12000.0))
            val kick = sin(2.0 * Math.PI * kickFreq * (sampleInBeat.toDouble() / sampleRate)) * kickAmplitude

            var snare = 0.0
            if (beatIndex % 2 == 1 && sampleInBeat < 8000) {
                val noise = (Math.random() * 2.0 - 1.0)
                val snareDecay = max(0.0, 1.0 - (sampleInBeat.toDouble() / 8000.0))
                snare = noise * snareDecay * 0.45
            }

            var hihat = 0.0
            val eighthNoteSamples = samplesPerBeat / 2
            val sampleInEighth = i % eighthNoteSamples
            if (sampleInEighth < 1500) {
                val noise = (Math.random() * 2.0 - 1.0)
                val hatDecay = max(0.0, 1.0 - (sampleInEighth.toDouble() / 1500.0))
                hihat = noise * hatDecay * 0.15
            }

            var synth = 0.0
            if (beatIndex % 4 != 0 && sampleInBeat > eighthNoteSamples && sampleInBeat < eighthNoteSamples + 6000) {
                val noteFreq = when (beatIndex % 4) {
                    1 -> 220.0
                    2 -> 261.63
                    else -> 293.66
                }
                val synthT = (sampleInBeat - eighthNoteSamples).toDouble() / sampleRate
                val synthDecay = max(0.0, 1.0 - ((sampleInBeat - eighthNoteSamples).toDouble() / 6000.0))
                synth = sin(2.0 * Math.PI * noteFreq * synthT) * synthDecay * 0.25
            }

            val mix = (kick * 0.6) + snare + hihat + synth
            buffer[i] = mix.toFloat().coerceIn(-1.0f, 1.0f)
        }

        writeWavFile(outputFile, buffer, CHANNELS_MONO, sampleRate, DEFAULT_EXPORT_BITS)
        return bpm
    }

    /**
     * Synthesizes a high-quality test Vocal WAV file
     */
    fun generateTestVocal(context: Context, outputFile: File) {
        val durationSec = 10.0
        val sampleRate = TARGET_SAMPLE_RATE
        val numSamples = (sampleRate * durationSec).toInt()
        val bpm = 90.0
        val beatIntervalSec = 60.0 / bpm
        val samplesPerBeat = (sampleRate * beatIntervalSec).toInt()

        val buffer = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val beatIndex = i / samplesPerBeat
            val sampleInBeat = i % samplesPerBeat

            var voice = 0.0
            val sixteenthSamples = samplesPerBeat / 4
            val sampleInSixteenth = i % sixteenthSamples
            val sixteenthIndex = sampleInBeat / sixteenthSamples

            val isRest = (beatIndex % 4 == 3)

            if (!isRest && sixteenthIndex < 3 && sampleInSixteenth < sixteenthSamples * 0.8) {
                val baseFreq = when (sixteenthIndex) {
                    0 -> 140.0
                    1 -> 155.0
                    else -> 130.0
                }
                val voiceT = sampleInSixteenth.toDouble() / sampleRate
                val h1 = sin(2.0 * Math.PI * baseFreq * voiceT)
                val h2 = sin(2.0 * Math.PI * (baseFreq * 2.0) * voiceT) * 0.5
                val h3 = sin(2.0 * Math.PI * (baseFreq * 3.0) * voiceT) * 0.25
                val formant = (h1 + h2 + h3) / 1.75

                val volumeEnv = sin(Math.PI * (sampleInSixteenth.toDouble() / (sixteenthSamples * 0.8)))
                voice = formant * volumeEnv * 0.5

                if (sixteenthIndex == 2 && sampleInSixteenth > sixteenthSamples * 0.6) {
                    val noise = (Math.random() * 2.0 - 1.0)
                    val sEnv = (sampleInSixteenth - sixteenthSamples * 0.6).toDouble() / (sixteenthSamples * 0.2)
                    voice += noise * sEnv * 0.25
                }
            }

            val hum = sin(2.0 * Math.PI * 50.0 * (i.toDouble() / sampleRate)) * 0.06
            val hiss = (Math.random() * 2.0 - 1.0) * 0.025

            val totalMix = voice + hum + hiss
            buffer[i] = totalMix.toFloat().coerceIn(-1.0f, 1.0f)
        }

        writeWavFile(outputFile, buffer, CHANNELS_MONO, sampleRate, DEFAULT_EXPORT_BITS)
    }

    fun analyzeAudioType(file: File): AudioType {
        val nameLower = file.name.lowercase()
        if (nameLower.contains("beat") || nameLower.contains("instrumental") || nameLower.contains("hudba") || nameLower.contains("instr")) {
            return AudioType.BEAT
        }
        if (nameLower.contains("vokal") || nameLower.contains("vocal") || nameLower.contains("vox") || nameLower.contains("spiv") || nameLower.contains("rap")) {
            return AudioType.VOCAL
        }

        try {
            val wavData = parseWavPCM(file)
            if (wavData != null && wavData.floatSamples.isNotEmpty()) {
                val samples = wavData.floatSamples
                var lowEnergyCount = 0
                var sumAmp = 0.0
                var peakAmp = 0.0f

                val windowSize = 1000
                var currentWindowSum = 0.0

                for (i in samples.indices) {
                    val amp = abs(samples[i])
                    sumAmp += amp
                    if (amp > peakAmp) peakAmp = amp

                    currentWindowSum += amp
                    if (i % windowSize == 0) {
                        val avgWinAmp = currentWindowSum / windowSize
                        if (avgWinAmp < 0.01f) {
                            lowEnergyCount++
                        }
                        currentWindowSum = 0.0
                    }
                }

                if (lowEnergyCount > (samples.size / windowSize) * 0.15) {
                    return AudioType.VOCAL
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze audio signal, defaulting by name", e)
        }

        return AudioType.BEAT
    }

    class BiquadPeakingEQ(frequency: Float, sampleRate: Float, q: Float, gainDb: Float) {
        private val b0: Float
        private val b1: Float
        private val b2: Float
        private val a1: Float
        private val a2: Float

        private var x1 = 0.0f
        private var x2 = 0.0f
        private var y1 = 0.0f
        private var y2 = 0.0f

        init {
            val aVal = 10.0.pow((gainDb / 40.0)).toFloat()
            val w0 = (2.0f * Math.PI.toFloat() * frequency / sampleRate)
            val alpha = (sin(w0.toDouble()).toFloat() / (2.0f * q))
            val cosW0 = Math.cos(w0.toDouble()).toFloat()

            val rawB0 = 1.0f + alpha * aVal
            val rawB1 = -2.0f * cosW0
            val rawB2 = 1.0f - alpha * aVal
            val rawA0 = 1.0f + alpha / aVal
            val rawA1 = -2.0f * cosW0
            val rawA2 = 1.0f - alpha / aVal

            b0 = rawB0 / rawA0
            b1 = rawB1 / rawA0
            b2 = rawB2 / rawA0
            a1 = rawA1 / rawA0
            a2 = rawA2 / rawA0
        }

        fun process(sample: Float): Float {
            val y0 = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = sample
            y2 = y1
            y1 = y0
            return y0
        }
    }

    fun detectBPM(file: File): Double {
        try {
            val wavData = parseWavPCM(file) ?: return 90.0
            val monoSamples = toMono(wavData.floatSamples, wavData.numChannels)
            val sampleRate = wavData.sampleRate

            if (sampleRate <= 0 || monoSamples.size < sampleRate * 3) return 90.0

            val skipSamples = sampleRate * 3
            val startOffset = if (monoSamples.size > skipSamples + sampleRate * 8) skipSamples else 0
            val secondsToAnalyze = 10
            val samplesToAnalyze = min(monoSamples.size - startOffset, sampleRate * secondsToAnalyze)

            if (samplesToAnalyze <= 1000) return 90.0

            val windowSize = 1024
            val numWindows = samplesToAnalyze / windowSize
            if (numWindows < 10) return 90.0
            val energies = DoubleArray(numWindows)

            for (w in 0 until numWindows) {
                var sum = 0.0
                for (s in 0 until windowSize) {
                    val idx = startOffset + w * windowSize + s
                    val amp = monoSamples[idx].toDouble()
                    sum += amp * amp
                }
                energies[w] = sum / windowSize
            }

            val smoothed = DoubleArray(numWindows)
            for (w in 1 until numWindows - 1) {
                smoothed[w] = (energies[w - 1] + energies[w] + energies[w + 1]) / 3.0
            }

            val peakIndices = mutableListOf<Int>()
            val thresholdMultiplier = 1.25
            for (w in 8 until numWindows - 8) {
                var localSum = 0.0
                for (offset in -8..8) {
                    localSum += smoothed[w + offset]
                }
                val localAvgEnergy = localSum / 17.0

                if (smoothed[w] > localAvgEnergy * thresholdMultiplier &&
                    smoothed[w] > smoothed[w - 1] && smoothed[w] > smoothed[w + 1]
                ) {
                    peakIndices.add(w)
                }
            }

            if (peakIndices.size >= 3) {
                val intervals = mutableListOf<Int>()
                for (i in 0 until peakIndices.size - 1) {
                    for (j in i + 1 until min(peakIndices.size, i + 5)) {
                        val diff = peakIndices[j] - peakIndices[i]
                        if (diff in 10..65) {
                            intervals.add(diff)
                        }
                    }
                }

                if (intervals.isNotEmpty()) {
                    val histogram = IntArray(100)
                    for (interval in intervals) {
                        if (interval in histogram.indices) {
                            histogram[interval]++
                            if (interval + 1 in histogram.indices) histogram[interval + 1]++
                            if (interval - 1 in histogram.indices) histogram[interval - 1]++
                        }
                    }

                    var bestInterval = 0
                    var maxCount = -1
                    for (i in histogram.indices) {
                        if (histogram[i] > maxCount) {
                            maxCount = histogram[i]
                            bestInterval = i
                        }
                    }

                    if (bestInterval > 0) {
                        val intervalSec = bestInterval * (windowSize.toDouble() / sampleRate)
                        val detectedBpm = 60.0 / intervalSec

                        var finalBpm = detectedBpm
                        while (finalBpm < 70.0) finalBpm *= 2.0
                        while (finalBpm > 145.0) finalBpm /= 2.0

                        return Math.round(finalBpm).toDouble()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during BPM detection", e)
        }
        return 90.0
    }

    /**
     * Applies vocal processing chain at standard 48 kHz high fidelity rate
     */
    fun processVocal(
        inputFile: File,
        outputFile: File,
        settings: VocalFileEntity
    ): Boolean {
        try {
            val wavData = parseWavPCM(inputFile) ?: return false
            var floatSamples = toMono(wavData.floatSamples, wavData.numChannels)
            
            // Resample to 48000 Hz processing rate if necessary
            val sampleRate = TARGET_SAMPLE_RATE
            if (wavData.sampleRate != sampleRate) {
                floatSamples = resample(floatSamples, CHANNELS_MONO, wavData.sampleRate, sampleRate)
            }

            // === 1. ODSTRANĚNÍ HLUKU / BRUMU (De-hum Notch Filter) ===
            if (settings.isHumRemovalEnabled) {
                val humFreq = settings.humRemovalFrequencyHz
                val w0 = 2.0f * Math.PI.toFloat() * humFreq / sampleRate
                val q = 15.0f
                val alpha = sin(w0.toDouble()).toFloat() / (2.0f * q)

                val b0 = 1.0f
                val b1 = -2.0f * Math.cos(w0.toDouble()).toFloat()
                val b2 = 1.0f
                val a0 = 1.0f + alpha
                val a1 = -2.0f * Math.cos(w0.toDouble()).toFloat()
                val a2 = 1.0f - alpha

                val nb0 = b0 / a0
                val nb1 = b1 / a0
                val nb2 = b2 / a0
                val na1 = a1 / a0
                val na2 = a2 / a0

                var x1 = 0.0f
                var x2 = 0.0f
                var y1 = 0.0f
                var y2 = 0.0f

                for (n in floatSamples.indices) {
                    val x0 = floatSamples[n]
                    val y0 = nb0 * x0 + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
                    x2 = x1
                    x1 = x0
                    y2 = y1
                    y1 = y0
                    floatSamples[n] = y0
                }
            }

            // === 2. ODSTRANĚNÍ ŠUMU (Noise Gate / Expander) ===
            if (settings.isNoiseGateEnabled) {
                val gateThreshold = 10.0f.pow(settings.noiseGateThresholdDb / 20.0f)
                val releaseCoeff = Math.exp(-1.0 / (sampleRate * (settings.noiseGateReleaseMs / 1000.0))).toFloat()

                var envelope = 0.0f
                val windowSize = 256
                for (i in floatSamples.indices step windowSize) {
                    val limit = min(floatSamples.size, i + windowSize)
                    var peak = 0.0f
                    for (k in i until limit) {
                        peak = max(peak, abs(floatSamples[k]))
                    }

                    if (peak > gateThreshold) {
                        envelope = 1.0f
                    } else {
                        envelope *= releaseCoeff
                    }

                    for (k in i until limit) {
                        floatSamples[k] *= envelope
                    }
                }
            }

            // === 3. ODSTRANĚNÍ OZVĚNY (De-reverb / Expander) ===
            if (settings.isEchoRemovalEnabled) {
                val attenuationFactor = 10.0f.pow(settings.echoRemovalAttenuationDb / 20.0f)
                var runningPeak = 0.0f
                val decayRate = 0.9999f
                for (n in floatSamples.indices) {
                    val inputAbs = abs(floatSamples[n])
                    if (inputAbs > runningPeak) {
                        runningPeak = inputAbs
                    } else {
                        runningPeak *= decayRate
                    }

                    if (runningPeak > 0.01f && inputAbs < runningPeak * 0.1f) {
                        floatSamples[n] *= attenuationFactor
                    }
                }
            }

            // === 4. NORMALIZACE HLASITOSTI ===
            if (settings.isNormalizedEnabled) {
                var absoluteMax = 0.0f
                for (n in floatSamples.indices) {
                    absoluteMax = max(absoluteMax, abs(floatSamples[n]))
                }

                if (absoluteMax > 0.0f) {
                    val targetAmplitude = 10.0f.pow(settings.normaliseTargetDb / 20.0f)
                    val normFactor = targetAmplitude / absoluteMax
                    for (n in floatSamples.indices) {
                        floatSamples[n] *= normFactor
                    }
                }
            }

            // === 5. DE-ESSER (Sibilance Control) ===
            if (settings.isDeEsserEnabled) {
                val deEsserThreshold = 10.0f.pow(settings.deEsserThresholdDb / 20.0f)
                var prevSample = 0.0f
                for (n in floatSamples.indices) {
                    val hp = floatSamples[n] - prevSample
                    prevSample = floatSamples[n]

                    if (abs(hp) > deEsserThreshold) {
                        floatSamples[n] *= 0.65f
                    }
                }
            }

            // === 6. EQ (HPF, Boxy Cut, Presence, Air) ===
            if (settings.isEqEnabled) {
                var lastIn = 0.0f
                var lastOut = 0.0f
                val hpfCutoff = settings.eqHighPassHz
                val dt = 1.0f / sampleRate
                val RC = 1.0f / (2.0f * Math.PI.toFloat() * hpfCutoff)
                val alpha = RC / (RC + dt)

                for (n in floatSamples.indices) {
                    val input = floatSamples[n]
                    val output = alpha * (lastOut + input - lastIn)
                    lastIn = input
                    lastOut = output
                    floatSamples[n] = output
                }

                val presenceGain = 10.0f.pow(settings.eqHighMidBoostDb / 20.0f) - 1.0f
                val airGain = 10.0f.pow(settings.eqHighShelfDb / 20.0f) - 1.0f
                val boxyReduction = 10.0f.pow(settings.eqLowMidCutDb / 20.0f)

                var e1 = 0.0f
                var e2 = 0.0f
                for (n in floatSamples.indices) {
                    val curr = floatSamples[n]
                    val highFreqs = curr - e1
                    val airFreqs = curr - e2

                    floatSamples[n] = curr * boxyReduction + (highFreqs * presenceGain * 0.4f) + (airFreqs * airGain * 0.3f)

                    e1 = e1 * 0.85f + curr * 0.15f
                    e2 = e2 * 0.95f + curr * 0.05f
                }
            }

            // === 7. KOMPRESE (Dynamic Range Compressor) ===
            if (settings.isCompressionEnabled) {
                val threshold = 10.0f.pow(settings.compressionThresholdDb / 20.0f)
                val ratio = settings.compressionRatio
                val attackCoeff = Math.exp(-1.0 / (sampleRate * (settings.compressionAttackMs / 1000.0))).toFloat()
                val releaseCoeff = Math.exp(-1.0 / (sampleRate * (settings.compressionReleaseMs / 1000.0))).toFloat()

                var envelope = 0.0f
                for (n in floatSamples.indices) {
                    val inputAbs = abs(floatSamples[n])

                    if (inputAbs > envelope) {
                        envelope = attackCoeff * envelope + (1.0f - attackCoeff) * inputAbs
                    } else {
                        envelope = releaseCoeff * envelope + (1.0f - releaseCoeff) * inputAbs
                    }

                    if (envelope > threshold && envelope > 0.0f) {
                        val gainDb = 20.0f * Math.log10(envelope.toDouble()).toFloat()
                        val threshDb = settings.compressionThresholdDb
                        val compressedGainDb = threshDb + (gainDb - threshDb) / ratio
                        val targetGain = 10.0f.pow(compressedGainDb / 20.0f) / envelope
                        floatSamples[n] *= targetGain
                    }
                }
            }

            // === 8 & 9. STEREORIZER & LIMITER ===
            var outChannels = CHANNELS_MONO
            var finalSamples = floatSamples

            if (settings.isStereorizerEnabled) {
                outChannels = CHANNELS_STEREO
                val delaySamples = (sampleRate * (settings.stereorizerDelayMs / 1000.0)).toInt()
                val stereoBuffer = FloatArray(floatSamples.size * 2)

                for (n in floatSamples.indices) {
                    stereoBuffer[n * 2] = floatSamples[n]
                    val delayedIndex = n - delaySamples
                    stereoBuffer[n * 2 + 1] = if (delayedIndex >= 0) floatSamples[delayedIndex] else 0.0f
                }
                finalSamples = stereoBuffer
            }

            val limitThreshold = if (settings.isLimiterEnabled) {
                10.0f.pow(settings.limiterThresholdDb / 20.0f)
            } else 0.98f
            val limitCeiling = if (settings.isLimiterEnabled) {
                10.0f.pow(settings.limiterCeilingDb / 20.0f)
            } else 0.98f

            for (n in finalSamples.indices) {
                var value = finalSamples[n]
                if (abs(value) > limitThreshold) {
                    value = Math.signum(value) * limitThreshold
                }
                finalSamples[n] = value.coerceIn(-limitCeiling, limitCeiling)
            }

            writeWavFile(outputFile, finalSamples, outChannels, sampleRate, DEFAULT_EXPORT_BITS)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process vocal track", e)
            return false
        }
    }

    /**
     * Mixes beat and vocal files into a master 24-bit / 48 kHz stereo WAV file with automatic pocket EQ carving and peak limiting.
     */
    fun mixProject(
        beatFile: File,
        vocalFiles: List<Pair<File, VocalFileEntity>>,
        outputFile: File
    ): Boolean {
        try {
            val beatWav = parseWavPCM(beatFile) ?: return false
            var beatStereo = toStereo(beatWav.floatSamples, beatWav.numChannels)

            val masterSampleRate = TARGET_SAMPLE_RATE
            if (beatWav.sampleRate != masterSampleRate) {
                beatStereo = resample(beatStereo, CHANNELS_STEREO, beatWav.sampleRate, masterSampleRate)
            }

            val numStereoSamples = beatStereo.size / 2
            val mixedL = FloatArray(numStereoSamples)
            val mixedR = FloatArray(numStereoSamples)

            for (i in 0 until numStereoSamples) {
                mixedL[i] = beatStereo[i * 2]
                mixedR[i] = beatStereo[i * 2 + 1]
            }

            // Apply Auto-EQ on beat at 48000 Hz to carve space for vocal presence (-3dB at 250Hz, -4.5dB at 1800Hz)
            val beatEq1L = BiquadPeakingEQ(250f, masterSampleRate.toFloat(), 1.0f, -3.0f)
            val beatEq1R = BiquadPeakingEQ(250f, masterSampleRate.toFloat(), 1.0f, -3.0f)
            val beatEq2L = BiquadPeakingEQ(1800f, masterSampleRate.toFloat(), 1.0f, -4.5f)
            val beatEq2R = BiquadPeakingEQ(1800f, masterSampleRate.toFloat(), 1.0f, -4.5f)

            for (i in 0 until numStereoSamples) {
                mixedL[i] = beatEq2L.process(beatEq1L.process(mixedL[i]))
                mixedR[i] = beatEq2R.process(beatEq1R.process(mixedR[i]))
            }

            val parsedVocals = vocalFiles.mapNotNull { (file, entity) ->
                val vWav = parseWavPCM(file) ?: return@mapNotNull null
                var vMono = toMono(vWav.floatSamples, vWav.numChannels)
                if (vWav.sampleRate != masterSampleRate) {
                    vMono = resample(vMono, CHANNELS_MONO, vWav.sampleRate, masterSampleRate)
                }
                Triple(vMono, entity, file.length())
            }

            val isDuplicate = BooleanArray(parsedVocals.size)
            for (i in parsedVocals.indices) {
                for (j in i + 1 until parsedVocals.size) {
                    val lenDiff = abs(parsedVocals[i].third - parsedVocals[j].third)
                    if (lenDiff < 1000) {
                        isDuplicate[i] = true
                        isDuplicate[j] = true
                    }
                }
            }

            for (idx in parsedVocals.indices) {
                val (vSamples, entity, _) = parsedVocals[idx]

                var currentVolume = entity.volume
                var currentPan = entity.panning
                var currentDelaySamples = (masterSampleRate * (entity.offsetMs / 1000.0)).toInt()

                if (isDuplicate[idx]) {
                    if (!entity.isMajor) {
                        currentPan = -0.75f
                        currentVolume *= 0.65f
                        currentDelaySamples += (masterSampleRate * 0.022).toInt()
                    } else {
                        currentPan = 0.75f
                        currentVolume *= 0.9f
                    }
                }

                for (vIdx in vSamples.indices) {
                    val targetMixedIdx = vIdx + currentDelaySamples
                    if (targetMixedIdx >= numStereoSamples) break
                    if (targetMixedIdx < 0) continue

                    val sampleValue = vSamples[vIdx] * currentVolume
                    val panFactor = (currentPan + 1.0f) * (Math.PI.toFloat() / 4.0f)
                    val leftGain = Math.cos(panFactor.toDouble()).toFloat()
                    val rightGain = Math.sin(panFactor.toDouble()).toFloat()

                    mixedL[targetMixedIdx] += sampleValue * leftGain
                    mixedR[targetMixedIdx] += sampleValue * rightGain
                }
            }

            var masterMax = 0.0f
            for (i in 0 until numStereoSamples) {
                masterMax = max(masterMax, abs(mixedL[i]))
                masterMax = max(masterMax, abs(mixedR[i]))
            }

            val masterFloat = FloatArray(numStereoSamples * 2)
            val scaleFactor = if (masterMax > 0.95f) 0.95f / masterMax else 1.0f

            for (i in 0 until numStereoSamples) {
                val clampedL = (mixedL[i] * scaleFactor).coerceIn(-0.98f, 0.98f)
                val clampedR = (mixedR[i] * scaleFactor).coerceIn(-0.98f, 0.98f)
                masterFloat[i * 2] = clampedL
                masterFloat[i * 2 + 1] = clampedR
            }

            writeWavFile(outputFile, masterFloat, CHANNELS_STEREO, masterSampleRate, DEFAULT_EXPORT_BITS)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error mixing project", e)
            return false
        }
    }

    /**
     * Utility method to write a FloatArray WAV file (16-bit or 24-bit PCM)
     */
    fun writeWavFile(
        file: File,
        floatSamples: FloatArray,
        numChannels: Int,
        sampleRate: Int = TARGET_SAMPLE_RATE,
        bitsPerSample: Int = DEFAULT_EXPORT_BITS
    ) {
        val bytesPerSample = bitsPerSample / 8
        val numFrames = floatSamples.size / numChannels
        val audioDataLen = numFrames * numChannels * bytesPerSample
        val totalDataLen = audioDataLen + 36
        val byteRate = sampleRate * numChannels * bytesPerSample
        val blockAlign = numChannels * bytesPerSample

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0 // PCM
        header[22] = numChannels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = blockAlign.toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (audioDataLen and 0xff).toByte()
        header[41] = ((audioDataLen shr 8) and 0xff).toByte()
        header[42] = ((audioDataLen shr 16) and 0xff).toByte()
        header[43] = ((audioDataLen shr 24) and 0xff).toByte()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val buffer = ByteBuffer.allocate(audioDataLen).order(ByteOrder.LITTLE_ENDIAN)
            if (bitsPerSample == 24) {
                for (sample in floatSamples) {
                    val clamped = sample.coerceIn(-1.0f, 1.0f)
                    val valInt = (clamped * 8388607.0f).toInt()
                    buffer.put((valInt and 0xFF).toByte())
                    buffer.put(((valInt shr 8) and 0xFF).toByte())
                    buffer.put(((valInt shr 16) and 0xFF).toByte())
                }
            } else {
                for (sample in floatSamples) {
                    val clamped = sample.coerceIn(-1.0f, 1.0f)
                    val valShort = (clamped * 32767.0f).toInt().toShort()
                    buffer.putShort(valShort)
                }
            }
            fos.write(buffer.array())
        }
    }

    /**
     * Backward-compatible helper to write ShortArray WAV file
     */
    fun writeWavFile(
        file: File,
        pcmData: ShortArray,
        numChannels: Int,
        sampleRate: Int = TARGET_SAMPLE_RATE
    ) {
        val floatSamples = FloatArray(pcmData.size) { i -> pcmData[i].toFloat() / 32768.0f }
        writeWavFile(file, floatSamples, numChannels, sampleRate, 16)
    }
}
