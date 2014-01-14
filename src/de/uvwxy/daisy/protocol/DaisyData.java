package de.uvwxy.daisy.protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.google.protobuf.MessageOrBuilder;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import de.uvwxy.daisy.proto.Messages;
import de.uvwxy.daisy.proto.ProtoHelper;
import de.uvwxy.daisy.proto.Messages.Annotation;
import de.uvwxy.daisy.proto.Messages.CamSize;
import de.uvwxy.daisy.proto.Messages.ChatMessage;
import de.uvwxy.daisy.proto.Messages.DataHandshake;
import de.uvwxy.daisy.proto.Messages.DataReply;
import de.uvwxy.daisy.proto.Messages.DataRequest;
import de.uvwxy.daisy.proto.Messages.DeploymentData;
import de.uvwxy.daisy.proto.Messages.DeploymentHeader;
import de.uvwxy.daisy.proto.Messages.DeploymentLogs;
import de.uvwxy.daisy.proto.Messages.DeploymentReply.Builder;
import de.uvwxy.daisy.proto.Messages.Image;
import de.uvwxy.daisy.proto.Messages.LogMessage;
import de.uvwxy.daisy.proto.Messages.MapViewConfig;
import de.uvwxy.daisy.proto.Messages.NameTag;
import de.uvwxy.daisy.proto.Messages.NodeCommunicationData;
import de.uvwxy.daisy.proto.Messages.NodeLocationData;
import de.uvwxy.daisy.proto.Messages.Peer;
import de.uvwxy.daisy.proto.Messages.UuidWithIndeces;
import de.uvwxy.daisy.proto.Messages.UuidWithLargestIndex;
import de.uvwxy.daisy.proto.ProtoObject;
import de.uvwxy.daisy.protocol.busmessages.DeploymentCreated;
import de.uvwxy.daisy.protocol.busmessages.DeploymentLoaded;
import de.uvwxy.daisy.protocol.busmessages.DiscoveredPeer;
import de.uvwxy.daisy.protocol.busmessages.NeedsSync;
import de.uvwxy.helper.ContextProxy;
import de.uvwxy.helper.FileNameFilterFromStringArray;
import de.uvwxy.helper.FileTools;
import de.uvwxy.helper.IntentTools;
import de.uvwxy.helper.IntentTools.ReturnStringECallback;
import de.uvwxy.helper.StringE;
import de.uvwxy.phone.PhoneID;

public class DaisyData {
	private interface DataVisitor {
		public void visitMessageOrBuilder(MessageOrBuilder m);
	}

	public static final String DAISY_BALLOON_IMAGES_FOLDER = "daisy_balloonImages/";
	public static final String DAISY_BALLOON_PREVIEW_FOLDER = "daisy_balloonPreviews/";
	public static final String IMAGES_FOLDER = "daisy_images/";
	public static final String DAISY_RECORDINGS_FOLDER = "daisy_recordings/";
	public static final String MAIN_DEPLOYMENT_FOLDER = "daisy_deployments/";
	public static final String MAP_ARCHIVE_FOLDER = "daisy_maps/";

	public static final String[] VALID_DEPLOYMENT_FILES = new String[] { "dpl" };
	public Bus bus;
	private ContextProxy ctx;

	private ArrayList<Peer> discoveredPeers = new ArrayList<Messages.Peer>();
	private String localUserName;
	private boolean isBalloon = false;

	private Messages.DeploymentData.Builder mTempDeploymentData;
	private Messages.DeploymentHeader.Builder mTempDeploymentHeader;
	private Messages.DeploymentLogs.Builder mTempDeploymentLogs;

	private int seed;
	private boolean useMemberDiscoveredPeerList = true;
	private NameTag.Builder userTag = NameTag.newBuilder();

	private ArrayList<CamSize> cameraResolutions = new ArrayList<Messages.CamSize>();

	public void setCameraResolutions(ArrayList<CamSize> cameraResolutions) {
		this.cameraResolutions = cameraResolutions;
	}

	public ArrayList<CamSize> getCameraResolutions() {
		return cameraResolutions;
	}

	private CamSize requestedResolution;

	public void setRequestedResolution(CamSize requestedResolution) {
		this.requestedResolution = requestedResolution;
	}

	public CamSize getRequestedResolution() {
		return requestedResolution;
	}

	public DaisyData(ContextProxy ctx, int seed, Bus bus) {
		Preconditions.checkNotNull(ctx);
		Preconditions.checkNotNull(bus);

		this.seed = seed;
		this.ctx = ctx;

		userTag.setSequenceNumber(0); // no data created yet.
		userTag.setUuid(PhoneID.getId(ctx.ctx(), seed));
		mTempDeploymentLogs = Messages.DeploymentLogs.newBuilder();
	}

	/**
	 * Add peer to discoveredPeers if it is not in this list yet.
	 * 
	 * @param peer
	 *            the peer to be added to the list
	 * @return true if this was a new peer, fals if it is already in the list
	 */
	public synchronized boolean addDiscoveredPeer(final Peer peer) {

		// we have to update peer.tag with a proper tag if we add it to the
		// deployment.
		// we do not update the tag if we store it the in the member list.f

		if (useMemberDiscoveredPeerList) {
			if (!ProtoObject.containsPeerWithAddress(discoveredPeers, peer)) {

				discoveredPeers.add(peer);
				log2bus("SeenDevices", "Found new device: " + peer.toString());
				log2bus("SeenDevices", "Detected peers: " + discoveredPeers.size());
				log2bus("SeenDevices", "Added to local list (member)");
				Handler h = new Handler(ctx.ctx().getMainLooper());
				h.post(new Runnable() {
					@Override
					public void run() {
						bus.post(new DiscoveredPeer(peer));
					}
				});
			} else {
				log2bus("SeenDevices", "Found existing device: " + peer.toString());
				return false;
			}
		} else {
			if (!ProtoObject.containsPeerWithAddress(mTempDeploymentData.getPeersList(), peer)) {

				// update with a correct name tag.
				// only request the next tag when we are going to add it.
				// containsPeer, does not check for equality on the tag field.
				final Peer peerInserted = peer.toBuilder().setTag(getNextTag()).build();
				mTempDeploymentLogs.addDiscoveredPeers(peerInserted);
				log2bus("SeenDevices", "Found new device: " + peerInserted.toString());
				log2bus("SeenDevices", "Detected peers: " + mTempDeploymentLogs.getDiscoveredPeersCount());
				log2bus("SeenDevices", "Added to local list (member)");
				Handler h = new Handler(ctx.ctx().getMainLooper());
				h.post(new Runnable() {
					@Override
					public void run() {
						bus.post(new DiscoveredPeer(peerInserted));
					}
				});
			} else {
				log2bus("SeenDevices", "Found existing device: " + peer.toString());
				return false;
			}
		}

		return true;

	}

	private synchronized void addOrUpdateUuuidWithMaxIndex(HashMap<String, Integer> mapUuidToMaxIndex,
			ArrayList<String> uuids, NameTag tag) {
		String uuid = tag.getUuid();
		if (!uuids.contains(uuid)) {
			uuids.add(uuid);
			mapUuidToMaxIndex.put(uuid, tag.getSequenceNumber());
		} else {
			Integer storedNumber = mapUuidToMaxIndex.get(uuid);
			// a negative value if the value of this integer is less than the
			// value of object;
			if (storedNumber.compareTo(tag.getSequenceNumber()) < 0) {
				mapUuidToMaxIndex.remove(uuid);
				mapUuidToMaxIndex.put(uuid, tag.getSequenceNumber());
			}
		}
	}

	/**
	 * Only use this function to add peers that we have discovered/have been
	 * connected to.
	 * 
	 * @param peer
	 * @return
	 */
	private synchronized boolean addParticipatingPeer(Peer peer) {
		if (!ProtoObject.containsPeerWithAddress(mTempDeploymentData.getPeersList(), peer)) {
			// now correctly set the peer ID for this item.
			peer = peer.toBuilder().setTag(getNextTag()).build();

			mTempDeploymentData.addPeers(peer);
			log2bus("SeenDevices", "Added new device: " + peer.toString());
			log2bus("SeenDevices", "Participating peers: " + mTempDeploymentData.getPeersCount());

			// remove list from discovered list
			if (useMemberDiscoveredPeerList) {
				ProtoObject.remove(discoveredPeers, peer);
			} else {
				for (int i = 0; i < mTempDeploymentLogs.getDiscoveredPeersCount(); i++) {
					if (mTempDeploymentLogs.getDiscoveredPeers(i).equals(peer)) {
						mTempDeploymentLogs.removeDiscoveredPeers(i);
						break;
					}
				}
			}

			return true;
		} else {
			log2bus("SeenDevices", "Not adding existing device: " + peer.toString());
			return false;
		}
	}

	private synchronized void addToReply(DataReply.Builder reply, MessageOrBuilder m) {
		if (m instanceof ChatMessage) {
			reply.addChatMessage((ChatMessage) m);
		}
		if (m instanceof NodeLocationData) {
			reply.addNodeLocationData((NodeLocationData) m);
		}
		if (m instanceof NodeCommunicationData) {
			reply.addNodeCommunicationData((NodeCommunicationData) m);
		}
		if (m instanceof Peer) {
			reply.addParticipatingPeers((Peer) m);
		}
		if (m instanceof Annotation) {
			reply.addMapAnnotations((Annotation) m);
		}
		if (m instanceof Image) {
			reply.addMapImages((Image) m);
		}
		/**
		 * SYNC ADD HERE.
		 */
	}

	private synchronized DataHandshake.Builder addUuidMapToHandshake(HashMap<String, Integer> mapUuidNumber) {
		DataHandshake.Builder b = DataHandshake.newBuilder();

		for (Entry<String, Integer> e : mapUuidNumber.entrySet()) {
			UuidWithLargestIndex.Builder bb = UuidWithLargestIndex.newBuilder();
			bb.setLargestIndex(e.getValue().intValue());
			bb.setUuid(e.getKey());
			b.addDataLargestIndex(bb.build());
		}
		return b;
	}

	@Subscribe
	public void busReceive(ChatMessage m) {
		if (deplOK()) {
			getDeploymentData().addChatMessageData(m);
		}
	}

	@Subscribe
	public void busReceive(LogMessage m) {
		if (deplOK()) {
			getDeploymentLogs().addLogMessageData(m);
		}
	}

	@Subscribe
	public void busReceive(android.location.Location m) {
		if (deplOK()) {
			getDeploymentLogs().addUserLocation(ProtoHelper.androidLocationToProtoLocation(m));
		}
	}

	@Subscribe
	public void busReceive(NodeCommunicationData ncd) {
		if (deplOK()) {
			getDeploymentData().addNodeCommunicationData(ncd);
		}
	}

	@Subscribe
	public void busReceive(Annotation a) {
		if (deplOK()) {
			getDeploymentData().addMapAnnotationData(a);
		}
	}

	@Subscribe
	public void busReceive(Image i) {
		if (deplOK()) {
			getDeploymentData().addMapImages(i);
		}
	}

	/**
	 * SYNC ADD HERE.
	 */

	@Subscribe
	public void busReceive(NodeLocationData nodeData) {
		if (mTempDeploymentData == null || nodeData == null) {
			return;
		}
		getDeploymentData().addNodeLocationData(nodeData);
	}

	@Subscribe
	public void busReceive(Peer m) {
		addParticipatingPeer(m);
	}

	public synchronized void createDeployment(String name, long timestamp) {
		Messages.DeploymentHeader.Builder deploymentHeaderBuilder = Messages.DeploymentHeader.newBuilder();
		deploymentHeaderBuilder.setIdAndTimeStamp(timestamp);

		userTag.setName(getLocalUserName());
		userTag.setSequenceNumber(0); // no data created yet.
		userTag.setUuid(PhoneID.getId(ctx.ctx(), seed));

		deploymentHeaderBuilder.setDeploymentName(name);

		mTempDeploymentHeader = deploymentHeaderBuilder;
		mTempDeploymentData = Messages.DeploymentData.newBuilder();

		mTempDeploymentLogs.setLastNumber(0);

		Log.i("OTTOBUS", "Posting depl cr");
		bus.post(new DeploymentCreated());
	}

	public synchronized boolean deplOK() {
		return mTempDeploymentData != null && mTempDeploymentHeader != null && mTempDeploymentLogs != null;
	}

	public synchronized ArrayList<StringE<String>> getAllDeploymentFileNames() {
		ArrayList<StringE<String>> stringEs = new ArrayList<StringE<String>>();
		for (File f : getAllDeploymentFiles()) {
			Messages.DeploymentHeader header = loadDeploymentHeader(f.getAbsolutePath());

			StringE<String> es = new StringE<String>();
			es.s = header.getDeploymentName();
			es.e = f.getAbsolutePath();
			stringEs.add(es);
		}
		return stringEs;
	}

	private synchronized File[] getAllDeploymentFiles() {
		File f = new File(FileTools.getAndCreateExternalFolder(MAIN_DEPLOYMENT_FOLDER));
		return f.listFiles(new FileNameFilterFromStringArray(VALID_DEPLOYMENT_FILES));
	}

	public synchronized DataHandshake getAllUuidsWithMaxNumber() {
		if (!deplOK()) {
			return DataHandshake.newBuilder().build();
		}

		final HashMap<String, Integer> mapUuidToMaxIndex = new HashMap<String, Integer>();

		final ArrayList<String> uuids = new ArrayList<String>();

		DataVisitor addOrUpdate = new DataVisitor() {
			@Override
			public void visitMessageOrBuilder(MessageOrBuilder m) {
				NameTag tag = getTagFromMessage(m);
				if (tag != null) {
					addOrUpdateUuuidWithMaxIndex(mapUuidToMaxIndex, uuids, tag);
				}
			}
		};

		visitAllData(addOrUpdate);

		return addUuidMapToHandshake(mapUuidToMaxIndex).build();
	}

	public synchronized List<ChatMessage> getChatMessageDataList() {
		if (deplOK()) {
			return getDeploymentData().getChatMessageDataList();
		}
		return null;
	}

	public synchronized DataReply getDataReply(DataRequest dataRequest) {
		List<UuidWithIndeces> list = dataRequest.getUuidWithIndicesList();

		final DataReply.Builder reply = DataReply.newBuilder();
		final HashMap<String, Integer> map = new HashMap<String, Integer>();
		final UuidWithIndeces[] array = new UuidWithIndeces[list.size()];

		for (int i = 0; i < list.size(); i++) {
			UuidWithIndeces uwi = list.get(i);
			// insert into array
			array[i] = uwi;
			// insert into map
			map.put(uwi.getUuid(), i);
		}

		DataVisitor addToReplyIfRequested = new DataVisitor() {

			@Override
			public void visitMessageOrBuilder(MessageOrBuilder m) {
				NameTag tag = getTagFromMessage(m);
				if (tag != null) {
					Integer inDex = map.get(tag.getUuid());
					if (inDex == null) {
						return;
					}
					int index = inDex.intValue();
					if (isInIndices(array[index], tag.getSequenceNumber())) {
						addToReply(reply, m);
					}
				}
			}

		};

		visitAllData(addToReplyIfRequested);

		return reply.build();
	}

	private synchronized DeploymentData.Builder getDeploymentData() {
		return mTempDeploymentData;
	}

	public synchronized String getDeploymentFileName() {
		if (!deplOK()) {
			return "";
		}

		return MAIN_DEPLOYMENT_FOLDER + mTempDeploymentHeader.getIdAndTimeStamp() + ".dpl";
	}

	private synchronized DeploymentHeader.Builder getDeploymentHeader() {
		return mTempDeploymentHeader;
	}

	private synchronized DeploymentLogs.Builder getDeploymentLogs() {
		return mTempDeploymentLogs;
	}

	/**
	 * Never returns null.
	 * 
	 * @return the name of the deployment or \"\"
	 */
	public synchronized String getDeploymentName() {
		if (deplOK()) {
			return mTempDeploymentHeader.getDeploymentName();
		}

		return "error";
	}

	/**
	 * Returns the list of discovered peers, NOT participating peers!
	 * 
	 * @return
	 */
	public synchronized List<Peer> getDiscoveredPeers() {
		if (useMemberDiscoveredPeerList) {
			return discoveredPeers;
		} else {
			return mTempDeploymentLogs.getDiscoveredPeersList();
		}
	}

	/**
	 * Returns the ID and timestamp
	 * 
	 * @return the value. 0 if deployment is not set correctly.
	 */
	public synchronized long getIdAndTimeStamp() {
		if (deplOK()) {
			return getDeploymentHeader().getIdAndTimeStamp();
		}
		return 0;
	}

	public synchronized NodeLocationData getLatestNodeLocationData(int nodeId) {
		ArrayList<NodeLocationData> list = new ArrayList<Messages.NodeLocationData>();
		for (NodeLocationData ncd : getNodeLocationDataList()) {
			if (ncd == null || ncd.getNodeId() != nodeId) {
				continue;
			}
			list.add(ncd);
		}

		timeSortNodeLocationData(list);

		NodeLocationData data = null;
		try {
			data = list.get(list.size() - 1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return data;

	}

	private synchronized void timeSortNodeLocationData(List<NodeLocationData> list) {
		Comparator<NodeLocationData> comparator = new Comparator<NodeLocationData>() {
			@Override
			public int compare(NodeLocationData lhs, NodeLocationData rhs) {
				return Long.valueOf(lhs.getTimestamp()).compareTo(Long.valueOf(rhs.getTimestamp()));
			}
		};
		Collections.sort(list, comparator);
	}

	public synchronized NodeCommunicationData getLatestNodeCommunicationDataWithSensorData(int nodeId) {
		ArrayList<NodeCommunicationData> list = new ArrayList<Messages.NodeCommunicationData>();
		for (NodeCommunicationData ncd : getNodeCommunicationDataList()) {
			if (ncd == null || !ncd.hasSensorData() || ncd.getSensorData().getNodeId() != nodeId) {
				continue;
			}
			list.add(ncd);
		}

		timeSortNodeCommunicationData(list);

		NodeCommunicationData data = null;
		try {
			data = list.get(list.size() - 1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return data;
	}

	public boolean isBalloon() {
		return isBalloon;
	}

	private synchronized void timeSortNodeCommunicationData(List<NodeCommunicationData> list) {
		Comparator<NodeCommunicationData> comparator = new Comparator<NodeCommunicationData>() {
			@Override
			public int compare(NodeCommunicationData lhs, NodeCommunicationData rhs) {
				return Long.valueOf(lhs.getTimestamp()).compareTo(Long.valueOf(rhs.getTimestamp()));
			}
		};
		Collections.sort(list, comparator);
	}

	public synchronized String getLocalUserName() {
		return localUserName;
	}

	public synchronized NameTag getLocalUserTag() {
		NameTag.Builder u = NameTag.newBuilder().setName(getLocalUserName()).setUuid(PhoneID.getId(ctx.ctx(), seed));
		if (useMemberDiscoveredPeerList) {
			// we have no deployment yet, i.e. we have note created any data
			// entries.
			u.setSequenceNumber(0);
		} else {
			u.setSequenceNumber(mTempDeploymentLogs.getLastNumber());
		}
		return u.build();
	}

	private synchronized de.uvwxy.daisy.proto.Messages.DeploymentLogs.Builder getLogData() {
		return mTempDeploymentLogs;
	}

	public synchronized List<LogMessage> getLogMessageDataList() {
		if (deplOK()) {
			return getLogData().getLogMessageDataList();
		}
		return null;
	}

	/**
	 * Returns the map view config if there is one. null if not.
	 * 
	 * @return
	 */
	public synchronized MapViewConfig getMapViewConfig() {
		if (deplOK()) {
			return getDeploymentData().getMapViewConfig();
		}
		return null;
	}

	/**
	 * 
	 * @param dataLargestIndexList
	 *            a list of uuids with the known largest index
	 * @return a list of uuids with missing indices
	 */
	public synchronized List<UuidWithIndeces> getMissingIndeces(List<UuidWithLargestIndex> dataLargestIndexList) {
		if (dataLargestIndexList.size() < 1) {
			return null;
		}

		final boolean[][] boolMask = new boolean[dataLargestIndexList.size()][];
		final HashMap<String, Integer> uuidBoolMaskIndexMap = new HashMap<String, Integer>();

		// initialize hashmap and boolmask.
		for (int x = 0; x < dataLargestIndexList.size(); x++) {
			UuidWithLargestIndex uli = dataLargestIndexList.get(x);
			uuidBoolMaskIndexMap.put(uli.getUuid(), x);

			// data entries are numbered starting by 1
			// Array Positions: [0,1,2,3,...]
			// Index values: [1,2,3,4,...]
			boolMask[x] = new boolean[uli.getLargestIndex()];
			// log.log("BOOLMASK", "boolMask[" + x + "].len = " +
			// uli.getLargestIndex());

		}

		// visit all items and update boolmask
		final DataVisitor updateBoolMask = new DataVisitor() {
			@Override
			public void visitMessageOrBuilder(MessageOrBuilder m) {
				NameTag tag = getTagFromMessage(m);
				if (tag != null) {
					// tag.getNumber() - 1 due to numbers starting by 1
					// log.log("UUID", "" + tag.getUuid() + " (tag number is " +
					// tag.getSequenceNumber() + ")");
					if (uuidBoolMaskIndexMap.get(tag.getUuid()) != null) {
						// log.log("BOOLMASK",
						// "setting boolMask[" +
						// uuidBoolMaskIndexMap.get(tag.getUuid()) + "]["
						// + (tag.getNumber() - 1) + "] = true");
						try {
							boolMask[uuidBoolMaskIndexMap.get(tag.getUuid())][tag.getSequenceNumber() - 1] = true;
						} catch (ArrayIndexOutOfBoundsException e) {
							// log.log("BOOLMASK",
							// "Index out of bounds. We have something the other side has not seen yet");
							// e.printStackTrace();
						}
					}
				}
			}
		};
		visitAllData(updateBoolMask);

		final UuidWithIndeces.Builder[] uuidWithIndecesList = new UuidWithIndeces.Builder[dataLargestIndexList.size()];

		for (int x = 0; x < boolMask.length; x++) {
			for (int y = 0; y < boolMask[x].length; y++) {
				if (!boolMask[x][y]) {
					// item y is missing
					if (uuidWithIndecesList[x] == null) {
						String s = dataLargestIndexList.get(x).getUuid();
						uuidWithIndecesList[x] = UuidWithIndeces.newBuilder().setUuid(s);
					}

					// y + 1 as we need to convert array index to data entry
					// index.
					uuidWithIndecesList[x].addIndices(y + 1);
				}
			}
		}

		ArrayList<UuidWithIndeces> ret = new ArrayList<UuidWithIndeces>();
		for (UuidWithIndeces.Builder u : uuidWithIndecesList) {
			if (u == null) {
				continue;
			}
			ret.add(u.build());
		}

		return ret;
	}

	/**
	 * Increments the getLastNumber by one and returns it.
	 * 
	 * @return
	 */
	private synchronized int getNextSequenceNumber() {
		int i = mTempDeploymentLogs.getLastNumber();
		i++;
		mTempDeploymentLogs.setLastNumber(i);
		// TODO: write last number to "*.depl.idx"
		File f = new File(FileTools.getExternalStorageFolder() + "/" + getDeploymentFileName() + ".seq");
		try {
			if (!f.exists()) {
				f.createNewFile();
			}
			FileWriter fw = new FileWriter(f, false);
			fw.write("" + i);
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return i;
	}

	/**
	 * Returns a name tag representing this node and the next free index.
	 * 
	 * @return
	 */
	public synchronized NameTag getNextTag() {
		return userTag.setSequenceNumber(getNextSequenceNumber()).build();
	}

	public synchronized List<NodeCommunicationData> getNodeCommunicationDataList() {
		if (deplOK()) {
			return getDeploymentData().getNodeCommunicationDataList();
		}
		return null;
	}

	public synchronized List<NodeLocationData> getNodeLocationDataList() {
		if (deplOK()) {
			return getDeploymentData().getNodeLocationDataList();
		}
		return null;
	}

	public synchronized ArrayList<NodeLocationData> getNodeLocationDataListUniqueLatest() {
		HashSet<Integer> checkedIDs = new HashSet<Integer>();
		List<NodeLocationData> list = getDeploymentData().getNodeLocationDataList();
		ArrayList<NodeLocationData> unique = new ArrayList<Messages.NodeLocationData>();

		if (deplOK()) {

			for (int i = 0; i < list.size(); i++) {
				NodeLocationData d = list.get(i);

				if (checkedIDs.contains(Integer.valueOf(d.getNodeId()))) {
					continue;
				}

				unique.add(getLatestNodeLocationData(d.getNodeId()));

				checkedIDs.add(Integer.valueOf(d.getNodeId()));
			}
		}
		return unique;
	}

	public synchronized List<Annotation> getMapAnnotationDataList() {
		if (deplOK()) {
			return getDeploymentData().getMapAnnotationDataList();
		}
		return null;
	}

	public synchronized List<Image> getMapImageDataList() {
		if (deplOK()) {
			return getDeploymentData().getMapImagesList();
		}
		return null;
	}

	public String getDeploymentPath(String daisyFolder) {
		return FileTools.getAndCreateExternalFolder(daisyFolder + getIdAndTimeStamp() + "/");
	}

	public synchronized int getNumberOfDeployments() {
		File[] files = getAllDeploymentFiles();
		if (files == null) {
			return 0;
		}
		return files.length;
	}

	/**
	 * Returns the list of participating peers
	 * 
	 * @return
	 */
	public synchronized List<Peer> getPeersList() {
		if (deplOK()) {
			return getDeploymentData().getPeersList();
		}
		return null;
	}

	NameTag getTag() {
		return userTag.build();
	}

	private synchronized NameTag getTagFromMessage(MessageOrBuilder m) {
		NameTag tag = null;
		if (m instanceof ChatMessage) {
			tag = ((ChatMessage) m).getTag();
		}
		if (m instanceof NodeLocationData) {
			tag = ((NodeLocationData) m).getTag();
		}
		if (m instanceof NodeCommunicationData) {
			tag = ((NodeCommunicationData) m).getTag();
		}
		if (m instanceof Peer) {
			tag = ((Peer) m).getTag();
		}
		if (m instanceof Annotation) {
			tag = ((Annotation) m).getTag();
		}
		if (m instanceof Image) {
			tag = ((Image) m).getTag();
		}

		/**
		 * SYNC ADD HERE.
		 */
		return tag;
	}

	public synchronized String getUuid() {
		return userTag.getUuid();
	}

	private synchronized boolean isInIndices(UuidWithIndeces u, int i) {
		for (Integer x : u.getIndicesList()) {
			if (x.intValue() == i) {
				return true;
			}
		}

		return false;
	}

	public synchronized void loadDeployment(DeploymentHeader.Builder tempDeploymentHeader,
			DeploymentData.Builder tempDeploymentData, DeploymentLogs.Builder tempDeploymentLogs) {
		Preconditions.checkNotNull(tempDeploymentHeader);
		Preconditions.checkNotNull(tempDeploymentData);
		// can be null:
		// Preconditions.checkNotNull(lTempDeploymentLogs);

		mTempDeploymentData = tempDeploymentData;
		mTempDeploymentHeader = tempDeploymentHeader;

		// todo: only if not null?
		mTempDeploymentLogs = tempDeploymentLogs;
		if (mTempDeploymentLogs == null) {
			mTempDeploymentLogs = DeploymentLogs.newBuilder();
			mTempDeploymentLogs.setLastNumber(0);
		}
		useMemberDiscoveredPeerList = false;

		mTempDeploymentLogs.addAllDiscoveredPeers(discoveredPeers);

		Log.i("OTTOBUS", "Posting depl ldd");
		Handler h = new Handler(ctx.ctx().getMainLooper());
		h.post(new Runnable() {

			@Override
			public void run() {
				bus.post(new DeploymentLoaded());
			}
		});

	}

	public synchronized String loadDeployment(String absolutePath) {

		try {

			File f = new File(absolutePath);
			FileInputStream fis = new FileInputStream(f);

			DeploymentHeader headerFromFile = Messages.DeploymentHeader.parseDelimitedFrom(fis);
			if (headerFromFile == null) {
				return "ERROR: Header from file was NULL";
			}
			DeploymentHeader.Builder lTempDeploymentHeader = Messages.DeploymentHeader.newBuilder(headerFromFile);

			DeploymentData dataFromFile = Messages.DeploymentData.parseDelimitedFrom(fis);
			if (dataFromFile == null) {
				return "ERROR: Data from file was NULL";
			}
			DeploymentData.Builder lTempDeploymentData = Messages.DeploymentData.newBuilder(dataFromFile);

			DeploymentLogs logsFromFile = Messages.DeploymentLogs.parseDelimitedFrom(fis);
			if (logsFromFile == null) {
				return "ERROR: Logs from file was NULL";
			}
			DeploymentLogs.Builder lTempDeploymentLogs = Messages.DeploymentLogs.newBuilder(logsFromFile);

			fis.close();

			loadDeployment(lTempDeploymentHeader, lTempDeploymentData, lTempDeploymentLogs);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return e.getMessage();
		} catch (IOException e) {
			e.printStackTrace();
			return e.getMessage();
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}

		File f = new File(FileTools.getExternalStorageFolder() + "/" + getDeploymentFileName() + ".seq");
		int seq = -1;
		if (f.exists()) {
			String[] lines = FileTools.readLinesOfFile(1, FileTools.getExternalStorageFolder() + "/"
					+ getDeploymentFileName() + ".seq");
			if (lines != null && lines[0] != null) {
				try {
					seq = Integer.parseInt(lines[0]);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		if (seq != -1) {
			mTempDeploymentLogs.setLastNumber(seq);
		}
		return "OK";
	}

	public synchronized Messages.DeploymentHeader loadDeploymentHeader(String absolutePath) {
		Messages.DeploymentHeader deploymentHeader;

		try {
			File f = new File(absolutePath);
			FileInputStream fis = new FileInputStream(f);
			deploymentHeader = Messages.DeploymentHeader.parseDelimitedFrom(fis);
			fis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		return deploymentHeader;
	}

	public void log2bus(final String tag, final String msg) {
		Handler h = new Handler(ctx.ctx().getMainLooper());
		h.post(new Runnable() {

			@Override
			public void run() {
				bus.post(LogMessage.newBuilder().setTag(tag).setMessage(msg).setTimestamp(System.currentTimeMillis())
						.build());
			}
		});
	}

	public void register(Bus bus) {
		this.bus = bus;
		bus.register(this);
	}

	protected synchronized void requestSync(final String uuidForm) {
		Log.i("OTTOBUS", "Posting needs rsc");
		Handler h = new Handler(ctx.ctx().getMainLooper());
		h.post(new Runnable() {
			@Override
			public void run() {
				bus.post(new NeedsSync(uuidForm));
			}
		});
	}

	public synchronized boolean saveActiveDeployment() {
		if (!deplOK()) {
			Toast.makeText(ctx.ctx(), "No deployment saved", Toast.LENGTH_LONG).show();
			return false;
		}

		if (saveDeployment(mTempDeploymentHeader, mTempDeploymentData, mTempDeploymentLogs)) {
			Toast.makeText(ctx.ctx(),
					"Successfuly Saved Active Deployment \"" + mTempDeploymentHeader.getDeploymentName() + "\"",
					Toast.LENGTH_LONG).show();
			return true;
		}

		Toast.makeText(ctx.ctx(), "Saving of active deployment failed", Toast.LENGTH_LONG).show();

		return false;
	}

	private synchronized boolean saveDeployment(Messages.DeploymentHeader.Builder header,
			Messages.DeploymentData.Builder data, Messages.DeploymentLogs.Builder logs) {
		Preconditions.checkNotNull(header);
		Preconditions.checkNotNull(data);
		Preconditions.checkNotNull(logs);

		File f = new File(FileTools.getAndCreateExternalFolder(MAIN_DEPLOYMENT_FOLDER) + header.getIdAndTimeStamp()
				+ "." + VALID_DEPLOYMENT_FILES[0]);

		try {
			FileOutputStream fout = new FileOutputStream(f);

			header.build().writeDelimitedTo(fout);

			data.build().writeDelimitedTo(fout);

			logs.build().writeDelimitedTo(fout);

			fout.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public void setBalloon(boolean isBalloon) {
		this.isBalloon = isBalloon;
	}

	public synchronized void setLocalUserName(String s) {
		Preconditions.checkNotNull(s);
		if (this.userTag != null) {
			this.userTag.setName(s);
		}
		this.localUserName = s;
	}

	/**
	 * Sets the current map config when deployment data is present.
	 * 
	 * @param build
	 */
	public synchronized void setMapViewConfig(MapViewConfig build) {
		if (deplOK()) {
			getDeploymentData().setMapViewConfig(build);
		}
	}

	/**
	 * f
	 * 
	 * @return the path to the deployment
	 */
	public synchronized void showSelectDeploymentDialog(ReturnStringECallback<String> selected) {
		if (selected == null) {
			throw new RuntimeException("ReturnStringCallback can not be null");
		}

		List<StringE<String>> deployments = getAllDeploymentFileNames();

		if (deployments == null) {
			selected.result(null);
		}

		IntentTools.userSelectStringE(ctx.ctx(), "Select a deployment", deployments, selected);
	}

	@Override
	public synchronized String toString() {
		String tmp = getDeploymentHeader().build().toString() + "\n###############\n" + getDeploymentData().build()
				+ "\n###############\n" + getDeploymentLogs().build();
		return tmp;
	}

	public void unregister(Bus bus) {
		bus.unregister(this);
	}

	/**
	 * Visits all items that are shared across nodes
	 * 
	 * @param visitor
	 */
	private synchronized void visitAllData(DataVisitor visitor) {
		if (mTempDeploymentData == null) {
			return;
		}

		if (mTempDeploymentData.getChatMessageDataList() != null) {
			for (ChatMessage m : mTempDeploymentData.getChatMessageDataList()) {
				visitor.visitMessageOrBuilder(m);
			}
		}

		if (mTempDeploymentData.getNodeLocationDataList() != null) {
			for (NodeLocationData m : mTempDeploymentData.getNodeLocationDataList()) {
				visitor.visitMessageOrBuilder(m);
			}
		}

		if (mTempDeploymentData.getNodeCommunicationDataList() != null) {
			for (NodeCommunicationData m : mTempDeploymentData.getNodeCommunicationDataList()) {
				visitor.visitMessageOrBuilder(m);
			}
		}

		if (mTempDeploymentData.getPeersList() != null) {
			for (Peer p : mTempDeploymentData.getPeersList()) {
				visitor.visitMessageOrBuilder(p);
			}
		}

		if (mTempDeploymentData.getMapAnnotationDataList() != null) {
			for (Annotation a : mTempDeploymentData.getMapAnnotationDataList()) {
				visitor.visitMessageOrBuilder(a);
			}
		}

		if (mTempDeploymentData.getMapImagesList() != null) {
			for (Image i : mTempDeploymentData.getMapImagesList()) {
				visitor.visitMessageOrBuilder(i);
			}
		}

		/**
		 * SYNC ADD HERE.
		 */

		/*
		 * Add new data types here and in the visitor inteface
		 */
	}

	public void writeDataAndHeaderTo(Builder db) {
		db.setData(getDeploymentData());
		db.setHeader(getDeploymentHeader());
	}
}
