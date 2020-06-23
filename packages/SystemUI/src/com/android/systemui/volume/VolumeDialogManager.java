/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.volume;

import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.Observer;

import com.android.systemui.plugins.VolumeDialog;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.settings.CurrentUserObservable;
import com.android.systemui.shared.plugins.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Manages custom clock faces for AOD and lock screen.
 */
@Singleton
public final class VolumeDialogManager {

    private static final String TAG = "ClockOptsProvider";

    private final AvailablePlugins mPreviewDialogs;
    private final List<Supplier<VolumeDialog>> mVolumeDialogs = new ArrayList<>();

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final SettingsWrapper mSettingsWrapper;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final CurrentUserObservable mCurrentUserObservable;

    /**
     * Observe settings changes to know when to switch the clock face.
     */
    private final ContentObserver mContentObserver =
            new ContentObserver(mMainHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri, int userId) {
                    super.onChange(selfChange, uri, userId);
                    if (Objects.equals(userId,
                            mCurrentUserObservable.getCurrentUser().getValue())) {
                        reload();
                    }
                }
            };

    /**
     * Observe user changes and react by potentially loading the custom clock for the new user.
     */
    private final Observer<Integer> mCurrentUserObserver = (newUserId) -> reload();

    private final PluginManager mPluginManager;

    /**
     * Listeners for onDialogChanged event.
     *
     * Each listener must receive a separate clock plugin instance. Otherwise, there could be
     * problems like attempting to attach a view that already has a parent. To deal with this issue,
     * each listener is associated with a collection of available dialogs. When onDialogChanged is
     * fired the current clock plugin instance is retrieved from that listeners available dialogs.
     */
    private final Map<DialogChangedListener, AvailablePlugins> mListeners = new ArrayMap<>();

    @Inject
    public VolumeDialogManager(Context context, PluginManager pluginManager) {
        this(context, pluginManager,
                context.getContentResolver(), new CurrentUserObservable(context),
                new SettingsWrapper(context.getContentResolver()));
    }

    @VisibleForTesting
    VolumeDialogManager(Context context, PluginManager pluginManager,
            ContentResolver contentResolver, CurrentUserObservable currentUserObservable,
            SettingsWrapper settingsWrapper) {
        mContext = context;
        mPluginManager = pluginManager;
        mContentResolver = contentResolver;
        mSettingsWrapper = settingsWrapper;
        mCurrentUserObservable = currentUserObservable;
        mPreviewDialogs = new AvailablePlugins();

        Resources res = context.getResources();

        addBuiltinDialog(() -> new VolumeDialogImpl(context));
        addBuiltinDialog(() -> new AospVolumeDialogImpl(context));
    }

    /**
     * Add listener to be notified when clock implementation should change.
     */
    public void addOnDialogChangedListener(DialogChangedListener listener) {
        if (mListeners.isEmpty()) {
            register();
        }
        AvailablePlugins AvailablePlugins = new AvailablePlugins();
        for (int i = 0; i < mVolumeDialogs.size(); i++) {
            AvailablePlugins.addVolumeDialog(mVolumeDialogs.get(i).get());
        }
        mListeners.put(listener, AvailablePlugins);
        mPluginManager.addPluginListener(AvailablePlugins, VolumeDialog.class, true);
        reload();
    }

    /**
     * Remove listener added with {@link addOnDialogChangedListener}.
     */
    public void removeOnDialogChangedListener(DialogChangedListener listener) {
        AvailablePlugins AvailablePlugins = mListeners.remove(listener);
        mPluginManager.removePluginListener(AvailablePlugins);
        if (mListeners.isEmpty()) {
            unregister();
        }
    }

    /**
     * Get information about available clock faces.
     */
    List<VolumeDialogInfo> getVolumeInfo() {
        return mPreviewDialogs.getInfo();
    }

    /**
     * Get the current clock.
     * @return current custom clock or null for default.
     */
    @Nullable
    VolumeDialog getCurrentDialog() {
        return mPreviewDialogs.getCurrentDialog();
    }

    @VisibleForTesting
    ContentObserver getContentObserver() {
        return mContentObserver;
    }

    private void addBuiltinDialog(Supplier<VolumeDialog> pluginSupplier) {
        VolumeDialog plugin = pluginSupplier.get();
        mPreviewDialogs.addVolumeDialog(plugin);
        mVolumeDialogs.add(pluginSupplier);
    }

    private void register() {
        mPluginManager.addPluginListener(mPreviewDialogs, VolumeDialog.class, true);
        mContentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.CUSTOM_VOLUME_DIALOG),
                false, mContentObserver, UserHandle.USER_ALL);
        mCurrentUserObservable.getCurrentUser().observeForever(mCurrentUserObserver);
    }

    private void unregister() {
        mPluginManager.removePluginListener(mPreviewDialogs);
        mContentResolver.unregisterContentObserver(mContentObserver);
        mCurrentUserObservable.getCurrentUser().removeObserver(mCurrentUserObserver);
    }

    private void reload() {
        mPreviewDialogs.reloadCurrentDialog();
        mListeners.forEach((listener, dialogs) -> {
            dialogs.reloadCurrentDialog();
            final VolumeDialog dialog = dialogs.getCurrentDialog();
            if (Looper.myLooper() == Looper.getMainLooper()) {
                listener.onDialogChanged(dialog instanceof VolumeDialogImpl ? null : dialog);
            } else {
                mMainHandler.post(() -> listener.onDialogChanged(
                        dialog instanceof VolumeDialogImpl ? null : dialog));
            }
        });
    }

    /**
     * Listener for events that should cause the custom clock face to change.
     */
    public interface DialogChangedListener {
        /**
         * Called when custom clock should change.
         *
         * @param clock Custom clock face to use. A null value indicates the default clock face.
         */
        void onDialogChanged(VolumeDialog dialog);
    }

    /**
     * Collection of available dialogs.
     */
    private final class AvailablePlugins implements PluginListener<VolumeDialog> {

        /**
         * Map from expected value stored in settings to plugin for custom clock face.
         */
        private final Map<String, VolumeDialog> mVolume = new ArrayMap<>();

        /**
         * Metadata about available dialogs, such as name and preview images.
         */
        private final List<VolumeDialogInfo> mVolumeInfo = new ArrayList<>();

        /**
         * Active VolumeDialog.
         */
        @Nullable private VolumeDialog mCurrentDialog;

        @Override
        public void onPluginConnected(VolumeDialog plugin, Context pluginContext) {
            addVolumeDialog(plugin);
            reloadIfNeeded(plugin);
        }

        @Override
        public void onPluginDisconnected(VolumeDialog plugin) {
            removeVolumeDialog(plugin);
            reloadIfNeeded(plugin);
        }

        /**
         * Get the current clock.
         * @return current custom clock or null for default.
         */
        @Nullable
        VolumeDialog getCurrentDialog() {
            return mCurrentDialog;
        }

        /**
         * Get information about available clock faces.
         */
        List<VolumeDialogInfo> getInfo() {
            return mVolumeInfo;
        }

        /**
         * Adds a clock plugin to the collection of available dialogs.
         *
         * @param plugin The plugin to add.
         */
        void addVolumeDialog(VolumeDialog plugin) {
            final String id = plugin.getClass().getName();
            mVolume.put(plugin.getClass().getName(), plugin);
            mVolumeInfo.add(VolumeDialogInfo.builder()
                    .setName(plugin.getName())
                    .setTitle(plugin::getTitle)
                    .setId(id)
                    .setThumbnail(plugin::getThumbnail)
                    .setPreview(plugin::getPreview)
                    .build());
        }

        private void removeVolumeDialog(VolumeDialog plugin) {
            final String id = plugin.getClass().getName();
            mVolume.remove(id);
            for (int i = 0; i < mVolumeInfo.size(); i++) {
                if (id.equals(mVolumeInfo.get(i).getId())) {
                    mVolumeInfo.remove(i);
                    break;
                }
            }
        }

        private void reloadIfNeeded(VolumeDialog plugin) {
            final boolean wasCurrentDialog = plugin == mCurrentDialog;
            reloadCurrentDialog();
            final boolean isCurrentDialog = plugin == mCurrentDialog;
            if (wasCurrentDialog || isCurrentDialog) {
                VolumeDialogManager.this.reload();
            }
        }

        /**
         * Update the current clock.
         */
        void reloadCurrentDialog() {
            mCurrentDialog = getVolumeDialog();
        }

        private VolumeDialog getVolumeDialog() {
            VolumeDialog plugin = null;
            final String name = mSettingsWrapper.getCustomVolumeDialog(
                    mCurrentUserObservable.getCurrentUser().getValue());
            if (name != null) {
                plugin = mVolume.get(name);
            }
            return plugin;
        }
    }
}
