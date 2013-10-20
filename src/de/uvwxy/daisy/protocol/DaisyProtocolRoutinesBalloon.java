package de.uvwxy.daisy.protocol;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import de.uvwxy.battery.BatteryReader;
import de.uvwxy.daisy.proto.Messages.BalloonHandshake;
import de.uvwxy.daisy.proto.Messages.BalloonImage;
import de.uvwxy.daisy.proto.Messages.BalloonImageRequest;
import de.uvwxy.daisy.proto.Messages.BalloonPreview;
import de.uvwxy.daisy.proto.Messages.BalloonPreviewRequest;
import de.uvwxy.daisy.proto.Messages.BalloonReply;
import de.uvwxy.daisy.proto.Messages.BalloonSettings;
import de.uvwxy.daisy.proto.Messages.BalloonStats;
import de.uvwxy.daisy.proto.Messages.CamSize;
import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.helper.BitmapTools;
import de.uvwxy.helper.FileTools;

public class DaisyProtocolRoutinesBalloon {

	static Splitter onFullStop = Splitter.on('.');
	static Splitter onUnderScore = Splitter.on('_');

	public static int getLargestSquenceNumber(DaisyData data, String folder) {
		Preconditions.checkNotNull(data);
		Preconditions.checkNotNull(folder);

		String path = folder;
		Log.d("BALLOON", "Looking into folder " + path);
		File dir = new File(path);
		String[] list = dir.list();

		if (list == null || list.length == 0) {
			Log.d("BALLOON", "list == null or length == 0");
			return -1;
		}

		int maxIndex = -1;

		for (String file : list) {
			if (file == null) {
				continue;
			}
			try {
				int tempIndex = getSequenceNumberFromFileName(file);
				if (tempIndex > maxIndex) {
					maxIndex = tempIndex;
					Log.d("BALLOON", "maxIndex = " + maxIndex);

				}
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
		}

		return maxIndex;
	}

	public static ArrayList<Integer> getMissingSequenceNumbers(DaisyData data, String folder) {
		ArrayList<Integer> result = new ArrayList<Integer>();

		int max = getLargestSquenceNumber(data, data.getDeploymentPath(folder));

		for (int i = 0; i < max; i++) {
			String path = folder + "balloon_" + i + ".jpg";

			if (new File(path).exists()) {
				continue;
			}

			result.add(i);
		}

		return result;
	}

	public static int getSequenceNumberFromFileName(String file) {
		List<String> strWithoutExtension = onFullStop.splitToList(file);
		String strIndex = onUnderScore.splitToList(strWithoutExtension.get(0)).get(1);
		try {
			Log.d("BALLOON", "Returning int?");
			return Integer.parseInt(strIndex);
		} catch (Exception e) {
			Log.d("BALLOON", "int parse err");
			e.printStackTrace();
		}
		return -1;
	}

	public static String getImagePath(DaisyData data, int imgNumber) {
		return data.getDeploymentPath(DaisyData.DAISY_BALLOON_IMAGES_FOLDER) + "balloon_" + imgNumber + ".jpg";
	}

	public static String getPreviewPath(DaisyData data, int imgNumber) {
		return data.getDeploymentPath(DaisyData.DAISY_BALLOON_PREVIEW_FOLDER) + "balloon_" + imgNumber + ".jpg";
	}

	public static DaisyProtocolMessage getBalloonHandshake(DaisyData data) {
		DaisyProtocolMessage.Builder b = DaisyProtocolMessage.newBuilder();
		BalloonHandshake bh = BalloonHandshake.newBuilder().setClientTag(data.getTag()).build();
		return b.setBalloonHandshake(bh).build();
	}

	public static DaisyProtocolMessage getBalloonReply(Context ctx, DaisyData data) {
		DaisyProtocolMessage.Builder b = DaisyProtocolMessage.newBuilder();
		BalloonReply.Builder br = BalloonReply.newBuilder();

		br.setHostTag(data.getTag());
		br.setIsBalloon(data.isBalloon());

		if (data.isBalloon()) {
			br.setLastImageSequenceNumber(getLargestSquenceNumber(data, DaisyData.DAISY_BALLOON_IMAGES_FOLDER));
			br.addAllAvailableResolutions(data.getCameraResolutions());

		}

		br.setBalloonStats(getBalloonStats(ctx, data));

		return b.setBalloonReply(br).build();
	}

	public static BalloonStats getBalloonStats(Context ctx, DaisyData data) {
		BalloonStats.Builder b = BalloonStats.newBuilder();

		b.setBatteryLevel(BatteryReader.getLevel(ctx));
		
		String path = FileTools.getAndCreateExternalFolder(data.getDeploymentPath(DaisyData.DAISY_BALLOON_IMAGES_FOLDER));
		long freeSpace = new File(path).getFreeSpace();
		b.setFreeBytes(freeSpace);

		// TODO: include position and further optional stats data

		return b.build();
	}

	public static DaisyProtocolMessage getBalloonPreviewRequest(CamSize previewSize, int sequence_number) {
		DaisyProtocolMessage.Builder b = DaisyProtocolMessage.newBuilder();

		BalloonPreviewRequest.Builder brp = BalloonPreviewRequest.newBuilder();

		brp.setSequenceNumber(sequence_number);
		brp.setResolution(previewSize);

		b.setBalloonPreviewRequest(brp.build());

		return b.build();
	}

	public static DaisyProtocolMessage getBalloonPreview(Context ctx, DaisyData data, int seq, CamSize camSize) {
		DaisyProtocolMessage.Builder b = DaisyProtocolMessage.newBuilder();

		BalloonPreview.Builder bp = BalloonPreview.newBuilder();
		boolean success = false;
		// TODO.
		String path = DaisyProtocolRoutinesBalloon.getImagePath(data, seq);
		File f = new File(path);
		if (f.exists()) {
			Bitmap bmp = BitmapTools.loadScaledBitmapPixels(ctx, path, camSize.getX(), camSize.getY());
			if (bmp == null) {
				Log.d("BALLOON", "loading scaled bitmap failed");
				success = false;
			} else {

				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
				bmp.compress(CompressFormat.JPEG, 85, byteStream);

				byte[] fileBytes = byteStream.toByteArray();// FileUtils.readFileToByteArray(f);
				Log.d("BALLOON", "fileBytes Size = " + fileBytes.length);
				bp.setPreviewData(ByteString.copyFrom(fileBytes));
				bp.setSequenceNumber(getSequenceNumberFromFileName(f.getName()));
				bp.setPreviewSize(camSize);
				success = true && bp.getSequenceNumber() != -1;
			}
		} else {
			Log.d("BALLOON", "file does not exist " + path);
		}

		bp.setBalloonStats(getBalloonStats(ctx, data));
		bp.setSuccess(success);

		b.setBalloonPreview(bp.build());
		return b.build();
	}

	public static DaisyProtocolMessage getBalloonImageRequest(int sequence_number) {
		DaisyProtocolMessage.Builder b = DaisyProtocolMessage.newBuilder();

		BalloonImageRequest.Builder bip = BalloonImageRequest.newBuilder();

		bip.setSequenceNumber(sequence_number);

		b.setBalloonImageRequest(bip.build());

		return b.build();
	}

	public static DaisyProtocolMessage getBalloonImage(Context ctx, DaisyData data, String path) {
		DaisyProtocolMessage.Builder b = DaisyProtocolMessage.newBuilder();

		BalloonImage.Builder bp = BalloonImage.newBuilder();
		boolean success = false;
		// TODO.
		File f = new File(path);
		if (f.exists()) {
			try {
				byte[] fileBytes = FileUtils.readFileToByteArray(f);
				bp.setImageData(ByteString.copyFrom(fileBytes));
				bp.setSequenceNumber(getSequenceNumberFromFileName(f.getName()));
				// TODO: read preview size
				success = true && bp.getSequenceNumber() != -1;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		File fpb = new File(path + ".pb");
		BalloonStats bStats = null;

		if (fpb.exists()) {
			try {
				bStats = BalloonStats.parseFrom(FileUtils.readFileToByteArray(fpb));
			} catch (InvalidProtocolBufferException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (bStats != null) {
			bp.setBalloonStats(bStats);
		} else {
			bp.setBalloonStats(getBalloonStats(ctx, data));

		}

		bp.setSuccess(success);

		b.setBalloonImage(bp.build());

		return b.build();
	}

	public static DaisyProtocolMessage getBalloonSettings(CamSize imageSize, int delay_seconds) {
		DaisyProtocolMessage.Builder b = DaisyProtocolMessage.newBuilder();

		BalloonSettings.Builder bs = BalloonSettings.newBuilder();

		if (imageSize != null) {
			bs.setImageSize(imageSize);
		}

		if (delay_seconds > 0) {
			bs.setCaptureDelay(delay_seconds);
		}

		b.setBalloonSettings(bs.build());

		return b.build();
	}

}
