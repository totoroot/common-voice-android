package org.commonvoice.saverio.ui.settings.nestedSettings

import android.view.LayoutInflater
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_listen_settings.*
import kotlinx.android.synthetic.main.fragment_offline_settings.*
import org.commonvoice.saverio.databinding.FragmentListenSettingsBinding
import org.commonvoice.saverio.ui.viewBinding.ViewBoundFragment
import org.commonvoice.saverio.utils.setupOnSwipeRight
import org.commonvoice.saverio_lib.preferences.ListenPrefManager
import org.commonvoice.saverio_lib.preferences.MainPrefManager
import org.commonvoice.saverio_lib.preferences.SettingsPrefManager
import org.koin.android.ext.android.inject

class ListenSettingsFragment : ViewBoundFragment<FragmentListenSettingsBinding>() {

    override fun inflate(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentListenSettingsBinding {
        return FragmentListenSettingsBinding.inflate(layoutInflater, container, false)
    }

    private val mainPrefManager by inject<MainPrefManager>()
    private val settingsPrefManager by inject<SettingsPrefManager>()
    private val listenPrefManager by inject<ListenPrefManager>()

    override fun onStart() {
        super.onStart()

        buttonBackSettingsSubSectionListen.setOnClickListener {
            activity?.onBackPressed()
        }

        withBinding {
            if (mainPrefManager.areGesturesEnabled)
                nestedScrollSettingsListen.setupOnSwipeRight(requireContext()) { activity?.onBackPressed() }

            switchAutoPlayClips.setOnCheckedChangeListener { _, isChecked ->
                listenPrefManager.isAutoPlayClipEnabled = isChecked
            }
            switchAutoPlayClips.isChecked = listenPrefManager.isAutoPlayClipEnabled

            switchShowSentencesTextWhenClipsCompleted.setOnCheckedChangeListener { _, isChecked ->
                listenPrefManager.isShowTheSentenceAtTheEnd = isChecked
            }
            switchShowSentencesTextWhenClipsCompleted.isChecked =
                listenPrefManager.isShowTheSentenceAtTheEnd
        }

        setTheme()
    }

    fun setTheme() {
        withBinding {
            theme.setElement(layoutSettingsListen)

            theme.setElements(requireContext(), settingsSectionListen)

            theme.setElement(requireContext(), 3, settingsSectionListen)

            theme.setTitleBar(requireContext(), titleSettingsSubSectionListen, textSize = 20F)
        }
    }

}