package org.commonvoice.saverio

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_speak.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonvoice.saverio.databinding.ActivitySpeakBinding
import org.commonvoice.saverio.ui.dialogs.NoClipsSentencesAvailableDialog
import org.commonvoice.saverio.ui.dialogs.SpeakReportDialogFragment
import org.commonvoice.saverio.ui.viewBinding.ViewBoundActivity
import org.commonvoice.saverio.utils.OnSwipeTouchListener
import org.commonvoice.saverio.utils.onClick
import org.commonvoice.saverio_ads.AdLoader
import org.commonvoice.saverio_lib.api.network.ConnectionManager
import org.commonvoice.saverio_lib.dataClasses.BadgeDialogMediator
import org.commonvoice.saverio_lib.models.Sentence
import org.commonvoice.saverio_lib.preferences.SettingsPrefManager
import org.commonvoice.saverio_lib.preferences.SpeakPrefManager
import org.commonvoice.saverio_lib.preferences.StatsPrefManager
import org.commonvoice.saverio_lib.viewmodels.SpeakViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.stateViewModel
import java.util.*

class SpeakActivity : ViewBoundActivity<ActivitySpeakBinding>(
    ActivitySpeakBinding::inflate
) {

    companion object {
        private const val RECORD_REQUEST_CODE = 101
    }

    private val speakViewModel: SpeakViewModel by stateViewModel()

    private val connectionManager: ConnectionManager by inject()
    private val statsPrefManager: StatsPrefManager by inject()

    private var numberSentThisSession: Int = 0
    private var verticalScrollStatus: Int = 2 //0 top, 1 middle, 2 end

    private var isAudioBarVisible: Boolean = false
    private var animationsCount: Int = 0

    private val settingsPrefManager by inject<SettingsPrefManager>()
    private val speakPrefManager by inject<SpeakPrefManager>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupInitialUIState()

        setupUI()
    }

    private fun checkOfflineMode(available: Boolean) {
        if (!speakViewModel.showingHidingOfflineIcon && (speakViewModel.offlineModeIconVisible == available)) {
            speakViewModel.showingHidingOfflineIcon = true
            if (!available && settingsPrefManager.isOfflineMode) {
                startAnimation(binding.imageOfflineModeSpeak, R.anim.zoom_in_speak_listen)
                speakViewModel.offlineModeIconVisible = true
                if (mainPrefManager.showOfflineModeMessage) {
                    showMessageDialog("", "", 10)
                }
            } else if (!settingsPrefManager.isOfflineMode) {
                showMessageDialog("", getString(R.string.offline_mode_is_not_enabled), type = 13)
            } else {
                startAnimation(binding.imageOfflineModeSpeak, R.anim.zoom_out_speak_listen)
                speakViewModel.offlineModeIconVisible = false
            }
            speakViewModel.showingHidingOfflineIcon = false
            binding.imageOfflineModeSpeak.isGone = available
        }
    }

    fun setShowOfflineModeMessage(value: Boolean = true) {
        mainPrefManager.showOfflineModeMessage = value
    }

    override fun onBackPressed() = withBinding {
        textMessageAlertSpeak.setText(R.string.txt_closing)
        buttonStartStopSpeak.setBackgroundResource(R.drawable.speak_cv)
        textSentenceSpeak.text = "···"
        textSentenceSpeak.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.title_very_big)
        )
        buttonRecordOrListenAgain.isGone = true
        if (settingsPrefManager.showReportIcon) {
            hideImage(imageReportIconSpeak)
        } else {
            buttonReportSpeak.isGone = true
        }
        buttonSkipSpeak.isEnabled = false
        buttonStartStopSpeak.isEnabled = false
        buttonSendSpeak.isGone = true
        speakSectionAudioBar.isGone = true

        speakViewModel.stop(true)

        hideAudioBar()

        super.onBackPressed()
    }

    private fun setupUI() {
        binding.imageOfflineModeSpeak.onClick {
            lifecycleScope.launch {
                val count = speakViewModel.getSentencesCount()
                withContext(Dispatchers.Main) {
                    NoClipsSentencesAvailableDialog(this@SpeakActivity, true, count, theme).show()
                }
            }
        }

        connectionManager.liveInternetAvailability.observe(this, { available ->
            checkOfflineMode(available)
        })

        speakViewModel.hasFinishedSentences.observe(this, {
            if (it && !connectionManager.isInternetAvailable) {
                NoClipsSentencesAvailableDialog(this, true, 0, theme).show {
                    onBackPressed()
                }
            }
        })

        speakViewModel.currentSentence.observe(this, { sentence ->
            setupUIStateStandby(sentence)
        })

        if (mainPrefManager.areGesturesEnabled) {
            setupGestures()
        }

        speakViewModel.state.observe(this, {
            checkState(it)
        })

        statsPrefManager.dailyGoal.observe(this, {
            if ((numberSentThisSession > 0) && it.checkDailyGoal()) {
                stopAndRefresh()
                showMessageDialog(
                    "",
                    getString(R.string.daily_goal_achieved_message).replace(
                        "{{*{{n_clips}}*}}",
                        "${it.validations}"
                    ).replace(
                        "{{*{{n_sentences}}*}}",
                        "${it.recordings}"
                    ), type = 12
                )
            }

            animateProgressBar(
                progressBarSpeakSpeak,
                sum = it.recordings + it.validations,
                dailyGoal = it.getDailyGoal(),
                currentContributions = it.recordings,
                color = R.color.colorSpeak
            )
            animateProgressBar(
                progressBarSpeakListen,
                sum = it.recordings + it.validations,
                dailyGoal = it.getDailyGoal(),
                currentContributions = it.validations,
                color = R.color.colorListen
            )
        })

        checkPermission()

        setupNestedScroll()

        setTheme(this)

        setupBadgeDialog()

        if (speakPrefManager.showAdBanner) {
            AdLoader.setupSpeakAdView(this, binding.adContainer)
        }
    }

    fun refreshAds() {
        if (speakPrefManager.showAdBanner) {
            AdLoader.setupSpeakAdView(this, binding.adContainer)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)


        animateProgressBar(
            progressBarSpeakSpeak,
            sum = statsPrefManager.dailyGoal.value!!.recordings + statsPrefManager.dailyGoal.value!!.validations,
            dailyGoal = statsPrefManager.dailyGoal.value!!.goal,
            currentContributions = statsPrefManager.dailyGoal.value!!.recordings,
            color = R.color.colorSpeak
        )
        animateProgressBar(
            progressBarSpeakListen,
            sum = statsPrefManager.dailyGoal.value!!.recordings + statsPrefManager.dailyGoal.value!!.validations,
            dailyGoal = statsPrefManager.dailyGoal.value!!.goal,
            currentContributions = statsPrefManager.dailyGoal.value!!.validations,
            color = R.color.colorListen
        )

        refreshAds()
    }

    fun shareCVAndroidDailyGoal() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "type/palin"
        val textToShare = getString(R.string.share_daily_goal_text_on_social).replace(
            "{{*{{link}}*}}",
            "https://bit.ly/2XhnO7h"
        )
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, textToShare)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_daily_goal_title)))
    }

    private fun checkState(status: SpeakViewModel.Companion.State?) {
        when (status) {
            SpeakViewModel.Companion.State.STANDBY -> {
                loadUIStateLoading()
                speakViewModel.loadNewSentence()
            }
            SpeakViewModel.Companion.State.NO_MORE_SENTENCES -> {
                loadUIStateNoMoreSentences()
                speakViewModel.loadNewSentence()
            }
            SpeakViewModel.Companion.State.RECORDING -> {
                loadUIStateRecording()
                isAudioBarVisible = true
                animateAudioBar()
            }
            SpeakViewModel.Companion.State.RECORDED -> {
                loadUIStateRecorded()
            }
            SpeakViewModel.Companion.State.LISTENING -> {
                loadUIStateListening()
            }
            SpeakViewModel.Companion.State.LISTENED -> {
                loadUIStateListened()
            }
            SpeakViewModel.Companion.State.RECORDING_ERROR -> {
                this.stopAndRefresh()
                showMessageDialog("", getString(R.string.messageDialogGenericError), type = 7)
                speakViewModel.currentSentence.value?.let { sentence ->
                    setupUIStateStandby(sentence)
                }
            }
            SpeakViewModel.Companion.State.RECORDING_TOO_SHORT -> {
                showMessageDialog("", getString(R.string.txt_recording_too_short), type = 7)
                speakViewModel.currentSentence.value?.let { sentence ->
                    setupUIStateStandby(sentence)
                }
            }
            SpeakViewModel.Companion.State.RECORDING_TOO_LONG -> {
                showMessageDialog("", getString(R.string.txt_recording_too_long), type = 7)
                speakViewModel.currentSentence.value?.let { sentence ->
                    setupUIStateStandby(sentence)
                }
            }
        }
    }

    private fun showMessageDialog(title: String, text: String, type: Int = 0) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        //val width = metrics.widthPixels
        val height = metrics.heightPixels
        val msg = MessageDialog(this, type, title, text, details = "", height = height)
        msg.setSpeakActivity(this)
        msg.show()
    }

    private fun setupNestedScroll() {
        binding.nestedScrollSpeak.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { nestedScrollView, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY) {
                verticalScrollStatus = 1
            }
            if (scrollY < oldScrollY) {
                verticalScrollStatus = 1
            }
            if (scrollY == 0) {
                verticalScrollStatus = 0
            }
            if (nestedScrollView.measuredHeight == (nestedScrollView.getChildAt(0).measuredHeight - scrollY)) {
                verticalScrollStatus = 2
            }
        })
    }

    private fun setupGestures() {
        binding.nestedScrollSpeak.setOnTouchListener(object :
            OnSwipeTouchListener(this@SpeakActivity) {
            override fun onSwipeLeft() {
                speakViewModel.skipSentence()
            }

            override fun onSwipeRight() {
                onBackPressed()
            }

            override fun onSwipeTop() {
                if (verticalScrollStatus == 2) {
                    openReportDialog()
                }
            }
        })
    }

    fun setTheme(view: Context) = withBinding {
        theme.setElement(layoutSpeak)
        theme.setElement(view, buttonSendSpeak)
        theme.setElement(view, 1, speakSectionBottom)
        theme.setElement(
            view,
            textMessageAlertSpeak,
            R.color.colorAlertMessage,
            R.color.colorAlertMessageDT,
            textSize = 15F
        )
        theme.setElement(view, buttonReportSpeak, background = false)
        theme.setElement(view, buttonSkipSpeak)

        setProgressBarColour(progressBarSpeakSpeak, false)
        setProgressBarColour(progressBarSpeakListen, false)
    }

    private fun openReportDialog() {
        if (!binding.buttonReportSpeak.isGone || !binding.imageReportIconSpeak.isGone) {
            if (speakViewModel.state.value == SpeakViewModel.Companion.State.RECORDING) {
                speakViewModel.stopRecording()
            }

            SpeakReportDialogFragment().show(supportFragmentManager, "SPEAK_REPORT")
        }
    }

    private fun stopAndRefresh() {
        speakViewModel.stop()
        speakViewModel.currentSentence.value?.let { sentence ->
            setupUIStateStandby(sentence)
        }
        hideAudioBar()
    }

    private fun setupInitialUIState() = withBinding {
        buttonSkipSpeak.onClick {
            speakViewModel.skipSentence()
        }

        buttonReportSpeak.onClick {
            openReportDialog()
        }

        imageReportIconSpeak.onClick {
            openReportDialog()
        }

        buttonRecordOrListenAgain.onClick {
            speakViewModel.startListening()
        }

        buttonSendSpeak.onClick {
            speakViewModel.sendRecording()
            numberSentThisSession++
            if (numberSentThisSession % 10 == 0) {
                //refreshAds()
            }
        }

        startAnimation(buttonStartStopSpeak, R.anim.zoom_in_speak_listen)
        startAnimation(buttonSkipSpeak, R.anim.zoom_in_speak_listen)
    }

    private fun loadUIStateLoading() = withBinding {
        textMessageAlertSpeak.setText(R.string.txt_loading_sentence)
        textSentenceSpeak.text = "···"

        resizeSentence()

        buttonRecordOrListenAgain.isGone = true
        if (settingsPrefManager.showReportIcon) {
            hideImage(imageReportIconSpeak)
        } else {
            buttonReportSpeak.isGone = true
        }
        buttonSendSpeak.isGone = true
        buttonSkipSpeak.isEnabled = false
        buttonStartStopSpeak.isEnabled = false
    }

    private fun loadUIStateNoMoreSentences() = withBinding {
        textMessageAlertSpeak.setText(R.string.txt_common_voice_sentences_finished)
        textSentenceSpeak.text = "···"

        resizeSentence()

        buttonRecordOrListenAgain.isGone = true
        if (settingsPrefManager.showReportIcon) {
            hideImage(imageReportIconSpeak)
        } else {
            buttonReportSpeak.isGone = true
        }
        buttonSendSpeak.isGone = true
        buttonSkipSpeak.isEnabled = false
        buttonStartStopSpeak.isEnabled = false
    }

    private fun setupUIStateStandby(sentence: Sentence) = withBinding {
        buttonSkipSpeak.isEnabled = true
        buttonStartStopSpeak.isEnabled = true

        if (settingsPrefManager.showReportIcon) {
            showImage(imageReportIconSpeak)
        } else {
            buttonReportSpeak.isGone = false
        }

        buttonSendSpeak.isGone = true

        buttonRecordOrListenAgain.isGone = true
        buttonStartStopSpeak.setBackgroundResource(R.drawable.speak_cv)

        hideAudioBar()

        textMessageAlertSpeak.setText(R.string.txt_press_icon_below_speak_1)
        textSentenceSpeak.text = sentence.sentenceText

        resizeSentence()

        buttonStartStopSpeak.onClick {
            checkPermission()
            speakViewModel.startRecording()
        }
    }

    private fun resizeSentence() {
        binding.textSentenceSpeak.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            when (binding.textSentenceSpeak.text.length) {
                in 0..10 -> resources.getDimension(R.dimen.title_very_big) * mainPrefManager.textSize
                in 11..20 -> resources.getDimension(R.dimen.title_big) * mainPrefManager.textSize
                in 21..40 -> resources.getDimension(R.dimen.title_medium) * mainPrefManager.textSize
                in 41..70 -> resources.getDimension(R.dimen.title_normal) * mainPrefManager.textSize
                else -> resources.getDimension(R.dimen.title_small) * mainPrefManager.textSize
            }
        )
    }

    private fun loadUIStateRecording() = withBinding {
        buttonRecordOrListenAgain.isGone = true
        buttonStartStopSpeak.setBackgroundResource(R.drawable.stop_cv)

        buttonSendSpeak.isGone = true
        textMessageAlertSpeak.setText(R.string.txt_press_icon_below_speak_2)
        speakViewModel.isFirstTimeListening = true

        buttonStartStopSpeak.onClick {
            checkPermission()
            speakViewModel.stopRecording()
        }
    }

    private fun loadUIStateRecorded() = withBinding {
        buttonRecordOrListenAgain.isGone = false
        startAnimation(buttonRecordOrListenAgain, R.anim.zoom_in_speak_listen)
        buttonRecordOrListenAgain.setBackgroundResource(R.drawable.speak2_cv)

        buttonStartStopSpeak.setBackgroundResource(R.drawable.listen2_cv)
        textMessageAlertSpeak.setText(R.string.txt_press_icon_below_listen_1)

        buttonStartStopSpeak.onClick {
            speakViewModel.startListening()
        }

        buttonRecordOrListenAgain.onClick {
            checkPermission()
            speakViewModel.redoRecording()
        }
    }

    private fun loadUIStateListening() = withBinding {
        buttonRecordOrListenAgain.isGone = true
        buttonStartStopSpeak.setBackgroundResource(R.drawable.stop_cv)
        textMessageAlertSpeak.setText(R.string.txt_press_icon_below_listen_2)

        buttonStartStopSpeak.onClick {
            speakViewModel.stopListening()
        }
    }

    private fun loadUIStateListened() = withBinding {
        buttonSendSpeak.isGone = false

        if (speakViewModel.isFirstTimeListening) {
            startAnimation(buttonSendSpeak, R.anim.zoom_in_speak_listen)
            speakViewModel.isFirstTimeListening = false
        }

        textMessageAlertSpeak.setText(R.string.txt_recorded_correct_or_wrong)
        buttonRecordOrListenAgain.isGone = false
        startAnimation(buttonRecordOrListenAgain, R.anim.zoom_in_speak_listen)
        buttonRecordOrListenAgain.setBackgroundResource(R.drawable.listen2_cv)
        buttonStartStopSpeak.setBackgroundResource(R.drawable.speak2_cv)


        buttonStartStopSpeak.onClick {
            checkPermission()
            speakViewModel.redoRecording()
        }

        buttonRecordOrListenAgain.onClick {
            speakViewModel.startListening()
        }
    }

    private fun setupBadgeDialog(): Any = if (mainPrefManager.isLoggedIn) {
        lifecycleScope.launch {
            statsPrefManager.badgeLiveData.collect {
                if (it is BadgeDialogMediator.Speak || it is BadgeDialogMediator.Level) {
                    showMessageDialog(
                        title = "",
                        text = getString(R.string.new_badge_earnt_message)
                            .replace("{{*{{profile}}*}}", getString(R.string.button_home_profile))
                            .replace(
                                "{{*{{all_badges}}*}}",
                                getString(R.string.btn_badges_loggedin)
                            )
                    )
                }
            }
        }
    } else Unit

    private fun checkPermission() {
        var conditions = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
        var permissionsArray = arrayOf(Manifest.permission.RECORD_AUDIO)
        if (speakPrefManager.saveRecordingsOnDevice) {
            conditions = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            permissionsArray = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        if (conditions) {
            ActivityCompat.requestPermissions(
                this,
                permissionsArray,
                RECORD_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        var conditions =
            grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED
        if (speakPrefManager.saveRecordingsOnDevice) conditions =
            grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            RECORD_REQUEST_CODE -> {
                if (conditions) {
                    onBackPressed()
                }
            }
        }
    }

    private fun animateProgressBar(
        progressBar: View,
        sum: Int = 0,
        dailyGoal: Int = 0,
        currentContributions: Int = 0,
        color: Int = R.color.colorBlack
    ) {
        val view: View = progressBar
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        //val height = metrics.heightPixels
        var newValue = 0

        if (dailyGoal == 0 || sum == 0) {
            newValue = width / 2
            setProgressBarColour(progressBar, forced = true)
        } else if (sum >= dailyGoal) {
            val tempContributions =
                (currentContributions.toFloat() * dailyGoal.toFloat()) / sum.toFloat()
            newValue =
                ((tempContributions.toFloat() / dailyGoal.toFloat()) * width).toInt()
            setProgressBarColour(progressBar, forced = false, color = color)
            progressBar.isGone = false
        } else if (currentContributions == 0) {
            progressBar.isGone = true
            progressBar.layoutParams.width = 1
            progressBar.requestLayout()
        } else {
            //currentRecordingsValidations : dailyGoal = X : 1 ==> currentRecordingsValidations / dailyGoal
            newValue =
                ((currentContributions.toFloat() / dailyGoal.toFloat()) * width).toInt()
            setProgressBarColour(progressBar, forced = false, color = color)
            progressBar.isGone = false
        }

        if (!view.isGone) {
            if (mainPrefManager.areAnimationsEnabled) {
                animationProgressBar(progressBar, view.width, newValue)
            } else {
                view.layoutParams.width = newValue
                view.requestLayout()
            }
        }
    }

    private fun setProgressBarColour(
        progressBar: View,
        forced: Boolean = false,
        color: Int = R.color.colorBlack
    ) {
        if (!settingsPrefManager.isProgressBarColouredEnabled || forced) {
            theme.setElement(
                this,
                progressBar,
                R.color.colorPrimaryDark,
                R.color.colorLightGray
            )
        } else {
            //coloured
            theme.setElement(
                this,
                progressBar,
                color,
                color
            )
        }
    }

    private fun animationProgressBar(progressBar: View, min: Int, max: Int) {
        val view: View = progressBar
        val animation: ValueAnimator =
            ValueAnimator.ofInt(min, max)
        animation.duration = 1000
        animation.addUpdateListener { anim ->
            val value = anim.animatedValue as Int
            view.layoutParams.width = value
            view.requestLayout()
        }
        animation.start()
    }

    private fun animateAudioBar() {
        if (mainPrefManager.areAnimationsEnabled) {
            this.animationsCount++
            binding.speakSectionAudioBar.children.forEach {
                animateAudioBar(it, animationsCount)
            }
        }
    }

    private fun hideAudioBar() {
        if (mainPrefManager.areAnimationsEnabled) {
            if (binding.imageAudioBarCenter.isVisible && isAudioBarVisible) {
                isAudioBarVisible = false
                binding.speakSectionAudioBar.children.forEach {
                    animateAudioBar(it, animationsCount)
                }
            }
        }
    }

    private fun animateAudioBar(view: View, animationsCountTemp: Int) {
        if (speakViewModel.state.value == SpeakViewModel.Companion.State.RECORDING && this.isAudioBarVisible) {
            animationAudioBar(view, view.height, (30..350).random(), animationsCountTemp)
            view.isVisible = true
        } else if (this.isAudioBarVisible && view.height >= 30) {
            animationAudioBar(view, view.height, 2, animationsCountTemp, forced = true)
            view.isVisible = true
        } else {
            view.isVisible = false
        }
    }

    private fun animationAudioBar(
        view: View,
        min: Int,
        max: Int,
        animationsCountTemp: Int,
        forced: Boolean = false
    ) {
        val animation: ValueAnimator =
            ValueAnimator.ofInt(min, max)
        animation.duration = 300
        animation.addUpdateListener { anim ->
            val value = anim.animatedValue as Int
            view.layoutParams.height = value
            view.requestLayout()
        }
        animation.doOnEnd {
            if (this.animationsCount == animationsCountTemp && forced && max == 2) {
                view.isVisible = false
            }
            if (this.isAudioBarVisible && view.isVisible && !forced && this.animationsCount == animationsCountTemp) {
                animateAudioBar(view, animationsCountTemp)
            }
        }
        animation.start()
    }

    private fun showImage(image: ImageView) {
        if (!image.isVisible) {
            image.isVisible = true
            image.isEnabled = true
            startAnimation(
                image,
                R.anim.zoom_in_speak_listen
            )
        }
    }

    private fun hideImage(image: ImageView, stop: Boolean = true) {
        if (stop) stopImage(image)
        image.isEnabled = false
        startAnimation(
            image,
            R.anim.zoom_out_speak_listen
        )
        image.isVisible = false
    }

    private fun stopImage(image: ImageView) {
        stopAnimation(image)
    }

}
