package de.uvwxy.daisy.osmdroid;

import android.content.Context;
import android.graphics.drawable.Drawable;
import de.uvwxy.daisy.proto.Messages.ChatMessage;
import de.uvwxy.daisy.proto.Messages.Location;
import de.uvwxy.daisy.protocol.DaisyData;
import de.uvwxy.helper.DateTools;

public class ExtractorChatMessage extends IOverlayExtractor<ChatMessage> {

	public ExtractorChatMessage(DaisyData data) {
		super(data);
	}

	@Override
	public Location getLocation(Context ctx, ChatMessage e) {
		return e.getLocation();
	}

	@Override
	public String getTitle(Context ctx, ChatMessage e) {
		if (!e.getTag().hasName()) {
			return e.getTag().getUuid();
		}
		return e.getTag().getName();
	}

	@Override
	public String getDescription(Context ctx, ChatMessage e) {
		return DateTools.getDateTimeLong(ctx, e.getTimestamp()) + ":\n" + e.getMessage();
	}

	@Override
	public Drawable getMapIcon(Context ctx, ChatMessage e) {
		return ctx.getResources().getDrawable(de.uvwxy.daisy.common.R.drawable.ic_action_chat);

	}

}
