/*
 * Copyright (C) 2023 DerpFest
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.bliss

import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.CellularTile
import com.android.systemui.qs.tiles.WifiTile
import com.android.systemui.qs.tiles.PreferredNetworkTile
import com.android.systemui.qs.tiles.DataSwitchTile
import com.android.systemui.qs.tiles.SmartPixelsTile

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface BlissModule {
    /** Inject CellularTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CellularTile.TILE_SPEC)
    fun bindCellularTile(cellularTile: CellularTile): QSTileImpl<*>

    /** Inject WifiTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(WifiTile.TILE_SPEC)
    fun bindWifiTile(wifiTile: WifiTile): QSTileImpl<*>

    /** Inject PreferredNetworkTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(PreferredNetworkTile.TILE_SPEC)
    fun bindPreferredNetworkTile(preferredNetworkTile: PreferredNetworkTile): QSTileImpl<*>

    /** Inject DataSwitchTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(DataSwitchTile.TILE_SPEC)
    fun bindDataSwitchTile(dataSwitchTile: DataSwitchTile): QSTileImpl<*>

    /** Inject SmartPixelsTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(SmartPixelsTile.TILE_SPEC)
    fun bindSmartPixelsTile(smartPixelsTile: SmartPixelsTile): QSTileImpl<*>
}
