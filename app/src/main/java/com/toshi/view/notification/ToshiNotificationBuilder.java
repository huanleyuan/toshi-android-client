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

package com.toshi.view.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.toshi.R;
import com.toshi.model.sofa.SofaType;
import com.toshi.util.sharedPrefs.SharedPrefs;
import com.toshi.view.BaseApplication;
import com.toshi.view.notification.model.ToshiNotification;

import java.util.Arrays;
import java.util.List;

public class ToshiNotificationBuilder {

    private static final String MESSAGES_CHANNEL_NAME = "Messages";
    private static final String PAYMENTS_CHANNEL_NAME = "Payments";

    /* package */ static NotificationCompat.Builder buildNotification(final ToshiNotification notification) {
        if (Build.VERSION.SDK_INT >= 26) {
            clearNotificationChannelsIfNeeded();
            createNotificationChannels();
        }

        final int lightOnRate = 1000 * 2;
        final int lightOffRate = 1000 * 15;
        final int notificationColor = ContextCompat.getColor(BaseApplication.get(), R.color.colorPrimary);
        final Uri notificationSound = Uri.parse("android.resource://" + BaseApplication.get().getPackageName() + "/" + R.raw.notification);
        final NotificationCompat.Style style = generateNotificationStyle(notification);
        final CharSequence contextText = notification.getLastMessage();
        final String channelId = getChannelId(notification);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(BaseApplication.get(), channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(notification.getLargeIcon())
                .setContentTitle(notification.getTitle())
                .setContentText(contextText)
                .setTicker(contextText)
                .setAutoCancel(true)
                .setSound(notificationSound)
                .setColor(notificationColor)
                .setLights(notificationColor, lightOnRate, lightOffRate)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(style)
                .setNumber(notification.getNumberOfUnreadMessages());

        final int maxNumberMessagesWithSound = 3;
        if (notification.getNumberOfUnreadMessages() > maxNumberMessagesWithSound) {
            builder
                    .setSound(null)
                    .setVibrate(null);
        }

        return builder;
    }

    private static String getChannelId(final ToshiNotification toshiNotification) {
        if (toshiNotification.getTypeOfLastMessage() == SofaType.PAYMENT) {
            return String.valueOf(SofaType.PAYMENT);
        } else {
            return String.valueOf(SofaType.PLAIN_TEXT);
        }
    }

    @RequiresApi(api = 26)
    private static void createNotificationChannels() {
        final NotificationManager manager = (NotificationManager) BaseApplication.get().getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannels(Arrays.asList(createMessagesChannel(), createPaymentsChannel()));
    }

    @RequiresApi(api = 26)
    private static NotificationChannel createMessagesChannel() {
        final String id = String.valueOf(SofaType.PLAIN_TEXT);
        final NotificationChannel notificationChannel = new NotificationChannel(id, MESSAGES_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        return addNotificationAttributes(notificationChannel);
    }

    @RequiresApi(api = 26)
    private static NotificationChannel createPaymentsChannel() {
        final String id = String.valueOf(SofaType.PAYMENT);
        final NotificationChannel notificationChannel = new NotificationChannel(id, PAYMENTS_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        return addNotificationAttributes(notificationChannel);
    }

    @RequiresApi(api = 26)
    private static NotificationChannel addNotificationAttributes(final NotificationChannel notificationChannel) {
        final int notificationColor = ContextCompat.getColor(BaseApplication.get(), R.color.colorPrimary);
        final Uri notificationSound = Uri.parse("android.resource://" + BaseApplication.get().getPackageName() + "/" + R.raw.notification);

        notificationChannel.enableLights(true);
        notificationChannel.setLightColor(notificationColor);
        notificationChannel.enableVibration(true);
        notificationChannel.setSound(notificationSound,
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
        );

        return notificationChannel;
    }

    //TODO: can be deleted 8th Feb 2018
    @RequiresApi(api = 26)
    private static void clearNotificationChannelsIfNeeded() {
        final boolean hasClearedNotificationChannels = SharedPrefs.INSTANCE.hasClearedNotificationChannels();
        if (hasClearedNotificationChannels) return;

        final NotificationManager manager = (NotificationManager) BaseApplication.get().getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        for (NotificationChannel channel : manager.getNotificationChannels()) {
            manager.deleteNotificationChannel(channel.getId());
        }

        SharedPrefs.INSTANCE.setHasClearedNotificationChannels();
    }

    private static NotificationCompat.Style generateNotificationStyle(final ToshiNotification notification) {
        final int numberOfUnreadMessages = notification.getNumberOfUnreadMessages();

        if (numberOfUnreadMessages == 1) {
            return new NotificationCompat
                    .BigTextStyle()
                    .setBigContentTitle(notification.getTitle())
                    .bigText(notification.getLastMessage());
        }

        final List<String> lastFewMessages = notification.getLastFewMessages();
        final NotificationCompat.Style style =
                new NotificationCompat
                        .InboxStyle()
                        .setBigContentTitle(notification.getTitle());
        for (final String message : lastFewMessages) {
            ((NotificationCompat.InboxStyle) style).addLine(message);
        }
        return style;
    }

    /* package */ static void showNotification(final ToshiNotification notification, final NotificationCompat.Builder builder) {
        final NotificationManager manager = (NotificationManager) BaseApplication.get().getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.notify(notification.getTag(), 1, builder.build());
    }
}
