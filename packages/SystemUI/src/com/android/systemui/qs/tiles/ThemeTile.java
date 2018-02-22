/*
 * Copyright (C) 2018 The Dirty Unicorns Project
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

package com.android.systemui.qs.tiles;

import static android.os.UserHandle.USER_SYSTEM;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.bliss.ThemesUtils;

import com.android.systemui.R;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSDetailItems;
import com.android.systemui.qs.QSDetailItems.Item;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class ThemeTile extends QSTileImpl<BooleanState> {

    final List<ThemeTileItem> sStyleItems = new ArrayList<ThemeTileItem>();
    {
        sStyleItems.add(new ThemeTileItem(UiModeManager.MODE_NIGHT_NO, -1,
                R.string.system_theme_style_light, "light"));
        sStyleItems.add(new ThemeTileItem(UiModeManager.MODE_NIGHT_YES, -1,
                R.string.system_theme_style_dark, "dark"));
        sStyleItems.add(new ThemeTileItem(UiModeManager.MODE_NIGHT_YES, -1,
                R.string.system_theme_style_solarized_dark, "solarized_dark"));
        sStyleItems.add(new ThemeTileItem(UiModeManager.MODE_NIGHT_YES, -1,
                R.string.system_theme_style_pitch_black, "pitch_black"));
        sStyleItems.add(new ThemeTileItem(UiModeManager.MODE_NIGHT_YES, -1,
                R.string.system_theme_style_dark_grey, "dark_grey"));
    }

    private enum Mode {
        STYLE
    }

    private static IOverlayManager mOverlayManager;
    private Mode mMode;
    private static UiModeManager mUiModeManager;

    @Inject
    public ThemeTile(QSHost host) {
        super(host);
        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));
        mUiModeManager = mContext.getSystemService(UiModeManager.class);
        mMode = Mode.STYLE;
    }

    private class ThemeTileItem {
        final int settingsVal;
        final int colorRes;
        final int labelRes;
        String uri;

        public ThemeTileItem(int settingsVal, int colorRes, int labelRes) {
            this.settingsVal = settingsVal;
            this.colorRes = colorRes;
            this.labelRes = labelRes;
        }

        public ThemeTileItem(int settingsVal, int colorRes, int labelRes, String uri) {
            this(settingsVal, colorRes, labelRes);
            this.uri = uri;
        }

        public String getLabel(Context context) {
            return context.getString(labelRes);
        }

        public void styleCommit(Context context) {
            mUiModeManager.setNightMode(settingsVal);
        }

        public QSTile.Icon getIcon(Context context) {
            QSTile.Icon icon = new QSTile.Icon() {
                @Override
                public Drawable getDrawable(Context context) {
                    ShapeDrawable oval = new ShapeDrawable(new OvalShape());
                    oval.setIntrinsicHeight(context.getResources()
                            .getDimensionPixelSize(R.dimen.qs_detail_image_height));
                    oval.setIntrinsicWidth(context.getResources()
                            .getDimensionPixelSize(R.dimen.qs_detail_image_width));
                    oval.getPaint().setColor(context.getColor(colorRes));
                    return oval;
                }
            };
            return icon;
        }
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return new ThemeDetailAdapter();
    }

    private class ThemeDetailAdapter
            implements DetailAdapter, AdapterView.OnItemClickListener {
        private QSDetailItemsList mItemsList;
        private QSDetailItemsList.QSDetailListAdapter mAdapter;
        private List<Item> mThemeItems = new ArrayList<>();

        @Override
        public CharSequence getTitle() {
            return mContext.getString(
                    R.string.quick_settings_theme_tile_style_detail_title);
        }

        @Override
        public Boolean getToggleState() {
            return null;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mItemsList = QSDetailItemsList.convertOrInflate(context, convertView, parent);
            mAdapter = new QSDetailItemsList.QSDetailListAdapter(context, mThemeItems);
            ListView listView = mItemsList.getListView();
            listView.setDivider(null);
            listView.setOnItemClickListener(this);
            listView.setAdapter(mAdapter);
            updateItems();
            return mItemsList;
        }

        private void updateItems() {
            if (mAdapter == null)
                return;
            mThemeItems.clear();
            mThemeItems.addAll(getStyleItems());
            mAdapter.notifyDataSetChanged();
        }

        private List<Item> getStyleItems() {
            List<Item> items = new ArrayList<Item>();
            for (ThemeTileItem styleItem : sStyleItems) {
                Item item = new Item();
                item.tag = styleItem;
                item.doDisableFocus = true;
                item.iconResId = R.drawable.ic_qs_style_list;
                item.line1 = styleItem.getLabel(mContext);
                items.add(item);
            }
            return items;
        }

        @Override
        public Intent getSettingsIntent() {
            return new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        }

        @Override
        public void setToggleState(boolean state) {
        }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.BLISSIFY;
        }


        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Item item = (Item) parent.getItemAtPosition(position);
            if (item == null || item.tag == null)
                return;
            ThemeTileItem themeItem = (ThemeTileItem) item.tag;
            showDetail(false);
            mHost.collapsePanels();
            themeItem.styleCommit(mContext);
            for (int i = 0; i < ThemesUtils.SOLARIZED_DARK.length; i++) {
                String solarized_dark = ThemesUtils.SOLARIZED_DARK[i];
                try {
                    mOverlayManager.setEnabled(solarized_dark,
                            themeItem.uri.equals("solarized_dark"), USER_SYSTEM);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            for (int i = 0; i < ThemesUtils.PITCH_BLACK.length; i++) {
                String pitch_black = ThemesUtils.PITCH_BLACK[i];
                try {
                    mOverlayManager.setEnabled(pitch_black,
                            themeItem.uri.equals("pitch_black"), USER_SYSTEM);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            for (int i = 0; i < ThemesUtils.DARK_GREY.length; i++) {
                String dark_grey = ThemesUtils.DARK_GREY[i];
                try {
                    mOverlayManager.setEnabled(dark_grey,
                            themeItem.uri.equals("dark_grey"), USER_SYSTEM);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        showDetail(true);
    }

    @Override
    protected void handleLongClick() {
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = mContext.getString(R.string.system_theme_style_title);
        state.icon = ResourceIcon.get(R.drawable.ic_qs_style);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BLISSIFY;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleSetListening(boolean listening) {
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_theme_tile_title);
    }
}



