package de.uvwxy.daisy.protocol;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.protobuf.ByteString;

import de.uvwxy.daisy.common.R;
import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.daisy.proto.Messages.ExistingFileDescriptor;
import de.uvwxy.daisy.proto.Messages.FileChunk;
import de.uvwxy.daisy.proto.Messages.MediaHandshake;
import de.uvwxy.daisy.proto.Messages.MediaReply;
import de.uvwxy.helper.FileTools;
import de.uvwxy.net.AConnection;

/**
 * A class to manage the media protocol file actions. Limited to ~2GB file size
 * (max(int32)).
 * 
 * @author paul
 * 
 */
public class DaisyProtocolRoutinesMedia {
	public static final int NOTIFICATION_ID = 3333;
	public static final long CHUNK_MAX_SIZE = 8 * 1024; // 8kb
	public static final String[] DAISY_FOLDERS = { DaisyData.MAP_ARCHIVE_FOLDER, DaisyData.IMAGES_FOLDER };

	/**
	 * Takes an existing file descriptor as input and checks if we need anything
	 * from this file. It computes a request for the remaining bytes of this
	 * file. If it exists and is complete null is returned
	 * 
	 * @param efd
	 *            the existing file descriptor
	 * @return null or a request for remaining bytes
	 */
	public static FileChunk checkIfExistsOrNeedsMoreData(ExistingFileDescriptor efd, DaisyData data) {

		File f = getFileFromFC(efd, data);

		if (f == null) {
			return null;
		}

		if (f.length() >= efd.getLength()) {
			Log.i("MEDIAPROTO", "File" + f.getAbsolutePath() + " complete, or we have more");

			// file complete or we have more -> don't need anything from this
			// peer, return null
			return null;
		}
		FileChunk.Builder efdb = FileChunk.newBuilder();
		efdb.setFolder(efd.getFolder());
		efdb.setName(efd.getName());

		if (!f.exists()) {
			Log.i("MEDIAPROTO", "File" + f.getAbsolutePath() + " need everything");
			efdb.setByteOffset(0);
			efdb.setNumBytes(efd.getLength());
		} else {
			int localFileSize = (int) f.length();
			if (localFileSize < efd.getLength()) {
				int fileMissingNumberOfBytes = efd.getLength() - localFileSize;
				efdb.setByteOffset(localFileSize);
				efdb.setNumBytes(fileMissingNumberOfBytes);
				Log.i("MEDIAPROTO", "File" + f.getAbsolutePath() + " need " + fileMissingNumberOfBytes + " bytes");
				Log.i("MEDIAPROTO", "File" + f.getAbsolutePath() + " starting from " + localFileSize);
			}
		}

		return efdb.build();
	}

	public static void sendFileAsChunks(FileChunk fc, AConnection c, DaisyData data, Context ctx) throws IOException {
		File f = getFileFromFC(fc, data);

		if (f == null) {
			return;
		}

		if (f.length() < fc.getByteOffset()) {
			// no need to reply as other side has more
			Log.i("MEDIAPROTO", f.getAbsolutePath() + " no need to reply as other side has more");

			return;
		}

		// where to start reading file
		int offset = fc.getByteOffset();
		int numberOfChunks = (int) (f.length() - offset);
		numberOfChunks /= CHUNK_MAX_SIZE;
		int currentChunkNumber = 1;

		RandomAccessFile raf;

		try {
			raf = new RandomAccessFile(f, "r");
		} catch (FileNotFoundException e) {
			throw e;
		}

		raf.skipBytes(offset);

		byte[] buffer = new byte[(int) CHUNK_MAX_SIZE];
		int currentOffset = offset;
		while (currentOffset < f.length()) { // if offset = length -> overrun =
												// fin (index starts @ 0)

			int bytesRead;
			try {

				bytesRead = raf.read(buffer, 0, (int) CHUNK_MAX_SIZE);
			} catch (IOException e) {
				raf.close();
				throw e;
			}

			FileChunk.Builder fcReply = FileChunk.newBuilder();
			fcReply.setFolder(fc.getFolder());
			fcReply.setName(fc.getName());
			fcReply.setByteOffset(currentOffset);
			fcReply.setNumBytes(bytesRead);
			fcReply.setData(ByteString.copyFrom(buffer, 0, bytesRead));

			Log.i("MEDIAPROTO", "File" + f.getAbsolutePath());
			Log.i("MEDIAPROTO", "Sending chunk " + currentChunkNumber + "/" + numberOfChunks);
			Log.i("MEDIAPROTO", "Sending with " + bytesRead + " bytes of data (offset = " + currentOffset + ")");
			MediaReply mr = MediaReply.newBuilder().setFileChunk(fcReply.build()).setLastChunk(false).setCurrentChunkNumber(currentChunkNumber)
					.setTotalNumberOfChunks((int) numberOfChunks).build();
			try {
				DaisyProtocolMessage.newBuilder().setMediaReply(mr).build().writeDelimitedTo(c.getOut());
			} catch (IOException e) {
				raf.close();
				throw e;
			}

			updateNotification(c, ctx, false, fc.getName(), currentChunkNumber, numberOfChunks);

			currentOffset += bytesRead;
			currentChunkNumber++;
		}
		hideNotification(ctx);

	}

	public static void saveChunk(FileChunk fc, DaisyData data) throws IOException {
		File f = getFileFromFC(fc, data);

		if (f == null) {
			return;
		}

		if (!f.exists()) {
			Log.i("MEDIAPROTO", "File" + f.getAbsolutePath() + " created");
			f.createNewFile();
		}

		if (f.length() != fc.getByteOffset()) {
			Log.e("MEDIAPROTO", "File" + f.getAbsolutePath() + " wrong offset " + f.length() + " vs " + fc.getByteOffset());
			throw new WrongOffsetException();
		}

		boolean append = true;
		FileOutputStream fos = new FileOutputStream(f, append);

		fos.write(fc.getData().toByteArray(), 0, (int) fc.getNumBytes());
		fos.close();
		Log.i("MEDIAPROTO", "File" + f.getAbsolutePath() + " written " + fc.getNumBytes() + "bytes");
	}

	public static File getFileFromFC(FileChunk fc, DaisyData data) {
		String dir = null;
		if (fc.getFolder().equals(DaisyData.MAP_ARCHIVE_FOLDER)) {
			dir = FileTools.getAndCreateExternalFolder(fc.getFolder());
		} else {
			dir = FileTools.getAndCreateExternalFolder(fc.getFolder() + data.getIdAndTimeStamp() + "/");
		}
		return new File(dir + fc.getName());

	}

	public static File getFileFromFC(ExistingFileDescriptor efd, DaisyData data) {
		String dir = null;
		if (efd.getFolder().equals(DaisyData.MAP_ARCHIVE_FOLDER)) {
			dir = FileTools.getAndCreateExternalFolder(efd.getFolder());
		} else {
			dir = FileTools.getAndCreateExternalFolder(efd.getFolder() + data.getIdAndTimeStamp() + "/");
		}
		return new File(dir + efd.getName());
	}

	public static DaisyProtocolMessage getMediaHandshake(DaisyData data) {
		DaisyProtocolMessage.Builder b = DaisyProtocolMessage.newBuilder();
		MediaHandshake m = MediaHandshake.newBuilder().addAllAvailableFiles(getExistingFileDescriptors(data)).setClientTag(data.getTag()).build();
		b.setMediaHandshake(m);
		return b.build();

	}

	public static ArrayList<ExistingFileDescriptor> getExistingFileDescriptors(DaisyData data) {
		ArrayList<ExistingFileDescriptor> a = new ArrayList<ExistingFileDescriptor>();

		for (String dir : DAISY_FOLDERS) {
			String absoluteFolder = null;
			if (dir.equals(DaisyData.MAP_ARCHIVE_FOLDER)) {
				absoluteFolder = FileTools.getAndCreateExternalFolder(dir);
			} else {
				absoluteFolder = FileTools.getAndCreateExternalFolder(dir + data.getIdAndTimeStamp() + "/");
			}

			Log.i("MEDIAPROTO", "Looking into " + absoluteFolder);

			File flist = new File(absoluteFolder);

			String[] list = flist.list();
			if (list == null || list.length < 1) {
				continue;
			}

			for (String filename : list) {
				if (filename == null) {
					continue;
				}
				File f = new File(absoluteFolder + filename);

				if (f.isDirectory()) {
					continue;
				}

				ExistingFileDescriptor.Builder b = ExistingFileDescriptor.newBuilder();
				b.setFolder(dir);
				b.setName(filename);
				b.setLength((int) f.length());
				a.add(b.build());
			}
		}

		return a;
	}

	private static NotificationCompat.Builder mBuilder = null;

	static void updateNotification(AConnection c, Context ctx, boolean incomming, String name, int current, int max) {
		if (mBuilder == null) {
			mBuilder = new NotificationCompat.Builder(ctx);
		}

		mBuilder.setSmallIcon(incomming ? R.drawable.ic_stat_sync_in : R.drawable.ic_stat_sync_out);

		String notification;
		if (current < max) {
			mBuilder.setProgress(max, current, false);
			notification = "" + (incomming ? "IN" : "OUT") + " [" + name + "]: " + current + "/" + max;
		} else {
			mBuilder.setProgress(0, 0, false);
			notification = "" + (incomming ? "IN" : "OUT") + " [" + name + "]: Complete";
		}
		mBuilder.setContentTitle(notification);

		String msg;
		if (incomming) {
			msg = "Receiving from " + c.getRemoteAddress() + " [" + c.getType() + "]";
		} else {
			msg = "Sending to " + c.getRemoteAddress() + " [" + c.getType() + "]";
		}

		mBuilder.setContentText(msg);

		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, mBuilder.build());
	}

	static void hideNotification(Context ctx) {
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(NOTIFICATION_ID);
	}

}
