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

package com.toshi.manager.transaction

import com.toshi.manager.model.incomingPayment.IncomingEthPaymentTask
import com.toshi.manager.model.incomingPayment.IncomingPaymentTask
import com.toshi.manager.model.incomingPayment.IncomingTokenPaymentTask
import com.toshi.manager.store.PendingTransactionStore
import com.toshi.model.local.PendingTransaction
import com.toshi.model.local.SendState
import com.toshi.model.local.User
import com.toshi.model.sofa.SofaAdapters
import com.toshi.model.sofa.SofaMessage
import com.toshi.model.sofa.payment.ERC20TokenPayment
import com.toshi.model.sofa.payment.Payment
import com.toshi.util.logging.LogUtil
import com.toshi.view.BaseApplication
import rx.Single
import rx.Subscription
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import rx.subscriptions.CompositeSubscription

class IncomingTransactionManager(private val pendingTransactionStore: PendingTransactionStore) {

    private val sofaMessageManager by lazy { BaseApplication.get().sofaMessageManager }
    private val recipientManager by lazy { BaseApplication.get().recipientManager }

    private val newIncomingPaymentQueue by lazy { PublishSubject.create<IncomingPaymentTask>() }
    private val subscriptions by lazy { CompositeSubscription() }
    private var incomingPaymentSub: Subscription? = null
    val incomingTokenPaymentsSubject: PublishSubject<Payment> by lazy { PublishSubject.create<Payment>() }

    fun attachNewIncomingPaymentSubscriber() {
        // Explicitly clear first to avoid double subscription
        clearSubscription()
        incomingPaymentSub = newIncomingPaymentQueue
                .subscribeOn(Schedulers.io())
                .subscribe(
                        { processNewIncomingPayment(it) },
                        { LogUtil.exception("Error while handling incoming payment $it") }
                )

        subscriptions.add(incomingPaymentSub)
    }

    private fun processNewIncomingPayment(paymentTask: IncomingPaymentTask) {
        when (paymentTask) {
            is IncomingEthPaymentTask -> handleEthPaymentTask(paymentTask)
            is IncomingTokenPaymentTask -> handleTokenPaymentTask(paymentTask)
        }
    }

    private fun handleEthPaymentTask(ethPaymentTask: IncomingEthPaymentTask) {
        getUpdatedPayment(ethPaymentTask)
                .subscribe(
                        { handleIncomingPayment(it) },
                        { LogUtil.exception("Error while getting updated payment $it") }
                )
    }

    private fun handleTokenPaymentTask(incomingPaymentTask: IncomingTokenPaymentTask) {
        incomingTokenPaymentsSubject.onNext(incomingPaymentTask.payment)
    }

    private fun getUpdatedPayment(incomingEthPaymentTask: IncomingEthPaymentTask): Single<IncomingEthPaymentTask> {
        val user = incomingEthPaymentTask.user
        return incomingEthPaymentTask.payment
                .generateLocalPrice(BaseApplication.get().balanceManager)
                .map { storePayment(user, it) }
                .map { incomingEthPaymentTask.copy(sofaMessage = it) }
                .subscribeOn(Schedulers.io())
    }

    private fun storePayment(user: User, payment: Payment): SofaMessage {
        val sofaMessage = generateMessageFromPayment(payment, user)
        storeMessage(user, sofaMessage)
        return sofaMessage
    }

    private fun storeMessage(receiver: User?, message: SofaMessage) {
        // receiver will be null if this is an external payment
        receiver?.let {
            message.sendState = SendState.STATE_SENDING
            sofaMessageManager.saveTransaction(receiver, message)
        }
    }

    private fun generateMessageFromPayment(payment: Payment, sender: User): SofaMessage {
        val messageBody = SofaAdapters.get().toJson(payment)
        return SofaMessage().makeNewFromTransaction(payment.txHash, sender, messageBody)
    }

    private fun handleIncomingPayment(ethPaymentTask: IncomingEthPaymentTask) {
        val pendingTransaction = PendingTransaction()
                .setTxHash(ethPaymentTask.payment.txHash)
                .setSofaMessage(ethPaymentTask.sofaMessage)
        pendingTransactionStore.save(pendingTransaction)
    }

    fun addIncomingPayment(payment: Payment) {
        when (payment) {
            is ERC20TokenPayment -> newIncomingPaymentQueue.onNext(IncomingTokenPaymentTask(payment))
            else -> addIncomingPaymentTask(payment)
        }
    }

    private fun addIncomingPaymentTask(payment: Payment) {
        recipientManager.getUserFromPaymentAddress(payment.fromAddress)
                .subscribe(
                        { addIncomingPaymentTask(it, payment) },
                        { LogUtil.exception("Error while getting user from payment address $it") }
                )
    }

    private fun addIncomingPaymentTask(sender: User, payment: Payment) {
        newIncomingPaymentQueue.onNext(IncomingEthPaymentTask(payment, sender))
    }

    private fun clearSubscription() = incomingPaymentSub?.unsubscribe()
    fun clearSubscriptions() = subscriptions.clear()
}