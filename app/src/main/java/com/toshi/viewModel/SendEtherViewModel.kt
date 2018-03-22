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

package com.toshi.viewModel

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.toshi.R
import com.toshi.crypto.util.TypeConverter
import com.toshi.extensions.createSafeBigDecimal
import com.toshi.extensions.isValidDecimal
import com.toshi.model.local.CurrencyMode
import com.toshi.model.network.Balance
import com.toshi.model.network.ExchangeRate
import com.toshi.util.CurrencyUtil
import com.toshi.util.EthUtil
import com.toshi.util.sharedPrefs.SharedPrefs
import com.toshi.util.logging.LogUtil
import com.toshi.view.BaseApplication
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription
import java.math.BigInteger
import java.util.Locale

class SendEtherViewModel : ViewModel() {

    private val subscriptions by lazy { CompositeSubscription() }
    private val balanceManager by lazy { BaseApplication.get().balanceManager }
    private var exchangeRate: ExchangeRate? = null
    var currencyMode = CurrencyMode.ETH
    val ethBalance by lazy { MutableLiveData<Balance>() }
    val isSendingMaxAmount by lazy { MutableLiveData<Boolean>() }

    init {
        getBalance()
        getExchangeRate()
        isSendingMaxAmount.value = false
    }

    private fun getBalance() {
        val sub = balanceManager
                .balanceObservable
                .observeOn(AndroidSchedulers.mainThread())
                .filter { balance -> balance != null }
                .flatMap { balance -> balance.getBalanceWithLocalBalance().toObservable() }
                .subscribe(
                        { ethBalance.value = it },
                        { LogUtil.w("Error while getting ethBalance $it") }
                )

        subscriptions.add(sub)
    }

    private fun getExchangeRate() {
        val sub = balanceManager
                .getLocalCurrencyExchangeRate()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { exchangeRate = it },
                        { LogUtil.w("Error while getting exchange rate $it") }
                )

        subscriptions.add(sub)
    }

    fun hasEnoughBalance(inputAmount: String): Boolean {
        if (isSendingMaxAmount.value == true) return true
        val inputEthAmount = when (currencyMode) {
            CurrencyMode.ETH -> createSafeBigDecimal(inputAmount)
            CurrencyMode.FIAT -> createSafeBigDecimal(fiatToEth(inputAmount))
        }
        val ethBalance = ethBalance.value
        val etherBalance = if (ethBalance != null) ethBalance.getUnconfirmedBalance() else BigInteger("0")
        val decimalEtherBalance = EthUtil.weiToEth(etherBalance)
        return inputEthAmount.compareTo(decimalEtherBalance) == 0 || inputEthAmount.compareTo(decimalEtherBalance) == -1
    }

    fun isAmountValid(inputAmount: String): Boolean {
        return inputAmount.isNotEmpty() && isValidDecimal(inputAmount)
    }

    fun getEncodedEthAmount(inputValue: String): String {
        return when (currencyMode) {
            CurrencyMode.ETH -> EthUtil.decimalStringToEncodedEthAmount(inputValue)
            CurrencyMode.FIAT -> EthUtil.decimalStringToEncodedEthAmount(fiatToEth(inputValue))
        }
    }

    fun ethToFiat(inputValue: String): String {
        if (exchangeRate == null) return TypeConverter.formatNumber(0, EthUtil.FIAT_FORMAT)
        val convertedInput = createSafeBigDecimal(inputValue)
                .stripTrailingZeros()
        return EthUtil.ethToFiat(exchangeRate, convertedInput)
    }

    fun fiatToEth(inputValue: String): String {
        if (exchangeRate == null) return TypeConverter.formatNumber(0, EthUtil.ETH_FORMAT)
        val convertedInput = createSafeBigDecimal(inputValue)
                .stripTrailingZeros()
        return EthUtil.fiatToEth(exchangeRate, convertedInput)
    }

    fun getCurrencyCode(): String {
        return when (currencyMode) {
            CurrencyMode.ETH -> BaseApplication.get().getString(R.string.eth_currency_code)
            CurrencyMode.FIAT -> getFiatCurrencyCode()
        }
    }

    fun getFiatCurrencyCode(): String = SharedPrefs.getCurrency()

    fun getFiatCurrencySymbol(): String {
        val currency = SharedPrefs.getCurrency()
        return CurrencyUtil.getSymbol(currency)
    }

    fun switchCurrencyMode(updateEthValue: () -> Unit, updateFiatValue: () -> Unit) {
        currencyMode = when (currencyMode) {
            CurrencyMode.ETH -> CurrencyMode.FIAT
            CurrencyMode.FIAT -> CurrencyMode.ETH
        }
        updateBalanceView(updateEthValue, updateFiatValue)
    }

    private fun updateBalanceView(updateEthValue: () -> Unit, updateFiatValue: () -> Unit) {
        when (currencyMode) {
            CurrencyMode.ETH -> updateEthValue()
            CurrencyMode.FIAT -> updateFiatValue()
        }
    }

    fun getConvertedValue(inputValue: String): String {
        return when (currencyMode) {
            CurrencyMode.ETH -> {
                val fiatAmount = ethToFiat(inputValue)
                val currencyCode = getFiatCurrencyCode()
                val currencySymbol = getFiatCurrencySymbol()
                "$currencySymbol$fiatAmount $currencyCode"
            }
            CurrencyMode.FIAT -> {
                val ethAmount = fiatToEth(inputValue)
                BaseApplication.get().getString(R.string.eth_amount, ethAmount)
            }
        }
    }

    fun getEtherBalance(): String {
        val ethBalance = ethBalance.value
        val etherBalance = if (ethBalance != null) ethBalance.getUnconfirmedBalance() else BigInteger("0")
        val decimalEtherBalance = EthUtil.weiAmountToUserVisibleString(etherBalance)
        return decimalEtherBalance.toString()
    }

    fun getMaxAmount(): String {
        return when (currencyMode) {
            CurrencyMode.ETH -> getMaxEthAmount()
            CurrencyMode.FIAT -> getMaxFiatAmount()
        }
    }

    private fun getMaxEthAmount(): String {
        val balance = ethBalance.value ?: return TypeConverter.formatNumber(0, EthUtil.ETH_FORMAT)
        val ethAmount = EthUtil.weiToEth(balance.getUnconfirmedBalance())
        val df = CurrencyUtil.getNumberFormatWithOutGrouping(Locale.ENGLISH)
        df.maximumFractionDigits = EthUtil.BIG_DECIMAL_SCALE
        return df.format(ethAmount)
    }

    private fun getMaxFiatAmount(): String {
        val defaultValue = TypeConverter.formatNumber(0, EthUtil.ETH_FORMAT)
        val balance = ethBalance.value ?: return TypeConverter.formatNumber(0, EthUtil.ETH_FORMAT)
        return exchangeRate?.let {
            val ethAmount = EthUtil.weiToEth(balance.getUnconfirmedBalance())
            val localAmount = it.rate.multiply(ethAmount)
            val df = CurrencyUtil.getNumberFormatWithOutGrouping(Locale.ENGLISH)
            df.maximumFractionDigits = EthUtil.NUM_FIAT_DECIMAL_PLACES
            return df.format(localAmount)
        } ?: defaultValue
    }

    fun getTotalEthAmount(balance: Balance): String = EthUtil.weiAmountToUserVisibleString(balance.unconfirmedBalance)

    override fun onCleared() {
        super.onCleared()
        subscriptions.clear()
    }
}
