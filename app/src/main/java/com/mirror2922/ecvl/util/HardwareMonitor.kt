package com.mirror2922.ecvl.util

import android.os.Process
import android.os.SystemClock

/**
 * Utility to monitor application CPU usage.
 */
object HardwareMonitor {
    private var lastCpuTime: Long = 0
    private var lastTime: Long = 0

    fun getCpuUsage(): Float {
        val currentCpuTime = Process.getElapsedCpuTime()
        val currentTime = SystemClock.elapsedRealtime()
        
        if (lastTime == 0L) {
            lastCpuTime = currentCpuTime
            lastTime = currentTime
            return 0.1f
        }
        
        val cpuDiff = currentCpuTime - lastCpuTime
        val timeDiff = currentTime - lastTime
        
        lastCpuTime = currentCpuTime
        lastTime = currentTime
        
        return if (timeDiff <= 0) 0f else (cpuDiff.toFloat() / timeDiff).coerceIn(0f, 1f)
    }
}
