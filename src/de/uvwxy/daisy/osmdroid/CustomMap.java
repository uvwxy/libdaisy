package de.uvwxy.daisy.osmdroid;

public class CustomMap {
	String mapName = "Mapnik";
	String fileName;
	boolean isMapNameAGuess = true;

	public CustomMap(String fileName) {
		super();
		this.fileName = fileName;
		this.mapName = "Mapnik";

		if (mapName != null) {
			isMapNameAGuess = true;
		}
	}

	public CustomMap(String fileName, String mapName) {
		super();
		this.mapName = mapName;
		this.fileName = fileName;

		if (mapName != null) {
			isMapNameAGuess = false;
		}
	}

	public String getMapName() {
		return mapName;
	}

	public String getFileName() {
		return fileName;
	}

	public boolean isMapNameAGuess() {
		return isMapNameAGuess;
	}

	public String toString() {
		return fileName + " [" + mapName + (isMapNameAGuess ? "?" : "") + "]";
	}

}
