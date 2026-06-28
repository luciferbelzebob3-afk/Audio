package com.example.audio

import android.content.Context
import android.util.Log
import com.example.data.VocalFileEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object AudioEngine {
    private const val TAG = "AudioEngine"
    private const val SAMPLE_RATE = 44100
    private const val CHANNELS_MONO = 1
    private const val CHANNELS_STEREO = 2
    private const val BITS_PER_SAMPLE_16 = 16

    enum class AudioType {
        BEAT,
        VOCAL
    }

    class WavData(val samples: ShortArray, val numChannels: Int, val sampleRate: Int, val bitsPerSample: Int)

    fun parseWavPCM(file: File): WavData? {
        try {
            val bytes = file.readBytes()
            if (bytes.size < 44) return null
            
            // Find "data" chunk
            var offset = 12 // skip RIFF + size + WAVE
            var numChannels = 2
            var sampleRate = 44100
            var bitsPerSample = 16
            
            while (offset + 8 <= bytes.size) {
                val chunkId = String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                
                if (chunkId == "fmt ") {
                    numChannels = ByteBuffer.wrap(bytes, offset + 8 + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                    sampleRate = ByteBuffer.wrap(bytes, offset + 8 + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample = ByteBuffer.wrap(bytes, offset + 8 + 14, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                } else if (chunkId == "data") {
                    val dataOffset = offset + 8
                    val dataSize = min(chunkSize, bytes.size - dataOffset)
                    if (dataSize <= 0) return null
                    
                    val shortBuffer = ShortArray(dataSize / 2)
                    ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer)
                    
                    return WavData(shortBuffer, numChannels, sampleRate, bitsPerSample)
                }
                offset += 8 + chunkSize
            }
            
            // Fallback if not found or malformed: assume 44-byte header
            val dataSize = bytes.size - 44
            if (dataSize <= 0) return null
            val shortBuffer = ShortArray(dataSize / 2)
            ByteBuffer.wrap(bytes, 44, dataSize).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer)
            return WavData(shortBuffer, 2, 44100, 16)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WAV file $file", e)
            return null
        }
    }

    /**
     * Synthesizes a high-quality test Beat WAV file (90 BPM)
     */
    fun generateTestBeat(context: Context, outputFile: File): Double {
        val durationSec = 10.0
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val bpm = 90.0
        val beatIntervalSec = 60.0 / bpm
        val samplesPerBeat = (SAMPLE_RATE * beatIntervalSec).toInt()

        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val beatIndex = i / samplesPerBeat
            val sampleInBeat = i % samplesPerBeat

            // Heavy sub-bass kick drum at start of every beat
            val kickFreq = max(40.0, 150.0 * 2.0.pow(-sampleInBeat.toDouble() / 2500.0))
            val kickAmplitude = max(0.0, 1.0 - (sampleInBeat.toDouble() / 12000.0))
            val kick = sin(2.0 * Math.PI * kickFreq * (sampleInBeat.toDouble() / SAMPLE_RATE)) * kickAmplitude

            // Snare on beat 1 and 3 (every alternate beat)
            var snare = 0.0
            if (beatIndex % 2 == 1 && sampleInBeat < 8000) {
                // Noise burst for snare
                val noise = (Math.random() * 2.0 - 1.0)
                val snareDecay = max(0.0, 1.0 - (sampleInBeat.toDouble() / 8000.0))
                snare = noise * snareDecay * 0.45
            }

            // Hi-hat on eighth notes
            var hihat = 0.0
            val eighthNoteSamples = samplesPerBeat / 2
            val sampleInEighth = i % eighthNoteSamples
            if (sampleInEighth < 1500) {
                val noise = (Math.random() * 2.0 - 1.0)
                val hatDecay = max(0.0, 1.0 - (sampleInEighth.toDouble() / 1500.0))
                hihat = noise * hatDecay * 0.15
            }

            // Add simple synth melody on off-beats
            var synth = 0.0
            if (beatIndex % 4 != 0 && sampleInBeat > eighthNoteSamples && sampleInBeat < eighthNoteSamples + 6000) {
                val noteFreq = when (beatIndex % 4) {
                    1 -> 220.0  // A3
                    2 -> 261.63 // C4
                    else -> 293.66 // D4
                }
                val synthT = (sampleInBeat - eighthNoteSamples).toDouble() / SAMPLE_RATE
                val synthDecay = max(0.0, 1.0 - ((sampleInBeat - eighthNoteSamples).toDouble() / 6000.0))
                synth = sin(2.0 * Math.PI * noteFreq * synthT) * synthDecay * 0.25
            }

            val mix = (kick * 0.6) + snare + hihat + synth
            val clamped = max(-1.0, min(1.0, mix))
            buffer[i] = (clamped * 32767).toInt().toShort()
        }

        writeWavFile(outputFile, buffer, CHANNELS_MONO)
        return bpm
    }

    /**
     * Synthesizes a high-quality test Vocal WAV file (contains voice phrases, hum, sibilance, noise)
     */
    fun generateTestVocal(context: Context, outputFile: File) {
        val durationSec = 10.0
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val bpm = 90.0
        val beatIntervalSec = 60.0 / bpm
        val samplesPerBeat = (SAMPLE_RATE * beatIntervalSec).toInt()

        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val beatIndex = i / samplesPerBeat
            val sampleInBeat = i % samplesPerBeat

            // Generate vocal-like synthesized formant wave only during certain parts of each beat
            // This simulates standard rap delivery (syllables on sixteenth notes with pauses)
            var voice = 0.0
            val sixteenthSamples = samplesPerBeat / 4
            val sampleInSixteenth = i % sixteenthSamples
            val sixteenthIndex = sampleInBeat / sixteenthSamples

            // Rest on last beat of 4 beats
            val isRest = (beatIndex % 4 == 3)

            if (!isRest && sixteenthIndex < 3 && sampleInSixteenth < sixteenthSamples * 0.8) {
                // Vocal simulation (formant-like rich triangle wave modulated by subharmonics)
                val baseFreq = when (sixteenthIndex) {
                    0 -> 140.0 // Pitch variation
                    1 -> 155.0
                    else -> 130.0
                }
                val voiceT = sampleInSixteenth.toDouble() / SAMPLE_RATE
                // Add first 3 harmonics to simulate human voice formant
                val h1 = sin(2.0 * Math.PI * baseFreq * voiceT)
                val h2 = sin(2.0 * Math.PI * (baseFreq * 2.0) * voiceT) * 0.5
                val h3 = sin(2.0 * Math.PI * (baseFreq * 3.0) * voiceT) * 0.25
                val formant = (h1 + h2 + h3) / 1.75

                // Syllable volume envelope
                val volumeEnv = sin(Math.PI * (sampleInSixteenth.toDouble() / (sixteenthSamples * 0.8)))
                voice = formant * volumeEnv * 0.5

                // Add simulated sibilant "S" at the end of some words
                if (sixteenthIndex == 2 && sampleInSixteenth > sixteenthSamples * 0.6) {
                    val noise = (Math.random() * 2.0 - 1.0)
                    val sEnv = (sampleInSixteenth - sixteenthSamples * 0.6).toDouble() / (sixteenthSamples * 0.2)
                    voice += noise * sEnv * 0.25 // Strong harsh high frequency sibilance!
                }
            }

            // ADD UNWANTED NOISE AND HUM FOR THE FILTER TESTING!
            // 50 Hz European AC ground loop hum
            val hum = sin(2.0 * Math.PI * 50.0 * (i.toDouble() / SAMPLE_RATE)) * 0.06

            // Continuous background hiss/white noise
            val hiss = (Math.random() * 2.0 - 1.0) * 0.025

            // Total vocal signal before processing
            val totalMix = voice + hum + hiss
            val clamped = max(-1.0, min(1.0, totalMix))
            buffer[i] = (clamped * 32767).toInt().toShort()
        }

        writeWavFile(outputFile, buffer, CHANNELS_MONO)
    }

    /**
     * Determines whether the selected audio file is VOCAL or BEAT
     */
    fun analyzeAudioType(file: File): AudioType {
        val nameLower = file.name.lowercase()
        if (nameLower.contains("beat") || nameLower.contains("instrumental") || nameLower.contains("hudba") || nameLower.contains("instr")) {
            return AudioType.BEAT
        }
        if (nameLower.contains("vokal") || nameLower.contains("vocal") || nameLower.contains("vox") || nameLower.contains("spiv") || nameLower.contains("rap")) {
            return AudioType.VOCAL
        }

        // Spectral and dynamic analysis if it's a valid WAV file
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(44)
                if (fis.read(header) == 44) {
                    val pcmBytes = ByteArray(100000) // Read about 50k samples
                    val readBytes = fis.read(pcmBytes)
                    if (readBytes > 40) {
                        val samples = ShortArray(readBytes / 2)
                        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

                        // 1. Check for silent passages (vocals have sections of zero amplitude)
                        var lowEnergyCount = 0
                        var highTransientCount = 0
                        var sumAmp = 0.0
                        var peakAmp = 0

                        val windowSize = 1000
                        var currentWindowSum = 0.0

                        for (i in samples.indices) {
                            val amp = abs(samples[i].toInt())
                            sumAmp += amp
                            if (amp > peakAmp) peakAmp = amp

                            currentWindowSum += amp
                            if (i % windowSize == 0) {
                                val avgWinAmp = currentWindowSum / windowSize
                                if (avgWinAmp < 300) { // Very quiet
                                    lowEnergyCount++
                                }
                                currentWindowSum = 0.0
                            }
                        }

                        val averageAmp = sumAmp / samples.size
                        val crestFactor = if (averageAmp > 0) peakAmp / averageAmp else 0.0

                        Log.d(TAG, "Audio Analysis - Average: $averageAmp, Peak: $peakAmp, Crest: $crestFactor, Quiet Windows: $lowEnergyCount")

                        // Beats have massive, regular peak transients (crest factor high, but low silent windows)
                        // Vocals have high silence count because they rest between lines
                        if (lowEnergyCount > (samples.size / windowSize) * 0.15) {
                            return AudioType.VOCAL
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze audio signal, defaulting by name", e)
        }

        return AudioType.BEAT
    }

    /**
     * Helper Biquad Peak Filter (Bell EQ) used to automatically carve space in instrumental beats for vocals.
     */
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
            val aVal = Math.pow(10.0, (gainDb / 40.0)).toFloat()
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

    /**
     * Accurately detects the BPM of a beat track using transient-energy pulse peaks
     */
    fun detectBPM(file: File): Double {
        try {
            val wavData = parseWavPCM(file) ?: return 90.0
            val samples = wavData.samples
            val channels = wavData.numChannels
            val sampleRate = wavData.sampleRate

            if (sampleRate <= 0 || channels <= 0) return 90.0

            val samplesPerSec = sampleRate * channels
            val skipSamples = samplesPerSec * 3 // Skip first 3 seconds
            
            val startSampleOffset = if (samples.size > skipSamples + samplesPerSec * 8) {
                skipSamples
            } else {
                0
            }
            
            val secondsToAnalyze = 10
            val samplesToAnalyze = min(samples.size - startSampleOffset, samplesPerSec * secondsToAnalyze)
            if (samplesToAnalyze <= 1000) return 90.0

            // Convert samples to mono float samples (-1.0 to 1.0)
            val totalFrames = samplesToAnalyze / channels
            val monoSamples = FloatArray(totalFrames)

            for (f in 0 until totalFrames) {
                var sum = 0.0f
                for (c in 0 until channels) {
                    sum += samples[startSampleOffset + f * channels + c].toFloat() / 32768.0f
                }
                monoSamples[f] = sum / channels
            }

            // Now compute energy envelope in windows of 1024 samples (approx 23ms at 44.1kHz)
            val windowSize = 1024
            val numWindows = monoSamples.size / windowSize
            if (numWindows < 10) return 90.0
            val energies = DoubleArray(numWindows)

            for (w in 0 until numWindows) {
                var sum = 0.0
                for (s in 0 until windowSize) {
                    val idx = w * windowSize + s
                    val amp = monoSamples[idx].toDouble()
                    sum += amp * amp
                }
                energies[w] = sum / windowSize
            }

            // Smooth energies
            val smoothed = DoubleArray(numWindows)
            for (w in 1 until numWindows - 1) {
                smoothed[w] = (energies[w-1] + energies[w] + energies[w+1]) / 3.0
            }

            // Peak/Onset detection using dynamic thresholding
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
                // Collect all reasonable peak intervals (representing beat/sub-beat durations)
                val intervals = mutableListOf<Int>()
                for (i in 0 until peakIndices.size - 1) {
                    for (j in i + 1 until min(peakIndices.size, i + 5)) { // look at immediate neighbors
                        val diff = peakIndices[j] - peakIndices[i]
                        if (diff in 10..65) {
                            intervals.add(diff)
                        }
                    }
                }

                if (intervals.isNotEmpty()) {
                    // Find the most frequent interval (mode) using a histogram bucket
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

                        // Standardize BPM within common musical ranges (70 to 145 BPM)
                        var finalBpm = detectedBpm
                        while (finalBpm < 70.0) finalBpm *= 2.0
                        while (finalBpm > 145.0) finalBpm /= 2.0

                        // Round to nearest integer BPM for cleanliness
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
     * Applies the 9-stage Rap vocal processing chain
     */
    fun processVocal(
        inputFile: File,
        outputFile: File,
        settings: VocalFileEntity
    ): Boolean {
        try {
            val wavData = parseWavPCM(inputFile) ?: return false
            val shortBuffer = wavData.samples
            
            // Convert to Float array (-1.0 to 1.0) and downmix stereo to mono if needed
            var floatSamples = if (wavData.numChannels == 2) {
                FloatArray(shortBuffer.size / 2) { i ->
                    (shortBuffer[i * 2].toFloat() + shortBuffer[i * 2 + 1].toFloat()) / 65536.0f
                }
            } else {
                FloatArray(shortBuffer.size) { i ->
                    shortBuffer[i].toFloat() / 32768.0f
                }
            }

            // === 1. ODSTRANĚNÍ HLUKU / BRUMU (De-hum Notch Filter) ===
            if (settings.isHumRemovalEnabled) {
                val humFreq = settings.humRemovalFrequencyHz
                // Implementation of a 2nd order IIR Notch filter for 50Hz/60Hz
                val w0 = 2.0f * Math.PI.toFloat() * humFreq / SAMPLE_RATE
                val q = 15.0f // Narrow notch band
                val alpha = sin(w0.toDouble()).toFloat() / (2.0f * q)
                
                val b0 = 1.0f
                val b1 = -2.0f * Math.cos(w0.toDouble()).toFloat()
                val b2 = 1.0f
                val a0 = 1.0f + alpha
                val a1 = -2.0f * Math.cos(w0.toDouble()).toFloat()
                val a2 = 1.0f - alpha

                // Coefficients normalized by a0
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
                val releaseCoeff = Math.exp(-1.0 / (SAMPLE_RATE * (settings.noiseGateReleaseMs / 1000.0))).toFloat()
                
                var envelope = 0.0f
                val windowSize = 256
                for (i in floatSamples.indices step windowSize) {
                    val limit = min(floatSamples.size, i + windowSize)
                    var peak = 0.0f
                    for (k in i until limit) {
                        peak = max(peak, abs(floatSamples[k]))
                    }

                    if (peak > gateThreshold) {
                        envelope = 1.0f // Open gate
                    } else {
                        envelope *= releaseCoeff // Slowly close gate
                    }

                    for (k in i until limit) {
                        floatSamples[k] *= envelope
                    }
                }
            }

            // === 3. ODSTRANĚNÍ OZVĚNY (De-reverb / Expander) ===
            if (settings.isEchoRemovalEnabled) {
                val attenuationFactor = 10.0f.pow(settings.echoRemovalAttenuationDb / 20.0f)
                // We damp the tail of high-energy decays to simulate echo removal
                var runningPeak = 0.0f
                val decayRate = 0.9999f
                for (n in floatSamples.indices) {
                    val inputAbs = abs(floatSamples[n])
                    if (inputAbs > runningPeak) {
                        runningPeak = inputAbs
                    } else {
                        runningPeak *= decayRate
                    }

                    // If we are in the decaying region (echo), apply dampening
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
                // Sibilant detection: check energy in 5-8kHz band vs low band
                // A simple bandpass-like difference filter to estimate high frequencies
                var prevSample = 0.0f
                for (n in floatSamples.indices) {
                    val hp = floatSamples[n] - prevSample // Simple high pass
                    prevSample = floatSamples[n]

                    // If high-frequency component is large, damp it
                    if (abs(hp) > deEsserThreshold) {
                        floatSamples[n] *= 0.65f // Attenuate sibilants
                    }
                }
            }

            // === 6. EQ (HPF, Boxy Cut, Presence, Air) ===
            if (settings.isEqEnabled) {
                // First-order High Pass Filter to cut below 90 Hz
                var lastIn = 0.0f
                var lastOut = 0.0f
                val hpfCutoff = settings.eqHighPassHz
                val dt = 1.0f / SAMPLE_RATE
                val RC = 1.0f / (2.0f * Math.PI.toFloat() * hpfCutoff)
                val alpha = RC / (RC + dt)

                for (n in floatSamples.indices) {
                    val input = floatSamples[n]
                    val output = alpha * (lastOut + input - lastIn)
                    lastIn = input
                    lastOut = output
                    floatSamples[n] = output
                }

                // Mid range dip and High boost: simple sliding difference for fast EQ
                // Low-mid boxiness cut: around 300Hz (represented by moving average difference)
                // High-mid presence boost: around 3.5kHz
                // High shelf air boost: 10kHz+
                val presenceGain = 10.0f.pow(settings.eqHighMidBoostDb / 20.0f) - 1.0f
                val airGain = 10.0f.pow(settings.eqHighShelfDb / 20.0f) - 1.0f
                val boxyReduction = 10.0f.pow(settings.eqLowMidCutDb / 20.0f) // negative dB

                var e1 = 0.0f
                var e2 = 0.0f
                for (n in floatSamples.indices) {
                    val curr = floatSamples[n]
                    val highFreqs = curr - e1 // high-pass representation
                    val airFreqs = curr - e2 // extreme high frequency

                    floatSamples[n] = curr * boxyReduction + (highFreqs * presenceGain * 0.4f) + (airFreqs * airGain * 0.3f)
                    
                    // low-pass updates for spectral separation
                    e1 = e1 * 0.85f + curr * 0.15f
                    e2 = e2 * 0.95f + curr * 0.05f
                }
            }

            // === 7. KOMPRESE (Dynamic Range Compressor) ===
            if (settings.isCompressionEnabled) {
                val threshold = 10.0f.pow(settings.compressionThresholdDb / 20.0f)
                val ratio = settings.compressionRatio
                val attackCoeff = Math.exp(-1.0 / (SAMPLE_RATE * (settings.compressionAttackMs / 1000.0))).toFloat()
                val releaseCoeff = Math.exp(-1.0 / (SAMPLE_RATE * (settings.compressionReleaseMs / 1000.0))).toFloat()

                var envelope = 0.0f
                for (n in floatSamples.indices) {
                    val inputAbs = abs(floatSamples[n])
                    
                    // Attack vs Release envelope follower
                    if (inputAbs > envelope) {
                        envelope = attackCoeff * envelope + (1.0f - attackCoeff) * inputAbs
                    } else {
                        envelope = releaseCoeff * envelope + (1.0f - releaseCoeff) * inputAbs
                    }

                    if (envelope > threshold && envelope > 0.0f) {
                        // Compress above threshold
                        val gainDb = 20.0f * Math.log10(envelope.toDouble()).toFloat()
                        val threshDb = settings.compressionThresholdDb
                        val compressedGainDb = threshDb + (gainDb - threshDb) / ratio
                        val targetGain = 10.0f.pow(compressedGainDb / 20.0f) / envelope
                        floatSamples[n] *= targetGain
                    }
                }
            }

            // === 8. STEREORIZER (Haas Delay - mono to stereo copy) ===
            // This is applied in final WAV write or output formatting
            val channelsToWrite = if (settings.isStereorizerEnabled) CHANNELS_STEREO else CHANNELS_MONO

            // === 9. LIMITER (Hard Peak Limiting) ===
            val limitThreshold = if (settings.isLimiterEnabled) {
                10.0f.pow(settings.limiterThresholdDb / 20.0f)
            } else 1.0f
            val limitCeiling = if (settings.isLimiterEnabled) {
                10.0f.pow(settings.limiterCeilingDb / 20.0f)
            } else 0.98f

            for (n in floatSamples.indices) {
                var value = floatSamples[n]
                if (abs(value) > limitThreshold) {
                    value = Math.signum(value) * limitThreshold
                }
                // Clamp strictly to ceiling
                if (value > limitCeiling) value = limitCeiling
                if (value < -limitCeiling) value = -limitCeiling
                floatSamples[n] = value
            }

            // Convert back to 16-bit Short buffer
            val finalShorts = ShortArray(floatSamples.size * channelsToWrite)
            if (settings.isStereorizerEnabled) {
                // Stereo Haas Delay
                val delaySamples = (SAMPLE_RATE * (settings.stereorizerDelayMs / 1000.0)).toInt()
                for (n in floatSamples.indices) {
                    // Left channel is original
                    finalShorts[n * 2] = (floatSamples[n] * 32767).toInt().toShort()

                    // Right channel is delayed original
                    val delayedIndex = n - delaySamples
                    val delayedSample = if (delayedIndex >= 0) floatSamples[delayedIndex] else 0.0f
                    // Pan right channel slightly or add delay
                    finalShorts[n * 2 + 1] = (delayedSample * 32767).toInt().toShort()
                }
            } else {
                for (n in floatSamples.indices) {
                    finalShorts[n] = (floatSamples[n] * 32767).toInt().toShort()
                }
            }

            writeWavFile(outputFile, finalShorts, channelsToWrite)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process vocal track", e)
            return false
        }
    }

    /**
     * Automatically aligns and mixes multiple vocal files with a beat file
     * Handling major/minor vocal double tracking and stereo width automatically.
     */
    fun mixProject(
        beatFile: File,
        vocalFiles: List<Pair<File, VocalFileEntity>>,
        outputFile: File
    ): Boolean {
        try {
            val beatWav = parseWavPCM(beatFile) ?: return false
            val beatShorts = beatWav.samples
            val beatChannels = beatWav.numChannels
            
            val numBeatStereoSamples = if (beatChannels == CHANNELS_STEREO) beatShorts.size / 2 else beatShorts.size

            val mixedL = FloatArray(numBeatStereoSamples)
            val mixedR = FloatArray(numBeatStereoSamples)

            if (beatChannels == CHANNELS_STEREO) {
                for (i in 0 until numBeatStereoSamples) {
                    mixedL[i] = beatShorts[i * 2].toFloat() / 32768.0f
                    mixedR[i] = beatShorts[i * 2 + 1].toFloat() / 32768.0f
                }
            } else {
                for (i in 0 until numBeatStereoSamples) {
                    val sample = beatShorts[i].toFloat() / 32768.0f
                    mixedL[i] = sample
                    mixedR[i] = sample
                }
            }

            // Apply Auto-EQ on the Beat to carve out space for vocals (cuts at 250Hz for mud, 1800Hz for vocal pocket)
            val beatEq1L = BiquadPeakingEQ(250f, SAMPLE_RATE.toFloat(), 1.0f, -3.0f)
            val beatEq1R = BiquadPeakingEQ(250f, SAMPLE_RATE.toFloat(), 1.0f, -3.0f)
            val beatEq2L = BiquadPeakingEQ(1800f, SAMPLE_RATE.toFloat(), 1.0f, -4.5f)
            val beatEq2R = BiquadPeakingEQ(1800f, SAMPLE_RATE.toFloat(), 1.0f, -4.5f)

            for (i in 0 until numBeatStereoSamples) {
                mixedL[i] = beatEq2L.process(beatEq1L.process(mixedL[i]))
                mixedR[i] = beatEq2R.process(beatEq1R.process(mixedR[i]))
            }
            Log.d(TAG, "Applied automatic pocket carving EQ on instrumental beat (-3dB at 250Hz, -4.5dB at 1800Hz)")

            // Process and layer each vocal file onto the mixed buffer
            val parsedVocals = vocalFiles.mapNotNull { (file, entity) ->
                val vWav = parseWavPCM(file) ?: return@mapNotNull null
                val vShorts = vWav.samples
                val vChannels = vWav.numChannels
                val vSamples = FloatArray(if (vChannels == CHANNELS_STEREO) vShorts.size / 2 else vShorts.size)

                if (vChannels == CHANNELS_STEREO) {
                    for (i in vSamples.indices) {
                        // Average stereo down to mono for panning/mixing consistency
                        vSamples[i] = (vShorts[i * 2].toFloat() + vShorts[i * 2 + 1].toFloat()) / 65536.0f
                    }
                } else {
                    for (i in vSamples.indices) {
                        vSamples[i] = vShorts[i].toFloat() / 32768.0f
                    }
                }
                Triple(vSamples, entity, file.length())
            }

            // Identify potential duplicates (vocal tracks with nearly identical file length +/- 1000 bytes)
            val isDuplicate = BooleanArray(parsedVocals.size)
            for (i in parsedVocals.indices) {
                for (j in i + 1 until parsedVocals.size) {
                    val lenDiff = abs(parsedVocals[i].third - parsedVocals[j].third)
                    if (lenDiff < 1000) { // Very likely the exact same vocal file!
                        isDuplicate[i] = true
                        isDuplicate[j] = true
                    }
                }
            }

            for (idx in parsedVocals.indices) {
                val (vSamples, entity, _) = parsedVocals[idx]
                
                // Determine Major/Minor mixing settings
                var currentVolume = entity.volume
                var currentPan = entity.panning
                var currentDelaySamples = (SAMPLE_RATE * (entity.offsetMs / 1000.0)).toInt()

                if (isDuplicate[idx]) {
                    // If duplicate same-sounding vocal is found, automatically configure Major/Minor stack
                    if (!entity.isMajor) {
                        // This is the MINOR backing vocal double
                        // Pan wide left (-0.85), slightly lower volume, delayed Haas style (20ms) to create massive space!
                        currentPan = -0.75f
                        currentVolume *= 0.65f // quieter
                        currentDelaySamples += (SAMPLE_RATE * 0.022).toInt() // Add 22ms offset to separate it in space!
                        Log.d(TAG, "Auto-Mixed Vocal ${entity.assignedName} as MINOR double track (panned left, -6dB, 22ms delayed)")
                    } else {
                        // This is the MAJOR lead vocal
                        // Centered, full volume, upfront!
                        currentPan = 0.75f // Pan it to opposite side (right) to complete the massive stereo wrap!
                        currentVolume *= 0.9f
                        Log.d(TAG, "Auto-Mixed Vocal ${entity.assignedName} as MAJOR lead track (panned right)")
                    }
                } else {
                    // Normal vocal panning and volume
                    currentPan = entity.panning
                }

                // Add vocal samples to the stereo master mixed buffer
                for (vIdx in vSamples.indices) {
                    val targetMixedIdx = vIdx + currentDelaySamples
                    if (targetMixedIdx >= numBeatStereoSamples) break
                    if (targetMixedIdx < 0) continue

                    val sampleValue = vSamples[vIdx] * currentVolume

                    // Pan formula: Constant Power Panning
                    // Left Gain = cos( (pan + 1) * PI / 4 )
                    // Right Gain = sin( (pan + 1) * PI / 4 )
                    val panFactor = (currentPan + 1.0f) * (Math.PI.toFloat() / 4.0f)
                    val leftGain = Math.cos(panFactor.toDouble()).toFloat()
                    val rightGain = Math.sin(panFactor.toDouble()).toFloat()

                    mixedL[targetMixedIdx] += sampleValue * leftGain
                    mixedR[targetMixedIdx] += sampleValue * rightGain
                }
            }

            // Write the merged master stereo WAV file with peak limiting to avoid clipping
            var masterMax = 0.0f
            for (i in 0 until numBeatStereoSamples) {
                masterMax = max(masterMax, abs(mixedL[i]))
                masterMax = max(masterMax, abs(mixedR[i]))
            }

            // Perfect dynamic limiter for mixed output
            val masterShorts = ShortArray(numBeatStereoSamples * 2)
            val scaleFactor = if (masterMax > 0.95f) 0.95f / masterMax else 1.0f

            for (i in 0 until numBeatStereoSamples) {
                val clampedL = max(-1.0f, min(1.0f, mixedL[i] * scaleFactor))
                val clampedR = max(-1.0f, min(1.0f, mixedR[i] * scaleFactor))
                masterShorts[i * 2] = (clampedL * 32767.0f).toInt().toShort()
                masterShorts[i * 2 + 1] = (clampedR * 32767.0f).toInt().toShort()
            }

            writeWavFile(outputFile, masterShorts, CHANNELS_STEREO)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error mixing project", e)
            return false
        }
    }

    /**
     * Utility method to write a standard 16-bit PCM WAV file with correct header
     */
    private fun writeWavFile(file: File, pcmData: ShortArray, numChannels: Int) {
        val totalAudioLen = pcmData.size * 2
        val totalDataLen = totalAudioLen + 36
        val byteRate = SAMPLE_RATE * numChannels * 2

        val header = ByteArray(44)
        header[0] = 'R'.toByte() // RIFF
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte() // file size - 8
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.toByte() // WAVE
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte() // fmt
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // size of fmt chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = numChannels.toByte() // mono or stereo
        header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte() // sample rate
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte() // byte rate
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (numChannels * 2).toByte() // block align
        header[33] = 0
        header[34] = BITS_PER_SAMPLE_16.toByte() // 16 bits per sample
        header[35] = 0
        header[36] = 'd'.toByte() // data chunk
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte() // data size
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val byteBuffer = ByteBuffer.allocate(pcmData.size * 2)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcmData) {
                byteBuffer.putShort(sample)
            }
            fos.write(byteBuffer.array())
        }
    }
}
