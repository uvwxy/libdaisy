package de.uvwxy.daisy.osmdroid;

import org.osmdroid.tileprovider.MapTile;

public class MapTileTMS extends MapTile {

	public MapTileTMS(int zoomLevel, int tileX, int tileY) {
		super(zoomLevel, tileX, tileY);
	}

	
	@Override
	public int getY() {
		return (int)(Math.pow(2, super.getZoomLevel()) - super.getY() - 1);
	}
	

}
