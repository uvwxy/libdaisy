package de.uvwxy.daisy.protocol;

import java.io.IOException;

import android.content.Context;
import android.util.Log;
import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.daisy.proto.Messages.NameTag;
import de.uvwxy.net.AConnection;
import de.uvwxy.net.IProtocol;
import de.uvwxy.proto.parser.ProtoInputStreamParser;

/**
 * This is done to test if a node does something useful on the other side of a
 * connecting socket.
 * 
 */
public class DaisyProtocolStartNameRequest extends ADaisyProtocol implements IProtocol {

	public DaisyProtocolStartNameRequest(DaisyData data, Context ctx, NameTag who) {
		super(data, ctx, who);
	}

	@Override
	public void doProtocol(final AConnection connection) {
		// TODO: check not null
		Log.i("DAISYNET", "Doing DaisyProtocolNameRequest");
		DaisyProtocol protocol = new DaisyProtocol(connection, data, ctx, who, false);

		ProtoInputStreamParser<DaisyProtocolMessage> parser = new ProtoInputStreamParser<DaisyProtocolMessage>(protocol, new DaisyProtocolMessageParser(
				connection), connection.getIn());

		connection.setProtoInputStreamParser(parser);
		try {
			DaisyProtocolRoutinesData.getNameRequest(data).writeDelimitedTo(connection.getOut());
			// no crash, i.e. success: start reading inputstream

			parser.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
