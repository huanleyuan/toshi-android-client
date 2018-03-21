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
import com.toshi.manager.network.EthereumInterface
import com.toshi.model.local.User
import com.toshi.model.network.ExchangeRate
import com.toshi.model.network.TransactionRequest
import com.toshi.model.network.UnsignedTransaction
import com.toshi.util.EthUtil
import com.toshi.util.paymentTask.PaymentTaskBuilder
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.notNullValue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import rx.Single
import rx.schedulers.Schedulers
import java.math.BigDecimal

class PaymentTaskBuilderTest {

    private lateinit var transactionManager: TransactionManager
    private lateinit var balanceManager: BalanceManager
    private lateinit var recipientManager: RecipientManager
    private lateinit var paymentTaskBuilder: PaymentTaskBuilder
    private lateinit var exchangeRate: ExchangeRate
    private lateinit var unsignedTransaction: UnsignedTransaction
    private lateinit var testSendAmountHex: String
    private val testSendAmount = "10.00"
    private val testSendUserPaymentAddress = "0x4a40d412f25db163a9af6190752c0758bdca6aa3"
    private val testReceiverUserPaymentAddress = "0x4a40d412f25db163a9af6190752c0758bdca6aa0"
    private val testTokenAddress = "0x4a40d412f25db163a9af6190752c0758bdca6aa1"
    private val fromCurrency = "ETH"
    private val toCurrency = "USD"
    private val tokenDecimals = 18

    @Before
    fun setup() {
        mockEthToUsdExchangeRate()
        calcSendAmount()
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
        val decimalString = EthUtil.fiatToEth(exchangeRate, createSafeBigDecimal(testSendAmount))
        val weiAmount = EthUtil.ethToWei(BigDecimal(decimalString))
        testSendAmountHex = TypeConverter.toJsonHex(weiAmount)
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
                .thenReturn(testSendAmountHex)
    }

    private fun mockTransactionManager() {
        val ethApi = Mockito.mock(EthereumInterface::class.java)
        Mockito
                .`when`(ethApi.createTransaction(ArgumentMatchers.any(TransactionRequest::class.java)))
                .thenReturn(Single.just(unsignedTransaction))

        transactionManager = TransactionManager(
                ethService = ethApi,
                scheduler = Schedulers.trampoline()
        )
    }

    private fun mockRecipientManager() {
        recipientManager = Mockito.mock(RecipientManager::class.java)
        Mockito
                .`when`(recipientManager.getUserFromPaymentAddress(testReceiverUserPaymentAddress))
                .thenReturn(Single.just(User()))
    }

    private fun mockBalanceManager() {
        balanceManager = Mockito.mock(BalanceManager::class.java)
        Mockito
                .`when`(balanceManager.getLocalCurrencyExchangeRate())
                .thenReturn(Single.just(exchangeRate))

        Mockito
                .`when`(balanceManager.toLocalCurrencyString(
                        ArgumentMatchers.any(ExchangeRate::class.java),
                        ArgumentMatchers.any(BigDecimal::class.java))
                )
                .thenCallRealMethod()
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
                fromPaymentAddress = testSendUserPaymentAddress,
                toPaymentAddress = testReceiverUserPaymentAddress,
                ethAmount = testSendAmountHex,
                sendMaxAmount = false
        ).toBlocking().value()

        assertThat(paymentTask, instanceOf(ToshiPaymentTask::class.java))
        paymentTask as ToshiPaymentTask
        assertThat(paymentTask.user, `is`(notNullValue()))
        assertPaymentTaskValues(paymentTask)
    }

    private fun assertPaymentTaskValues(paymentTask: PaymentTask) {
        assertThat(paymentTask.payment.toAddress, `is`(testReceiverUserPaymentAddress))
        assertThat(paymentTask.payment.fromAddress, `is`(testSendUserPaymentAddress))
        assertThat(paymentTask.payment.value, `is`(testSendAmountHex))
        val expectedEthAmount = EthUtil.weiToEth(TypeConverter.StringHexToBigInteger(testSendAmountHex))
        assertThat(paymentTask.paymentAmount.ethAmount, `is`(expectedEthAmount))
        assertThat(paymentTask.totalAmount.ethAmount, `is`(expectedEthAmount + paymentTask.gasPrice.ethAmount))
    }

    @Test
    fun testBuildERC20PaymentTask() {
        val paymentTask = paymentTaskBuilder.buildERC20PaymentTask(
                fromPaymentAddress = testSendUserPaymentAddress,
                toPaymentAddress = testReceiverUserPaymentAddress,
                value = testSendAmount,
                tokenAddress = testTokenAddress,
                tokenSymbol = "ETH",
                tokenDecimals = tokenDecimals
        ).toBlocking().value()
        assertThat(paymentTask, instanceOf(ERC20TokenPaymentTask::class.java))
    }
}