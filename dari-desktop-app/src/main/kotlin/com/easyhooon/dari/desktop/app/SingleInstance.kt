package com.easyhooon.dari.desktop.app

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/**
 * One Dari per user. macOS already refocuses a running .app, but a second copy on Windows (or a
 * second `run`) would clear the first one's adb forwards on start and cut its connection.
 */
object SingleInstance {
    // Held for the life of the process; the OS releases it when Dari exits, even on a crash.
    private var lock: FileLock? = null

    fun acquire(directory: File = AppLog.directory): Boolean {
        directory.mkdirs()
        lock = runCatching { RandomAccessFile(File(directory, "dari.lock"), "rw").channel.tryLock() }.getOrNull()
        return lock != null
    }
}
