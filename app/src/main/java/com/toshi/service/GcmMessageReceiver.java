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

/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.toshi.service;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.toshi.model.local.IncomingMessage;
import com.toshi.model.local.User;
import com.toshi.model.sofa.payment.Payment;
import com.toshi.model.sofa.SofaAdapters;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.model.sofa.SofaType;
import com.toshi.util.logging.LogUtil;
import com.toshi.util.sharedPrefs.SharedPrefs;
import com.toshi.view.BaseApplication;
import com.toshi.view.notification.ChatNotificationManager;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import rx.Completable;
import rx.Single;

import static com.toshi.manager.chat.SofaMessageReceiver.INCOMING_MESSAGE_TIMEOUT;

public class GcmMessageReceiver extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(final RemoteMessage message) {
        if (SharedPrefs.INSTANCE.hasSignedOut()) return;


        tryInitApp()
        .subscribe(
                () -> handleIncomingMessage(message.getData()),
                throwable -> LogUtil.exception("Error during incoming message", throwable)
        );
    }

    private Completable tryInitApp() {
        return BaseApplication
                .get()
                .getToshiManager()
                .tryInit();
    }

    private void handleIncomingMessage(final Map data) {
        try {
            final String messageBody = (String) data.get("message");
            LogUtil.i("Incoming PN: " + messageBody);

            if (messageBody == null) {
                tryShowIncomingMessage();
                return;
            }

            final SofaMessage sofaMessage = new SofaMessage().makeNew(messageBody);

            if (sofaMessage.getType() == SofaType.PAYMENT) {
                final Payment payment = SofaAdapters.get().paymentFrom(sofaMessage.getPayload());
                checkIfUserIsBlocked(payment);
            } else if (sofaMessage.getType() == SofaType.TOKEN_PAYMENT) {
                final Payment payment = SofaAdapters.get().tokenPaymentFrom(sofaMessage.getPayload());
                addIncomingPayment(payment);
            } else {
                tryShowIncomingMessage();
            }

        } catch (final Exception ex) {
            LogUtil.exception("Error while parsing incoming message: " + ex);
        }
    }

    private void checkIfUserIsBlocked(final Payment payment) {
        isUserBlocked(payment.getFromAddress())
                .subscribe(
                        isBlocked -> handlePayment(isBlocked, payment),
                        __ -> handlePayment(payment)
                );
    }

    private void handlePayment(final boolean isBlocked,
                               final Payment payment) {
        if (isBlocked) return;
        handlePayment(payment);
    }

    private Single<Boolean> isUserBlocked(final String paymentAddress) {
        return getUserFromPaymentAddress(paymentAddress)
                .flatMap(user ->
                        BaseApplication
                        .get()
                        .getRecipientManager()
                        .isUserBlocked(user.getToshiId())
                );
    }

    private void handlePayment(final Payment payment) {
        payment.getPaymentDirection()
               .subscribe(
                   (paymentDirection) -> this.handleValidPayment(payment, paymentDirection),
                   this::handleInvalidPayment
               );
    }

    private void handleValidPayment(final Payment payment, final @Payment.PaymentDirection Integer paymentDirection) {
        switch (paymentDirection) {
            case Payment.FROM_LOCAL_USER:
                updatePayment(payment);
                refreshBalance();
                return;
            case Payment.TO_LOCAL_USER:
                addIncomingPayment(payment);
                refreshBalance();
                return;
            default:
            case Payment.NOT_RELEVANT:
                handleInvalidPayment(new IllegalArgumentException("Not handling transaction that doesn't involve local user"));
        }
    }

    private void handleInvalidPayment(final Throwable throwable) {
        LogUtil.exception("Invalid payment", throwable);
    }

    private void tryShowIncomingMessage() {
        final IncomingMessage incomingMessage;
        try {
            incomingMessage = getIncomingMessage();
        } catch (InterruptedException | TimeoutException e) {
            LogUtil.i("Fetched all new messages");
            return;
        }

        ChatNotificationManager.showNotification(incomingMessage);
        // There may be more messages.
        tryShowIncomingMessage();
    }

    private IncomingMessage getIncomingMessage() throws TimeoutException, InterruptedException {
        return BaseApplication
                .get()
                .getSofaMessageManager()
                .fetchLatestMessage()
                .timeout(INCOMING_MESSAGE_TIMEOUT, TimeUnit.SECONDS)
                .toBlocking()
                .value();
    }

    private void updatePayment(final Payment payment) {
        BaseApplication
                .get()
                .getTransactionManager()
                .updatePayment(payment);
    }

    private void addIncomingPayment(final Payment payment) {
        BaseApplication
                .get()
                .getTransactionManager()
                .addIncomingPayment(payment);
    }

    private void refreshBalance() {
        BaseApplication
                .get()
                .getBalanceManager()
                .refreshBalance();
    }

    private Single<User> getUserFromPaymentAddress(final String paymentAddress) {
        return BaseApplication
                .get()
                .getRecipientManager()
                .getUserFromPaymentAddress(paymentAddress);
    }
}
