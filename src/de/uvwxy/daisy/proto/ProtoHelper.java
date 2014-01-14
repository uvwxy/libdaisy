package de.uvwxy.daisy.proto;

import java.security.MessageDigest;

import org.apache.http.util.ByteArrayBuffer;

import com.google.common.base.Preconditions;

import de.uvwxy.daisy.proto.Messages.Location;
import de.uvwxy.daisy.proto.Messages.NodeLocationData;

public class ProtoHelper {

	@SuppressWarnings("unused")
	private static final String MD5 = "MD5";

	public static String getDescription(NodeLocationData nld) {
		String desc = "";
		desc += String.format("Height: %.2f m", nld.getHeight());
		desc += String.format("\nLat: %.6f deg", nld.getLocation().getLatitude());
		desc += String.format("\nLon: %.6f deg", nld.getLocation().getLongitude());
		desc += String.format("\nAlt: %.2f m", nld.getLocation().getAltitude());
		desc += String.format("\nAccuracy: %.2f m", nld.getLocation().getAccuracy());
		desc += "\n";
		desc += String.format("\nQR-Bearing: %.2f deg", nld.getQrCodeBearing());
		desc += String.format("\nOri.X: %.2f deg", nld.getParallelOrientationX());
		desc += String.format("\nOri.Y: %.2f deg", nld.getParallelOrientationY());
		desc += String.format("\nOri.Z: %.2f deg", nld.getParallelOrientationZ());
		return desc;
	}

	public static android.location.Location protoLocationToAndroidLocation(
			de.uvwxy.daisy.proto.Messages.Location protoLoc) {
		android.location.Location androidLoc = new android.location.Location(protoLoc.getProvider());
		androidLoc.setAccuracy((float) protoLoc.getAccuracy());
		androidLoc.setAltitude(protoLoc.getAltitude());
		androidLoc.setBearing((float) protoLoc.getBearing());
		androidLoc.setLatitude(protoLoc.getLatitude());
		androidLoc.setLongitude(protoLoc.getLongitude());
		androidLoc.setSpeed((float) protoLoc.getSpeed());
		androidLoc.setTime(protoLoc.getTime());
		return androidLoc;
	}

	public static de.uvwxy.daisy.proto.Messages.Location androidLocationToProtoLocation(
			android.location.Location androLoc) {
		Preconditions.checkNotNull(androLoc);

		Messages.Location.Builder locBuilder = Messages.Location.newBuilder();
		locBuilder.setLatitude(androLoc.getLatitude());
		locBuilder.setLongitude(androLoc.getLongitude());
		locBuilder.setAltitude(androLoc.getAltitude());
		locBuilder.setBearing(androLoc.getBearing());
		locBuilder.setAccuracy(androLoc.getAccuracy());
		locBuilder.setTime(androLoc.getTime());
		locBuilder.setSpeed(androLoc.getSpeed());
		locBuilder.setProvider(androLoc.getProvider());

		return locBuilder.build();
	}

	/**
	 * Add a proto location object to the hash
	 * 
	 * @param md
	 * @param loc
	 */
	@SuppressWarnings("unused")
	private static void updateLocationDigest(MessageDigest md, Location loc) {
		ByteArrayBuffer bab = new ByteArrayBuffer(100);

		bab.append((int) (loc.getAccuracy() * 1000000));
		bab.append((int) (loc.getAltitude() * 1000000));
		bab.append((int) (loc.getBearing() * 1000000));
		bab.append((int) (loc.getLatitude() * 1000000));
		bab.append((int) (loc.getLongitude() * 1000000));
		bab.append((int) (loc.getSpeed() * 1000000));
		bab.append((int) loc.getTime());

		byte[] providerBytes = loc.getProvider().getBytes();
		bab.append(providerBytes, 0, providerBytes.length);

		md.update(bab.buffer());
	}

	/**
	 * Add a long to the hash
	 * 
	 * @param md
	 * @param l
	 */
	@SuppressWarnings("unused")
	private static void updateLongDigest(MessageDigest md, long l) {
		for (int i = 0; i < 7; i++) {
			md.update((byte) (l >>> 7));
		}
	}

	@SuppressWarnings("unused")
	private static void updateFloatDigest(MessageDigest md, float f) {
		md.update(Float.toString(f).getBytes());
	}

}
