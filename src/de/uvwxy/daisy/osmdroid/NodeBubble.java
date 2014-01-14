package de.uvwxy.daisy.osmdroid;

import java.io.File;

import org.osmdroid.bonuspack.overlays.DefaultInfoWindow;
import org.osmdroid.bonuspack.overlays.ExtendedOverlayItem;
import org.osmdroid.views.MapView;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;

import com.google.common.base.Preconditions;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import de.uvwxy.daisy.common.R;
import de.uvwxy.daisy.proto.Messages.Annotation;
import de.uvwxy.daisy.proto.Messages.Image;
import de.uvwxy.daisy.proto.Messages.NodeLocationData;
import de.uvwxy.daisy.protocol.DaisyData;
import de.uvwxy.helper.BitmapTools;
import de.uvwxy.helper.FileTools;
import de.uvwxy.helper.IntentTools;

public class NodeBubble extends DefaultInfoWindow {
	private NodeLocationData mNodeLocData;
	private Image mImage;
	private Annotation mAnnotation;

	private Context ctx;
	@SuppressWarnings("unused")
	private Activity act;
	private DaisyData data;

	public NodeBubble(MapView mapView, final Activity act, Context ctx, final DaisyData data) {
		super(R.layout.bonuspack_bubble, mapView);

		Preconditions.checkNotNull(ctx);
		Preconditions.checkNotNull(act);
		Preconditions.checkNotNull(data);
		this.ctx = ctx;
		this.act = act;
		this.data = data;

		Button btn = (Button) (mView.findViewById(R.id.bubble_moreinfo));
		// bonuspack_bubble layouts already contain a "more info" button.
		btn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				if (mNodeLocData != null) {
					if (mNodeLocData.hasNodeId()) {
						IntentTools.showNodeData(act, mNodeLocData.getNodeId());
					}
				}
				if (mAnnotation != null) {
					if (mAnnotation.hasAudioFile()) {
						final String path = FileTools.getAndCreateExternalFolder(DaisyData.IMAGES_FOLDER) + data.getIdAndTimeStamp() + "/"
								+ mAnnotation.getAudioFile();
						IntentTools.showAudioFile(act, path);
					}
				}
			}
		});
	}

	@Override
	public void onOpen(Object item) {
		super.onOpen(item);
		Log.i("NODEBUBBLE", "onOpen");
		imageUpdaterRunning = true;
		Object o = ((ExtendedOverlayItem)item).getRelatedObject();
		if (o == null) {
			Log.i("NODEBUBBLE", "Object was null");
			return;
		}

		if (o instanceof NodeLocationData) {
			mNodeLocData = (NodeLocationData) ((ExtendedOverlayItem)item).getRelatedObject();
			setupNodeLocBubble();
		}

		if (o instanceof Image) {
			mImage = (Image) ((ExtendedOverlayItem)item).getRelatedObject();
			setupImageBubble();
		}

		if (o instanceof Annotation) {
			mAnnotation = (Annotation) ((ExtendedOverlayItem)item).getRelatedObject();
			setupAnnotationBubble();
		}
	}

	@Override
	public void onClose() {
		super.onClose();
		imageUpdaterRunning = false;
	}

	private Runnable imageUpdater = new Runnable() {

		@Override
		public void run() {
			if (!imageUpdaterRunning) {
				return;
			}
			int max = mNodeLocData.getImagePathCount();

			i++;
			String path;
			try {
				path = FileTools.getAndCreateExternalFolder(DaisyData.IMAGES_FOLDER) + data.getIdAndTimeStamp() + "/" + mNodeLocData.getImagePath(i % max);
			} catch (Exception e) {
				return;
			}

			Picasso.with(ctx) //
					.load(new File(path)) //
					.error(R.drawable.missing_image) //
					.resize(800, 600)//
					.transform(cropSquare) //
					.into(imageView);
			if (!imageUpdaterRunning) {
				return;
			} else {
				h.postDelayed(imageUpdater, delayMillis);
			}

		}
	};

	Handler h;
	int i = 0;
	private long delayMillis = 3000;;
	private boolean imageUpdaterRunning = true;

	ImageView imageView;

	private void setupNodeLocBubble() {
		// Fetch the thumbnail in background
		if (mNodeLocData != null && mNodeLocData.getImagePathCount() > 0) {
			imageView = (ImageView) mView.findViewById(R.id.bubble_image);
			//int dp = 65;
			// int pix = BitmapTools.dipToPixels(ctx, dp);
			imageView.setVisibility(View.VISIBLE);
			String path = FileTools.getAndCreateExternalFolder(DaisyData.IMAGES_FOLDER) + data.getIdAndTimeStamp() + "/" + mNodeLocData.getImagePath(0);
			Picasso.with(ctx) //
					.load(new File(path)) //
					.error(R.drawable.missing_image) //
					.resize(800, 600)//
					.transform(cropSquare) //
					.skipCache() //
					.into(imageView);
			h = new Handler(ctx.getMainLooper());
			h.postDelayed(imageUpdater, delayMillis);
		}

		// Show or hide "more info" button:
		if (mNodeLocData != null && mNodeLocData.hasNodeId()) {
			mView.findViewById(R.id.bubble_moreinfo).setVisibility(View.VISIBLE);
		} else {
			mView.findViewById(R.id.bubble_moreinfo).setVisibility(View.GONE);
		}
	}

	private void setupImageBubble() {
		if (mImage != null) {
			imageView = (ImageView) mView.findViewById(R.id.bubble_image);
			//int dp = 65;
			//int pix = BitmapTools.dipToPixels(ctx, dp);
			imageView.setVisibility(View.VISIBLE);
			final String path = FileTools.getAndCreateExternalFolder(DaisyData.IMAGES_FOLDER) + data.getIdAndTimeStamp() + "/" + mImage.getImagePath();
			Picasso.with(ctx) //
					.load(new File(path)) //
					.error(R.drawable.missing_image) //
					.resize(320, 240)//
					// .transform(cropSquare) //
					.skipCache() //
					.into(imageView);
			// Show or hide "more info" button:
			if (path != null) {
				imageView.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						IntentTools.showImage(ctx, path);
					}
				});
			}
		}
	}

	private void setupAnnotationBubble() {
		// does not change anything at runtime: (nexus 4, 4.2.2)
		// TextView title = (TextView) mView.findViewById(R.id.bubble_title);
		// title.setWidth(BitmapTools.dipToPixels(ctx, 128));

		boolean showImage = true;
		if (showImage) {
			imageView = (ImageView) mView.findViewById(R.id.bubble_image);

			//int dp = 28;
			//int pix = BitmapTools.dipToPixels(ctx, dp);
			imageView.setVisibility(View.VISIBLE);

			Picasso.with(ctx) //
					.load(R.drawable.ic_action_annotation) //
					.error(R.drawable.missing_image) //
					.resize(160, 120)//
					// .transform(cropSquare) //
					.skipCache() //
					.into(imageView);
		}

		if (mAnnotation != null && mAnnotation.hasAudioFile()) {

			mView.findViewById(R.id.bubble_moreinfo).setVisibility(View.VISIBLE);
		}
	}

	private final Transformation cropSquare = new CropSquareTransformation();

	class SquaredImageView extends ImageView {
		public SquaredImageView(Context context) {
			super(context);
		}

		public SquaredImageView(Context context, AttributeSet attrs) {
			super(context, attrs);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
		}
	}

	class CropSquareTransformation implements Transformation {
		@Override
		public Bitmap transform(Bitmap source) {
			int size = BitmapTools.dipToPixels(ctx, 48);// Math.min(source.getWidth(),
														// source.getHeight());

			int x = (source.getWidth() - size) / 2;
			int y = (source.getHeight() - size) / 2;

			Bitmap squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
			if (squaredBitmap != source) {
				source.recycle();
			}
			return squaredBitmap;
		}

		@Override
		public String key() {
			return "square()";
		}
	}
}
