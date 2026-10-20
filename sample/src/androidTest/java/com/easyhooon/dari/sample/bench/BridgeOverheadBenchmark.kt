package com.easyhooon.dari.sample.bench

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.easyhooon.dari.Dari
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Times one bridge round trip (request + response call) per setup and payload size.
 * Not part of CI; run by hand with `am instrument -e class ...BridgeOverheadBenchmark`. Results go to logcat
 * (tag DariBench) and to files/dari-bench.txt.
 */
@RunWith(AndroidJUnit4::class)
class BridgeOverheadBenchmark {

    private val results = mutableListOf<String>()

    @Test
    fun roundTrip() {
        val interceptor = Dari.createInterceptor(tag = "Bench")
        val positional = listOf(
            "none" to BarePositionalBridge(),
            "manual" to ManualPositionalBridge(interceptor),
            "plugin" to MappedPositionalBridge(),
        )
        val json = listOf(
            "none" to BareJsonBridge(),
            "manual" to ManualJsonBridge(interceptor),
            "plugin" to MappedJsonBridge(),
        )
        report("device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT} recorder=${if (interceptor == null) "dari-noop" else "dari"}")
        for (size in SIZES) {
            val payload = payloadOf(size)
            for ((setup, bridge) in positional) {
                measure("positional", setup, size) { i ->
                    val id = "r$i"
                    timed {
                        bridge.onRequest(HANDLER, id, payload)
                        bridge.respond(HANDLER, id, true, payload)
                    }
                }
            }
            for ((setup, bridge) in json) {
                measure("json", setup, size) { i ->
                    val request = """{"handler":"$HANDLER","id":"j$i","data":$payload}"""
                    val response = """{"handler":"$HANDLER","id":"j$i","ok":true,"data":$payload}"""
                    timed {
                        bridge.onMessage(request)
                        bridge.reply(response)
                    }
                }
            }
        }
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "dari-bench.txt")
        file.writeText(results.joinToString("\n"))
    }

    // Each case starts from an empty store and gives background writes time to settle.
    private fun measure(shape: String, setup: String, size: Int, iteration: (Int) -> Long) {
        Dari.clear()
        Thread.sleep(SETTLE_MS)
        repeat(WARMUP) {
            if (size >= LARGE && it % LARGE_BATCH == 0) drain()
            iteration(-it - 1)
        }
        val samples = LongArray(ITERATIONS) {
            // Buffered 1 MB entries and their queued writes exceed the default 256 MB heap; drain between batches, untimed.
            if (size >= LARGE && it % LARGE_BATCH == 0) drain()
            iteration(it)
        }.sorted()
        val median = samples[samples.size / 2] / 1_000.0
        val p95 = samples[(samples.size * 95) / 100] / 1_000.0
        report("shape=$shape setup=$setup size=$size n=$ITERATIONS median_us=${"%.1f".format(median)} p95_us=${"%.1f".format(p95)}")
    }

    private fun drain() {
        Dari.clear()
        Thread.sleep(DRAIN_MS)
    }

    private inline fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun report(line: String) {
        Log.i(TAG, line)
        results += line
    }

    private companion object {
        const val TAG = "DariBench"
        const val HANDLER = "bench"
        const val WARMUP = 100
        const val ITERATIONS = 1_000
        const val SETTLE_MS = 2_000L
        const val LARGE = 1_000_000
        const val LARGE_BATCH = 20
        const val DRAIN_MS = 300L
        val SIZES = listOf(100, 10_000, LARGE)

        // A JSON array of small objects, padded to about [size] bytes.
        fun payloadOf(size: Int): String = buildString {
            append("""{"items":[""")
            var i = 0
            while (length < size - 64) {
                if (i > 0) append(',')
                append("""{"id":$i,"title":"Item $i","tags":["a","b"]}""")
                i++
            }
            append("]}")
        }
    }
}
