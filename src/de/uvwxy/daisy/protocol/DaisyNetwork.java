package de.uvwxy.daisy.protocol;

import java.util.UUID;

import com.google.common.base.Preconditions;

import android.content.Context;
import android.util.Log;
import de.uvwxy.daisy.proto.Messages.Peer;
import de.uvwxy.daisy.proto.Messages.PeerType;
import de.uvwxy.helper.ContextProxy;
import de.uvwxy.net.AConnection;
import de.uvwxy.net.ICancelHandler;
import de.uvwxy.net.IConnectHandler;
import de.uvwxy.net.IProtocol;
import de.uvwxy.net.ISetupCallback;
import de.uvwxy.net.bluetooth.BTConnectionSetup;
import de.uvwxy.net.tcpip.TcpIPConnectionSetup;
import de.uvwxy.net.wifi.WIFIP2PConnectionSetup;

public class DaisyNetwork {

	public static void connect(Context ctx, DaisyData data, DaisyNetwork net, ADaisyProtocol protocol, Peer p, Object commLock) {
		// start sync with peer
		switch (p.getPeerType()) {
		case BLUETOOTH:
			net.btConnect(protocol, p.getAddress(), commLock);
			break;
		case IP:
			net.tcpIpConnect(protocol, p.getAddress(), commLock);
			break;
		case WIFI_MAC:
			// TODO
			break;
		case XBEE:
			// TODO
			break;
		}
	}

	private ISetupCallback bluetoothCallback = new ISetupCallback() {

		public void _log(String s) {
			data.log2bus("DAISYNET", s);
		}

		@Override
		public void discoveryStopped() {
			data.log2bus("DAISYNET", "Scan stopped");
			btDiscoRunning = false;
		}

		public void foundDevice(final String address) {
			data.log2bus("DAISYNET", String.format("Discovered device %s", address));
			// the tag set here is not a tag with a valid index! (-1)
			Peer peer = Peer.newBuilder().setAddress(address).setPeerType(PeerType.BLUETOOTH).setTag(data.getTag().toBuilder().setSequenceNumber(-1).build())
					.build();
			data.addDiscoveredPeer(peer);

		}
	};

	private IConnectHandler btConnect = new IConnectHandler() {

		@Override
		public void onConnect(IProtocol protocol, AConnection c, String usedAddress) {
			data.log2bus("DAISYNET", "Conencted to " + usedAddress);
			protocol.doProtocol(c);
		}

		@Override
		public void onConnectTimeout() {
			data.log2bus("DAISYNET", "Connection timed out");
		}
	};

	private BTConnectionSetup btConnectionSetup;
	private boolean btDiscoRunning = false;
	private boolean btIsListening = false;
	private ICancelHandler btListenHandler;
	private ContextProxy ctx;
	private DaisyData data;
	private boolean ipIsListening = false;
	private ISetupCallback tcpIpCallback = new ISetupCallback() {

		@Override
		public void _log(String s) {
			data.log2bus("TcpIp", s);
		}

		@Override
		public void discoveryStopped() {
			// TODO there is no discovery done via tcp ip
		}

		@Override
		public void foundDevice(String address) {
			//TODO there is no discovery done via tcp ip
		}
	};

	private IConnectHandler tcpIpConnect = new IConnectHandler() {

		@Override
		public void onConnect(IProtocol protocol, AConnection c, String usedAddress) {
			data.log2bus("DAISYNET", "Conencted to " + usedAddress);
			protocol.doProtocol(c);
		}

		@Override
		public void onConnectTimeout() {
			data.log2bus("DAISYNET", "Connection timed out");
		}
	};

	private TcpIPConnectionSetup tcpIpConnectionSetup;
	private ICancelHandler tcpIpListenHandler;

	private ISetupCallback wifiP2pCallback = new ISetupCallback() {

		@Override
		public void _log(String s) {
			data.log2bus("WiFi", s);
			Log.i("WIFI", s);
		}

		@Override
		public void discoveryStopped() {
			data.log2bus("WiFi", "discoveryStopped");
			wifiP2pDiscoRunning = false;
		}

		@Override
		public void foundDevice(String address) {
			data.log2bus("WiFi", String.format("Found device %s", address));
			Log.i("WiFi", String.format("Found device %s", address));

			Peer peer = Peer.newBuilder().setAddress(address).setPeerType(PeerType.WIFI_MAC).setTag(data.getNextTag()).build();
			data.addDiscoveredPeer(peer);

		}
	};
	private IConnectHandler wifiP2pConnect = new IConnectHandler() {

		@Override
		public void onConnect(IProtocol protcol, AConnection c, String usedAddress) {
			data.log2bus("WiFi", "Conencted to " + usedAddress);
		}

		@Override
		public void onConnectTimeout() {
			data.log2bus("WiFi", "Connect timed out");
		}
	};

	private WIFIP2PConnectionSetup wifiP2PConnectionSetup;

	private boolean wifiP2pDiscoRunning = false;

	private boolean wifiP2pListen = false;

	public DaisyNetwork(ContextProxy ctx, DaisyData data) {
		Preconditions.checkNotNull(ctx);
		Preconditions.checkNotNull(data);

		this.data = data;
		this.ctx = ctx;
	}

	public synchronized void btConnect(IProtocol protocol, String address, Object commLock) {
		btInitIfNeeded();
		// TODO: do sth. with returned value
		btConnectionSetup.connect(protocol, btConnect, address, commLock);
	}

	public void btInitIfNeeded() {
		if (btConnectionSetup == null) {
			btConnectionSetup = new BTConnectionSetup(ctx.ctx(), bluetoothCallback);
			btConnectionSetup.setName("u" + (int) (Math.random() * 100));
			btConnectionSetup.setUUID(new UUID(123, 456));
		}
	}

	public boolean btIsDiscovering() {
		return btDiscoRunning;
	}

	public boolean btIsListening() {
		return btIsListening;
	}

	public synchronized void btListen(IProtocol protocol) {
		btInitIfNeeded();
		btIsListening = true;
		btListenHandler = btConnectionSetup.listen(protocol);
	}

	public synchronized void btStartDiscovery() {

		btInitIfNeeded();

		Thread tDiscovery = new Thread(new Runnable() {

			@Override
			public void run() {
				btConnectionSetup.startDiscovery();
				btDiscoRunning = true;
			}
		});

		tDiscovery.run();

	}

	public synchronized void btStopDiscovery() {
		if (btConnectionSetup == null) {
			return;
		}

		btConnectionSetup.stopDiscovery();
		btDiscoRunning = false;
	}

	public synchronized void btStopListen() {
		btIsListening = false;
		if (btListenHandler != null) {
			btListenHandler.cancel();
		}
	}

	public synchronized void tcpIpConnect(IProtocol protocol, String address, Object commLock) {
		tcpIpInitIfNeeded();
		tcpIpConnectionSetup.connect(protocol, tcpIpConnect, address, commLock);
	}

	public synchronized void tcpIpInitIfNeeded() {
		if (tcpIpConnectionSetup == null) {
			tcpIpConnectionSetup = new TcpIPConnectionSetup(tcpIpCallback);
			tcpIpConnectionSetup.setPort(41952);
		}
	}

	public synchronized boolean tcpIpIsListening() {
		return ipIsListening;
	}

	public synchronized void tcpIpListen(IProtocol protocol) {
		tcpIpInitIfNeeded();
		ipIsListening = true;
		tcpIpListenHandler = tcpIpConnectionSetup.listen(protocol);
	}

	public synchronized void tcpIpStopListen() {
		ipIsListening = false;
		if (tcpIpListenHandler != null) {
			tcpIpListenHandler.cancel();
		}
	}

	public synchronized void wifiP2pConnect(IProtocol protocol, String address, Object commLock) {
		if (wifiP2PConnectionSetup == null) {
			wifiP2PConnectionSetup = new WIFIP2PConnectionSetup(ctx.ctx(), wifiP2pCallback);
		}

		wifiP2PConnectionSetup.connect(protocol, btConnect, address, commLock);

	}

	public boolean wifiP2pIsDiscoring() {
		return wifiP2pDiscoRunning;
	}

	public boolean wifiP2pIsListening() {
		return wifiP2pListen;
	}

	public synchronized void wifiP2pListen(IProtocol protocol) {
		// not implemented
		wifiP2PConnectionSetup.listen(protocol);
	}

	public synchronized void wifiP2pStartDiscovery() {

		if (wifiP2PConnectionSetup == null) {
			wifiP2PConnectionSetup = new WIFIP2PConnectionSetup(ctx.ctx(), wifiP2pCallback);
		}

		Thread tDiscovery = new Thread(new Runnable() {

			@Override
			public void run() {
				wifiP2PConnectionSetup.startDiscovery();
				wifiP2pDiscoRunning = true;
			}
		});

		tDiscovery.run();

	}

	public synchronized void wifiP2pStopDiscovery() {
		if (wifiP2PConnectionSetup == null) {
			return;
		}

		wifiP2PConnectionSetup.stopDiscovery();
		wifiP2pDiscoRunning = false;
	}
}
