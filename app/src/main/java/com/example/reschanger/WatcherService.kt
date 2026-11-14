package com.example.reschanger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class WatcherService : Service() {

    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notif("Memantau Free Fire..."))
        if (worker == null) {
            running = true
            worker = Thread { loop() }.also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        super.onDestroy()
    }

    private fun loop() {
        var applied = false
        var miss = 0
        try {
            var phys: WmHelper.Phys? = null
            while (running && phys == null) {
                phys = try { WmHelper.readPhysical() } catch (e: Throwable) { null }
                if (phys == null) Thread.sleep(3000)
            }
            while (running) {
                val line = try { WmHelper.foregroundLine() } catch (e: Throwable) { "" }
                if (line.isNotBlank() && phys != null) {
                    if (WmHelper.isFreeFire(line)) {
                        miss = 0
                        if (!applied) {
                            val w = getSharedPreferences("cfg", MODE_PRIVATE).getInt("width", 720)
                            val r = WmHelper.apply(phys, w)
                            applied = true
                            update("Free Fire aktif: resolusi $r")
                        }
                    } else if (applied) {
                        miss++
                        if (miss >= 2) {
                            WmHelper.reset()
                            applied = false
                            miss = 0
                            update("Memantau Free Fire... (resolusi normal)")
                        }
                    }
                }
                Thread.sleep(2500)
            }
        } catch (e: InterruptedException) {
            // berhenti
        } finally {
            if (applied) {
                try { WmHelper.reset() } catch (e: Throwable) {}
            }
        }
    }

    private fun notif(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("watch", "Pemantau", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, "watch")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("FF Auto Resolution")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun update(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notif(text))
    }
}
