package de.uvwxy.daisy.sensornode;

import java.nio.ByteBuffer;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;

import de.uvwxy.daisy.proto.Messages.CTPHeader;
import de.uvwxy.daisy.proto.Messages.NodeCommunicationData;
import de.uvwxy.daisy.proto.Messages.NodeLocationData;
import de.uvwxy.daisy.proto.Messages.SensorsDataCollectionMsg;
import de.uvwxy.daisy.proto.Messages.TinyOSHeader;
import de.uvwxy.daisy.proto.Messages.XBeeMessage;
import de.uvwxy.daisy.proto.Messages.ZigBeeMessage90;
import de.uvwxy.daisy.protocol.DaisyData;
import de.uvwxy.xbee.apimode.messages.APIMessage;
import de.uvwxy.xbee.apimode.messages.MessageID;
import de.uvwxy.xbee.apimode.messages.MsgRXPacket16;
import de.uvwxy.xbee.apimode.messages.MsgRXPacket64;
import de.uvwxy.xbee.apimode.messages.MsgZigBeeReceivePacket;

public class SensorNetworkMessageParser {
	
	public static Drawable getBatteryIcon(DaisyData data, Context ctx, NodeLocationData e) {
		if (e == null) {
			return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.node_batt_5);
		}

		NodeCommunicationData ncd = data.getLatestNodeCommunicationDataWithSensorData(e.getNodeId());

		if (ncd == null) {
			return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.node_batt_5);
		}

		double x = ncd.getSensorData().getBatteryVoltage() / 1000.0;

		double min = 2.7;
		double max = 3.3;
		double intervalSpan = max - min;
		Log.i("BATTERYICON", "intervalSpan = " + intervalSpan);
		double intervalSize = intervalSpan / 5.0;
		Log.i("BATTERYICON", "intervalSize = " + intervalSize);
		double diff = x - min;
		Log.i("BATTERYICON", "diff = " + diff);
		int i = (int) (diff / intervalSize);
		Log.i("BATTERYICON", "i = " + i);
		switch (i) {
		case 0:
			return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.node_batt_0);
		case 1:
			return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.node_batt_1);
		case 2:
			return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.node_batt_2);
		case 3:
			return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.node_batt_3);
		case 4:
			return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.node_batt_4);
		}

		if (i > 4) {
			return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.node_batt_4);
		}

		return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.node_batt_5);
	}


	/**
	 * 
	 * @param data
	 * @param m
	 * @param l
	 * @return a NodeCommunicationData.Builder with only a missing tag (null on
	 *         fail)
	 */
	public static NodeCommunicationData.Builder apiMessageToNodeCommunicationData(DaisyData data, APIMessage m) {
		Preconditions.checkNotNull(data);

		if (m == null) {
			Log.i("NCD BUILDER", "APIMessage m was null");
			return null;
		}

		NodeCommunicationData.Builder b = NodeCommunicationData.newBuilder();

		b.setTimestamp(System.currentTimeMillis());

		int payloadDataOffset = -1;

		if (m.getMessageID().equals(MessageID.MSG_RX_PACKET_64)) {
			Log.i("NCD BUILDER", "msgRxPacketToNodeCommunicationData 64");

			b.setXbeeMessage(msgRxPacketToNodeCommunicationData(m));
			payloadDataOffset = 8 + 1 + 1;

		} else if (m.getMessageID().equals(MessageID.MSG_RX_PACKET_16)) {
			Log.i("NCD BUILDER", "msgRxPacketToNodeCommunicationData 16");

			b.setXbeeMessage(msgRxPacketToNodeCommunicationData(m));
			payloadDataOffset = 2 + 1 + 1;
		}

		if (m.getMessageID().equals(MessageID.MSG_ZIGBEE_RECEIVE_PACKET)) {
			Log.i("NCD BUILDER", "msgZigBeeReceivePacket");

			b.setZigbeeMessage90(msgZigBeeReceivePacket(data, m));
			payloadDataOffset = 11;
		}

		if (m.getCmdData().length >= payloadDataOffset + 8) {
			Log.i("NCD BUILDER", "getTinyOSHeader");

			b.setTinyOsHeader(getTinyOSHeader(m.getCmdData(), payloadDataOffset));
		}

		if (m.getCmdData().length >= payloadDataOffset + 8 + 8) {
			Log.i("NCD BUILDER", "getCTPHeader");

			b.setCtpHeader(getCTPHeader(m.getCmdData(), payloadDataOffset + 8));
		}

		SensorsDataCollectionMsg sdcm = null;

		if (m.getCmdData().length >= payloadDataOffset + 8 + 8 + 28 - 1) {
			Log.i("NCD BUILDER", "readSensorDataColletion");

			byte[] sdcmBytes = new byte[28];

			// add TinyOS header, CTP header, length to offset
			int sensorDataOffset = payloadDataOffset + 8 + 8;

			System.arraycopy(m.getCmdData(), sensorDataOffset, sdcmBytes, 0, 28);
			sdcm = readSensorDataColletion(sdcmBytes);

		}
		// create sdcm
		if (sdcm != null) {
			b.setSensorData(sdcm);
		} else {
			// something did not match in length, keep everything, including
			// headers
			b.setRawPayloadData(ByteString.copyFrom(m.getCmdData()));
		}

		return b;
	}

	private static TinyOSHeader getTinyOSHeader(byte[] bytes, int offset) {
		TinyOSHeader.Builder b = TinyOSHeader.newBuilder();

		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.position(offset);

		b.setId(bb.get() & 0xff);
		b.setDestination(bb.getShort() & 0xffff);
		b.setSource(bb.getShort() & 0xffff);
		b.setLength(bb.get() & 0xff);
		b.setGroup(bb.get() & 0xff);
		b.setType(bb.get() & 0xff);

		return b.build();
	}

	private static CTPHeader getCTPHeader(byte[] bytes, int offset) {
		CTPHeader.Builder b = CTPHeader.newBuilder();

		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.position(offset);

		b.setCtpOptions(bb.get() & 0xff);
		b.setThl(bb.get() & 0xff);
		b.setEtx(bb.getShort() & 0xffff);
		b.setOrigin(bb.getShort() & 0xffff);
		b.setOriginSequenceNumber(bb.get() & 0xff);
		b.setType(bb.get() & 0xff);

		return b.build();
	}

	public static SensorsDataCollectionMsg readSensorDataColletion(byte[] bytes) {
		if (bytes.length != 28) {
			
			return null;
		}

		ByteBuffer bb = ByteBuffer.wrap(bytes);

		SensorsDataCollectionMsg.Builder b = SensorsDataCollectionMsg.newBuilder();

		int offset = 0;
		// nx_int16_t nodeId;
		b.setNodeId(bb.getShort(offset));
		offset += 2;

		// nx_int16_t parentNodeId;
		b.setParentNodeId(bb.getShort(offset));
		offset += 2;

		// nx_uint16_t etx;
		b.setEtx(bb.getShort(offset) & 0xFFFF);
		offset += 2;

		// nx_uint32_t time;
		b.setTime(bb.getInt(offset) & 0xFFFFFFFF);
		offset += 4;

		// nx_uint16_t batteryVoltage;
		b.setBatteryVoltage(bb.getShort(offset) & 0xFFFF);
		offset += 2;

		// nx_int16_t accelerationX;
		b.setAccelerationX(bb.getShort(offset));
		offset += 2;

		// nx_int16_t accelerationY;
		b.setAccelerationY(bb.getShort(offset));
		offset += 2;

		// nx_int16_t accelerationZ;
		b.setAccelerationZ(bb.getShort(offset));
		offset += 2;

		// nx_int16_t accelerationTemperature;
		b.setAccelerationTemperature(bb.getShort(offset));
		offset += 2;

		// nx_int32_t inclinationX;
		b.setInclinationX(bb.getInt(offset));
		offset += 4;

		// nx_int32_t inclinationY;
		b.setInclinationY(bb.getInt(offset));
		offset += 4; // but not needed

		return b.build();
	}

	private static ZigBeeMessage90 msgZigBeeReceivePacket(DaisyData data, APIMessage m) {
		Preconditions.checkNotNull(m);

		ZigBeeMessage90.Builder b = ZigBeeMessage90.newBuilder();

		ByteBuffer addressBytes = ByteBuffer.wrap(((MsgZigBeeReceivePacket) m).getSourceAddress());
		b.setSourceAddress(addressBytes.getLong());

		ByteBuffer sourceBytes = ByteBuffer.wrap(((MsgZigBeeReceivePacket) m).getSourceNetworkAddress());
		b.setSourceNetworkAddress(sourceBytes.getShort());

		b.setReceiveOptionsBitfield(((MsgZigBeeReceivePacket) m).getOptions());
		return b.build();
	}

	private static XBeeMessage msgRxPacketToNodeCommunicationData(APIMessage m) {
		Preconditions.checkNotNull(m);

		XBeeMessage.Builder b = XBeeMessage.newBuilder();

		ByteBuffer addressBytes = ByteBuffer.wrap(((MsgRXPacket16) m).getSourceAddress());
		if (m.getMessageID().equals(MessageID.MSG_RX_PACKET_64)) {
			b.setSourceAddress(addressBytes.getLong());
			b.setIs16Bit(false);
		}
		if (m.getMessageID().equals(MessageID.MSG_RX_PACKET_16)) {
			b.setSourceAddress(addressBytes.getShort() & 0xffff);
			b.setIs16Bit(true);
		}

		// 16 bit packet derived from this.
		if (m instanceof MsgRXPacket64) {
			MsgRXPacket64 m64 = (MsgRXPacket64) m;
			b.setRssi(m64.getRSSI() & 0xff);
			b.setOptions(m64.getOptions() & 0xff);
		} else {
			return null;
		}

		return null;
	}
}
