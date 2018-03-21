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

package com.toshi.util.paymentTask

import com.toshi.crypto.util.TypeConverter
import com.toshi.manager.BalanceManager
import com.toshi.manager.RecipientManager
import com.toshi.manager.TransactionManager
import com.toshi.manager.model.ERC20TokenPaymentTask
import com.toshi.manager.model.ExternalPaymentTask
import com.toshi.manager.model.PaymentTask
import com.toshi.manager.model.ToshiPaymentTask
import com.toshi.manager.model.W3PaymentTask
import com.toshi.model.local.EthAndFiat
import com.toshi.model.local.UnsignedW3Transaction
import com.toshi.model.local.User
import com.toshi.model.network.ExchangeRate
import com.toshi.model.network.UnsignedTransaction
import com.toshi.model.sofa.payment.ERC20TokenPayment
import com.toshi.model.sofa.payment.Payment
import com.toshi.util.EthUtil
import com.toshi.util.logging.LogUtil
import rx.Single
import java.math.BigDecimal

class PaymentTaskBuilder(
        private val transactionManager: TransactionManager,
        private val balanceManager: BalanceManager,
        private val recipientManager: RecipientManager,
        private val transactionBuilder: TransactionRequestBuilder = TransactionRequestBuilder()
) {

    fun buildPaymentTask(fromPaymentAddress: String,
                         toPaymentAddress: String,
                         ethAmount: String,
                         sendMaxAmount: Boolean): Single<PaymentTask> {
        val payment = Payment()
                .setValue(ethAmount)
                .setFromAddress(fromPaymentAddress)
                .setToAddress(toPaymentAddress)

        return payment.generateLocalPrice()
                .flatMap { createUnsignedTransaction(payment, sendMaxAmount) }
                .flatMap { getPaymentInfoAndUser(it, toPaymentAddress) }
                .map { buildPaymentTask(it.first, it.second, payment) }
                .doOnError { LogUtil.exception("Error while building payment task", it) }
    }

    fun buildERC20PaymentTask(fromPaymentAddress: String,
                              toPaymentAddress: String,
                              value: String,
                              tokenAddress: String,
                              tokenSymbol: String,
                              tokenDecimals: Int): Single<ERC20TokenPaymentTask> {
        val hexEncodedValue = TypeConverter.toJsonHex(EthUtil.ethToWei(value, tokenDecimals))
        val decimalEncodedValue = TypeConverter.formatHexString(hexEncodedValue, tokenDecimals, EthUtil.ETH_FORMAT)
        val erc20TokenPayment = ERC20TokenPayment(
                hexEncodedValue,
                tokenAddress,
                toPaymentAddress,
                fromPaymentAddress
        )

        return createUnsignedTransaction(erc20TokenPayment)
                .flatMap { calculateERC20PaymentInfo(it) }
                .map { buildPaymentTask(it, erc20TokenPayment, tokenSymbol, decimalEncodedValue) }
                .doOnError { LogUtil.exception("Error while building token payment task", it) }
    }

    private fun createUnsignedTransaction(payment: Payment, sendMaxAmount: Boolean): Single<UnsignedTransaction> {
        val transactionRequest = if (sendMaxAmount) transactionBuilder.generateMaxAmountTransactionRequest(payment)
        else transactionBuilder.generateTransactionRequest(payment)
        return transactionManager
                .createTransaction(transactionRequest)
                .doOnError { LogUtil.exception("Error while creating unsigned transaction with max amount", it) }
    }

    private fun createUnsignedTransaction(payment: ERC20TokenPayment): Single<UnsignedTransaction> {
        val transactionRequest = transactionBuilder.generateTransactionRequest(payment)
        return transactionManager
                .createTransaction(transactionRequest)
                .doOnError { LogUtil.exception("Error while creating unsigned token transaction", it) }
    }

    private fun getPaymentInfoAndUser(unsignedTransaction: UnsignedTransaction,
                                      paymentAddress: String): Single<Pair<PaymentTaskInfo, User>> {
        return Single.zip(
                calculatePaymentInfo(unsignedTransaction),
                getUserFromPaymentAddress(paymentAddress),
                { paymentInfo, user -> Pair(paymentInfo, user) }
        )
    }

    private fun buildPaymentTask(paymentTaskInfo: PaymentTaskInfo,
                                 receiver: User?,
                                 payment: Payment): PaymentTask {
        val paymentTask = PaymentTask(
                paymentAmount = paymentTaskInfo.paymentAmount,
                gasPrice = paymentTaskInfo.gasAmount,
                totalAmount = paymentTaskInfo.totalAmount,
                unsignedTransaction = paymentTaskInfo.unsignedTransaction,
                payment = payment
        )

        return receiver
                ?.let { ToshiPaymentTask(paymentTask, it) }
                ?: ExternalPaymentTask(paymentTask)
    }

    private fun buildPaymentTask(paymentTaskInfo: PaymentTaskInfo,
                                 payment: ERC20TokenPayment,
                                 tokenSymbol: String,
                                 tokenValue: String): ERC20TokenPaymentTask {
        val paymentTask = PaymentTask(
                paymentAmount = paymentTaskInfo.paymentAmount,
                gasPrice = paymentTaskInfo.gasAmount,
                totalAmount = paymentTaskInfo.totalAmount,
                unsignedTransaction = paymentTaskInfo.unsignedTransaction,
                payment = payment
        )

        return ERC20TokenPaymentTask(paymentTask, tokenSymbol, tokenValue)
    }

    private fun getUserFromPaymentAddress(paymentAddress: String): Single<User> {
        return recipientManager
                .getUserFromPaymentAddress(paymentAddress)
                .onErrorReturn { null }
    }

    fun buildW3PaymentTask(callbackId: String, unsignedW3Transaction: UnsignedW3Transaction): Single<W3PaymentTask> {
        val payment = Payment()
                .setValue(unsignedW3Transaction.value)
                .setFromAddress(unsignedW3Transaction.from)
                .setToAddress(unsignedW3Transaction.to)

        return createUnsignedW3Transaction(unsignedW3Transaction)
                .flatMap { calculatePaymentInfo(it) }
                .map { buildW3PaymentTask(it, payment, callbackId) }
                .doOnError { LogUtil.exception("Error while building W3 payment task", it) }
    }

    private fun createUnsignedW3Transaction(unsignedW3Transaction: UnsignedW3Transaction): Single<UnsignedTransaction> {
        val transactionRequest = transactionBuilder.generateTransactionRequest(unsignedW3Transaction)
        return transactionManager
                .createTransaction(transactionRequest)
                .doOnError { LogUtil.exception("Error while creating unsigned W3 transaction", it) }
    }

    private fun calculateERC20PaymentInfo(unsignedTransaction: UnsignedTransaction): Single<PaymentTaskInfo> {
        val sendTokenAmount = getSendEthAmount(unsignedTransaction)
        val gasEthAmount = getGasEthAmount(unsignedTransaction)
        return balanceManager.getLocalCurrencyExchangeRate()
                .map { mapPaymentValuesToFiat(it, sendTokenAmount, gasEthAmount, gasEthAmount, unsignedTransaction) }
                .doOnError { LogUtil.exception("Error while calculating token payment info", it) }
    }

    private fun calculatePaymentInfo(unsignedTransaction: UnsignedTransaction): Single<PaymentTaskInfo> {
        val sendEthAmount = getSendEthAmount(unsignedTransaction)
        val gasEthAmount = getGasEthAmount(unsignedTransaction)
        val totalEthAmount = BigDecimal(0).add(sendEthAmount).add(gasEthAmount)

        return balanceManager.getLocalCurrencyExchangeRate()
                .map { mapPaymentValuesToFiat(it, sendEthAmount, gasEthAmount, totalEthAmount, unsignedTransaction) }
                .doOnError { LogUtil.exception("Error while calculating payment info", it) }
    }

    private fun getGasEthAmount(unsignedTransaction: UnsignedTransaction): BigDecimal {
        val gas = TypeConverter.StringHexToBigInteger(unsignedTransaction.gas)
        val gasPrice = TypeConverter.StringHexToBigInteger(unsignedTransaction.gasPrice)
        return EthUtil.weiToEth(gasPrice.multiply(gas))
    }

    private fun getSendEthAmount(unsignedTransaction: UnsignedTransaction): BigDecimal {
        val weiAmount = TypeConverter.StringHexToBigInteger(unsignedTransaction.value)
        return EthUtil.weiToEth(weiAmount)
    }

    private fun mapPaymentValuesToFiat(exchangeRate: ExchangeRate,
                                       sendAmount: BigDecimal,
                                       gasEthAmount: BigDecimal,
                                       totalEthAmount: BigDecimal,
                                       unsignedTransaction: UnsignedTransaction): PaymentTaskInfo {
        val sendLocalAmount = balanceManager.toLocalCurrencyString(exchangeRate, sendAmount)
        val sendAmountEthAndFiat = EthAndFiat(sendAmount, sendLocalAmount)
        val gasLocalAmount = balanceManager.toLocalCurrencyString(exchangeRate, gasEthAmount)
        val gasAmountEthAndFiat = EthAndFiat(gasEthAmount, gasLocalAmount)
        val totalLocalAmount = balanceManager.toLocalCurrencyString(exchangeRate, totalEthAmount)
        val totalAmountEthAndFiat = EthAndFiat(totalEthAmount, totalLocalAmount)
        return PaymentTaskInfo(sendAmountEthAndFiat, gasAmountEthAndFiat, totalAmountEthAndFiat, unsignedTransaction)
    }

    private fun buildW3PaymentTask(paymentTaskInfo: PaymentTaskInfo,
                                   payment: Payment,
                                   callbackId: String): W3PaymentTask {
        return W3PaymentTask(
                paymentAmount = paymentTaskInfo.paymentAmount,
                gasPrice = paymentTaskInfo.gasAmount,
                totalAmount = paymentTaskInfo.totalAmount,
                unsignedTransaction = paymentTaskInfo.unsignedTransaction,
                payment = payment,
                callbackId = callbackId
        )
    }
}

private data class PaymentTaskInfo(
        val paymentAmount: EthAndFiat,
        val gasAmount: EthAndFiat,
        val totalAmount: EthAndFiat,
        val unsignedTransaction: UnsignedTransaction
)