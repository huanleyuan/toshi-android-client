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

package com.toshi.manager.chat;


import com.toshi.crypto.signal.ChatService;
import com.toshi.crypto.signal.SignalPreferences;
import com.toshi.crypto.signal.store.ProtocolStore;
import com.toshi.manager.OnboardingManager;
import com.toshi.util.GcmPrefsUtil;
import com.toshi.util.GcmUtil;
import com.toshi.util.logging.LogUtil;

import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;

import rx.Completable;
import rx.schedulers.Schedulers;

public class SofaMessageRegistration {

    private final ChatService chatService;
    private final ProtocolStore protocolStore;

    public SofaMessageRegistration(final ChatService chatService, final ProtocolStore protocolStore) {
        this.chatService = chatService;
        this.protocolStore = protocolStore;

        if (this.chatService == null || this.protocolStore == null) {
            throw new NullPointerException("Initialised with null");
        }
    }

    public Completable registerIfNeeded() {
        if (!SignalPreferences.getRegisteredWithServer()) {
            return this.chatService
                    .registerKeys(this.protocolStore)
                    .andThen(setRegisteredWithServer())
                    .andThen(registerChatGcm())
                    .andThen(new OnboardingManager().tryTriggerOnboarding())
                    .doOnError(throwable -> LogUtil.exception("Error while registering keys", throwable));
        } else {
            return registerChatGcm();
        }
    }

    public Completable registerIfNeededWithOnboarding() {
        if (SignalPreferences.getRegisteredWithServer()) return Completable.complete();
        return this.chatService
                .registerKeys(this.protocolStore)
                .andThen(setRegisteredWithServer())
                .andThen(registerChatGcm())
                .andThen(new OnboardingManager().tryTriggerOnboarding())
                .doOnError(throwable -> LogUtil.exception("Error while registering keys", throwable));
    }

    private Completable setRegisteredWithServer() {
        return Completable.fromAction(SignalPreferences::setRegisteredWithServer);
    }

    public Completable registerChatGcm() {
        return GcmUtil.getGcmToken()
                .flatMapCompletable(this::tryRegisterChatGcm);
    }

    private Completable tryRegisterChatGcm(final String token) {
        if (token == null) {
            LogUtil.exception("Token is null while trying GCM registration");
            return Completable.error(new IllegalStateException("Token can't be null"));
        }
        return Completable.fromAction(() -> {
            try {
                final Optional<String> optional = Optional.of(token);
                this.chatService.setGcmId(optional);
            } catch (IOException e) {
                LogUtil.exception("Error during registering of GCM " + e.getMessage());
                Completable.error(e);
            }
        })
        .subscribeOn(Schedulers.io());
    }

    public Completable tryUnregisterGcm() {
        return Completable.fromAction(() -> {
            try {
                this.chatService.setGcmId(Optional.absent());
            } catch (IOException e) {
                LogUtil.exception("Error during unregistering of GCM " + e.getMessage());
                Completable.error(e);
            }
        })
        .subscribeOn(Schedulers.io());
    }

    public void clearGcmPrefs() {
        GcmPrefsUtil.clear();
    }
}
