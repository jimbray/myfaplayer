package org.stagex.danmaku.comment;

import android.graphics.Bitmap;

public interface CPI {

	public final static int EVENT_COMMENT_LOADED = 10001;

	public final static int EVENT_COMMENT_SNAPSHOT = 10002;

	public void onCommentLoadComplete(boolean success);

	public void onCommentSnapshotReady(Bitmap bitmap);

}
