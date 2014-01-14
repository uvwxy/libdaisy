package de.uvwxy.daisy.osmdroid;

import android.content.Context;
import android.graphics.drawable.Drawable;
import de.uvwxy.daisy.proto.Messages.Image;
import de.uvwxy.daisy.proto.Messages.Location;
import de.uvwxy.daisy.protocol.DaisyData;
import de.uvwxy.helper.DateTools;

public class ExtractorImage extends IOverlayExtractor<Image> {

	public ExtractorImage(DaisyData data) {
		super(data);
	}

	@Override
	public Location getLocation(Context ctx, Image e) {
		return e.getLocation();
	}

	@Override
	public String getTitle(Context ctx, Image e) {
		return "Image: " + DateTools.getDateTimeLong(ctx, e.getTimestamp());
	}

	@Override
	public String getDescription(Context ctx, Image e) {
		return null;
	}

	@Override
	public Drawable getMapIcon(Context ctx, Image e) {
		return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.ic_action_image);
	}

}
