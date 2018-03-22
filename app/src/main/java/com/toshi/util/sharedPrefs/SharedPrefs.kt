/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.util.sharedPrefs

import android.content.Context
import com.toshi.exception.CurrencyException
import com.toshi.model.local.Network
import com.toshi.util.CurrencyUtil
import com.toshi.util.FileNames
import com.toshi.view.BaseApplication

object SharedPrefs : SharedPrefsInterface {
    private const val STORED_QR_CODE = "STORED_QR_CODE"
    private const val HAS_ONBOARDED = "hasOnboarded"
    private const val HAS_SIGNED_OUT = "hasSignedIn"
    private const val HAS_BACKED_UP_PHRASE = "hasBackedUpPhrase"
    private const val HAS_LOADED_APP_FIRST_TIME = "hasLoadedAppFirstTime"
    private const val LOCAL_CURRENCY_CODE = "localCurrencyCode"
    private const val WAS_MIGRATED = "wasMigrated"
    private const val FORCE_USER_UPDATE = "forceUserUpdate_2"
    private const val CURRENT_NETWORK = "currentNetwork"
    private const val HAS_CLEARED_NOTIFICATION_CHANNELS = "hasClearedNotificationChannels"

    private val prefs by lazy { BaseApplication.get().getSharedPreferences(FileNames.USER_PREFS, Context.MODE_PRIVATE) }

    override fun hasOnboarded(): Boolean = prefs.getBoolean(HAS_ONBOARDED, false)

    override fun setHasOnboarded(hasOnboarded: Boolean) = prefs.edit().putBoolean(HAS_ONBOARDED, hasOnboarded).apply()

    override fun hasLoadedApp(): Boolean = prefs.getBoolean(HAS_LOADED_APP_FIRST_TIME, false)

    override fun setHasLoadedApp() = prefs.edit().putBoolean(HAS_LOADED_APP_FIRST_TIME, true).apply()

    override fun setSignedIn() = prefs.edit().putBoolean(HAS_SIGNED_OUT, false).apply()

    override fun setSignedOut() = prefs.edit().putBoolean(HAS_SIGNED_OUT, true).apply()

    override fun hasSignedOut(): Boolean = prefs.getBoolean(HAS_SIGNED_OUT, false)

    override fun setHasBackedUpPhrase() = prefs.edit().putBoolean(HAS_BACKED_UP_PHRASE, true).apply()

    override fun hasBackedUpPhrase(): Boolean = prefs.getBoolean(HAS_BACKED_UP_PHRASE, false)

    override fun saveCurrency(currencyCode: String) = prefs.edit().putString(LOCAL_CURRENCY_CODE, currencyCode).apply()

    @Throws(CurrencyException::class)
    override fun getCurrency(): String {
        val currencyCode = prefs.getString(LOCAL_CURRENCY_CODE, null)
        return currencyCode ?: getCurrencyFromLocaleAndSave()
    }

    @Throws(CurrencyException::class)
    override fun getCurrencyFromLocaleAndSave(): String {
        val currency = CurrencyUtil.getCurrencyFromLocale()
        saveCurrency(currency)
        return currency
    }

    override fun setWasMigrated(wasMigrated: Boolean) = prefs.edit().putBoolean(WAS_MIGRATED, wasMigrated).apply()

    override fun wasMigrated(): Boolean = prefs.getBoolean(WAS_MIGRATED, false)

    override fun setForceUserUpdate(forceUpdate: Boolean) = prefs.edit().putBoolean(FORCE_USER_UPDATE, forceUpdate).apply()

    override fun shouldForceUserUpdate(): Boolean = prefs.getBoolean(FORCE_USER_UPDATE, true)

    override fun setCurrentNetwork(network: Network) = prefs.edit().putString(CURRENT_NETWORK, network.id).apply()

    override fun getCurrentNetworkId(): String? = prefs.getString(CURRENT_NETWORK, null)

    override fun setHasClearedNotificationChannels() = prefs.edit().putBoolean(HAS_CLEARED_NOTIFICATION_CHANNELS, true).apply()

    override fun hasClearedNotificationChannels(): Boolean = prefs.getBoolean(HAS_CLEARED_NOTIFICATION_CHANNELS, false)

    // INFO: Does not clear all preferences.
    override fun clear() {
        prefs.edit()
                .putBoolean(HAS_BACKED_UP_PHRASE, false)
                .putString(STORED_QR_CODE, null)
                .putString(LOCAL_CURRENCY_CODE, null)
                .putBoolean(WAS_MIGRATED, false)
                .putBoolean(HAS_ONBOARDED, false)
                .apply()
    }
}