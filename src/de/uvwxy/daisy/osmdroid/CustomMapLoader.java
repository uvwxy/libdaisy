package de.uvwxy.daisy.osmdroid;

import java.io.File;
import java.util.ArrayList;

import android.util.Log;
import de.uvwxy.helper.FileNameFilterFromStringArray;
import de.uvwxy.helper.FileTools;

public class CustomMapLoader {
	public static ArrayList<CustomMap> getCustomMaps(String baseDir) {
		File f = new File(baseDir);
		Log.i("MAP", "Reading : " + baseDir);

		String[] ls = f.list(new FileNameFilterFromStringArray(new String[] { "zip", "gemf", "sqlite", "mbtiles" }));

		if (ls == null) {
			return null;
		}
		Log.i("MAP", "Read : " + ls.length + " files");

		ArrayList<CustomMap> mapList = new ArrayList<CustomMap>();
		for (String s : ls) {
			if (s == null) {
				continue;
			}
			String mapName = readMapName(baseDir + s);
			Log.i("MAP", "Reading: " + s);
			CustomMap map = mapName == null ? new CustomMap(s) : new CustomMap(s, mapName);
			mapList.add(map);
		}

		return mapList;
	}

	public static String readMapName(String pathToArchive) {
		String[] lines = FileTools.readLinesOfFile(1, pathToArchive + ".mapName");

		if (lines != null && lines.length > 0 && lines[0] != null) {
			return lines[0];
		}

		return null;
	}
}
