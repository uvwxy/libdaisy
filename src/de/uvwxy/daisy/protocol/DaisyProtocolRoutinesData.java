package de.uvwxy.daisy.protocol;

import java.util.List;

import de.uvwxy.daisy.proto.Messages.DaisyProtocolMessage;
import de.uvwxy.daisy.proto.Messages.DataReply;
import de.uvwxy.daisy.proto.Messages.DataRequest;
import de.uvwxy.daisy.proto.Messages.DeploymentReply;
import de.uvwxy.daisy.proto.Messages.DeploymentRequest;
import de.uvwxy.daisy.proto.Messages.NameReply;
import de.uvwxy.daisy.proto.Messages.NameRequest;
import de.uvwxy.daisy.proto.Messages.UuidWithIndeces;

public class DaisyProtocolRoutinesData {
	private static DaisyProtocolMessage.Builder mkBuilder() {
		return DaisyProtocolMessage.newBuilder();
	}

	public static DaisyProtocolMessage getNameRequest(DaisyData data) {
		NameRequest nameRequest = NameRequest.newBuilder().setClientTag(data.getLocalUserTag()).build();
		return mkBuilder().setNameRequest(nameRequest).build();
	}

	public static DaisyProtocolMessage getNameReply(DaisyData data) {
		NameReply nr = NameReply.newBuilder().setHostTag(data.getLocalUserTag()).build();
		return mkBuilder().setNameReply(nr).build();
	}

	public static DaisyProtocolMessage requestDeployment(DaisyData data) {
		DeploymentRequest dr = DeploymentRequest.newBuilder().setClientTag(data.getLocalUserTag()).build();
		return mkBuilder().setDeploymentRequest(dr).build();
	}

	public static DaisyProtocolMessage getDeploymentReply(DaisyData data) {
		DeploymentReply.Builder db = DeploymentReply.newBuilder();
		db.setNameOfHost(data.getLocalUserTag());
		data.writeDataAndHeaderTo(db);
		return mkBuilder().setDeploymentReply(db.build()).build();
	}

	public static DaisyProtocolMessage getDataHandshake(DaisyData data) {
		return mkBuilder().setDataHandshake(data.getAllUuidsWithMaxNumber()).build();
	}

	public static DaisyProtocolMessage getDataRequest(DaisyProtocolMessage mob, DaisyData data) {
		List<UuidWithIndeces> list = data.getMissingIndeces(mob.getDataHandshake().getDataLargestIndexList());

		DataRequest.Builder b = DataRequest.newBuilder();

		if (list != null) {
			b.addAllUuidWithIndices(list);
		}

		return mkBuilder().setDataRequest(b).build();
	}

	public static DaisyProtocolMessage getDataReply(DaisyProtocolMessage mob, DaisyData data) {
		DataReply rep = data.getDataReply(mob.getDataRequest());
		return mkBuilder().setDataReply(rep).build();
	}
}