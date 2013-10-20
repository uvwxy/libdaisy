package de.uvwxy.daisy.protocol;

import android.content.Context;
import android.util.Log;
import de.uvwxy.daisy.proto.Messages;
import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.daisy.proto.Messages.NameTag;
import de.uvwxy.net.AConnection;
import de.uvwxy.net.IProtocol;
import de.uvwxy.proto.parser.ProtoInputStreamParser;

/**
 * This is done by all listening sockets.
 * 
 */
public class DaisyProtocolStartMainReceiverLoop extends ADaisyProtocol implements IProtocol {

	public DaisyProtocolStartMainReceiverLoop(DaisyData data, Context ctx, NameTag who) {
		super(data, ctx, who);
	}

	@Override
	public void doProtocol(final AConnection connection) {
		// TODO: check not null
		Log.i("DAISYNET", "Doing DaisyProtocolImpl");
		DaisyProtocol protocol = new DaisyProtocol(connection, data, ctx, who, true);

		ProtoInputStreamParser<DaisyProtocolMessage> parser = new ProtoInputStreamParser<Messages.DaisyProtocolMessage>(protocol,
				new DaisyProtocolMessageParser(connection), connection.getIn());

		connection.setProtoInputStreamParser(parser);

		Log.i("DAISYNET", "Doing parser.start();");

		parser.start();
	}

}
