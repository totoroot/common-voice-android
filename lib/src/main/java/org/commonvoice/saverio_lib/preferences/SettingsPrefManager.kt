package org.commonvoice.saverio_lib.preferences

import android.content.Context
import org.commonvoice.saverio_lib.api.responseBodies.ResponseLanguage

class SettingsPrefManager(ctx: Context) {

    private val preferences = ctx.getSharedPreferences("settingsPreferences", Context.MODE_PRIVATE)

    var isOfflineMode: Boolean
        get() = preferences.getBoolean(Keys.OFFLINE_MODE.name, true)
        set(value) = preferences.edit().putBoolean(Keys.OFFLINE_MODE.name, value).apply()

    var automaticallyCheckForUpdates: Boolean
        get() = preferences.getBoolean(Keys.AUTOMATICALLY_CHECK_FOR_UPDATES.name, true)
        set(value) = preferences.edit().putBoolean(Keys.AUTOMATICALLY_CHECK_FOR_UPDATES.name, value)
            .apply()

    var showReportIcon: Boolean
        get() = preferences.getBoolean(Keys.SHOW_REPORT_ICON.name, false)
        set(value) = preferences.edit().putBoolean(Keys.SHOW_REPORT_ICON.name, value)
            .apply()

    var latestVersion: String
        get() = preferences.getString(Keys.LATEST_VERSION.name, "") ?: ""
        set(value) = preferences.edit().putString(Keys.LATEST_VERSION.name, value).apply()

    var appLanguages: String
        get() = preferences.getString(Keys.APP_LANGUAGES.name, ResponseLanguage.DEFAULT_VALUE) ?: ""
        set(value) = preferences.edit().putString(Keys.APP_LANGUAGES.name, value).apply()

    var isProgressBarColouredEnabled: Boolean
        get() = preferences.getBoolean(Keys.DAILYGOAL_PROGRESSBAR_COLOURED.name, false)
        set(value) = preferences.edit().putBoolean(Keys.DAILYGOAL_PROGRESSBAR_COLOURED.name, value).apply()

    private enum class Keys {
        AUTOMATICALLY_CHECK_FOR_UPDATES,
        LATEST_VERSION,
        OFFLINE_MODE,
        SHOW_REPORT_ICON,
        APP_LANGUAGES,
        DAILYGOAL_PROGRESSBAR_COLOURED,
    }

}