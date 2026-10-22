package com.microsip.android.sip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Read-only view of the phone's address book, used for name lookup and the "Phone" contacts tab. */
object DeviceContacts {
    data class Entry(val name: String, val number: String)

    val entries = MutableStateFlow<List<Entry>>(emptyList())
    @Volatile private var byDigits: Map<String, String> = emptyMap()

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Loads contacts on a background thread. Safe to call repeatedly. */
    fun load(ctx: Context) {
        if (!hasPermission(ctx)) return
        val app = ctx.applicationContext
        Thread {
            val list = mutableListOf<Entry>()
            runCatching {
                app.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ),
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(0) ?: continue
                        val number = c.getString(1) ?: continue
                        list += Entry(name, number)
                    }
                }
            }
            val distinct = list.distinctBy { it.name + "|" + digits(it.number) }
            byDigits = distinct.associate { digits(it.number).takeLast(9) to it.name }
            entries.value = distinct
        }.start()
    }

    private fun digits(s: String) = s.filter { it.isDigit() }

    /** Matches on the last 9 digits so "+44 20 ..." and "020 ..." find the same person. */
    fun nameForNumber(user: String?): String? {
        if (user.isNullOrBlank()) return null
        val d = digits(user)
        if (d.length < 3) return null
        return byDigits[d.takeLast(9)]
    }
}
