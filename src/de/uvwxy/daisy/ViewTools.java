package de.uvwxy.daisy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.uvwxy.daisy.common.R;
import de.uvwxy.helper.BitmapTools;
import de.uvwxy.helper.IntentTools;

public class ViewTools {

	public interface RemovePath {
		public void removePath(String path);
	}

	public static void addToLinearLayout(final Context ctx, final LinearLayout llPhotoList, Bitmap bmp, final String path, final RemovePath rmp) {
		if (ctx == null) {
			throw new RuntimeException("Context was null!");
		}
		if (llPhotoList == null) {
			throw new RuntimeException("LinearLayout was null!");
		}
		if (bmp == null) {
			throw new RuntimeException("Bitmap was null!");
		}
		if (path == null) {
			throw new RuntimeException("String path was null!");
		}

		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View entry = inflater.inflate(R.layout.item_photolist, null);

		TextView tvPhotoInfo = (TextView) entry.findViewById(R.id.tvPhotoInfo);

		ImageView ivPhoto = (ImageView) entry.findViewById(R.id.ivPhoto);
		OnLongClickListener deleteImageListener = new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				AlertDialog.Builder alertDialog = new AlertDialog.Builder(ctx);

				alertDialog.setPositiveButton("Yes!", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (rmp != null) {
							rmp.removePath(path);
						}
						llPhotoList.removeView(entry);
					}
				});

				alertDialog.setNegativeButton("Cancel", null);
				alertDialog.setMessage("Please confirm if you want to remove this image from this deployment. (The image is still found on your phone)");
				alertDialog.setTitle("Remove entry?");
				alertDialog.show();
				return true;
			}
		};

		OnClickListener showImageListener = new OnClickListener() {

			@Override
			public void onClick(View v) {
				IntentTools.showImage(ctx, path);
			}
		};

		ivPhoto.setAdjustViewBounds(true);
		int dip = 128; // TODO: this is hard coded, derive from xml?
		ivPhoto.setMaxHeight(BitmapTools.dipToPixels(ctx, dip));
		ivPhoto.setMaxWidth(BitmapTools.dipToPixels(ctx, dip));
		ivPhoto.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		ivPhoto.setImageBitmap(bmp);

		tvPhotoInfo.setText("Image: " + path);// + "\nSize " + bmp.getWidth() + "x" + bmp.getHeight());

		entry.setOnLongClickListener(deleteImageListener);
		tvPhotoInfo.setOnLongClickListener(deleteImageListener);
		ivPhoto.setOnLongClickListener(deleteImageListener);
		entry.setOnClickListener(showImageListener);
		tvPhotoInfo.setOnClickListener(showImageListener);
		ivPhoto.setOnClickListener(showImageListener);

		llPhotoList.addView(entry, 0);
	}

	public static void updateViewOnUIThread(Activity act, final TextView v, final String txt) {
		Runnable r = new Runnable() {

			@Override
			public void run() {
				if (v != null) {
					v.setText(txt);
				}

			}
		};

		act.runOnUiThread(r);
	}


}
