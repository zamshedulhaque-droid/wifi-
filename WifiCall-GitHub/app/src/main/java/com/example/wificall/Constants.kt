package com.example.wificall

object Constants {
    const val SERVICE_TYPE = "_wificall._tcp."
    const val RECEIVER_COUNT = 5
    const val PREFS_NAME = "wificall_prefs"
    const val KEY_CALLER_NAME = "caller_name"
    const val KEY_RECEIVER_SLOT = "receiver_slot" // 1..5, 0 = not set
    const val KEY_SLOT_NAME_PREFIX = "slot_name_" // + slot number -> custom display name
    const val NSD_ATTR_DISPLAY_NAME = "displayName"
    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val NOTIF_CHANNEL_ID = "incoming_call_channel"
    const val NOTIF_SERVICE_CHANNEL_ID = "receiver_service_channel"
}
