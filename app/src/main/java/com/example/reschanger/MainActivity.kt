package com.example.reschanger

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQ_SHIZUKU = 1001
        const val MIN_W = 240
        const val STEP = 10
    }

    private lateinit var tvShizuku: TextView
    private lateinit var tvInfo: TextView
    private lateinit var tvTarget: TextView
    private lateinit var tvLog: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnAuto: Button

    private var phys: WmHelper.Phys? = null

    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, result ->
            if (result == PackageManager.PERMISSION_GRANTED) loadPhysicalInfo()
            updateShizukuStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvShizuku = findViewById(R.id.tvShizuku)
        tvInfo = findViewById(R.id.tvInfo)
        tvTarget = findViewById(R.id.tvTarget)
        tvLog = findViewById(R.id.tvLog)
        seekBar = findViewById(R.id.seekBar)
        btnAuto = findViewById(R.id.btnAuto)

        Shizuku.addRequestPermissionResultListener(permListener)

        seekBar.progress = (prefs.getInt("width", 720) - MIN_W) / STEP
        tvTarget.text = "Resolusi saat main: ${targetWidth()}"
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                tvTarget.text = "Resolusi saat main: ${targetWidth()}"
                prefs.edit().putInt("width", targetWidth()).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        findViewById<Button>(R.id.btn240).setOnClickListener { setWidth(240) }
        findViewById<Button>(R.id.btn480).setOnClickListener { setWidth(480) }
        findViewById<Button>(R.id.btn720).setOnClickListener { setWidth(720) }
        findViewById<Button>(R.id.btnApply).setOnClickListener { applyManual() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetNow() }
        btnAuto.setOnClickListener { toggleAuto() }

        updateAutoButton()
        updateShizukuStatus()
        if (ensurePermission()) loadPhysicalInfo()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun setWidth(w: Int) { seekBar.progress = (w - MIN_W) / STEP }
    private fun targetWidth() = MIN_W + seekBar.progress * STEP

    private fun updateShizukuStatus() {
        tvShizuku.text = try {
            when {
                !Shizuku.pingBinder() -> "Status Shizuku: BELUM AKTIF (buka app Shizuku & Start)"
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                    "Status Shizuku: aktif, izin belum diberikan"
                else -> "Status Shizuku: SIAP"
            }
        } catch (e: Throwable) {
            "Status Shizuku: tidak terpasang"
        }
    }

    private fun ensurePermission(): Boolean = try {
        if (!Shizuku.pingBinder()) false
        else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) true
        else { Shizuku.requestPermission(REQ_SHIZUKU); false }
    } catch (e: Throwable) { false }

    private fun requireReady(): Boolean {
        if (!WmHelper.shizukuReady()) {
            Toast.makeText(this, "Shizuku belum siap", Toast.LENGTH_SHORT).show()
            ensurePermission()
            return false
        }
        return true
    }

    private fun loadPhysicalInfo() {
        thread {
            try {
                val p = WmHelper.readPhysical()
                phys = p
                runOnUiThread {
                    tvInfo.text = if (p != null)
                        "Layar asli: ${p.w}x${p.h} @ ${p.density}dpi" else "Gagal baca info layar"
                }
            } catch (e: Throwable) {
                runOnUiThread { tvLog.text = "Gagal baca info layar: ${e.message}" }
            }
        }
    }

    // ---------- Mode otomatis ----------

    private fun autoOn() = prefs.getBoolean("auto", false)

    private fun updateAutoButton() {
        btnAuto.text = if (autoOn()) "STOP MODE OTOMATIS" else "MULAI MODE OTOMATIS"
    }

    private fun toggleAuto() {
        val svc = Intent(this, WatcherService::class.java)
        if (autoOn()) {
            stopService(svc)
            prefs.edit().putBoolean("auto", false).apply()
            tvLog.text = "Mode otomatis dimatikan."
        } else {
            if (!requireReady()) return
            ContextCompat.startForegroundService(this, svc)
            prefs.edit().putBoolean("auto", true).apply()
            tvLog.text = "Mode otomatis aktif. Sekarang buka Free Fire. Aplikasi ini boleh ditutup."
        }
        updateAutoButton()
    }

    // ---------- Manual ----------

    private fun applyManual() {
        if (!requireReady()) return
        val p = phys
        if (p == null) { loadPhysicalInfo(); Toast.makeText(this, "Coba lagi", Toast.LENGTH_SHORT).show(); return }
        val w = targetWidth()
        thread {
            try {
                val r = WmHelper.apply(p, w)
                runOnUiThread { tvLog.text = "Diterapkan: $r"; showConfirmDialog() }
            } catch (e: Throwable) {
                runOnUiThread { tvLog.text = "Gagal: ${e.message}" }
            }
        }
    }

    private fun resetNow() {
        if (!requireReady()) return
        thread {
            try {
                WmHelper.reset()
                runOnUiThread { tvLog.text = "Resolusi dikembalikan ke normal" }
            } catch (e: Throwable) {
                runOnUiThread { tvLog.text = "Gagal reset: ${e.message}" }
            }
        }
    }

    private fun showConfirmDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Pertahankan resolusi ini?")
            .setMessage("Otomatis kembali normal dalam 10 detik")
            .setCancelable(false)
            .setPositiveButton("Pertahankan", null)
            .setNegativeButton("Kembalikan", null)
            .create()
        dialog.show()
        val timer = object : CountDownTimer(10_000, 1_000) {
            override fun onTick(ms: Long) {
                dialog.setMessage("Otomatis kembali normal dalam ${ms / 1000 + 1} detik")
            }
            override fun onFinish() {
                if (dialog.isShowing) { dialog.dismiss(); resetNow() }
            }
        }.start()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { timer.cancel(); dialog.dismiss() }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { timer.cancel(); dialog.dismiss(); resetNow() }
    }
}
