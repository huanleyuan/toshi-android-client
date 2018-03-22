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
import com.toshi.model.network.Balance
import com.toshi.util.FileNames
import com.toshi.view.BaseApplication

class BalancePrefs : BalancePrefsInterface {

    companion object {
        private const val LAST_KNOWN_BALANCE = "lkb"
    }

    private val prefs by lazy { BaseApplication.get().getSharedPreferences(FileNames.BALANCE_PREFS, Context.MODE_PRIVATE) }

    override fun readLastKnownBalance(): String = prefs.getString(LAST_KNOWN_BALANCE, "0x0")

    override fun writeLastKnownBalance(balance: Balance) {
        prefs.edit()
                .putString(LAST_KNOWN_BALANCE, balance.unconfirmedBalanceAsHex)
                .apply()
    }

    override fun clear() = prefs.edit().clear().apply()
}