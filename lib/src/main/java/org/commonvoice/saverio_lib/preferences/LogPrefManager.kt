package org.commonvoice.saverio_lib.preferences

import android.content.Context

class LogPrefManager(ctx: Context) {
    private val preferences = ctx.getSharedPreferences("logPreferences", Context.MODE_PRIVATE)

    var saveLogFile: Boolean
        get() = preferences.getBoolean(Keys.Save_LogFile.name, false)
        set(value) {
            preferences.edit().putBoolean(Keys.Save_LogFile.name, value).apply()
        }

    var isLogFileSent: Boolean
        get() = preferences.getBoolean(Keys.IS_LogFile_Sent.name, true)
        set(value) {
            preferences.edit().putBoolean(Keys.IS_LogFile_Sent.name, value).apply()
        }

    var stackTrace: String
        get() = preferences.getString(Keys.STACK_TRACE.name, "").toString()
        set(value) {
            preferences.edit().putString(Keys.STACK_TRACE.name, value).apply()
        }

    private enum class Keys {
        Save_LogFile,
        IS_LogFile_Sent,
        STACK_TRACE
    }
}