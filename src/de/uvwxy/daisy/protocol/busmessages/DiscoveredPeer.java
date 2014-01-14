package de.uvwxy.daisy.protocol.busmessages;

import de.uvwxy.daisy.proto.Messages.Peer;

public class DiscoveredPeer {
	public Peer p;
	public DiscoveredPeer(Peer p) {
		this.p = p;
	}
}
