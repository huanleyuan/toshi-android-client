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

import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.toshi.R;
import com.toshi.model.network.Currencies;
import com.toshi.model.network.Currency;
import com.toshi.util.CurrencyComparator;
import com.toshi.util.logging.LogUtil;
import com.toshi.util.sharedPrefs.SharedPrefs;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.CurrencyActivity;
import com.toshi.view.adapter.CurrencyAdapter;
import com.toshi.view.custom.HorizontalLineDivider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Single;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class CurrencyPresenter implements Presenter<CurrencyActivity> {

    private CurrencyActivity activity;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;
    private List<Currency> currencies;
    private int scrollPosition = -1;

    @Override
    public void onViewAttached(CurrencyActivity view) {
        this.activity = view;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }

        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
        this.currencies = new ArrayList<>();
    }

    private void initShortLivingObjects() {
        initRecyclerView();
        initClickListeners();
        hideAppBarIfScrolled();
        getCurrencies();
    }

    private void initRecyclerView() {
        final RecyclerView currencyList = this.activity.getBinding().currencyList;
        final LinearLayoutManager llm = new LinearLayoutManager(this.activity);
        currencyList.setLayoutManager(llm);
        final CurrencyAdapter adapter = new CurrencyAdapter(this.currencies)
                .setOnClickListener(this::handleCurrencyClicked);
        currencyList.setAdapter(adapter);
        final int padding = this.activity.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        final HorizontalLineDivider lineDivider =
                new HorizontalLineDivider(ContextCompat.getColor(this.activity, R.color.divider))
                        .setRightPadding(padding)
                        .setLeftPadding(padding);
        currencyList.addItemDecoration(lineDivider);
        llm.scrollToPosition(this.scrollPosition);
    }

    private void hideAppBarIfScrolled() {
        if (this.scrollPosition != -1)
        this.activity.getBinding().appBar.setExpanded(false);
    }

    private void initClickListeners() {
        this.activity.getBinding().closeButton.setOnClickListener(__ -> this.activity.finish());
    }

    private void handleCurrencyClicked(final Currency currency) {
        SharedPrefs.INSTANCE.saveCurrency(currency.getCode());
        BaseApplication.get().getBalanceManager().refreshBalance();
        this.activity.finish();
    }

    private void getCurrencies() {
        final Subscription sub =
                BaseApplication
                .get()
                .getBalanceManager()
                .getCurrencies()
                .map(Currencies::getCurrencies)
                .flatMap(this::filterCryptocurrencies)
                .flatMap(this::sortCurrencies)
                .doOnSuccess(currencies -> this.currencies = currencies)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        __ -> handleCurrencies(),
                        this::handleError
                );

        this.subscriptions.add(sub);
    }

    private Single<List<Currency>> sortCurrencies(final List<Currency> currencies) {
        return Single.fromCallable(() -> {
            Collections.sort(currencies, new CurrencyComparator());
            return currencies;
        })
                .subscribeOn(Schedulers.io());
    }

    private Single<List<Currency>> filterCryptocurrencies(final List<Currency> currencies) {
        return Single.fromCallable(() -> {
            currencies.remove(new Currency("BTC"));
            return currencies;
        });
    }

    private void handleCurrencies() {
        if (this.activity == null) return;
        final CurrencyAdapter adapter = (CurrencyAdapter) this.activity.getBinding().currencyList.getAdapter();
        adapter.addItems(this.currencies);
    }

    private void handleError(final Throwable throwable) {
        LogUtil.exception(throwable);
    }

    @Override
    public void onViewDetached() {
        cacheScrollPosition();
        this.subscriptions.clear();
        this.activity = null;
    }

    private void cacheScrollPosition() {
        if (this.activity == null) return;
        final LinearLayoutManager llm = (LinearLayoutManager) this.activity.getBinding().currencyList.getLayoutManager();
        this.scrollPosition = llm.findFirstVisibleItemPosition();
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.activity = null;
    }
}
