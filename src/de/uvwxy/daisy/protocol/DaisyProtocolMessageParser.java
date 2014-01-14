package de.uvwxy.daisy.protocol;

import java.io.IOException;
import java.io.InputStream;

import de.uvwxy.daisy.proto.Messages;
import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.net.AConnection;
import de.uvwxy.proto.parser.IProtoMessageParser;

public class DaisyProtocolMessageParser implements IProtoMessageParser<Messages.DaisyProtocolMessage> {

	private AConnection connection;

	public DaisyProtocolMessageParser(AConnection connection) {
		this.connection = connection;
	}

	@Override
	public DaisyProtocolMessage parseMessageFromStream(InputStream in) {
		try {
			return DaisyProtocolMessage.parseDelimitedFrom(in);
		} catch (IOException e) {
			e.printStackTrace();
			connection.close();
			return null;
		}
	}

}
