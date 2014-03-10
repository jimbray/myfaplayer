package org.stagex.danmaku.comment;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class CommentDrawable extends Drawable {

	private Bitmap mBitmap = null;

	private int mWidth = -1;
	private int mHeight = -1;

	@Override
	public void draw(Canvas canvas) {
		// XXX
		if (mBitmap != null) {
			int w = mBitmap.getWidth();
			int h = mBitmap.getHeight();
			int x = (mWidth - w) >> 1;
			int y = (mHeight - h) >> 1;
			canvas.drawBitmap(mBitmap, x, y, null);
		}
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public void setAlpha(int alpha) {

	}

	@Override
	public void setColorFilter(ColorFilter filter) {

	}

	@Override
	public int getIntrinsicWidth() {
		return mWidth;
	}

	@Override
	public int getIntrinsicHeight() {
		return mHeight;
	}

	public void setBitmap(Bitmap bitmap) {
		mBitmap = bitmap;
	}

	public void setSize(int width, int height) {
		mWidth = width;
		mHeight = height;
	}

}
