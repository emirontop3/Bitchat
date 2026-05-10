package com.emirontop3.bitchat.permissions

import android.Manifest

object PermissionManager {
    val smsPermissions = listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
    val contactsPermission = Manifest.permission.READ_CONTACTS
}
