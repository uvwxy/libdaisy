package de.uvwxy.daisy.protocol;

import java.io.IOException;

import android.content.Context;
import android.util.Log;
import de.uvwxy.daisy.proto.Messages;
import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.daisy.proto.Messages.NameTag;
import de.uvwxy.net.AConnection;
import de.uvwxy.net.IProtocol;
import de.uvwxy.proto.parser.ProtoInputStreamParser;

/**
 * This is done to synchronize data between two connected nodes.
 * 
 */
public class DaisyProtocolStartSyncRequest extends ADaisyProtocol implements IProtocol {

	public DaisyProtocolStartSyncRequest(DaisyData data, Context ctx, NameTag who) {
		super(data, ctx, who);
	}

	@Override
	public void doProtocol(final AConnection connection) {
		// TODO: check not null
		Log.i("DAISYNET", "Doing DaisyProtocolSyncRequest");
		DaisyProtocol protocol = new DaisyProtocol(connection, data, ctx, who, false);

		ProtoInputStreamParser<DaisyProtocolMessage> parser = new ProtoInputStreamParser<Messages.DaisyProtocolMessage>(protocol,
				new DaisyProtocolMessageParser(connection), connection.getIn());

		connection.setProtoInputStreamParser(parser);
		Log.i("DAISYNET", "Doing connection.setProtoInputStreamParser(parser);");

		try {
			DaisyProtocolRoutinesData.getDataHandshake(data).writeDelimitedTo(connection.getOut());
			// no crash, i.e. success: start reading inputstream
			Log.i("DAISYNET", "Doing getDataHandshake writeDelimitedTo");

			parser.start();
			Log.i("DAISYNET", "Doing parser.start();");

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
