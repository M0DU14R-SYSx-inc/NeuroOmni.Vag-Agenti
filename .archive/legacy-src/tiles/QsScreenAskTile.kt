package com.horizons.tiles

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QsScreenAskTile : TileService() {
    override fun onClick() {
        super.onClick()
        // Phase 7: bridge to ScreenAskTile flow without needing the floating overlay.
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }
}
