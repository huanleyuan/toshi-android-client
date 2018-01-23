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

package com.toshi.manager.model;

import android.support.annotation.IntDef;

import com.toshi.model.local.EthAndFiat;
import com.toshi.model.local.User;
import com.toshi.model.network.SentTransaction;
import com.toshi.model.network.UnsignedTransaction;
import com.toshi.model.sofa.Payment;
import com.toshi.model.sofa.SofaMessage;

public class PaymentTask {


    @IntDef({INCOMING, OUTGOING, OUTGOING_EXTERNAL, OUTGOING_RESEND})
    public @interface Action {}
    public static final int INCOMING = 0;
    public static final int OUTGOING = 1;
    public static final int OUTGOING_EXTERNAL = 2;
    public static final int OUTGOING_RESEND = 3;

    private User user;
    private Payment payment;
    private @Action int action;
    private UnsignedTransaction unsignedTransaction;
    private SofaMessage sofaMessage;
    private SentTransaction sentTransaction;
    private EthAndFiat paymentAmount;
    private EthAndFiat gasPrice;
    private EthAndFiat totalAmount;
    private String callbackId;

    public User getUser() {
        return user;
    }

    public Payment getPayment() {
        return payment;
    }

    public int getAction() {
        return action;
    }

    public UnsignedTransaction getUnsignedTransaction() {
        return unsignedTransaction;
    }

    public SofaMessage getSofaMessage() {
        return sofaMessage;
    }

    public SentTransaction getSentTransaction() {
        return sentTransaction;
    }

    public EthAndFiat getPaymentAmount() {
        return paymentAmount;
    }

    public EthAndFiat getGasPrice() {
        return gasPrice;
    }

    public EthAndFiat getTotalAmount() {
        return totalAmount;
    }

    public String getCallbackId() {
        return callbackId;
    }

    public boolean isToshiPayment() {
        return this.user != null;
    }

    public boolean isW3Transaction() {
        return this.callbackId != null;
    }

    public boolean isValidOutgoingTask() {
        if (this.action == OUTGOING) return isValidOutGoingTask();
        else if (this.action == OUTGOING_RESEND) return isValidOutGoingResendTask();
        else if (this.action == OUTGOING_EXTERNAL) return isValidOutGoingExternalTask();
        return false;
    }

    private boolean isValidOutGoingTask() {
        return this.action == OUTGOING
                && this.payment != null
                && this.unsignedTransaction != null
                && this.user != null;
    }

    private boolean isValidOutGoingResendTask() {
        return this.action == OUTGOING_RESEND
                && this.payment != null
                && this.unsignedTransaction != null
                && this.user != null
                && this.sofaMessage != null;
    }

    private boolean isValidOutGoingExternalTask() {
        return this.action == OUTGOING_EXTERNAL
                && this.payment != null
                && this.unsignedTransaction != null;
    }

    private PaymentTask(final Builder builder) {
        this.user = builder.user;
        this.payment = builder.payment;
        this.action = builder.action;
        this.unsignedTransaction = builder.unsignedTransaction;
        this.sofaMessage = builder.sofaMessage;
        this.sentTransaction = builder.sentTransaction;
        this.paymentAmount = builder.paymentAmount;
        this.gasPrice = builder.gasPrice;
        this.totalAmount = builder.totalAmount;
        this.callbackId = builder.callbackId;
    }

    public static class Builder {
        private User user;
        private Payment payment;
        private @Action int action;
        private UnsignedTransaction unsignedTransaction;
        private SofaMessage sofaMessage;
        private SentTransaction sentTransaction;
        private EthAndFiat paymentAmount;
        private EthAndFiat gasPrice;
        private EthAndFiat totalAmount;
        private String callbackId;

        public Builder() {}

        public Builder(final PaymentTask paymentTask) {
            this.user = paymentTask.user;
            this.payment = paymentTask.payment;
            this.action = paymentTask.action;
            this.unsignedTransaction = paymentTask.unsignedTransaction;
            this.sofaMessage = paymentTask.sofaMessage;
            this.sentTransaction = paymentTask.sentTransaction;
            this.paymentAmount = paymentTask.paymentAmount;
            this.gasPrice = paymentTask.gasPrice;
            this.totalAmount = paymentTask.totalAmount;
            this.callbackId = paymentTask.callbackId;
        }

        public Builder setUser(final User user) {
            this.user = user;
            return this;
        }

        public Builder setPayment(Payment payment) {
            this.payment = payment;
            return this;
        }

        public Builder setAction(int action) {
            this.action = action;
            return this;
        }

        public Builder setUnsignedTransaction(UnsignedTransaction unsignedTransaction) {
            this.unsignedTransaction = unsignedTransaction;
            return this;
        }

        public Builder setSofaMessage(SofaMessage sofaMessage) {
            this.sofaMessage = sofaMessage;
            return this;
        }

        public Builder setSentTransaction(SentTransaction sentTransaction) {
            this.sentTransaction = sentTransaction;
            return this;
        }

        public Builder setPaymentAmount(EthAndFiat paymentAmount) {
            this.paymentAmount = paymentAmount;
            return this;
        }

        public Builder setGasPrice(EthAndFiat gasPrice) {
            this.gasPrice = gasPrice;
            return this;
        }

        public Builder setTotalAmount(EthAndFiat totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder setCallbackId(String callbackId) {
            this.callbackId = callbackId;
            return this;
        }

        public PaymentTask build() {
            return new PaymentTask(this);
        }
    }

    @Override
    public String toString() {
        return "User: " + user
                + " Payment: " + payment
                + " action: " + action
                + " UnsignedTransaction: " + unsignedTransaction
                + " SofaMessage: " + sofaMessage
                + " SendTransaction: " + sentTransaction
                + " paymentAmount " + paymentAmount
                + " gasPrice " + gasPrice
                + " totalAmount " + totalAmount
                + " callbackId " + callbackId;
    }
}
