package de.uvwxy.daisy.protocol;

import java.io.IOException;
import java.util.List;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.common.base.Preconditions;

import de.uvwxy.daisy.common.R;
import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.daisy.proto.Messages.DeploymentReply;
import de.uvwxy.daisy.proto.Messages.Fin;
import de.uvwxy.daisy.proto.Messages.FinAck;
import de.uvwxy.daisy.proto.Messages.NameReply;
import de.uvwxy.daisy.proto.Messages.NameTag;
import de.uvwxy.daisy.proto.Messages.Peer;
import de.uvwxy.daisy.proto.Messages.PeerType;
import de.uvwxy.net.AConnection;
import de.uvwxy.proto.parser.IProtoMessageReceiver;

public class DaisyProtocol implements IProtoMessageReceiver<DaisyProtocolMessage> {
	AConnection connection;

	private DaisyProtocolProcessData dppData;
	private DaisyProtocolProcessMedia dppMedia;
	private DaisyProtocolProcessBalloon dppBalloon;

	private Context ctx;

	private DaisyData data;

	boolean isServer = false;
	boolean needsResync = false;

	private int notification_in = 111;
	private int notification_out = 222;
	NameTag who;

	public DaisyProtocol(AConnection connection, DaisyData data, Context ctx, NameTag who, boolean isServer) {
		Log.d("PROTOCOL", "DaisyProtocol");
		Preconditions.checkNotNull(connection);
		Preconditions.checkNotNull(data);
		Preconditions.checkNotNull(ctx);
		this.who = who;
		this.data = data;
		this.connection = connection;
		this.ctx = ctx;
		this.isServer = isServer;

		this.dppData = new DaisyProtocolProcessData(ctx, data, this);
		this.dppMedia = new DaisyProtocolProcessMedia(ctx, data, this);
		this.dppBalloon = new DaisyProtocolProcessBalloon(ctx, data, this);

		if (!isServer) {
			showOutNotification(ctx);
		} else {
			showInNotification(ctx);
		}
	}

	<E> void busPostListObjects(List<E> list) {
		for (final E msg : list) {
			if (msg == null) {
				continue;
			}
			Handler h = new Handler(ctx.getMainLooper());
			h.post(new Runnable() {
				@Override
				public void run() {
					data.bus.post(msg);
				}
			});
		}
	}

	void closeAndKillNotification() {
		Log.i("PROTOCOL", "CLOSING SOCKET DUE TO SEND ERROR OR FINACK");
		connection.close();
		hideNotification(ctx);
	}

	private Peer createPeer(AConnection connection, NameTag nameTag) {
		Peer.Builder peerBuilder = Peer.newBuilder();
		peerBuilder.setAddress(connection.getRemoteAddress());
		peerBuilder.setLastSeenTimestamp(System.currentTimeMillis());
		peerBuilder.setPeerNameTag(nameTag);
		peerBuilder.setPeerType(PeerType.valueOf(connection.getType().getValue()));
		// keep track who discovered this peer, but wait with setting a new
		// number (-1)
		peerBuilder.setTag(data.getTag().toBuilder().setSequenceNumber(-1).build());
		return peerBuilder.build();
	}

	private void hideInNotification(Context ctx) {
		Log.d("PROTOCOL", "hideInNotification");
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(notification_in);
	}

	private void hideNotification(Context ctx) {
		Log.d("PROTOCOL", "hideNotification");
		if (!isServer) {
			hideOutNotification(ctx);
		} else {
			hideInNotification(ctx);
		}
	}

	private void hideOutNotification(Context ctx) {
		Log.d("PROTOCOL", "hideOutNotification");
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(notification_out);
	}

	@Override
	public void onReceive(DaisyProtocolMessage mob) {

		if (mob.hasNameRequest()) {
			Log.i("PROTOCOL", "Received hasNameRequest");
			processNameRequest(mob);
		} else if (mob.hasNameReply()) {
			Log.i("PROTOCOL", "Received hasNameReply");
			processNameReply(mob);
		} else if (mob.hasDataHandshake()) {
			Log.i("PROTOCOL", "Received hasDataHandshake");
			dppData.processDataHandshake(mob);
		} else if (mob.hasDataRequest()) {
			Log.i("PROTOCOL", "Received hasDataRequest");
			dppData.processDataRequest(mob);
		} else if (mob.hasDataReply()) {
			Log.i("PROTOCOL", "Received hasDataReply");
			dppData.processDataReply(mob);
		} else if (mob.hasDeploymentRequest()) {
			Log.i("PROTOCOL", "Received hasDeploymentRequest");
			processDeploymentRequest(mob);
		} else if (mob.hasDeploymentReply()) {
			Log.i("PROTOCOL", "Received hasDeploymentReply");
			// set CM.DEPLOYMENT stuffs
			processDeploymentReply(mob);
		} else if (mob.hasMediaHandshake()) {
			Log.i("PROTOCOL", "Received hasMediaHandshake");
			dppMedia.processMediaHandshake(mob);
		} else if (mob.hasMediaRequest()) {
			Log.i("PROTOCOL", "Received hasMediaRequest");
			dppMedia.processMediaRequest(mob);
		} else if (mob.hasMediaReply()) {
			Log.i("PROTOCOL", "Received hasMediaReply");
			dppMedia.processMediaReply(mob);
		} else if (mob.hasFin()) {
			Log.i("PROTOCOL", "Received hasMediaFin");
			try {
				DaisyProtocolMessage.newBuilder().setFinAck(FinAck.newBuilder().build()).build().writeDelimitedTo(connection.getOut());
			} catch (IOException e) {
				closeAndKillNotification();
				e.printStackTrace();
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			closeAndKillNotification();
		} else if (mob.hasFinAck()) {
			Log.i("PROTOCOL", "Received hasFinAck");
			closeAndKillNotification();
		} else if (mob.hasBalloonHandshake()) {
			Log.i("PROTOCOL", "Received hasBalloonHandshake");
			dppBalloon.processBalloonHandshake(mob);
		} else if (mob.hasBalloonImage()) {
			Log.i("PROTOCOL", "Received hasBalloonImage");
			dppBalloon.processBalloonImage(mob);
		} else if (mob.hasBalloonImageRequest()) {
			Log.i("PROTOCOL", "Received hasBalloonImageRequest");
			dppBalloon.processBalloonImageRequest(mob);
		} else if (mob.hasBalloonPreview()) {
			Log.i("PROTOCOL", "Received hasBalloonPreview");
			dppBalloon.processBalloonPreview(mob);
		} else if (mob.hasBalloonPreviewRequest()) {
			Log.i("PROTOCOL", "Received hasBalloonPreviewRequest");
			dppBalloon.processBalloonPreviewRequest(mob);
		} else if (mob.hasBalloonReply()) {
			Log.i("PROTOCOL", "Received hasBalloonReply");
			dppBalloon.processBalloonReply(mob);
		} else if (mob.hasBalloonSettings()){
			Log.i("PROTOCOL", "Received hasBalloonSettings");
			dppBalloon.processBalloonSettings(mob);
		}
	}

	private void processDeploymentReply(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processDeploymentRequest");

		final DeploymentReply deplReply = mob.getDeploymentReply();
		data.loadDeployment(deplReply.getHeader().toBuilder(), deplReply.getData().toBuilder(), null);

		Handler h = new Handler(ctx.getMainLooper());
		h.post(new Runnable() {
			@Override
			public void run() {
				data.bus.post(createPeer(connection, deplReply.getNameOfHost()));
				//data.bus.post(createPeer(connection, deplReply.getNameOfHost()));
			}
		});
		sendFin();
	}

	private void processDeploymentRequest(DaisyProtocolMessage mob) {

		// TODO !!!
		if (data.deplOK()) {
			try {
				Log.d("PROTOCOL", "processDeploymentRequest");
				DaisyProtocolRoutinesData.getDeploymentReply(data).writeDelimitedTo(connection.getOut());
			} catch (IOException e) {
				closeAndKillNotification();
				e.printStackTrace();
			}
		}
	}

	public void processNameReply(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processNameReply");
		NameReply nameReply = mob.getNameReply();

		if (data.deplOK()) {
			final Peer p = createPeer(connection, nameReply.getHostTag());

			// add participating peer to peers list
			Handler h = new Handler(ctx.getMainLooper());
			h.post(new Runnable() {
				@Override
				public void run() {
					data.bus.post(p);
				}
			});

			closeAndKillNotification();

		} else {
			// request deployment
			try {
				Log.d("PROTOCOL", "send requestDeployment");
				DaisyProtocolRoutinesData.requestDeployment(data).writeDelimitedTo(connection.getOut());
			} catch (IOException e) {
				closeAndKillNotification();
				e.printStackTrace();
			}
		}

	}

	private void processNameRequest(DaisyProtocolMessage mob) {
		Log.d("PROTOCOL", "processNameRequest");
		this.who = mob.getNameRequest().getClientTag();

		if (data.deplOK()) {
			final Peer p = createPeer(connection, mob.getNameRequest().getClientTag());

			// add participating peer to peers list
			Handler h = new Handler(ctx.getMainLooper());
			h.post(new Runnable() {
				@Override
				public void run() {
					data.bus.post(p);
				}
			});

			// send back name
			try {
				Log.d("PROTOCOL", "send getNameReply");
				DaisyProtocolRoutinesData.getNameReply(data).writeDelimitedTo(connection.getOut());
			} catch (IOException e) {
				closeAndKillNotification();
				e.printStackTrace();
			}
		}
	}

	void sendFin() {
		Log.d("PROTOCOL", "sendFin");
		try {
			DaisyProtocolMessage.newBuilder().setFin(Fin.newBuilder().build()).build().writeDelimitedTo(connection.getOut());
		} catch (IOException e) {
			closeAndKillNotification();
			e.printStackTrace();
		}
	}

	private void showInNotification(Context ctx) {
		Log.d("PROTOCOL", "showInNotification");
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ctx);
		mBuilder.setSmallIcon(R.drawable.ic_stat_sync_in);
		String notification = "Daisy: IN [" + connection.getType() + "]";
		mBuilder.setContentTitle(notification);
		String msg = "Answering to sync from " + connection.getRemoteAddress();
		if (who != null) {
			msg = "[" + (who.hasName() ? who.getName() : "N/A") + "]";
		}

		mBuilder.setContentText(msg);
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(notification_in, mBuilder.build());
	}

	private void showOutNotification(Context ctx) {
		Log.d("PROTOCOL", "showOutNotification");
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ctx);
		mBuilder.setSmallIcon(R.drawable.ic_stat_sync_out);
		String notification = "Daisy: OUT [" + connection.getType() + "]";
		mBuilder.setContentTitle(notification);
		String msg = "Requesting sync from " + connection.getRemoteAddress();
		if (who != null) {
			msg = "[" + (who.hasName() ? who.getName() : "N/A") + "]";
		}

		mBuilder.setContentText(msg);
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(notification_out, mBuilder.build());
	}

}
