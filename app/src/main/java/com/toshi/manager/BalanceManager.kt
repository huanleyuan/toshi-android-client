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

package com.toshi.manager

import com.toshi.crypto.HDWallet
import com.toshi.crypto.util.TypeConverter
import com.toshi.manager.network.CurrencyInterface
import com.toshi.manager.network.CurrencyService
import com.toshi.manager.network.EthereumInterface
import com.toshi.manager.network.EthereumService
import com.toshi.model.local.Network
import com.toshi.model.local.Networks
import com.toshi.model.network.Balance
import com.toshi.model.network.Currencies
import com.toshi.model.network.ERC721TokenWrapper
import com.toshi.model.network.ExchangeRate
import com.toshi.model.network.GcmDeregistration
import com.toshi.model.network.GcmRegistration
import com.toshi.model.network.ServerTime
import com.toshi.model.network.token.ERC20Tokens
import com.toshi.model.network.token.ERC721Tokens
import com.toshi.model.network.token.ERCToken
import com.toshi.model.sofa.payment.Payment
import com.toshi.util.CurrencyUtil
import com.toshi.util.EthUtil
import com.toshi.util.EthUtil.BIG_DECIMAL_SCALE
import com.toshi.util.GcmPrefsUtil
import com.toshi.util.GcmUtil
import com.toshi.util.logging.LogUtil
import com.toshi.util.sharedPrefs.BalancePrefs
import com.toshi.util.sharedPrefs.BalancePrefsInterface
import com.toshi.util.sharedPrefs.SharedPrefs
import com.toshi.util.sharedPrefs.SharedPrefsInterface
import com.toshi.view.BaseApplication
import rx.Completable
import rx.Single
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

class BalanceManager(
        private val ethService: EthereumInterface = EthereumService.getApi(),
        private val currencyService: CurrencyInterface = CurrencyService.getApi(),
        private val balancePrefs: BalancePrefsInterface = BalancePrefs(),
        private val sharedPrefs: SharedPrefsInterface = SharedPrefs
) {
    private lateinit var wallet: HDWallet
    private val networks by lazy { Networks.getInstance() }
    private var connectivitySub: Subscription? = null
    val balanceObservable: BehaviorSubject<Balance> = BehaviorSubject.create<Balance>()

    fun init(wallet: HDWallet): Completable {
        this.wallet = wallet
        initCachedBalance()
        return registerEthGcm()
                .onErrorComplete()
                .doOnCompleted { attachConnectivityObserver() }
    }

    private fun initCachedBalance() {
        val cachedBalance = Balance(readLastKnownBalance())
        handleNewBalance(cachedBalance)
    }

    private fun attachConnectivityObserver() {
        clearConnectivitySubscription()
        connectivitySub = BaseApplication
                .get()
                .isConnectedSubject
                .subscribeOn(Schedulers.io())
                .filter { isConnected -> isConnected }
                .subscribe(
                        { handleConnectivity() },
                        { LogUtil.exception("Error checking connection state", it) }
                )
    }

    private fun handleConnectivity() {
        refreshBalance()
        registerEthGcm()
                .subscribeOn(Schedulers.io())
                .subscribe(
                        { },
                        { LogUtil.exception("Error while registering eth gcm", it) }
                )
    }

    fun refreshBalance() {
        getBalance()
                .observeOn(Schedulers.io())
                .subscribe(
                        { handleNewBalance(it) },
                        { LogUtil.exception("Error while fetching balance", it) }
                )
    }

    fun refreshBalanceCompletable(): Completable {
        return getBalance()
                .doOnSuccess { handleNewBalance(it) }
                .toCompletable()
    }

    private fun getBalance(): Single<Balance> {
        return getWallet()
                .flatMap { ethService.getBalance(it.paymentAddress) }
                .subscribeOn(Schedulers.io())
    }

    fun getERC20Tokens(): Single<ERC20Tokens> {
        return getWallet()
                .flatMap { ethService.getTokens(it.paymentAddress) }
                .subscribeOn(Schedulers.io())
    }

    fun getERC20Token(contractAddress: String): Single<ERCToken> {
        return getWallet()
                .flatMap { ethService.getToken(it.paymentAddress, contractAddress) }
                .subscribeOn(Schedulers.io())
    }

    fun getERC721Tokens(): Single<ERC721Tokens> {
        return getWallet()
                .flatMap { ethService.getCollectibles(it.paymentAddress) }
                .subscribeOn(Schedulers.io())
    }

    fun getERC721Token(contactAddress: String): Single<ERC721TokenWrapper> {
        return getWallet()
                .flatMap { ethService.getCollectible(it.paymentAddress, contactAddress) }
                .subscribeOn(Schedulers.io())
    }

    private fun handleNewBalance(balance: Balance) {
        writeLastKnownBalance(balance)
        balanceObservable.onNext(balance)
    }

    fun generateLocalPrice(payment: Payment): Single<Payment> {
        val weiAmount = TypeConverter.StringHexToBigInteger(payment.value)
        val ethAmount = EthUtil.weiToEth(weiAmount)
        return getLocalCurrencyExchangeRate()
                .map { toLocalCurrencyString(it, ethAmount) }
                .map(payment::setLocalPrice)
    }

    fun getLocalCurrencyExchangeRate(): Single<ExchangeRate> {
        return getLocalCurrency()
                .flatMap { fetchLatestExchangeRate(it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    private fun fetchLatestExchangeRate(code: String) = currencyService.getRates(code)

    fun getCurrencies(): Single<Currencies> {
        return currencyService
                .currencies
                .subscribeOn(Schedulers.io())
    }

    fun convertEthToLocalCurrencyString(ethAmount: BigDecimal): Single<String> {
        return getLocalCurrencyExchangeRate()
                .map { toLocalCurrencyString(it, ethAmount) }
    }

    fun toLocalCurrencyString(exchangeRate: ExchangeRate, ethAmount: BigDecimal): String {
        val marketRate = exchangeRate.rate
        val localAmount = marketRate.multiply(ethAmount)

        val numberFormat = CurrencyUtil.getNumberFormat()
        numberFormat.isGroupingUsed = true
        numberFormat.maximumFractionDigits = 2
        numberFormat.minimumFractionDigits = 2

        val amount = numberFormat.format(localAmount)
        val currencyCode = CurrencyUtil.getCode(exchangeRate.to)
        val currencySymbol = CurrencyUtil.getSymbol(exchangeRate.to)

        return String.format("%s%s %s", currencySymbol, amount, currencyCode)
    }

    fun getLocalCurrency(): Single<String> = Single.fromCallable { sharedPrefs.getCurrency() }

    fun convertEthToLocalCurrency(ethAmount: BigDecimal): Single<BigDecimal> {
        return getLocalCurrencyExchangeRate()
                .flatMap { mapToLocalCurrency(it, ethAmount) }
    }

    private fun mapToLocalCurrency(exchangeRate: ExchangeRate,
                                   ethAmount: BigDecimal): Single<BigDecimal> {
        return Single.fromCallable {
            val marketRate = exchangeRate.rate
            marketRate.multiply(ethAmount)
        }
    }

    fun convertLocalCurrencyToEth(localAmount: BigDecimal): Single<BigDecimal> {
        return getLocalCurrencyExchangeRate()
                .flatMap { mapToEth(it, localAmount) }
    }

    private fun mapToEth(exchangeRate: ExchangeRate,
                         localAmount: BigDecimal): Single<BigDecimal> {
        return Single.fromCallable {
            if (localAmount.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
            val marketRate = exchangeRate.rate
            if (marketRate.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
            localAmount.divide(marketRate, BIG_DECIMAL_SCALE, RoundingMode.HALF_DOWN)
        }
    }

    private fun unregisterEthGcmWithTimestamp(token: String, serverTime: ServerTime?): Completable {
        return if (serverTime == null) Completable.error(IllegalStateException("Unable to fetch server time"))
        else ethService
                .unregisterGcm(serverTime.get(), GcmDeregistration(token))
                .toCompletable()
    }

    fun getTransactionStatus(transactionHash: String): Single<Payment> {
        return EthereumService
                .get()
                .getStatusOfTransaction(transactionHash)
    }

    //Don't unregister the default network
    fun changeNetwork(network: Network): Completable {
        return if (networks.onDefaultNetwork()) {
            changeEthBaseUrl(network)
                    .andThen(registerEthGcm())
                    .subscribeOn(Schedulers.io())
                    .doOnCompleted { sharedPrefs.setCurrentNetwork(network) }
        } else GcmUtil
                .getGcmToken()
                .flatMapCompletable { unregisterFromEthGcm(it) }
                .andThen(changeEthBaseUrl(network))
                .andThen(registerEthGcm())
                .subscribeOn(Schedulers.io())
                .doOnCompleted { sharedPrefs.setCurrentNetwork(network) }
    }

    fun unregisterFromEthGcm(token: String): Completable {
        val currentNetworkId = networks.currentNetwork.id
        return ethService
                .timestamp
                .subscribeOn(Schedulers.io())
                .flatMapCompletable { unregisterEthGcmWithTimestamp(token, it) }
                .doOnCompleted { GcmPrefsUtil.setEthGcmTokenSentToServer(currentNetworkId, false) }
    }

    private fun changeEthBaseUrl(network: Network): Completable {
        return Completable.fromAction { EthereumService.get().changeBaseUrl(network.url) }
    }

    fun forceRegisterEthGcm(): Completable {
        if (wallet == null || networks == null) {
            return Completable.error(IllegalStateException("Unable to register GCM as class hasn't been initialised yet"))
        }
        val currentNetworkId = networks.currentNetwork.id
        GcmPrefsUtil.setEthGcmTokenSentToServer(currentNetworkId, false)
        return registerEthGcm()
    }

    private fun registerEthGcm(): Completable {
        val currentNetworkId = networks.currentNetwork.id
        return if (GcmPrefsUtil.isEthGcmTokenSentToServer(currentNetworkId)) Completable.complete()
        else GcmUtil
                .getGcmToken()
                .flatMapCompletable { registerEthGcmToken(it) }
    }

    private fun registerEthGcmToken(token: String): Completable {
        return ethService
                .timestamp
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .flatMapCompletable { registerEthGcmWithTimestamp(token, it) }
                .doOnCompleted { setEthGcmTokenSentToServer() }
                .doOnError { handleGcmRegisterError(it) }
    }

    private fun setEthGcmTokenSentToServer() {
        val currentNetworkId = networks.currentNetwork.id
        GcmPrefsUtil.setEthGcmTokenSentToServer(currentNetworkId, true)
    }

    @Throws(IllegalStateException::class)
    private fun registerEthGcmWithTimestamp(token: String, serverTime: ServerTime?): Completable {
        if (serverTime == null) throw IllegalStateException("ServerTime was null")
        return ethService
                .registerGcm(serverTime.get(), GcmRegistration(token, wallet.paymentAddress))
                .toCompletable()
    }

    private fun handleGcmRegisterError(throwable: Throwable) {
        LogUtil.exception("Error during registering of GCM", throwable)
        val currentNetworkId = networks.currentNetwork.id
        GcmPrefsUtil.setEthGcmTokenSentToServer(currentNetworkId, false)
    }

    private fun readLastKnownBalance(): String = balancePrefs.readLastKnownBalance()

    private fun writeLastKnownBalance(balance: Balance) = balancePrefs.writeLastKnownBalance(balance)

    fun clear() {
        clearConnectivitySubscription()
        balancePrefs.clear()
    }

    private fun getWallet(): Single<HDWallet> {
        return Single.fromCallable<HDWallet> {
            while (wallet == null) Thread.sleep(100)
            wallet
        }
        .subscribeOn(Schedulers.io())
        .timeout(20, TimeUnit.SECONDS)
    }

    private fun clearConnectivitySubscription() = connectivitySub?.unsubscribe()
}