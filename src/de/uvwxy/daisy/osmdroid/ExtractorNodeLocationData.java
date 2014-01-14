package de.uvwxy.daisy.osmdroid;

import android.content.Context;
import android.graphics.drawable.Drawable;
import de.uvwxy.daisy.proto.Messages.Location;
import de.uvwxy.daisy.proto.Messages.NodeLocationData;
import de.uvwxy.daisy.proto.ProtoHelper;
import de.uvwxy.daisy.protocol.DaisyData;
import de.uvwxy.daisy.sensornode.SensorNetworkMessageParser;

public class ExtractorNodeLocationData extends IOverlayExtractor<NodeLocationData> {

	public ExtractorNodeLocationData(DaisyData data) {
		super(data);
	}

	@Override
	public Location getLocation(Context ctx, NodeLocationData e) {
		return e.getLocation();
	}

	@Override
	public String getTitle(Context ctx, NodeLocationData e) {
		return "Node: " + e.getNodeId();
	}

	@Override
	public String getDescription(Context ctx, NodeLocationData e) {
		return ProtoHelper.getDescription(e);
	}

	@Override
	public String getSubDescription(Context ctx, NodeLocationData e) {
		if (e.hasLandmarkText()) {
			return "Landmark Text:\n" + e.getLandmarkText();
		}
		return null;
	}

	@Override
	public Drawable getMapIcon(Context ctx, NodeLocationData e) {
		return SensorNetworkMessageParser.getBatteryIcon(data, ctx, e);
	}

}
