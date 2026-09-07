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
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import com.android.internal.telephony.TelephonyIntents
import com.android.internal.telephony.util.LocaleUtils
import com.google.android.setupcompat.util.SystemBarHelper
import com.google.android.setupdesign.GlifRecyclerLayout
import com.google.android.setupdesign.items.ItemGroup
import com.google.android.setupdesign.items.RadioButtonItem
import com.google.android.setupdesign.items.RecyclerItemAdapter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.lineageos.setupwizard.R
import org.lineageos.setupwizard.SetupWizardApp
import org.lineageos.setupwizard.base.BaseSetupWizardActivity

class LocaleActivity : BaseSetupWizardActivity() {

    private val recyclerLayout by lazy { glifLayout as GlifRecyclerLayout }

    private val locales = mutableListOf<Locale>()
    private val localeItems = mutableListOf<RadioButtonItem>()
    private var selectedIndex = -1

    private var currentLocale: Locale? = null

    private val handler = Handler(Looper.getMainLooper())
    private val fetchSimLocaleExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }
    private var pendingLocaleUpdate = false
    private var paused = true

    private val setupWizardApp: SetupWizardApp by lazy { application as SetupWizardApp }

    private val updateLocale = Runnable {
        val locale = currentLocale ?: return@Runnable
        com.android.internal.app.LocalePicker.updateLocale(locale)
    }

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
        setNextText(R.string.next)
        currentLocale = Locale.getDefault()
        loadLanguages()
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
        // Neither may outlive the screen: the locale is applied with a delay, and the lookup
        // posts its result back here.
        handler.removeCallbacks(updateLocale)
        fetchSimLocaleExecutor.shutdownNow()
    }

    override val layoutResId: Int = R.layout.setup_locale

    override val titleResId: Int = R.string.setup_locale

    override val iconResId: Int = R.drawable.ic_locale

    private fun loadLanguages() {
        val isInDeveloperMode =
            Settings.Global.getInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0,
            ) != 0
        val localeInfos =
            com.android.internal.app.LocalePicker.getAllAssetLocales(this, isInDeveloperMode)

        val itemGroup = ItemGroup()
        for (localeInfo in localeInfos) {
            val item =
                RadioButtonItem().apply {
                    title = localeInfo.label
                    isChecked = localeInfo.locale == currentLocale
                    setOnCheckedChangeListener { checkedItem, isChecked ->
                        if (isChecked) {
                            onLocalePicked(checkedItem)
                        }
                    }
                }
            locales += localeInfo.locale
            localeItems += item
            itemGroup.addChild(item)
        }

        selectedIndex = localeItems.indexOfFirst { it.isChecked }

        val adapter = RecyclerItemAdapter(itemGroup)
        adapter.setOnItemSelectedListener { item ->
            (item as? RadioButtonItem)?.let { onLocalePicked(it) }
        }
        recyclerLayout.adapter = adapter
        scrollToChecked()
    }

    private fun scrollToChecked() {
        if (selectedIndex >= 0) {
            recyclerLayout.recyclerView.scrollToPosition(selectedIndex)
        }
    }

    private fun onLocalePicked(item: RadioButtonItem) {
        val index = localeItems.indexOf(item)
        if (index < 0 || index == selectedIndex) {
            return
        }
        // The user made a choice, a SIM showing up later should not override it.
        setupWizardApp.ignoreSimLocale = true
        select(index)
        onLocaleChanged(locales[index])
    }

    /**
     * [RadioButtonItem] does not know about its siblings, so the group is kept exclusive here.
     * [selectedIndex] is set first because checking an item calls back into [onLocalePicked].
     */
    private fun select(index: Int) {
        selectedIndex = index
        localeItems.forEachIndexed { itemIndex, item -> item.isChecked = itemIndex == index }
    }

    private fun onLocaleChanged(locale: Locale) {
        handler.removeCallbacks(updateLocale)
        currentLocale = locale
        handler.postDelayed(updateLocale, LOCALE_UPDATE_DELAY_MS)
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
                if (locale == currentLocale || setupWizardApp.ignoreSimLocale || isDestroyed) {
                    return@post
                }
                Toast.makeText(
                        this,
                        getString(R.string.sim_locale_changed, locale.displayName),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                val index = locales.indexOf(locale)
                if (index >= 0) {
                    select(index)
                    scrollToChecked()
                }
                onLocaleChanged(locale)
                setupWizardApp.ignoreSimLocale = true
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
        val activeSub =
            subscriptionManager?.activeSubscriptionInfoList?.firstOrNull() ?: return null

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

    companion object {
        private const val TAG = "LocaleActivity"

        /** Long enough that picking through the list does not restart the wizard each time. */
        private const val LOCALE_UPDATE_DELAY_MS = 1000L
    }
}
