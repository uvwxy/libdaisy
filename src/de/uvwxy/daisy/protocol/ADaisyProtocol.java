package de.uvwxy.daisy.protocol;

import android.content.Context;
import de.uvwxy.daisy.proto.Messages.NameTag;
import de.uvwxy.net.IProtocol;

public abstract class ADaisyProtocol implements IProtocol {
	DaisyData data;
	Context ctx;
	NameTag who;

	public ADaisyProtocol(DaisyData data, Context ctx, NameTag who) {
		super();
		this.data = data;
		this.ctx = ctx;
		this.who = who;
	}

}
