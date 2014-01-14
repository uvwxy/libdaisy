package de.uvwxy.daisy.protocol;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.google.common.base.Preconditions;

import de.uvwxy.daisy.proto.Messages.BalloonImage;
import de.uvwxy.daisy.proto.Messages.BalloonPreview;
import de.uvwxy.daisy.proto.Messages.BalloonReply;
import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.daisy.protocol.busmessages.BalloonBusReply;

public class DaisyProtocolProcessBalloon {

	Context ctx;
	DaisyData data;
	DaisyProtocol parent;

	public DaisyProtocolProcessBalloon(Context ctx, DaisyData data, DaisyProtocol parent) {
		Preconditions.checkNotNull(ctx);
		Preconditions.checkNotNull(data);
		Preconditions.checkNotNull(parent);

		this.ctx = ctx;
		this.data = data;
		this.parent = parent;
	}

	void processBalloonHandshake(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processBalloonHandshake");
		parent.who = mob.getBalloonHandshake().getClientTag();

		try {
			Log.d("PROTOCOL", "send getBalloonReply");
			DaisyProtocolRoutinesBalloon.getBalloonReply(ctx, data).writeDelimitedTo(parent.connection.getOut());
		} catch (IOException e) {
			parent.closeAndKillNotification();
			e.printStackTrace();
		}
	}

	void processBalloonImage(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processBalloonImage");
		final BalloonImage bi = mob.getBalloonImage();
		String imagePath = DaisyProtocolRoutinesBalloon.getImagePath(data, bi.getSequenceNumber());
		byte[] previewData = bi.getImageData().toByteArray();
		try {
			FileUtils.writeByteArrayToFile(new File(imagePath), previewData);
			FileUtils.writeByteArrayToFile(new File(imagePath + ".pb"), bi.getBalloonStats().toByteArray());
		} catch (IOException e) {
			e.printStackTrace();
		}

		Handler h = new Handler(ctx.getMainLooper());
		h.post(new Runnable() {
			@Override
			public void run() {
				data.bus.post(bi);
				data.bus.post(bi.getBalloonStats());
			}
		});
	}

	void processBalloonImageRequest(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processBalloonImageRequest");
		String path = DaisyProtocolRoutinesBalloon.getImagePath(data, mob.getBalloonImageRequest().getSequenceNumber());
		try {
			Log.d("PROTOCOL", "send getBalloonImage");
			DaisyProtocolRoutinesBalloon.getBalloonImage(ctx, data, path).writeDelimitedTo(parent.connection.getOut());
		} catch (IOException e) {
			parent.closeAndKillNotification();
			e.printStackTrace();
		}
	}

	void processBalloonPreview(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processBalloonPreview");
		final BalloonPreview bp = mob.getBalloonPreview();
		String imagePath = DaisyProtocolRoutinesBalloon.getPreviewPath(data, bp.getSequenceNumber());
		byte[] previewData = bp.getPreviewData().toByteArray();
		Log.d("PROTOCOL", "previewData Size = " + previewData.length);
		try {
			Log.i("PROTOCOL", "Trying to save preview " + bp.getSequenceNumber());
			FileUtils.writeByteArrayToFile(new File(imagePath), previewData);
		} catch (IOException e) {
			e.printStackTrace();
		}

		Handler h = new Handler(ctx.getMainLooper());

		h.post(new Runnable() {
			@Override
			public void run() {
				data.bus.post(bp);
				data.bus.post(bp.getBalloonStats());
			}
		});

	}

	/**
	 * Generates a new preview from original image source, does not cache/save
	 * anything
	 * 
	 * @param mob
	 */
	void processBalloonPreviewRequest(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processBalloonPreviewRequest");
		int seq = mob.getBalloonPreviewRequest().getSequenceNumber();

		if (seq == -1) {
			seq = DaisyProtocolRoutinesBalloon.getLargestSquenceNumber(data,
					data.getDeploymentPath(DaisyData.DAISY_BALLOON_IMAGES_FOLDER));
		}

		// String path = DaisyProtocolRoutinesBalloon.getPreviewPath(data, seq);
		try {
			Log.d("PROTOCOL", "send getBalloonPreview");
			DaisyProtocolRoutinesBalloon.getBalloonPreview(ctx, data, seq,
					mob.getBalloonPreviewRequest().getResolution()).writeDelimitedTo(parent.connection.getOut());
		} catch (IOException e) {
			parent.closeAndKillNotification();
			e.printStackTrace();
		}
	}

	void processBalloonReply(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processBalloonReply");
		final BalloonReply br = mob.getBalloonReply();

		parent.who = br.getHostTag();

		Handler h = new Handler(ctx.getMainLooper());

		if (br.hasBalloonStats()) {
			h.post(new Runnable() {
				@Override
				public void run() {
					data.bus.post(br.getBalloonStats());
				}
			});
		}

		if (br.getIsBalloon()) {
			h.post(new Runnable() {
				@Override
				public void run() {
					data.bus.post(new BalloonBusReply(true, br.getAvailableResolutionsList()));
				}
			});
		} else {
			// the other side is not operating in balloon mode
			parent.sendFin();
			h.post(new Runnable() {
				@Override
				public void run() {
					data.bus.post(new BalloonBusReply(false, null));
				}
			});
		}
	}

	void processBalloonSettings(final DaisyProtocolMessage mob) {
		// passthrough to possible gui ;)
		Handler h = new Handler(ctx.getMainLooper());
		h.post(new Runnable() {
			@Override
			public void run() {
				if (mob.hasBalloonSettings()) {
					data.bus.post(mob.getBalloonSettings());
				}
			}
		});

	}
}
