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

package com.toshi.presenter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Toast;

import com.toshi.BuildConfig;
import com.toshi.R;
import com.toshi.crypto.HDWallet;
import com.toshi.model.local.Network;
import com.toshi.model.local.Networks;
import com.toshi.util.BuildTypes;
import com.toshi.util.DialogUtil;
import com.toshi.util.logging.LogUtil;
import com.toshi.util.QrCode;
import com.toshi.util.sharedPrefs.SharedPrefs;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.BackupPhraseInfoActivity;
import com.toshi.view.activity.DepositActivity;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class DepositPresenter implements Presenter<DepositActivity> {

    private DepositActivity activity;
    private HDWallet wallet;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;
    private AlertDialog warningDialog;

    @Override
    public void onViewAttached(DepositActivity view) {
        this.activity = view;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }

        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initShortLivingObjects() {
        showWarningDialogIfNotBackedUp();
        initNetworkView();
        initClickListeners();
        attachListeners();
    }

    private void showWarningDialogIfNotBackedUp() {
        if (SharedPrefs.INSTANCE.hasBackedUpPhrase()) return;

        final AlertDialog.Builder builder =
                DialogUtil.getBaseDialog(
                        this.activity,
                        R.string.balance_dialog_title,
                        R.string.balance_dialog_body,
                        R.string.backup,
                        R.string.cancel,
                        (dialog, __) -> handlePositiveButtonClicked(dialog)
                );

        this.warningDialog = builder.create();
        this.warningDialog.show();
    }

    private void handlePositiveButtonClicked(final DialogInterface dialog) {
        dialog.dismiss();
        final Intent intent = new Intent(this.activity, BackupPhraseInfoActivity.class);
        this.activity.startActivity(intent);
    }

    private void initNetworkView() {
        final boolean showNetwork = BuildConfig.BUILD_TYPE.equals(BuildTypes.DEBUG);
        this.activity.getBinding().network.setVisibility(showNetwork ? View.VISIBLE : View.GONE);

        if (showNetwork) {
            final Network network = Networks.getInstance().getCurrentNetwork();
            this.activity.getBinding().network.setText(network.getName());
        }
    }

    private void initClickListeners() {
        this.activity.getBinding().closeButton.setOnClickListener(this::handleCloseButtonClicked);
        this.activity.getBinding().copyToClipboard.setOnClickListener(this::handleCopyToClipboardClicked);
    }

    private void handleCloseButtonClicked(final View v) {
        this.activity.finish();
    }

    private void handleCopyToClipboardClicked(final View v) {
        final ClipboardManager clipboard = (ClipboardManager) this.activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText(this.activity.getString(R.string.payment_address), this.wallet.getPaymentAddress());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this.activity, this.activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
    }

    private void attachListeners() {
        final Subscription sub =
                BaseApplication.get()
                .getToshiManager()
                .getWallet()
                .toObservable()
                .filter(wallet -> wallet != null)
                .toSingle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleWalletCallback,
                        this::handleWalletError
                );

        this.subscriptions.add(sub);
    }

    private void handleWalletError(final Throwable throwable) {
        LogUtil.exception("Error while fetching wallet", throwable);
        showToast(R.string.qr_code_error);
    }

    private void handleWalletCallback(final HDWallet wallet) {
        this.wallet = wallet;
        updateView(wallet);
    }

    private void updateView(final HDWallet wallet) {
        if (this.activity == null) return;
        this.activity.getBinding().ownerAddress.setText(wallet.getPaymentAddress());
        this.activity.getBinding().copyToClipboard.setEnabled(true);
        generateQrCode(wallet);
    }

    private void generateQrCode(final HDWallet wallet) {
        final Subscription sub =
                QrCode.generatePaymentAddressQrCode(wallet.getPaymentAddress())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::renderQrCode,
                        this::handleQrCodeError
                );

        this.subscriptions.add(sub);
    }

    private void handleQrCodeError(final Throwable throwable) {
        LogUtil.exception("Error while generating qr code", throwable);
        showToast(R.string.qr_code_error);
    }

    private void renderQrCode(final Bitmap qrCodeBitmap) {
        if (this.activity == null) return;
        this.activity.getBinding().qrCode.setAlpha(0.0f);
        this.activity.getBinding().qrCode.setImageBitmap(qrCodeBitmap);
        this.activity.getBinding().qrCode.animate().alpha(1f).setDuration(200).start();
    }

    private void showToast(@StringRes final int messageId) {
        Toast.makeText(this.activity, messageId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onViewDetached() {
        closeDialog();
        this.subscriptions.clear();
        this.activity = null;
    }

    private void closeDialog() {
        if (this.warningDialog != null) {
            this.warningDialog.dismiss();
            this.warningDialog = null;
        }
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.activity = null;
    }
}
