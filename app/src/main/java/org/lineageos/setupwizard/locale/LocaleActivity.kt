/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.locale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.android.internal.app.LocaleHelper
import com.android.internal.app.LocaleStore
import com.android.internal.telephony.TelephonyIntents
import com.android.internal.telephony.util.LocaleUtils
import com.google.android.setupcompat.util.SystemBarHelper
import com.google.android.setupdesign.GlifRecyclerLayout
import com.google.android.setupdesign.items.Item
import com.google.android.setupdesign.items.ItemGroup
import com.google.android.setupdesign.items.RecyclerItemAdapter
import com.google.android.setupdesign.items.SectionItem
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.lineageos.setupwizard.R
import org.lineageos.setupwizard.SetupWizardApp
import org.lineageos.setupwizard.base.BaseSetupWizardActivity

/**
 * Languages first, then the regions of the language that was picked, if it has more than one.
 * Picking a locale applies it and moves on, so there is no button to press afterwards.
 */
class LocaleActivity : BaseSetupWizardActivity() {

    private val recyclerLayout by lazy { glifLayout as GlifRecyclerLayout }

    /** Set while showing the regions of a language, null while showing the languages. */
    private var parentLanguage: LocaleStore.LocaleInfo? = null

    /** Enabled only while the regions are up, where back returns to the languages. */
    private val regionsBackCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showLanguages()
            }
        }

    private val handler = Handler(Looper.getMainLooper())
    private val fetchSimLocaleExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }
    private var pendingLocaleUpdate = false
    private var paused = true

    private val setupWizardApp: SetupWizardApp by lazy { application as SetupWizardApp }

    private val simChangedReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == TelephonyIntents.ACTION_SIM_STATE_CHANGED) {
                    fetchAndUpdateSimLocale()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBarHelper.setBackButtonVisible(window, true)

        // Back steps out of the regions before it leaves the screen.
        onBackPressedDispatcher.addCallback(this, regionsBackCallback)

        showLanguages()
        fetchAndUpdateSimLocale()
    }

    override fun onPause() {
        super.onPause()
        paused = true
        unregisterReceiver(simChangedReceiver)
    }

    override fun onResume() {
        super.onResume()
        paused = false
        registerReceiver(
            simChangedReceiver,
            IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED),
        )
        if (pendingLocaleUpdate) {
            pendingLocaleUpdate = false
            fetchAndUpdateSimLocale()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        fetchSimLocaleExecutor.shutdownNow()
    }

    override val layoutResId: Int = R.layout.setup_locale

    override val titleResId: Int = R.string.setup_locale

    override val iconResId: Int = R.drawable.ic_locale

    /** Picking a locale is what moves the wizard along here. */
    override val installFooterBar: Boolean = false

    private fun showLanguages() {
        parentLanguage = null
        regionsBackCallback.isEnabled = false
        show(levelLocales(parent = null), countryMode = false)
    }

    private fun showRegions(language: LocaleStore.LocaleInfo) {
        parentLanguage = language
        regionsBackCallback.isEnabled = true
        show(levelLocales(parent = language), countryMode = true)
    }

    private fun levelLocales(parent: LocaleStore.LocaleInfo?): List<LocaleStore.LocaleInfo> =
        LocaleStore.getLevelLocales(this, emptySet(), parent, /* translatedOnly= */ true)
            .sortedWith(LocaleHelper.LocaleInfoComparator(Locale.getDefault(), parent != null))

    /**
     * The locales the device suggests, typically from the SIM, are grouped ahead of the rest. An
     * untitled section is what separates the two groups into their own cards.
     */
    private fun show(locales: List<LocaleStore.LocaleInfo>, countryMode: Boolean) {
        val (suggested, remaining) = locales.partition { it.isSuggested }

        val root = ItemGroup()
        suggested.forEach { root.addChild(localeItem(it, countryMode)) }

        if (remaining.isNotEmpty()) {
            val section = SectionItem()
            remaining.forEach { section.addChild(localeItem(it, countryMode)) }
            if (suggested.isNotEmpty()) {
                // A header only counts as a group divider once it has text, and the divider is
                // what gives the two groups their own cards. There is nothing to say, so it is
                // empty rather than titled.
                section.setHeaderTitle("")
            }
            root.addChild(section)
        }

        val adapter = RecyclerItemAdapter(root)
        adapter.setOnItemSelectedListener { item ->
            (item as? LocaleItem)?.let { onItemSelected(it) }
        }
        recyclerLayout.adapter = adapter
        recyclerLayout.recyclerView.scrollToPosition(0)
    }

    private fun localeItem(localeInfo: LocaleStore.LocaleInfo, countryMode: Boolean) =
        LocaleItem(localeInfo).apply {
            title =
                if (countryMode) {
                    localeInfo.fullCountryNameNative
                } else {
                    localeInfo.fullNameNative
                }
        }

    private fun onItemSelected(item: LocaleItem) {
        val localeInfo = item.localeInfo
        // A language with more than one region asks which one before it is applied.
        if (parentLanguage == null && levelLocales(parent = localeInfo).size > 1) {
            showRegions(localeInfo)
            return
        }
        setupWizardApp.ignoreSimLocale = true
        applyLocale(localeInfo.locale)
    }

    private fun applyLocale(locale: Locale) {
        com.android.internal.app.LocalePicker.updateLocale(locale)
        nextAction(RESULT_OK)
    }

    private fun fetchAndUpdateSimLocale() {
        if (setupWizardApp.ignoreSimLocale || isDestroyed) {
            return
        }
        if (paused) {
            pendingLocaleUpdate = true
            return
        }
        fetchSimLocaleExecutor.execute {
            val locale = simLocale() ?: return@execute
            handler.post {
                if (
                    locale == Locale.getDefault() ||
                        setupWizardApp.ignoreSimLocale ||
                        isDestroyed
                ) {
                    return@post
                }
                Toast.makeText(
                        this,
                        getString(R.string.sim_locale_changed, locale.displayName),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                setupWizardApp.ignoreSimLocale = true
                com.android.internal.app.LocalePicker.updateLocale(locale)
            }
        }
    }

    /** The locale the active SIM asks for, by its MCC and failing that its own preference. */
    private fun simLocale(): Locale? {
        if (isFinishing || isDestroyed) {
            return null
        }
        val telephonyManager = getSystemService(TelephonyManager::class.java) ?: return null

        // If the sim is currently pin locked, return
        val state = telephonyManager.simState
        if (
            state == TelephonyManager.SIM_STATE_PIN_REQUIRED ||
                state == TelephonyManager.SIM_STATE_PUK_REQUIRED
        ) {
            return null
        }

        val subscriptionManager = getSystemService(SubscriptionManager::class.java)
        val activeSub = subscriptionManager?.activeSubscriptionInfoList?.firstOrNull() ?: return null

        // Fetch locale for active sim's MCC
        val mccString = activeSub.mccString
        val mcc = mccString?.toIntOrNull()
        if (mcc == null) {
            Log.w(TAG, "Unexpected mccString: '$mccString'")
        } else {
            LocaleUtils.getLocaleFromMccMnc(this, mcc, null, null)?.let {
                return it
            }
        }

        // If that fails, fall back to preferred languages reported by the sim
        return telephonyManager.simLocale
    }

    /** Carries the locale a row stands for, so the tap handler does not have to look it up. */
    private class LocaleItem(val localeInfo: LocaleStore.LocaleInfo) : Item()

    companion object {
        private const val TAG = "LocaleActivity"
    }
}
