package de.uvwxy.daisy.osmdroid;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.common.base.Preconditions;

import de.uvwxy.daisy.proto.Messages;
import de.uvwxy.daisy.protocol.DaisyData;

public abstract class IOverlayExtractor<E> {
	protected DaisyData data;

	public IOverlayExtractor(DaisyData data) {
		Preconditions.checkNotNull(data);
		this.data = data;
	}

	public abstract Messages.Location getLocation(Context ctx, E e);

	public abstract String getTitle(Context ctx, E e);

	public abstract String getDescription(Context ctx, E e);

	public String getSubDescription(Context ctx, E e) {
		return null;
	}

	public abstract Drawable getMapIcon(Context ctx, E e);

}
