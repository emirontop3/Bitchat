package com.emirontop3.bitchat.messaging.sms

import android.telephony.SmsManager
import javax.inject.Inject

class SmsManagerWrapper @Inject constructor() {
    fun sendSms(number: String, message: String) {
        SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
    }
}
