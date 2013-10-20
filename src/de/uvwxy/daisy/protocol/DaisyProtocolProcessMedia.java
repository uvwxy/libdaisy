package de.uvwxy.daisy.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;

import android.content.Context;
import android.util.Log;
import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.daisy.proto.Messages.ExistingFileDescriptor;
import de.uvwxy.daisy.proto.Messages.FileChunk;
import de.uvwxy.daisy.proto.Messages.MediaReply;
import de.uvwxy.daisy.proto.Messages.MediaRequest;

public class DaisyProtocolProcessMedia {

	Context ctx;
	DaisyData data;
	DaisyProtocol parent;

	public DaisyProtocolProcessMedia(Context ctx, DaisyData data, DaisyProtocol parent) {
		Preconditions.checkNotNull(ctx);
		Preconditions.checkNotNull(data);
		Preconditions.checkNotNull(parent);

		this.ctx = ctx;
		this.data = data;
		this.parent = parent;
	}

	void processMediaHandshake(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processMediaHandshake");
		parent.who = mob.getMediaHandshake().getClientTag();
		// get files available on connecting sides
		List<ExistingFileDescriptor> existingFiles = mob.getMediaHandshake().getAvailableFilesList();

		ArrayList<FileChunk> missingLocalFileTailChunks = new ArrayList<FileChunk>();

		FileChunk tmp = null;
		for (ExistingFileDescriptor efd : existingFiles) {
			tmp = DaisyProtocolRoutinesMedia.checkIfExistsOrNeedsMoreData(efd, data);
			if (tmp == null) {
				continue;
			}

			missingLocalFileTailChunks.add(tmp);
		}

		try {
			Log.d("PROTOCOL", "send processMediaHandshake");
			DaisyProtocolMessage.newBuilder()//
					.setMediaRequest(MediaRequest.newBuilder().addAllRequestedFiles(missingLocalFileTailChunks))//
					.build()//
					.writeDelimitedTo(parent.connection.getOut());
		} catch (IOException e) {
			parent.closeAndKillNotification();
			e.printStackTrace();
		}
	}

	void processMediaReply(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processMediaReply");
		FileChunk fc = mob.getMediaReply().getFileChunk();

		if (mob.getMediaReply().hasCurrentChunkNumber() && mob.getMediaReply().hasTotalNumberOfChunks()) {
			int current = mob.getMediaReply().getCurrentChunkNumber();
			int total = mob.getMediaReply().getTotalNumberOfChunks();
			DaisyProtocolRoutinesMedia.updateNotification(parent.connection, ctx, false, fc.getName(), current, total);
			Log.i("MEDIAPROTO", fc.getFolder() + "/" + fc.getName() + ": chunk: " + current + "/" + total);
		}

		if (mob.getMediaReply().getLastChunk()) {

			if (parent.isServer) {
				try {
					Log.d("PROTOCOL", "send getMediaHandshake");
					DaisyProtocolRoutinesMedia.getMediaHandshake(data).writeDelimitedTo(parent.connection.getOut());
				} catch (IOException e) {
					parent.closeAndKillNotification();
					e.printStackTrace();
				}
			} else {
				Log.i("PROTOCOL", "PROTOCOL is over");
				parent.sendFin();
			}

		} else {

			try {
				DaisyProtocolRoutinesMedia.saveChunk(fc, data);
			} catch (IOException e) {
				parent.closeAndKillNotification();
				e.printStackTrace();
			}

		}
		DaisyProtocolRoutinesMedia.hideNotification(ctx);
	}

	void processMediaRequest(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processMediaRequest");
		List<FileChunk> requestedChunks = mob.getMediaRequest().getRequestedFilesList();

		for (FileChunk fc : requestedChunks) {
			Log.i("MEDIAPROTO", "fc = " + fc.getName());

			try {
				Log.d("PROTOCOL", "send sendFileAsChunks");
				DaisyProtocolRoutinesMedia.sendFileAsChunks(fc, parent.connection, data, ctx);
			} catch (IOException e) {
				e.printStackTrace();
				parent.closeAndKillNotification();
				return;
			}

		}

		// notify other side we are done
		try {
			Log.d("PROTOCOL", "send setLastChunk");

			DaisyProtocolMessage.newBuilder()//
					.setMediaReply(MediaReply.newBuilder().setLastChunk(true).build())//
					.build()//
					.writeDelimitedTo(parent.connection.getOut());
		} catch (IOException e) {
			parent.closeAndKillNotification();
			e.printStackTrace();
		}
	}
}
