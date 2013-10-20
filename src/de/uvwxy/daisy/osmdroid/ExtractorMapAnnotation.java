package de.uvwxy.daisy.osmdroid;

import android.content.Context;
import android.graphics.drawable.Drawable;
import de.uvwxy.daisy.proto.Messages.Annotation;
import de.uvwxy.daisy.proto.Messages.Location;
import de.uvwxy.daisy.protocol.DaisyData;
import de.uvwxy.helper.DateTools;

public class ExtractorMapAnnotation extends IOverlayExtractor<Annotation> {

	public ExtractorMapAnnotation(DaisyData data) {
		super(data);
	}

	@Override
	public Location getLocation(Context ctx, Annotation e) {
		return e.getLocation();
	}

	@Override
	public String getTitle(Context ctx, Annotation e) {
		return "Annotation: " + DateTools.getDateTimeLong(ctx, e.getTimestamp());
	}

	@Override
	public String getDescription(Context ctx, Annotation e) {
		return e.getNote();
	}

	@Override
	public Drawable getMapIcon(Context ctx, Annotation e) {
		return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.ic_action_annotation);
	}

}
