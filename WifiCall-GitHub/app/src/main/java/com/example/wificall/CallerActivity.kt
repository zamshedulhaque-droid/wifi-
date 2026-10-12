package com.example.wificall

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wificall.databinding.ActivityCallerBinding
import java.net.Socket
import kotlin.concurrent.thread

class CallerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallerBinding
    private lateinit var nsdHelper: NsdHelper
    private var callerName: String = ""

    // slot -> (host, port)
    private val onlineReceivers = HashMap<Int, Pair<String, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        callerName = prefs.getString(Constants.KEY_CALLER_NAME, null) ?: run {
            val generated = "Caller-${(1000..9999).random()}"
            prefs.edit().putString(Constants.KEY_CALLER_NAME, generated).apply()
            generated
        }
        binding.callerNameInput.setText(callerName)

        binding.btnSaveName.setOnClickListener {
            val newName = binding.callerNameInput.text.toString().trim()
            if (newName.isNotEmpty()) {
                callerName = newName
                prefs.edit().putString(Constants.KEY_CALLER_NAME, newName).apply()
                Toast.makeText(this, "নাম সংরক্ষণ করা হয়েছে", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "নাম খালি রাখা যাবে না", Toast.LENGTH_SHORT).show()
            }
        }

        nsdHelper = NsdHelper(this)
        val buttons = getSlotButtons()
        buttons.forEach { it.isEnabled = false }

        nsdHelper.startDiscovery(
            onFound = { slot, host, port, displayName ->
                runOnUiThread {
                    onlineReceivers[slot] = host to port
                    if (slot in 1..Constants.RECEIVER_COUNT) {
                        val button = buttons[slot - 1]
                        button.isEnabled = true
                        button.text = if (!displayName.isNullOrBlank()) {
                            "$displayName কে কল করুন"
                        } else {
                            "Receiver $slot কে কল করুন"
                        }
                    }
                }
            },
            onLost = { slot ->
                runOnUiThread {
                    onlineReceivers.remove(slot)
                    if (slot in 1..Constants.RECEIVER_COUNT) {
                        val button = buttons[slot - 1]
                        button.isEnabled = false
                        button.text = "Receiver $slot (অফলাইন)"
                    }
                }
            }
        )

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { callReceiver(index + 1) }
        }
    }

    private fun getSlotButtons(): List<Button> = listOf(
        binding.btnCall1, binding.btnCall2, binding.btnCall3, binding.btnCall4, binding.btnCall5
    )

    private fun callReceiver(slot: Int) {
        val target = onlineReceivers[slot] ?: run {
            Toast.makeText(this, "Receiver $slot এখন অনলাইনে নেই", Toast.LENGTH_SHORT).show()
            return
        }
        val nameToSend = callerName
        thread {
            try {
                Socket(target.first, target.second).use { socket ->
                    socket.soTimeout = 5000
                    socket.getOutputStream().write("$nameToSend\n".toByteArray())
                    socket.getOutputStream().flush()
                }
                runOnUiThread { Toast.makeText(this, "কল পাঠানো হয়েছে", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "কল পাঠানো যায়নি: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onDestroy() {
        nsdHelper.stopDiscovery()
        super.onDestroy()
    }
}
