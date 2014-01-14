package de.uvwxy.daisy.osmdroid;

import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.tileprovider.tilesource.ITileSource;

import android.graphics.drawable.Drawable;

public class MapTileProviderArrayTMS extends MapTileProviderArray {
	protected MapTileProviderArrayTMS(ITileSource pTileSource, IRegisterReceiver pRegisterReceiver) {
		super(pTileSource, pRegisterReceiver);
	}

	public MapTileProviderArrayTMS(ITileSource pTileSource, IRegisterReceiver aRegisterReceiver,
			MapTileModuleProviderBase[] pTileProviderArray) {
		super(pTileSource, aRegisterReceiver, pTileProviderArray);
	}
	
	@Override
	public Drawable getMapTile(MapTile tile) {
		MapTileTMS conversionToTMS = new MapTileTMS(tile.getZoomLevel(), tile.getX(), tile.getY());
		return super.getMapTile(conversionToTMS);
	}

}
