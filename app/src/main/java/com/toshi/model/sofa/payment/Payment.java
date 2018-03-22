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

package com.toshi.model.sofa.payment;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.squareup.moshi.Json;
import com.toshi.R;
import com.toshi.crypto.HDWallet;
import com.toshi.model.local.SendState;
import com.toshi.model.sofa.SofaType;
import com.toshi.util.LocaleUtil;
import com.toshi.view.BaseApplication;

import rx.Single;
import rx.schedulers.Schedulers;

public class Payment {

    /* package */ String value;
    /* package */ String toAddress;
    /* package */ String fromAddress;
    private String txHash;
    private String status;

    @IntDef({
            NOT_RELEVANT,
            TO_LOCAL_USER,
            FROM_LOCAL_USER,
    })
    public @interface PaymentDirection {}
    public static final int NOT_RELEVANT = 0;
    public static final int TO_LOCAL_USER = 1;
    public static final int FROM_LOCAL_USER = 2;

    @Json(name = SofaType.LOCAL_ONLY_PAYLOAD)
    private ClientSideCustomData androidClientSideCustomData;

    public Payment() {}

    public Payment setValue(final String value) {
        this.value = value;
        return this;
    }

    public String getValue() {
        return this.value;
    }

    public String getToAddress() {
        return this.toAddress;
    }

    public Payment setToAddress(final String toAddress) {
        this.toAddress = toAddress;
        return this;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public Payment setFromAddress(final String ownerAddress) {
        this.fromAddress = ownerAddress;
        return this;
    }

    public String getTxHash() {
        return this.txHash;
    }

    public Payment setTxHash(final String txHash) {
        this.txHash = txHash;
        return this;
    }

    // Returns unconfirmed if the state is unknown for whatever reason
    public @NonNull String getStatus() {
        if (this.status == null) {
            return SofaType.UNCONFIRMED;
        }
        return this.status;
    }

    public Payment setStatus(final String status) {
        this.status = status;
        return this;
    }

    public Payment setLocalPrice(final String localPrice) {
        if (this.androidClientSideCustomData == null) {
            this.androidClientSideCustomData = new ClientSideCustomData();
        }

        this.androidClientSideCustomData.localPrice = localPrice;
        return this;
    }

    public String getLocalPrice() {
        if (this.androidClientSideCustomData == null) {
            return null;
        }

        return this.androidClientSideCustomData.localPrice;
    }

    public String toUserVisibleString(final boolean sentByLocal, final @SendState.State int sentStatus) {
        final @StringRes int successMessageId = sentByLocal
                ? R.string.latest_message__payment_outgoing
                : R.string.latest_message__payment_incoming;
        final @StringRes int messageId = sentStatus == SendState.STATE_FAILED
                ? R.string.latest_message__payment_failed
                : successMessageId;

        return String.format(
                LocaleUtil.getLocale(),
                BaseApplication.get().getResources().getString(messageId),
                getLocalPrice());
    }

    public Single<Integer> getPaymentDirection() {
        return BaseApplication.get()
                .getToshiManager()
                .getWallet()
                .toObservable()
                .map(this::getPaymentDirection)
                .toSingle()
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io());
    }

    private @PaymentDirection int getPaymentDirection(final HDWallet hdWallet) {
        if (toAddress.equals(hdWallet.getPaymentAddress())) {
            return TO_LOCAL_USER;
        }

        if (fromAddress.equals(hdWallet.getPaymentAddress())) {
            return FROM_LOCAL_USER;
        }

        return NOT_RELEVANT;
    }


    private static class ClientSideCustomData {
        private String localPrice;
    }
}
