package com.pr0gramm.app.util

import android.os.SystemClock
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.pr0gramm.app.BuildConfig
import java.util.concurrent.atomic.AtomicInteger


inline class Logger(val name: String) {
    inline fun debug(block: () -> String) {
        if (BuildConfig.DEBUG) {
            Logging.log(Log.DEBUG, name, block())
        }
    }

    inline fun info(block: () -> String) {
        Logging.log(Log.INFO, name, block())
    }

    inline fun warn(block: () -> String) {
        Logging.log(Log.WARN, name, block())
    }

    inline fun warn(err: Throwable, block: () -> String) {
        warn(block(), err)
    }

    inline fun error(block: () -> String) {
        Logging.log(Log.ERROR, name, block())
    }

    inline fun error(err: Throwable, block: () -> String) {
        error(block(), err)
    }

    fun warn(text: String, err: Throwable) {
        warn { text + "\n" + Log.getStackTraceString(err) }
    }

    fun error(text: String, err: Throwable) {
        error { text + "\n" + Log.getStackTraceString(err) }
    }
}

object Logging {
    private const val ENTRY_BUFFER_SIZE = 4096

    class Entry(
            var time: Long = 0,
            var level: Int = 0,
            var name: String = "",
            var message: String = "",
            var thread: String = "")

    private val array = arrayOf("0", "1", "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "ASSERT")

    private val entries = Array(ENTRY_BUFFER_SIZE) { Entry() }
    private val entriesIndex = AtomicInteger()

    private var cachedCrashlytics: CrashlyticsCore? = null
        get() {
            if (field == null) {
                Crashlytics.getInstance()?.let {
                    field = it.core
                }
            }

            return field
        }

    fun log(level: Int, name: String, message: String) {
        val thread = Thread.currentThread().name
        val tag = "[$thread] $name"

        if (BuildConfig.DEBUG) {
            // only log to logcat.
            Log.println(level, tag, message)

        } else {
            // only log to crashlytics
            cachedCrashlytics?.log(level, tag, message)
        }

        // store in buffer. Not 100% thread safe but close enough.
        val index = entriesIndex.getAndIncrement() % ENTRY_BUFFER_SIZE
        entries[index].apply {
            this.time = SystemClock.elapsedRealtime()
            this.level = level
            this.name = name
            this.message = message
            this.thread = thread
        }
    }

    fun recentMessages(): List<String> {
        var result: List<String> = listOf()

        repeat(16) { _ ->
            val now = SystemClock.elapsedRealtime()

            val beginIndex = entriesIndex.get()

            result = entries.indices.reversed().mapNotNullTo(ArrayList(entries.size)) { offset ->
                val entry = entries[(beginIndex + offset) % entries.size]
                entry.takeIf { it.time != 0L }?.let {
                    "%4.3fs [%16s] [%5s] %s: %s".format(0.001f * (entry.time - now),
                            entry.thread,
                            array.getOrNull(entry.level) ?: "DEBUG",
                            entry.name, entry.message)
                }
            }

            // logs did not change, take them
            if (beginIndex == entriesIndex.get()) {
                return result
            }
        }

        return result
    }
}
