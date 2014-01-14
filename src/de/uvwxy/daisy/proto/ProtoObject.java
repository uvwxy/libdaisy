package de.uvwxy.daisy.proto;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Objects;

import de.uvwxy.daisy.proto.Messages.Peer;

public class ProtoObject {

	public static boolean equals(Peer lhs, Peer rhs) {
		return Objects.equal(peer2String(lhs), peer2String(rhs));
	}

	/**
	 * compares peers, but NOT the tag
	 * 
	 * @param list
	 * @param p
	 * @return
	 */
	public static boolean containsPeerWithAddress(List<Peer> list, Peer p) {
		for (Peer x : list) {
			if (x != null && p != null) {
				if (x.getAddress().equals(p.getAddress()) && x.getPeerType().equals(p.getPeerType())) {
					return true;
				}
				// for now a peer is equal if the required fields Address and PeerType are equal.
				// last seen timestamp , peer_name_tag doe not have to equal (for now).
				// this way the NameTag tag kann differ, i.e. not set properly 
				//(see usage of -1 as index in temporary peer list)
			}
		}
		return false;
	}

	public static void remove(List<Peer> list, Peer p) {
		// fix UnsupportedOperationException on List.remove(..) 
		list = new ArrayList<Messages.Peer>(list);
		for (int i = 0; i < list.size(); i++) {
			if (equals(list.get(i), p)) {
				list.remove(i);
			}
		}
	}

	public static String peer2String(Peer peer) {
		if (peer == null) {
			return null;
		}
		return Objects.toStringHelper("Peer").add("address", peer.getAddress()).add("type", peer.getPeerType().getNumber()).toString();
	}
}
