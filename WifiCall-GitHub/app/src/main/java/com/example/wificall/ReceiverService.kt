package com.example.wificall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * Runs on the phone acting as a Receiver. Keeps a TCP ServerSocket open and registers
 * itself via NSD as "ReceiverN". When a Caller connects and sends its name, this launches
 * IncomingCallActivity (and a full-screen notification) so the phone rings immediately.
 */
class ReceiverService : Service() {

    private var serverSocket: ServerSocket? = null
    private lateinit var nsdHelper: NsdHelper
    @Volatile private var running = false
    private var serverThread: Thread? = null

    companion object {
        const val EXTRA_SLOT = "extra_slot"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val SERVICE_NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        nsdHelper = NsdHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val slot = intent?.getIntExtra(EXTRA_SLOT, 0) ?: 0
        val name = intent?.getStringExtra(EXTRA_DISPLAY_NAME)
        if (slot in 1..Constants.RECEIVER_COUNT && !running) {
            startForeground(SERVICE_NOTIF_ID, buildServiceNotification(slot, name))
            startServer(slot, name)
        }
        return START_STICKY
    }

    private fun startServer(slot: Int, name: String?) {
        running = true
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(0) // system picks a free port
                val port = serverSocket!!.localPort
                nsdHelper.registerReceiver(slot, port, name)

                while (running) {
                    val client: Socket = serverSocket!!.accept()
                    handleClient(client)
                }
            } catch (e: Exception) {
                // socket closed on stop, or network error
            }
        }
        serverThread?.start()
    }

    private fun handleClient(client: Socket) {
        Thread {
            try {
                createNotificationChannels() // re-sync DND-bypass flag if access was granted later
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val callerName = reader.readLine() ?: "Unknown"
                client.getOutputStream().write("OK\n".toByteArray())
                client.getOutputStream().flush()

                val intent = Intent(this, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(Constants.EXTRA_CALLER_NAME, callerName)
                }
                try { startActivity(intent) } catch (e: Exception) { /* background start may be blocked */ }

                val fullScreenPendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notif = NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.sym_call_incoming)
                    .setContentTitle("ইনকামিং কল")
                    .setContentText("$callerName ডাকছে")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setContentIntent(fullScreenPendingIntent)
                    .setAutoCancel(true)
                    .build()
                getSystemService(NotificationManager::class.java).notify(callerName.hashCode(), notif)
            } catch (e: Exception) {
                // ignore malformed connection
            } finally {
                try { client.close() } catch (e: Exception) {}
            }
        }.start()
    }

    private fun buildServiceNotification(slot: Int, name: String?): Notification {
        val label = if (!name.isNullOrBlank()) name else "Receiver $slot"
        return NotificationCompat.Builder(this, Constants.NOTIF_SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("$label সক্রিয়")
            .setContentText("এই ফোনটি এখন \"$label\" হিসেবে কল গ্রহণ করছে")
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val dndGranted = nm.isNotificationPolicyAccessGranted
            val callChannel = NotificationChannel(
                Constants.NOTIF_CHANNEL_ID, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming Wi-Fi calls"
                setBypassDnd(dndGranted)
                enableVibration(true)
            }
            val serviceChannel = NotificationChannel(
                Constants.NOTIF_SERVICE_CHANNEL_ID, "Receiver Status", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(callChannel)
            nm.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        running = false
        try { serverSocket?.close() } catch (e: Exception) {}
        nsdHelper.unregister()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
