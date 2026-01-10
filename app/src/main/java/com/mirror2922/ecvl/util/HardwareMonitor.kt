package com.mirror2922.ecvl.util

import android.os.Process
import android.os.SystemClock

/**
 * Utility to monitor CPU usage.
 * Since /proc/stat is restricted on modern Android, we estimate app CPU usage.
 */
object HardwareMonitor {
    private var lastCpuTime: Long = 0
    private var lastTime: Long = 0

    /**
     * Returns an estimated CPU usage percentage for the app (0.0 to 1.0).
     * This represents the fraction of time the app was actively using a CPU core.
     */
    fun getCpuUsage(): Float {
        val currentCpuTime = Process.getElapsedCpuTime() // ms
        val currentTime = SystemClock.elapsedRealtime() // ms
        
        if (lastTime == 0L) {
            lastCpuTime = currentCpuTime
            lastTime = currentTime
            return 0.1f // Initial placeholder
        }
        
        val cpuDiff = currentCpuTime - lastCpuTime
        val timeDiff = currentTime - lastTime
        
        lastCpuTime = currentCpuTime
        lastTime = currentTime
        
        return if (timeDiff <= 0) 0f else (cpuDiff.toFloat() / timeDiff).coerceIn(0f, 1f)
    }
}