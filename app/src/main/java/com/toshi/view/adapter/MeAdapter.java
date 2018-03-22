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

package com.toshi.view.adapter;


import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.toshi.R;
import com.toshi.exception.CurrencyException;
import com.toshi.util.sharedPrefs.SharedPrefs;
import com.toshi.view.BaseApplication;
import com.toshi.view.adapter.listeners.OnItemClickListener;
import com.toshi.view.adapter.viewholder.ClickableViewHolder;

public class MeAdapter extends RecyclerView.Adapter<MeAdapter.ViewHolder> {

    public static final int LOCAL_CURRENCY = 0;
    public static final int ADVANCED = 1;
    public static final int SIGN_OUT = 2;

    private final String[] settings;
    public OnItemClickListener<Integer> onItemClickListener;

    public MeAdapter() {
        this.settings = BaseApplication.get().getResources().getStringArray(R.array.settings_options);
    }

    public void setOnItemClickListener(final OnItemClickListener<Integer> listener) {
        this.onItemClickListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__settings, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        String label = this.settings[position];

        //Add the currency in parentheses
        final String localCurrency = BaseApplication.get().getString(R.string.local_currency);
        if (label.equals(localCurrency)) {
            label = String.format("%s (%s)", label, getCurrency());
        }

        holder.label.setText(label);
        holder.bind(position, onItemClickListener);
    }

    private String getCurrency() {
        try {
            return SharedPrefs.INSTANCE.getCurrency();
        } catch (CurrencyException e) {
            return "";
        }
    }

    @Override
    public int getItemCount() {
        return this.settings.length;
    }

    static class ViewHolder extends ClickableViewHolder {
        private TextView label;

        private ViewHolder(final View view) {
            super(view);
            this.label = view.findViewById(R.id.label);
        }

        public void bind(final int position, final OnItemClickListener<Integer> listener) {
            this.itemView.setOnClickListener(view -> {
                if (listener == null) return;
                listener.onItemClick(position);
            });
        }
    }
}
