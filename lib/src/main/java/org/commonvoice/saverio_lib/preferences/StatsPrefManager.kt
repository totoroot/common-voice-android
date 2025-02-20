package org.commonvoice.saverio_lib.preferences

import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.commonvoice.saverio_lib.dataClasses.BadgeDialogMediator
import org.commonvoice.saverio_lib.dataClasses.DailyGoal
import org.commonvoice.saverio_lib.utils.isOnADifferentDayFromToday
import java.util.*

class StatsPrefManager(ctx: Context) {

    private val preferences = ctx.getSharedPreferences("statsPreferences", Context.MODE_PRIVATE)

    init {
        updateDailyGoal()
    }

    val dailyGoal: LiveData<DailyGoal> = DailyGoalLivePreference(
        preferences,
        todayRecorded = Keys.TODAY_RECORDED.name,
        todayValidated = Keys.TODAY_VALIDATED.name,
        dailyObjective = Keys.DAILY_GOAL_OBJECTIVE.name
    )

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val _badgeLiveData = MutableSharedFlow<BadgeDialogMediator>()
    val badgeLiveData: SharedFlow<BadgeDialogMediator> get() = _badgeLiveData.asSharedFlow()

    var dailyGoalObjective: Int
        get() {
            updateDailyGoal()
            return preferences.getInt(Keys.DAILY_GOAL_OBJECTIVE.name, 0)
        }
        set(value) {
            updateDailyGoal()
            preferences.edit().putInt(Keys.DAILY_GOAL_OBJECTIVE.name, value).apply()
        }

    var reviewOnPlayStoreCounter: Int
        get() = preferences.getInt(Keys.REVIEW_ON_PLAYSTORE_COUNTER.name, 0)
        set(value) = preferences.edit().putInt(Keys.REVIEW_ON_PLAYSTORE_COUNTER.name, value).apply()

    private var todayContributingDate: Calendar
        get() = Calendar.getInstance().also {
            val currentMillis = Calendar.getInstance().timeInMillis
            it.timeInMillis = preferences.getLong(Keys.TODAY_CONTRIBUTING_DATE.name, currentMillis)
            if (it.timeInMillis == currentMillis) {
                preferences.edit().putLong(Keys.TODAY_CONTRIBUTING_DATE.name, currentMillis).apply()
            }
        }
        set(value) = preferences.edit().putLong(Keys.TODAY_CONTRIBUTING_DATE.name, value.timeInMillis).apply()

    var todayValidated: Int
        get() {
            updateDailyGoal()
            return preferences.getInt(Keys.TODAY_VALIDATED.name, 0)
        }
        set(value) = preferences.edit().putInt(Keys.TODAY_VALIDATED.name, value).apply()

    var todayRecorded: Int
        get() {
            updateDailyGoal()
            return preferences.getInt(Keys.TODAY_RECORDED.name, 0)
        }
        set(value) = preferences.edit().putInt(Keys.TODAY_RECORDED.name, value).apply()

    var allTimeRecorded: Int
        get() = preferences.getInt(Keys.ALLTIME_RECORDED.name, 0)
        set(value) = preferences.edit().putInt(Keys.ALLTIME_RECORDED.name, value).apply()

    var allTimeValidated: Int
        get() = preferences.getInt(Keys.ALLTIME_VALIDATED.name, 0)
        set(value) = preferences.edit().putInt(Keys.ALLTIME_VALIDATED.name, value).apply()

    var allTimeLevel: Int
        get() = preferences.getInt(Keys.ALLTIME_LEVEL.name, 0)
        set(value) = preferences.edit().putInt(Keys.ALLTIME_LEVEL.name, value).apply()

    var localRecorded: Int
        get() = preferences.getInt(Keys.LOCAL_RECORDED.name, 0)
        set(value) {
            val old = getStatsTextArrayIndex(localRecorded + allTimeRecorded)
            val new = getStatsTextArrayIndex(localRecorded + allTimeRecorded + 1)
            preferences.edit().putInt(Keys.LOCAL_RECORDED.name, value).apply()
            if (old != new) coroutineScope.launch {
                _badgeLiveData.emit(BadgeDialogMediator.Speak)
            }
        }

    var localValidated: Int
        get() = preferences.getInt(Keys.LOCAL_VALIDATED.name, 0)
        set(value) {
            val old = getStatsTextArrayIndex(localValidated + allTimeValidated)
            val new = getStatsTextArrayIndex(localValidated + allTimeValidated + 1)
            preferences.edit().putInt(Keys.LOCAL_VALIDATED.name, value).apply()
            if (old != new) coroutineScope.launch {
                _badgeLiveData.emit(BadgeDialogMediator.Listen)
            }
        }

    var localLevel: Int
        get() = preferences.getInt(Keys.LOCAL_LEVEL.name, 0)
        set(value) {
            val old = getLevel(localLevel + allTimeLevel)
            val new = getLevel(localLevel + allTimeLevel + 1)
            preferences.edit().putInt(Keys.LOCAL_LEVEL.name, value).apply()
            if (old != new) coroutineScope.launch {
                _badgeLiveData.emit(BadgeDialogMediator.Level)
            }
        }

    val parsedLevel: Int
        get() = getLevel(allTimeLevel)

    private fun updateDailyGoal() {
        if (todayContributingDate.isOnADifferentDayFromToday()) {
            todayRecorded = 0
            todayValidated = 0
            todayContributingDate = Calendar.getInstance()
        }
    }

    private enum class Keys {
        DAILY_GOAL_OBJECTIVE,

        REVIEW_ON_PLAYSTORE_COUNTER,

        TODAY_CONTRIBUTING_DATE,
        TODAY_VALIDATED,
        TODAY_RECORDED,

        ALLTIME_RECORDED,
        ALLTIME_VALIDATED,
        ALLTIME_LEVEL,

        LOCAL_RECORDED,
        LOCAL_VALIDATED,
        LOCAL_LEVEL,
    }

    companion object {

        fun getStatsTextArrayIndex(number: Int) = when (number) {
            in 0..4 -> 0
            in 5..49 -> 1
            in 50..99 -> 2
            in 100..499 -> 3
            in 500..999 -> 4
            in 1000..4999 -> 5
            in 5000..9999 -> 6
            in 10000..49999 -> 7
            in 50000..99999 -> 8
            in 100000..199999 -> 9
            in 200000..399999 -> 10
            /*in 400000..799999 -> 11
            in 800000..1999999 -> 12
            in 2000000..3999999 -> 13
            in 4000000..7999999 -> 14
            in 8000000..999999990000 -> 15*/
            else -> 0
        }

        fun getLevel(number: Int) = when (number) {
            in 0..9 -> 1
            in 10..49 -> 2
            in 50..99 -> 3
            in 100..499 -> 4
            in 500..999 -> 5
            in 1000..4999 -> 6
            in 5000..9999 -> 7
            in 10000..49999 -> 8
            in 50000..99999 -> 9
            in 100000..199999 -> 10
            in 200000..399999 -> 11
            in 400000..999999 -> 12
            in 1000000..1999999 -> 13
            in 2000000..3999999 -> 14
            in 4000000..99999990000 -> 15
            else -> 1
        }

    }

}