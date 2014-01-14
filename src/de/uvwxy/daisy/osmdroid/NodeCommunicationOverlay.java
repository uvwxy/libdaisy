package de.uvwxy.daisy.osmdroid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.Overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Point;

import com.google.common.base.Preconditions;

import de.uvwxy.daisy.proto.Messages.NodeCommunicationData;
import de.uvwxy.daisy.proto.Messages.NodeLocationData;
import de.uvwxy.daisy.proto.Messages.SensorsDataCollectionMsg;
import de.uvwxy.daisy.proto.ProtoHelper;
import de.uvwxy.daisy.protocol.DaisyData;

public class NodeCommunicationOverlay extends Overlay {

	ArrayList<NodeCommunicationData> commData = new ArrayList<NodeCommunicationData>();
	ArrayList<NodeLocationData> nodeLocData = new ArrayList<NodeLocationData>();
	HashMap<Integer, GeoPoint> nodeIDGeoPointMap = new HashMap<Integer, GeoPoint>();

	DaisyData data;

	public NodeCommunicationOverlay(Context ctx, DaisyData data) {
		super(ctx);
		Preconditions.checkNotNull(data);
		this.data = data;
	}

	public void replaceObjects(Context ctx, List<NodeCommunicationData> list) {
		Preconditions.checkNotNull(ctx);

		commData.clear();
		commData.addAll(list);

		nodeLocData.clear();
		nodeLocData.addAll(data.getNodeLocationDataList());

		nodeIDGeoPointMap.clear();
		// create a list to get geopoint form node id
		for (NodeLocationData nld : nodeLocData) {
			GeoPoint nldPoint = new GeoPoint(ProtoHelper.protoLocationToAndroidLocation(nld.getLocation()));
			nodeIDGeoPointMap.put(Integer.valueOf(nld.getNodeId()), nldPoint);
		}
	}

	@Override
	protected void draw(final Canvas canvas, final MapView mapView, final boolean shadow) {
		if (shadow) {
			return;
		}

		final Projection pj = mapView.getProjection();

		final int size = this.commData.size();

		Paint p = new Paint();
		p.setColor(Color.RED);
		p.setStrokeWidth(16.0f);
		p.setAntiAlias(true);
		p.setAlpha(60);
		p.setStrokeCap(Cap.ROUND);

		for (int i = 0; i < size; i++) {
			NodeCommunicationData ncd = commData.get(i);
			if (ncd == null) {
				continue;
			}
			drawNCD(canvas, pj, ncd, p);
		}

	}

	protected void drawNCD(final Canvas canvas, final Projection pj, final NodeCommunicationData ncd, Paint p) {
		if (!ncd.hasSensorData()) {
			// nothing to draw, as we do not have a parsed message in this
			// object.
			return;
		}

		SensorsDataCollectionMsg data = ncd.getSensorData();
		int from = data.getNodeId();
		int to = data.getParentNodeId();

		GeoPoint ptFrom = nodeIDGeoPointMap.get(Integer.valueOf(from));
		GeoPoint ptTo = nodeIDGeoPointMap.get(Integer.valueOf(to));

		Point a = new Point();
		Point b = new Point();
		pj.toPixels(ptFrom, a);
		pj.toPixels(ptTo, b);

		canvas.drawLine(a.x, a.y, b.x, b.y, p);
	}

	public int getColor(float h, int alpha) {
		float s = 1.0f;
		float v = 1.0f;
		float[] hsv = new float[] { h, s, v };

		return Color.HSVToColor(alpha, hsv);
	}
}
