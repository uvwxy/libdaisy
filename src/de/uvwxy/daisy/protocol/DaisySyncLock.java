package de.uvwxy.daisy.protocol;

public class DaisySyncLock {

	private static DaisySyncLock instance = null;

	public static DaisySyncLock getInstance() {
		if (instance == null) {
			instance = new DaisySyncLock();
		}
		return instance;
	}

	private DaisySyncLock() {
		// singleton..
	}

	private Object mLock = new Object();
	private boolean isLocked = false;

	public synchronized boolean waitAndLock() {
		synchronized (mLock) {
			if (isLocked) {
				try {
					mLock.wait();
					this.isLocked = true;
					// unlocked, all ok
					return true;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else {
				this.isLocked = true;
				// not locked, all ok
				return true;
			}
		}

		return false;
	}

	public synchronized boolean isLocked() {
		return isLocked;
	}

	public synchronized void release() {
		if (isLocked) {
			this.isLocked = false;
			try {
				mLock.notify();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
