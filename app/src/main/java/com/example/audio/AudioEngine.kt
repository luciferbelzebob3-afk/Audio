package com.example.audio

import android.util.Log
import com.example.data.VocalFileEntity
import java.io.*
import kotlin.math.*

object AudioEngine {

    private const val TAG = "AudioEngine"


fun detectBPM(file: File): Int {

    return try {

        val samples = readWavSamples(file)

        if (samples.isEmpty())
            return 120


        val peaks = mutableListOf<Int>()

        val threshold = 0.65f


        for (i in 1 until samples.size - 1) {

            val current = abs(samples[i])

            if (
                current > threshold &&
                current > abs(samples[i - 1]) &&
                current > abs(samples[i + 1])
            ) {
                peaks.add(i)
            }
        }


        if (peaks.size < 2)
            return 120


        val avgDistance =
            peaks.zipWithNext()
                .map { it.second - it.first }
                .average()


        val bpm =
    (44100.0 * 60.0 / avgDistance)
         .toInt()


        bpm.coerceIn(50, 200)


    } catch (e: Exception) {

        Log.e(
            TAG,
            "BPM detection error",
            e
        )

        120
    }
}



fun extractWaveform(
    file: File,
    bars: Int = 50
): FloatArray? {

    return try {

        val samples =
            readWavSamples(file)


        if (samples.isEmpty())
            return null


        val result =
            FloatArray(bars)


        val step =
            samples.size / bars


        for (i in 0 until bars) {

            var peak = 0f


            val start =
                i * step


            val end =
                minOf(
                    start + step,
                    samples.size
                )


            for (x in start until end) {

                peak =
                    max(
                        peak,
                        abs(samples[x])
                    )
            }


            result[i] = peak
        }


        result


    } catch (e: Exception) {

        Log.e(
            TAG,
            "Waveform error",
            e
        )

        null
    }
}



    fun processVocal(
        input: File,
        output: File,
        vocal: VocalFileEntity
    ) {


        if(!input.exists())
            throw IOException(
                "Input vocal missing"
            )


        val samples =
            readWavSamples(input)



        val processed =
            FloatArray(samples.size)



        for(i in samples.indices){

    var value = samples[i]


    // noise gate
    if(abs(value) < 0.01f)
        value = 0f


    // compressor
    value =
        sign(value) *
        (1f - exp(-abs(value) * 3f))


    // vocal gain
    value *= vocal.volume


    // soft limiter
    value =
        value.coerceIn(
            -0.95f,
            0.95f
        )


    processed[i] = value
}



        writeWavFile(
            output,
            processed,
            44100,
            1
        )
    }
    private fun readWavSamples(
        file: File
    ): FloatArray {


        val bytes =
            file.readBytes()



        if(bytes.size < 44)
            return FloatArray(0)



        val dataStart =
            44



        val samples =
            (bytes.size - dataStart) / 2



        val result =
            FloatArray(samples)



        var index = 0



        for(i in 0 until samples) {


            val low =
                bytes[dataStart + index]
                    .toInt()
                    .and(0xff)



            val high =
                bytes[dataStart + index + 1]
                    .toInt()



            val value =
                (high shl 8) or low



            result[i] =
                value / 32768f



            index += 2
        }


        return result
    }




