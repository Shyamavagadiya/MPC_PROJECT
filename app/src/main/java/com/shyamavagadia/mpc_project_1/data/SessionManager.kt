package com.shyamavagadia.mpc_project_1.data

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    fun setLoggedInUserId(userId: Long) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply()
    }

    fun getLoggedInUserId(): Long? {
        val id = prefs.getLong(KEY_USER_ID, NO_ID)
        return if (id == NO_ID) null else id
    }

    fun clear() {
        prefs.edit().remove(KEY_USER_ID).apply()
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val NO_ID = -1L
    }
}


