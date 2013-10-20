package de.uvwxy.daisy.osmdroid;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.Overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationManager;

public class UserLocationOverlay extends Overlay {

	private Location location = new Location("Dummy");

	public UserLocationOverlay(Context ctx) {
		super(ctx);
	}

	public void replaceObjects(Location l) {
		if (l == null) {
			return;
		}

		this.location = l;

	}

	@Override
	protected void draw(final Canvas canvas, final MapView mapView, final boolean shadow) {
		if (shadow) {
			return;
		}

		final Projection pj = mapView.getProjection();

		Paint p = new Paint();
		if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
			p.setColor(Color.GREEN);
		} else if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
			p.setColor(Color.BLUE);
		} else {
			p.setColor(Color.RED);
		}

		p.setStrokeWidth(16.0f);
		p.setAntiAlias(true);
		p.setAlpha(60);
		p.setStrokeCap(Cap.ROUND);

		GeoPoint locationUser = new GeoPoint(location);

		Point a = new Point();

		pj.toPixels(locationUser, a);
		canvas.drawCircle(a.x, a.y, location.getAccuracy() * pj.metersToEquatorPixels(1), p);
		p.setStyle(Style.STROKE);
		p.setAlpha(255);
		p.setStrokeWidth(1);
		canvas.drawCircle(a.x, a.y, location.getAccuracy() * pj.metersToEquatorPixels(1), p);

		p.setStrokeWidth(4);
		canvas.drawLine(a.x, a.y - 16, a.x, a.y + 16, p);
		canvas.drawLine(a.x - 16, a.y, a.x + 16, a.y, p);
	}

	public int getColor(float h, int alpha) {
		float s = 1.0f;
		float v = 1.0f;
		float[] hsv = new float[] { h, s, v };

		return Color.HSVToColor(alpha, hsv);
	}
}
