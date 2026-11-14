package com.example.reschanger

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object WmHelper {

    data class Phys(val w: Int, val h: Int, val density: Int)

    fun shizukuReady(): Boolean =
        try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }

    fun runShell(cmd: String): String {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        p.waitFor()
        return (out + err).trim()
    }

    fun readPhysical(): Phys? {
        val size = runShell("wm size")
        val dens = runShell("wm density")
        val s = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(size) ?: return null
        val d = Regex("Physical density:\\s*(\\d+)").find(dens) ?: return null
        val a = s.groupValues[1].toInt()
        val b = s.groupValues[2].toInt()
        return Phys(minOf(a, b), maxOf(a, b), d.groupValues[1].toInt())
    }

    /** Turunkan resolusi layar ke lebar [width], rasio asli dipertahankan. */
    fun apply(p: Phys, width: Int): String {
        var h = Math.round(width.toFloat() * p.h / p.w)
        if (h % 2 != 0) h++
        val density = maxOf(72, Math.round(p.density.toFloat() * width / p.w))
        runShell("wm size ${width}x${h}; wm density $density")
        return "${width}x${h} @ ${density}dpi"
    }

    fun reset() {
        runShell("wm size reset; wm density reset")
    }

    /** Baris activity yang sedang tampil di depan (kosong jika tidak diketahui). */
    fun foregroundLine(): String =
        runShell("dumpsys activity activities | grep -i resumedactivity")

    fun isFreeFire(line: String) =
        line.contains("com.dts.freefireth") || line.contains("com.dts.freefiremax")
}
