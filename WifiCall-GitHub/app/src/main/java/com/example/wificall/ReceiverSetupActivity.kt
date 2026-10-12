package com.example.wificall

import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wificall.databinding.ActivityReceiverSetupBinding

class ReceiverSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiverSetupBinding
    private lateinit var nameInputs: List<EditText>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val currentSlot = prefs.getInt(Constants.KEY_RECEIVER_SLOT, 0)

        nameInputs = listOf(
            binding.nameSlot1, binding.nameSlot2, binding.nameSlot3, binding.nameSlot4, binding.nameSlot5
        )
        nameInputs.forEachIndexed { index, editText ->
            val slot = index + 1
            editText.setText(prefs.getString(Constants.KEY_SLOT_NAME_PREFIX + slot, ""))
            editText.hint = "Receiver $slot এর নাম (ঐচ্ছিক)"
        }

        updateStatusText(currentSlot, prefs)
        updateDndButtonLabel()

        val buttons = listOf(binding.btnSlot1, binding.btnSlot2, binding.btnSlot3, binding.btnSlot4, binding.btnSlot5)
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { activateSlot(index + 1) }
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, ReceiverService::class.java))
            prefs.edit().putInt(Constants.KEY_RECEIVER_SLOT, 0).apply()
            binding.statusText.text = "Receiver বন্ধ আছে"
        }

        binding.btnDndAccess.setOnClickListener { requestDndAccess() }
    }

    override fun onResume() {
        super.onResume()
        updateDndButtonLabel()
    }

    private fun activateSlot(slot: Int) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val customName = nameInputs[slot - 1].text.toString().trim()

        stopService(Intent(this, ReceiverService::class.java))
        prefs.edit()
            .putInt(Constants.KEY_RECEIVER_SLOT, slot)
            .putString(Constants.KEY_SLOT_NAME_PREFIX + slot, customName)
            .apply()

        val intent = Intent(this, ReceiverService::class.java).apply {
            putExtra(ReceiverService.EXTRA_SLOT, slot)
            putExtra(ReceiverService.EXTRA_DISPLAY_NAME, customName)
        }
        ContextCompat.startForegroundService(this, intent)
        updateStatusText(slot, prefs)
    }

    private fun updateStatusText(slot: Int, prefs: SharedPreferences) {
        if (slot in 1..Constants.RECEIVER_COUNT) {
            val name = prefs.getString(Constants.KEY_SLOT_NAME_PREFIX + slot, "")
            val label = if (!name.isNullOrBlank()) "\"$name\" (Receiver $slot)" else "Receiver $slot"
            binding.statusText.text = "এই ফোনটি এখন $label হিসেবে সক্রিয়"
        } else {
            binding.statusText.text = "এই ফোনটি এখনো কোনো Receiver স্লট হিসেবে সক্রিয় নয়"
        }
    }

    private fun requestDndAccess() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } else {
            Toast.makeText(this, "DND Access ইতিমধ্যে দেওয়া আছে", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDndButtonLabel() {
        val nm = getSystemService(NotificationManager::class.java)
        binding.btnDndAccess.text =
            if (nm.isNotificationPolicyAccessGranted) "DND Access দেওয়া আছে ✓"
            else "Silent/DND-এও রিং বাজাতে Access দিন"
    }
}
