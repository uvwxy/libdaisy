package de.uvwxy.daisy.protocol.busmessages;

import java.util.List;

import de.uvwxy.daisy.proto.Messages;

public class BalloonBusReply {
	public boolean hostWasBalloon = false;
	public List<Messages.CamSize> resolutions = null;

	public BalloonBusReply(boolean wasBalloon, List<Messages.CamSize> resolutions) {
		this.hostWasBalloon = wasBalloon;
		this.resolutions = resolutions;
	}
}
