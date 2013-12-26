package de.uvwxy.daisy.osmdroid;

import java.util.List;

import org.osmdroid.bonuspack.overlays.DefaultInfoWindow;
import org.osmdroid.bonuspack.overlays.ExtendedOverlayItem;
import org.osmdroid.bonuspack.overlays.InfoWindow;
import org.osmdroid.bonuspack.utils.BonusPackHelper;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.safecanvas.ISafeCanvas;
import org.osmdroid.views.safecanvas.SafeTranslatedCanvas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import de.uvwxy.daisy.proto.Messages.Annotation;
import de.uvwxy.daisy.proto.Messages.ChatMessage;
import de.uvwxy.daisy.proto.Messages.Image;
import de.uvwxy.daisy.proto.Messages.Location;
import de.uvwxy.daisy.proto.Messages.NodeLocationData;

/**
 * An itemized overlay with an InfoWindow or "bubble" which opens when the user
 * taps on an overlay item, and displays item attributes. <br>
 * Items must be ExtendedOverlayItem. <br>
 * 
 * 
 * @see ExtendedOverlayItem
 * @see InfoWindow
 * 
 * @author Original author M.Kergall, modified by Paul Smith
 */
public class ItemizedOverlayWithBubble<Item extends OverlayItem> extends ItemizedIconOverlay<Item> {
	//protected List<Item> mItemsList;
	protected InfoWindow mBubble; //only one for all items of this overlay => one at a time
	protected OverlayItem mItemWithBubble; //the item currently showing the bubble. Null if none. 

	private static final SafeTranslatedCanvas sSafeCanvas = new SafeTranslatedCanvas();

	static int layoutResId = 0;

	public ItemizedOverlayWithBubble(final Context context, final List<Item> aList, final MapView mapView,
			final InfoWindow bubble) {
		super(context, aList, new OnItemGestureListener<Item>() {
			@Override
			public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
				return false;
			}

			@Override
			public boolean onItemLongPress(final int index, final OverlayItem item) {
				return false;
			}
		});
		//mItemsList = aList;
		if (bubble != null) {
			mBubble = bubble;
		} else {
			//build default bubble:
			String packageName = context.getPackageName();
			Log.d("OVERLAY", "packageName stuff " + packageName);

			if (layoutResId == 0) {
				layoutResId = context.getResources().getIdentifier("layout/bonuspack_bubble", null, packageName);
				if (layoutResId == 0)
					Log.e(BonusPackHelper.LOG_TAG, "ItemizedOverlayWithBubble: layout/bonuspack_bubble not found in "
							+ packageName);
			}
			mBubble = new DefaultInfoWindow(layoutResId, mapView);
		}
		mItemWithBubble = null;
	}

	public ItemizedOverlayWithBubble(final Context context, final List<Item> aList, final MapView mapView) {
		this(context, aList, mapView, null);
	}

	/**
	 * Opens the bubble on the item. For each ItemizedOverlay, only one bubble
	 * is opened at a time. If you want more bubbles opened simultaneously, use
	 * many ItemizedOverlays.
	 * 
	 * @param index
	 *            of the overlay item to show
	 * @param mapView
	 */
	public void showBubbleOnItem(final int index, final MapView mapView, boolean panIntoView) {
		ExtendedOverlayItem eItem = (ExtendedOverlayItem) (getItem(index));
		mItemWithBubble = eItem;
		if (eItem != null) {
			eItem.showBubble(mBubble, mapView, panIntoView);
			//setFocus((Item)eItem);
		}
	}

	/**
	 * Close the bubble (if it's opened).
	 */
	public void hideBubble() {
		mBubble.close();
		mItemWithBubble = null;
	}

	@Override
	protected boolean onSingleTapUpHelper(final int index, final Item item, final MapView mapView) {
		Log.d("OVERLAY", "onTap item");
		showBubbleOnItem(index, mapView, true);
		mapView.refreshDrawableState();
		return true;
	}

	/** @return the item currently showing the bubble, or null if none. */
	public OverlayItem getBubbledItem() {
		if (mBubble.isOpen())
			return mItemWithBubble;
		else
			return null;
	}

	/**
	 * @return the index of the item currently showing the bubble, or -1 if
	 *         none.
	 */
	public int getBubbledItemId() {
		OverlayItem item = getBubbledItem();
		if (item == null)
			return -1;
		else
			return mItemList.indexOf(item);
	}

	@Override
	public synchronized Item removeItem(final int position) {
		Item result = super.removeItem(position);
		if (mItemWithBubble == result) {
			hideBubble();
		}
		return result;
	}

	@Override
	public synchronized boolean removeItem(final Item item) {
		boolean result = super.removeItem(item);
		if (mItemWithBubble == item) {
			hideBubble();
		}
		return result;
	}

	@Override
	public synchronized void removeAllItems() {
		super.removeAllItems();
		hideBubble();
	}

	@Override
	public synchronized void draw(final Canvas canvas, final MapView mapView, final boolean shadow) {
		final Projection pj = mapView.getProjection();
		final int size = mItemList.size() - 1;
		final Point mCurScreenCoords = new Point();

		sSafeCanvas.setCanvas(canvas);

		// Find the screen offset
		Rect screenRect = mapView.getProjection().getScreenRect();
		sSafeCanvas.xOffset = -screenRect.left;
		sSafeCanvas.yOffset = -screenRect.top;

		Paint p = new Paint();
		p.setColor(Color.GRAY);
		p.setAntiAlias(true);

		//1. Fixing drawing focused item on top in ItemizedOverlay (osmdroid issue 354):
		//2. Fixing lack of synchronization on mItemList
		if (shadow) {
			for (int i = size; i >= 0; i--) {
				final Item item = getItem(i);
				if (item instanceof ExtendedOverlayItem) {
					ExtendedOverlayItem e = (ExtendedOverlayItem) item;

					Object o = e.getRelatedObject();
					Location tempLoc = null;

					if (o == null) {
						Log.i("NODEBUBBLE", "Object was null");
						continue;
					}

					if (o instanceof NodeLocationData) {
						p.setColor(Color.BLACK);
						tempLoc = ((NodeLocationData) e.getRelatedObject()).getLocation();
					}

					if (o instanceof Image) {
						p.setColor(Color.CYAN);
						tempLoc = ((Image) e.getRelatedObject()).getLocation();
					}

					if (o instanceof Annotation) {
						p.setColor(Color.GRAY);
						tempLoc = ((Annotation) e.getRelatedObject()).getLocation();
					}

					if (o instanceof ChatMessage) {
						p.setColor(Color.WHITE);
						tempLoc = ((ChatMessage) e.getRelatedObject()).getLocation();
					}

					pj.toMapPixels(item.getPoint(), mCurScreenCoords);

					canvas.drawCircle(mCurScreenCoords.x, mCurScreenCoords.y, 6, p);
					p.setStrokeWidth(1);
					p.setColor(Color.RED);
					canvas.drawLine(mCurScreenCoords.x, mCurScreenCoords.y - 3, mCurScreenCoords.x,
							mCurScreenCoords.y + 3, p);
					canvas.drawLine(mCurScreenCoords.x - 3, mCurScreenCoords.y, mCurScreenCoords.x + 3,
							mCurScreenCoords.y, p);

					if (tempLoc != null) {
						double accuracy = tempLoc.getAccuracy();
						p.setColor(Color.YELLOW);
						p.setAlpha(50);
						canvas.drawCircle(mCurScreenCoords.x, mCurScreenCoords.y,
								(float) accuracy * pj.metersToEquatorPixels(1), p);
						p.setColor(Color.BLACK);
						p.setAlpha(255);
						p.setStyle(Style.STROKE);
						canvas.drawCircle(mCurScreenCoords.x, mCurScreenCoords.y,
								(float) accuracy * pj.metersToEquatorPixels(1), p);
						p.setStyle(Style.FILL_AND_STROKE);

					}

					if (o instanceof NodeLocationData) {
						NodeLocationData n = ((NodeLocationData) o);

						p.setColor(Color.GREEN);
						p.setStrokeWidth(3);

						float rotation = 0;

						if (n.hasParallelOrientationX()) {
							rotation = n.getParallelOrientationX();
						} else {
							rotation = n.getQrCodeBearing();
						}

						Point rot = new Point();
						rot.x = mCurScreenCoords.x + 32;
						rot.y = mCurScreenCoords.y;

						// fix to compass 0 degrees going up
						rotation -= 90;
						rot = rotate(rot, mCurScreenCoords, rotation);

						canvas.drawLine(mCurScreenCoords.x, mCurScreenCoords.y, rot.x, rot.y, p);

						if (n.hasHeight()) {
							p.setColor(Color.BLUE);
							canvas.drawLine(mCurScreenCoords.x - 10, mCurScreenCoords.y, mCurScreenCoords.x - 10,
									mCurScreenCoords.y - n.getHeight() * pj.metersToEquatorPixels(1), p);
						}
					}

				}
			}
			return;
		}

		/*
		 * Draw in backward cycle, so the items with the least index are on the
		 * front.
		 */
		for (int i = size; i >= 0; i--) {
			final Item item = getItem(i);
			if (item != mItemWithBubble) {
				pj.toMapPixels(item.getPoint(), mCurScreenCoords);
				onDrawItem(sSafeCanvas, item, mCurScreenCoords, 0f);
			}
		}
		//draw focused item last:
		if (mItemWithBubble != null) {
			pj.toMapPixels(mItemWithBubble.getPoint(), mCurScreenCoords);
			onDrawItem(sSafeCanvas, (Item) mItemWithBubble, mCurScreenCoords, 0f);
		}
	}

	public void repopulate() {
		populate();
	}

	Matrix transform = new Matrix();

	public Point rotate(Point myPoint, Point center, float degrees) {
		// taken from:
		// http://stackoverflow.com/questions/7795028/get-new-position-of-coordinate-after-rotation-with-matrix

		// This is to rotate about the Rectangles center
		transform.setRotate(degrees, center.x, center.y);

		// Create new float[] to hold the rotated coordinates
		float[] pts = new float[2];

		// Initialize the array with our Coordinate
		pts[0] = myPoint.x;
		pts[1] = myPoint.y;

		// Use the Matrix to map the points
		transform.mapPoints(pts);

		// NOTE: pts will be changed by transform.mapPoints call
		// after the call, pts will hold the new cooridnates

		// Now, create a new Point from our new coordinates
		Point newPoint = new Point((int) pts[0], (int) pts[1]);

		// Return the new point
		return newPoint;
	}
}
