package de.uvwxy.daisy.osmdroid;

import android.content.Context;
import android.graphics.drawable.Drawable;
import de.uvwxy.daisy.proto.Messages.Location;
import de.uvwxy.daisy.protocol.DaisyData;
import de.uvwxy.helper.DateTools;

public class ExtractorUserLocation extends IOverlayExtractor<Location> {

	public ExtractorUserLocation(DaisyData data) {
		super(data);
	}

	@Override
	public Location getLocation(Context ctx, Location e) {
		return e;
	}

	@Override
	public String getTitle(Context ctx, Location e) {
		return "Last Location: ";
	}

	@Override
	public String getDescription(Context ctx, Location e) {
		return DateTools.getDateTimeLong(ctx, e.getTime());
	}

	@Override
	public Drawable getMapIcon(Context ctx, Location e) {
		return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.ic_user_location);

	}
}
