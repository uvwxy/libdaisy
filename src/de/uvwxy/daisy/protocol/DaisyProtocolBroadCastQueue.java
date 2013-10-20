package de.uvwxy.daisy.protocol;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.common.base.Preconditions;

import de.uvwxy.daisy.proto.Messages;
import de.uvwxy.daisy.proto.Messages.Peer;
import de.uvwxy.daisy.proto.ProtoObject;

public class DaisyProtocolBroadCastQueue {

	private DaisyData data;
	private DaisyNetwork daisyNet;

	private Object syncLock = new Object();
	private boolean retrigger = false;
	private boolean syncIsRunning = false;
	private Context ctx;

	public DaisyProtocolBroadCastQueue(DaisyData data, DaisyNetwork daisyNet, Context ctx) {
		Preconditions.checkNotNull(data);
		Preconditions.checkNotNull(daisyNet);
		Preconditions.checkNotNull(ctx);

		this.data = data;
		this.daisyNet = daisyNet;
		this.ctx = ctx;
	}

	private Object externalConnectionLock = new Object();
	private boolean isExternallyLock = false;

	public void setExternallyLock(boolean isExternallyLock) {
		this.isExternallyLock = isExternallyLock;
	}

	class SyncAllPeersTask extends AsyncTask<String, Integer, Void> {

		@Override
		protected Void doInBackground(String... params) {
			DaisySyncLock.getInstance().waitAndLock();
			if (params != null && params.length > 0) {
				syncWithAllPeersDataAndMedia(params[0]);
				//				syncWithAllPeersMedia(params[0]);
			} else {
				syncWithAllPeersDataAndMedia(null);
				//				syncWithAllPeersMedia(null);
			}
			DaisySyncLock.getInstance().release();
			synchronized (syncLock) {
				if (retrigger) {
					retrigger = false;
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					new SyncAllPeersTask().execute();
				} else {
					syncIsRunning = false;
					retrigger = false;
				}
			}
			return null;
		}

	}

	private void syncWithAllPeersDataAndMedia(String ommitUUID) {
		ArrayList<Peer> uniquePeers = getUniquePeerList(data, data.getPeersList());
		Log.i("SYNC", "Will sync with " + uniquePeers.size() + "connections");

		for (Peer p : uniquePeers) {
			if (p == null) {
				continue;
			}
			if (p.getTag().getUuid().equals(ommitUUID)) {
				Log.i("QUEUE", "Ommitting " + ommitUUID);
				continue;
			}
			syncWithPeerData(p);
		}
	}

	private void syncWithAllPeersMedia(String ommitUUID) {
		ArrayList<Peer> uniquePeers = getUniquePeerList(data, data.getPeersList());
		Log.i("SYNC", "Will sync with " + uniquePeers.size() + "connections");

		for (Peer p : uniquePeers) {
			if (p == null) {
				continue;
			}
			if (p.getTag().getUuid().equals(ommitUUID)) {
				Log.i("QUEUE", "Ommitting " + ommitUUID);
				continue;
			}
			syncWithPeerMedia(p);
		}
	}

	public static ArrayList<Peer> getUniquePeerList(DaisyData data, List<Peer> input) {
		ArrayList<Peer> uniquePeers = new ArrayList<Messages.Peer>();

		for (Peer p : input) {
			if (p == null) {
				continue;
			}

			// only add if not present and not us
			if (!ProtoObject.containsPeerWithAddress(uniquePeers, p) && !p.getPeerNameTag().getUuid().equals(data.getTag().getUuid())) {
				uniquePeers.add(p);
			}
		}

		return uniquePeers;
	}

	private void syncWithPeerData(Peer p) {
		Preconditions.checkNotNull(p);

		Object commLock = new Object();

		data.log2bus("SYNC", "Starting Sync with " + p.getPeerNameTag().getUuid());

		DaisyNetwork.connect(ctx, data, daisyNet, new DaisyProtocolStartSyncRequest(data, ctx, p.getPeerNameTag()), p, commLock);

		try {
			Log.i("QUEUE", "Waiting on commLock..");
			try {
				synchronized (commLock) {
					commLock.wait();
				}
			} catch (IllegalMonitorStateException e) {
				e.printStackTrace();
			}
			Log.i("QUEUE", "CommLock has been released..");
		} catch (InterruptedException e) {
			Log.i("QUEUE", "Comm Lock has been broken..");
			e.printStackTrace();
		}
	}

	private void syncWithPeerMedia(Peer p) {
		Preconditions.checkNotNull(p);

		Object commLock = new Object();

		data.log2bus("SYNC", "Starting Sync with " + p.getPeerNameTag().getUuid());

		DaisyNetwork.connect(ctx, data, daisyNet, new DaisyProtocolStartMediaRequest(data, ctx, p.getPeerNameTag()), p, commLock);
		try {
			Log.i("QUEUE", "Waiting on commLock..");
			try {
				synchronized (commLock) {
					commLock.wait();
				}
			} catch (IllegalMonitorStateException e) {
				e.printStackTrace();
			}
			Log.i("QUEUE", "CommLock has been released..");
		} catch (InterruptedException e) {
			Log.i("QUEUE", "Comm Lock has been broken..");
			e.printStackTrace();
		}
	}

	/**
	 * This acts as a call to trigger sync'ing with all known devices. If a sync
	 * is already posted it will return. If no sync is posted it will trigger a
	 * new sync if the wait time out has passed, or register a new sync. If a
	 * sync has been registered, no new sync will be registered.
	 */
	public void sync(String ommitID) {
		// determine last sync
		synchronized (syncLock) {
			if (retrigger) {
				data.log2bus("SYNC", "Sync has been retriggered in the past, skipping request");
				return;
			}

			if (syncIsRunning) {
				data.log2bus("SYNC", "Sync is in progress, setting to retrigger");
				retrigger = true;
				return;
			}

			if (!syncIsRunning) {
				data.log2bus("SYNC", "Sync has been posted");
				syncIsRunning = true;
				new SyncAllPeersTask().execute();
			}
		}

	}
}
