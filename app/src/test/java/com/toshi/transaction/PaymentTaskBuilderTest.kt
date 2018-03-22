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

package com.toshi.transaction

import com.toshi.crypto.util.TypeConverter
import com.toshi.extensions.createSafeBigDecimal
import com.toshi.manager.BalanceManager
import com.toshi.manager.RecipientManager
import com.toshi.manager.TransactionManager
import com.toshi.manager.model.ERC20TokenPaymentTask
import com.toshi.manager.model.PaymentTask
import com.toshi.manager.model.ToshiPaymentTask
import com.toshi.manager.model.W3PaymentTask
import com.toshi.manager.network.CurrencyInterface
import com.toshi.manager.network.EthereumInterface
import com.toshi.model.local.UnsignedW3Transaction
import com.toshi.model.local.User
import com.toshi.model.network.ExchangeRate
import com.toshi.model.network.TransactionRequest
import com.toshi.model.network.UnsignedTransaction
import com.toshi.util.EthUtil
import com.toshi.util.paymentTask.PaymentTaskBuilder
import com.toshi.util.sharedPrefs.BalancePrefsInterface
import com.toshi.util.sharedPrefs.SharedPrefsInterface
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.notNullValue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import rx.Single
import rx.schedulers.Schedulers
import java.math.BigDecimal
import java.text.DecimalFormat

class PaymentTaskBuilderTest {

    private lateinit var transactionManager: TransactionManager
    private lateinit var balanceManager: BalanceManager
    private lateinit var recipientManager: RecipientManager
    private lateinit var paymentTaskBuilder: PaymentTaskBuilder
    private lateinit var exchangeRate: ExchangeRate
    private lateinit var unsignedTransaction: UnsignedTransaction
    private lateinit var testEthAmountHex: String
    private lateinit var unsignedW3Transaction: UnsignedW3Transaction
    private val testFiatAmount = "10.00"
    private val testTokenAmount = "15.00"
    private val testSenderPaymentAddress = "0x4a40d412f25db163a9af6190752c0758bdca6aa3"
    private val testReceiverPaymentAddress = "0x4a40d412f25db163a9af6190752c0758bdca6aa0"
    private val testTokenAddress = "0x4a40d412f25db163a9af6190752c0758bdca6aa1"
    private val fromCurrency = "ETH"
    private val toCurrency = "USD"
    private val tokenDecimals = 18
    private val callbackId = "1"
    private val tokenSymbol = "OMG"

    @Before
    fun setup() {
        mockEthToUsdExchangeRate()
        calcSendAmount()
        mockUnsignedW3Transaction()
        mockUnsignedTransaction()
        mockTransactionManager()
        mockRecipientManager()
        mockBalanceManager()
        createPaymentTaskBuilder()
    }

    private fun mockEthToUsdExchangeRate() {
        exchangeRate = Mockito.mock(ExchangeRate::class.java)
        Mockito
                .`when`(exchangeRate.from)
                .thenReturn(fromCurrency)
        Mockito
                .`when`(exchangeRate.to)
                .thenReturn(toCurrency)
        Mockito
                .`when`(exchangeRate.rate)
                .thenReturn(BigDecimal("581.10"))
    }

    private fun calcSendAmount() {
        val decimalString = EthUtil.fiatToEth(exchangeRate, createSafeBigDecimal(testFiatAmount))
        val weiAmount = EthUtil.ethToWei(BigDecimal(decimalString))
        testEthAmountHex = TypeConverter.toJsonHex(weiAmount)
    }

    private fun mockUnsignedW3Transaction() {
        unsignedW3Transaction = Mockito.mock(UnsignedW3Transaction::class.java)
        Mockito
                .`when`(unsignedW3Transaction.from)
                .thenReturn(testSenderPaymentAddress)

        Mockito
                .`when`(unsignedW3Transaction.to)
                .thenReturn(testReceiverPaymentAddress)

        Mockito
                .`when`(unsignedW3Transaction.value)
                .thenReturn(testEthAmountHex)
    }

    private fun mockUnsignedTransaction() {
        unsignedTransaction = Mockito.mock(UnsignedTransaction::class.java)
        Mockito
                .`when`(unsignedTransaction.gas)
                .thenReturn("0x5208")
        Mockito
                .`when`(unsignedTransaction.gasPrice)
                .thenReturn("0x3b9aca00")
        Mockito
                .`when`(unsignedTransaction.nonce)
                .thenReturn("0x746f6b65e2")
        Mockito
                .`when`(unsignedTransaction.transaction)
                .thenReturn("0xef85746f6b65e2843b9aca00825208944a40d412f25db163a9af6190752c0758bdca6aa387061d3d89a8900080748080")
        Mockito
                .`when`(unsignedTransaction.value)
                .thenReturn(testEthAmountHex)
    }

    private fun mockTransactionManager() {
        val ethApi = Mockito.mock(EthereumInterface::class.java)
        Mockito
                .`when`(ethApi.createTransaction(any(TransactionRequest::class.java)))
                .thenReturn(Single.just(unsignedTransaction))

        transactionManager = TransactionManager(
                ethService = ethApi,
                scheduler = Schedulers.trampoline()
        )
    }

    private fun mockRecipientManager() {
        recipientManager = Mockito.mock(RecipientManager::class.java)
        Mockito
                .`when`(recipientManager.getUserFromPaymentAddress(testReceiverPaymentAddress))
                .thenReturn(Single.just(User()))
    }

    private fun mockBalanceManager() {
        val ethApi = Mockito.mock(EthereumInterface::class.java)
        val currencyApi = Mockito.mock(CurrencyInterface::class.java)
        val sharedPrefs = Mockito.mock(SharedPrefsInterface::class.java)
        val balancePrefs = Mockito.mock(BalancePrefsInterface::class.java)

        Mockito
                .`when`(currencyApi.getRates(anyString()))
                .thenReturn(Single.just(exchangeRate))

        Mockito
                .`when`(sharedPrefs.getCurrency())
                .thenReturn("USD")

        balanceManager = BalanceManager(
                ethService = ethApi,
                currencyService = currencyApi,
                sharedPrefs = sharedPrefs,
                balancePrefs = balancePrefs,
                subscribeOnScheduler = Schedulers.trampoline()
        )
    }

    private fun createPaymentTaskBuilder() {
        paymentTaskBuilder = PaymentTaskBuilder(
                transactionManager = transactionManager,
                balanceManager = balanceManager,
                recipientManager = recipientManager
        )
    }

    @Test
    fun testBuildToshiPaymentTask() {
        val paymentTask = paymentTaskBuilder.buildPaymentTask(
                fromPaymentAddress = testSenderPaymentAddress,
                toPaymentAddress = testReceiverPaymentAddress,
                ethAmount = testEthAmountHex,
                sendMaxAmount = false
        ).toBlocking().value()

        assertThat(paymentTask, instanceOf(ToshiPaymentTask::class.java))
        paymentTask as ToshiPaymentTask
        assertThat(paymentTask.user, `is`(notNullValue()))
        assertPaymentTaskValues(paymentTask)
    }

    @Test
    fun testBuildW3PaymentTask() {
        val paymentTask = paymentTaskBuilder
                .buildW3PaymentTask(callbackId, unsignedW3Transaction)
                .toBlocking()
                .value()

        assertThat(paymentTask, instanceOf(W3PaymentTask::class.java))
        assertThat(paymentTask.callbackId, `is`(notNullValue()))
        assertPaymentTaskValues(paymentTask)
    }

    private fun assertPaymentTaskValues(paymentTask: PaymentTask) {
        assertThat(paymentTask.payment.toAddress, `is`(testReceiverPaymentAddress))
        assertThat(paymentTask.payment.fromAddress, `is`(testSenderPaymentAddress))
        assertThat(paymentTask.payment.value, `is`(testEthAmountHex))
        val expectedEthAmount = EthUtil.weiToEth(TypeConverter.StringHexToBigInteger(testEthAmountHex))
        assertThat(paymentTask.paymentAmount.ethAmount, `is`(expectedEthAmount))
        assertThat(paymentTask.totalAmount.ethAmount, `is`(expectedEthAmount + paymentTask.gasPrice.ethAmount))
    }

    @Test
    fun testBuildERC20PaymentTask() {
        val paymentTask = paymentTaskBuilder.buildERC20PaymentTask(
                fromPaymentAddress = testSenderPaymentAddress,
                toPaymentAddress = testReceiverPaymentAddress,
                value = testTokenAmount,
                tokenAddress = testTokenAddress,
                tokenSymbol = tokenSymbol,
                tokenDecimals = tokenDecimals
        ).toBlocking().value()

        assertThat(paymentTask, instanceOf(ERC20TokenPaymentTask::class.java))
        assertThat(paymentTask.tokenSymbol, `is`(tokenSymbol))
        assertThat(paymentTask.payment.toAddress, `is`(testReceiverPaymentAddress))
        assertThat(paymentTask.payment.fromAddress, `is`(testSenderPaymentAddress))
        assertThat(paymentTask.gasPrice.ethAmount, `is`(notNullValue()))
        assertTokenValue(paymentTask)
    }

    private fun assertTokenValue(paymentTask: ERC20TokenPaymentTask) {
        val expectedTokenValue = BigDecimal(testTokenAmount)
        val actualTokenValue = BigDecimal(paymentTask.tokenValue)
        val decimalFormat = DecimalFormat("#.00")
        assertThat(decimalFormat.format(expectedTokenValue), `is`(decimalFormat.format(actualTokenValue)))
        val actualTokenHexValue = TypeConverter.formatHexString(paymentTask.payment.value, tokenDecimals, EthUtil.ETH_FORMAT)
        assertThat(decimalFormat.format(BigDecimal(actualTokenHexValue)), `is`(decimalFormat.format(BigDecimal(testTokenAmount))))
    }
}