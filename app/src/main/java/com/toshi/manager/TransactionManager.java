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

package com.toshi.manager;

import com.toshi.crypto.HDWallet;
import com.toshi.manager.model.ERC20TokenPaymentTask;
import com.toshi.manager.model.ExternalPaymentTask;
import com.toshi.manager.model.PaymentTask;
import com.toshi.manager.model.ResendToshiPaymentTask;
import com.toshi.manager.model.ToshiPaymentTask;
import com.toshi.manager.model.W3PaymentTask;
import com.toshi.manager.network.EthereumService;
import com.toshi.manager.store.PendingTransactionStore;
import com.toshi.manager.transaction.IncomingTransactionManager;
import com.toshi.manager.transaction.OutgoingTransactionManager;
import com.toshi.manager.transaction.TransactionSigner;
import com.toshi.manager.transaction.UpdateTransactionManager;
import com.toshi.model.local.OutgoingPaymentResult;
import com.toshi.model.local.PendingTransaction;
import com.toshi.model.local.UnsignedW3Transaction;
import com.toshi.model.local.User;
import com.toshi.model.network.SentTransaction;
import com.toshi.model.network.SignedTransaction;
import com.toshi.model.network.TransactionRequest;
import com.toshi.model.network.UnsignedTransaction;
import com.toshi.model.sofa.PaymentRequest;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.model.sofa.payment.ERC20TokenPayment;
import com.toshi.model.sofa.payment.Payment;
import com.toshi.util.logging.LogUtil;
import com.toshi.util.paymentTask.PaymentTaskBuilder;
import com.toshi.util.paymentTask.TransactionRequestBuilder;
import com.toshi.view.BaseApplication;

import rx.Observable;
import rx.Single;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subscriptions.CompositeSubscription;

public class TransactionManager {

    private HDWallet wallet;
    private PendingTransactionStore pendingTransactionStore;
    private PaymentTaskBuilder paymentTaskBuilder;
    private IncomingTransactionManager incomingTransactionManager;
    private OutgoingTransactionManager outgoingTransactionManager;
    private UpdateTransactionManager updateTransactionManager;
    private TransactionSigner transactionSigner;
    private CompositeSubscription subscriptions;

    /*package */ TransactionManager() {
        initDatabase();
        initSubscriptions();
        initPaymentTaskBuilder();
        initIncomingTransactionManager();
        initTransactionSigner();
        initOutgoingTransactionManager();
        initUpdateTransactionManager();
    }

    private void initDatabase() {
        this.pendingTransactionStore = new PendingTransactionStore();
    }

    private void initSubscriptions() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initPaymentTaskBuilder() {
        this.paymentTaskBuilder = new PaymentTaskBuilder(
                this,
                BaseApplication.get().getBalanceManager(),
                BaseApplication.get().getRecipientManager(),
                new TransactionRequestBuilder()
        );
    }

    private void initIncomingTransactionManager() {
        this.incomingTransactionManager = new IncomingTransactionManager(this.pendingTransactionStore);
    }

    private void initTransactionSigner() {
        this.transactionSigner = new TransactionSigner();
    }

    private void initOutgoingTransactionManager() {
        this.outgoingTransactionManager = new OutgoingTransactionManager(this.pendingTransactionStore, this.transactionSigner);
    }

    private void initUpdateTransactionManager() {
        this.updateTransactionManager = new UpdateTransactionManager(this.pendingTransactionStore);
    }

    public TransactionManager init(final HDWallet wallet) {
        this.wallet = wallet;
        transactionSigner.setWallet(wallet);
        new Thread(this::initEverything).start();
        return this;
    }

    private void initEverything() {
        updatePendingTransactions();
        attachSubscribers();
    }

    private void updatePendingTransactions() {
        this.updateTransactionManager.updatePendingTransactions();
    }

    public Single<SignedTransaction> signW3Transaction(final W3PaymentTask paymentTask) {
        return this.transactionSigner.signW3Transaction(paymentTask);
    }

    public Single<SentTransaction> sendSignedTransaction(final SignedTransaction signedTransaction) {
        return this.transactionSigner.sendSignedTransaction(signedTransaction);
    }

    private void attachSubscribers() {
        attachNewOutgoingPaymentSubscriber();
        attachNewIncomingPaymentSubscriber();
        attachUpdatePaymentSubscriber();
    }

    private void attachNewIncomingPaymentSubscriber() {
        this.incomingTransactionManager.attachNewIncomingPaymentSubscriber();
    }

    private void attachNewOutgoingPaymentSubscriber() {
        this.outgoingTransactionManager.attachNewOutgoingPaymentSubscriber();
    }

    private void attachUpdatePaymentSubscriber() {
        this.updateTransactionManager.attachUpdatePaymentSubscriber();
    }

    public void sendPayment(final ToshiPaymentTask paymentTask) {
        this.outgoingTransactionManager.addOutgoingToshiPaymentTask(paymentTask);
    }

    public void resendPayment(final ResendToshiPaymentTask paymentTask) {
        this.outgoingTransactionManager.addOutgoingResendPaymentTask(paymentTask);
    }

    public void sendExternalPayment(final ExternalPaymentTask paymentTask) {
        this.outgoingTransactionManager.addOutgoingExternalPaymentTask(paymentTask);
    }

    public void sendERC20TokenPayment(final ERC20TokenPaymentTask paymentTask) {
        this.outgoingTransactionManager.addOutgoingERC20PaymentTask(paymentTask);
    }

    public Observable<OutgoingPaymentResult> getOutgoingPaymentResultObservable() {
        return this.outgoingTransactionManager.getOutgoingPaymentResultSubject()
                .asObservable()
                .subscribeOn(Schedulers.io());
    }

    public final void updatePayment(final Payment payment) {
        this.updateTransactionManager.updatePayment(payment);
    }

    public final void updatePaymentRequestState(final User remoteUser,
                                                final SofaMessage sofaMessage,
                                                final @PaymentRequest.State int newState) {
        this.updateTransactionManager.updatePaymentRequestState(remoteUser, sofaMessage, newState);
    }

    public void addIncomingPayment(final Payment payment) {
        this.incomingTransactionManager.addIncomingPayment(payment);
    }

    public PublishSubject<PendingTransaction> getPendingTransactionObservable() {
        return this.pendingTransactionStore.getPendingTransactionObservable();
    }

    public Single<PaymentTask> buildPaymentTask(final String fromPaymentAddress,
                                                final String toPaymentAddress,
                                                final String ethAmount,
                                                final boolean sendMaxAmount) {
        return this.paymentTaskBuilder
                .buildPaymentTask(fromPaymentAddress, toPaymentAddress, ethAmount, sendMaxAmount);
    }

    public Single<ERC20TokenPaymentTask> buildPaymentTask(final String fromPaymentAddress,
                                                          final String toPaymentAddress,
                                                          final String ethAmount,
                                                          final String tokenAddress,
                                                          final String tokenSymbol,
                                                          final int tokenDecimals) {
        return this.paymentTaskBuilder
                .buildERC20PaymentTask(
                        fromPaymentAddress,
                        toPaymentAddress,
                        ethAmount,
                        tokenAddress,
                        tokenSymbol,
                        tokenDecimals
                );
    }

    public Single<W3PaymentTask> buildPaymentTask(final String callbackId, final UnsignedW3Transaction unsignedW3Transaction) {
        return this.paymentTaskBuilder
                .buildW3PaymentTask(callbackId, unsignedW3Transaction);
    }

    public Observable<ERC20TokenPayment> listenForNewIncomingTokenPayments() {
        return this.incomingTransactionManager
                .getIncomingTokenPaymentsSubject()
                .asObservable()
                .ofType(ERC20TokenPayment.class);
    }

    public Single<UnsignedTransaction> createTransaction(final TransactionRequest transactionRequest) {
        return EthereumService
                .getApi()
                .createTransaction(transactionRequest)
                .doOnError(throwable -> LogUtil.exception("Error while creating transaction", throwable));
    }

    public void clear() {
        this.subscriptions.clear();
        this.incomingTransactionManager.clearSubscriptions();
        this.outgoingTransactionManager.clearSubscriptions();
        this.updateTransactionManager.clearSubscription();
    }
}