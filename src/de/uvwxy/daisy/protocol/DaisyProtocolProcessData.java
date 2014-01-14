package de.uvwxy.daisy.protocol;

import java.io.IOException;

import android.content.Context;
import android.util.Log;

import com.google.common.base.Preconditions;

import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.daisy.proto.Messages.DataReply;

public class DaisyProtocolProcessData {

	Context ctx;
	DaisyData data;
	DaisyProtocol parent;

	public DaisyProtocolProcessData(Context ctx, DaisyData data, DaisyProtocol parent) {
		Preconditions.checkNotNull(ctx);
		Preconditions.checkNotNull(data);
		Preconditions.checkNotNull(parent);

		this.ctx = ctx;
		this.data = data;
		this.parent = parent;
	}

	void processDataHandshake(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processDataHandshake");
		try {
			Log.d("PROTOCOL", "send getDataRequest");
			DaisyProtocolRoutinesData.getDataRequest(mob, data).writeDelimitedTo(parent.connection.getOut());
		} catch (IOException e) {
			parent.closeAndKillNotification();
			e.printStackTrace();
		}
	}

	void processDataReply(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processDataReply");
		DataReply reply = mob.getDataReply();

		parent.busPostListObjects(reply.getChatMessageList());
		parent.busPostListObjects(reply.getNodeLocationDataList());
		parent.busPostListObjects(reply.getNodeCommunicationDataList());
		parent.busPostListObjects(reply.getParticipatingPeersList());
		parent.busPostListObjects(reply.getMapAnnotationsList());
		parent.busPostListObjects(reply.getMapImagesList());
		/**
		 * SYNC ADD HERE.
		 */

		if (parent.isServer) {
			// send data Handshake to give other side possibility to sync
			try {
				Log.d("PROTOCOL", "send getDataHandshake");
				DaisyProtocolRoutinesData.getDataHandshake(data).writeDelimitedTo(parent.connection.getOut());
			} catch (IOException e) {
				parent.closeAndKillNotification();
				e.printStackTrace();
			}
		} else {
			try {
				Log.d("PROTOCOL", "send getMediaHandshake");
				DaisyProtocolRoutinesMedia.getMediaHandshake(data).writeDelimitedTo(parent.connection.getOut());
			} catch (IOException e) {
				parent.closeAndKillNotification();
				e.printStackTrace();
			}
		}

	}

	void processDataRequest(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processDataRequest");
		try {
			Log.d("PROTOCOL", "send getDataReply");
			DaisyProtocolRoutinesData.getDataReply(mob, data).writeDelimitedTo(parent.connection.getOut());

		} catch (IOException e) {
			parent.closeAndKillNotification();
			e.printStackTrace();
		}
	}

}
