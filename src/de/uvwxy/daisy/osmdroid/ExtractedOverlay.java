package de.uvwxy.daisy.osmdroid;

import java.util.ArrayList;
import java.util.List;

import org.osmdroid.bonuspack.overlays.ExtendedOverlayItem;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.OverlayItem;

import android.app.Activity;
import android.content.Context;

import com.google.common.base.Preconditions;

import de.uvwxy.daisy.proto.ProtoHelper;
import de.uvwxy.daisy.protocol.DaisyData;

public class ExtractedOverlay<E> {
	private ItemizedOverlayWithBubble<ExtendedOverlayItem> mMyLocationOverlay;
	private IOverlayExtractor<E> extractor;
	private ItemizedOverlayWithBubble overlay;

	public ExtractedOverlay(IOverlayExtractor<E> extractor, Context ctx, MapView mv, Activity act, DaisyData data) {
		Preconditions.checkNotNull(extractor);
		Preconditions.checkNotNull(ctx);
		Preconditions.checkNotNull(mv);

		overlay = new ItemizedOverlayWithBubble<ExtendedOverlayItem>(ctx, new ArrayList<ExtendedOverlayItem>(), mv, new NodeBubble(mv, act, ctx, data));
		this.extractor = extractor;
	}

	public ItemizedOverlayWithBubble<OverlayItem> getOverlay() {
		return overlay;
	}

	public void replaceObjects(Context ctx, List<E> list) {
		Preconditions.checkNotNull(ctx);

		overlay.removeAllItems();
		for (E e : list) {
			addObject(ctx, e);
		}
	}

	private void addObject(Context ctx, E e) {
		Preconditions.checkNotNull(ctx);

		if (e == null) {
			return;
		}

		GeoPoint aGeoPoint = new GeoPoint(ProtoHelper.protoLocationToAndroidLocation(extractor.getLocation(ctx, e)));
		ExtendedOverlayItem item = new ExtendedOverlayItem(extractor.getTitle(ctx, e), extractor.getDescription(ctx, e), aGeoPoint, ctx);
		item.setMarker(extractor.getMapIcon(ctx, e));

		if (extractor.getSubDescription(ctx, e) != null) {
			item.setSubDescription(extractor.getSubDescription(ctx, e));
		}
		item.setRelatedObject(e);
		overlay.addItem(item);
	}

}
