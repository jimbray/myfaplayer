package org.stagex.danmaku.comment;

import java.util.LinkedList;
import java.util.ListIterator;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.util.Log;

public class PositionManager {

	private int mStageWidth = -1;
	private int mStageHeight = -1;

	private LinkedList<Position> mFlyUsed = new LinkedList<Position>();
	private LinkedList<Position> mFlyList = new LinkedList<Position>();
	private LinkedList<Position> mTopUsed = new LinkedList<Position>();
	private LinkedList<Position> mBotUsed = new LinkedList<Position>();

	protected static void setTopPosition(LinkedList<Position> list, Position p,
			int height) {
		int n = list.size();
		if (n > 0) {
			// check if there is space before first
			Position first = list.getFirst();
			if (first.y >= p.height) {
				p.y = 0;
				list.addFirst(p);
				return;
			}
			// check if there is space between two
			ListIterator<Position> it = (ListIterator<Position>) list
					.iterator();
			Position p1 = null, p2 = it.next();
			for (int i = 0; i < n - 1; i++) {
				p1 = p2;
				p2 = it.next();
				int space = p2.y - p1.y - p1.height;
				if (space >= p.height) {
					p.y = p1.y + p1.height;
					it.add(p);
					return;
				}
			}
			// check if there is space after last
			Position last = list.getLast();
			if (height - last.y - last.height >= p.height) {
				p.y = last.y + last.height;
				list.addLast(p);
				return;
			} else {
				// XXX:
				p.y = (first.y + first.height + p.height) % height;
				list.addLast(p);
				return;
			}
		} else {
			p.y = 0;
			list.addFirst(p);
		}
	}

	protected static void setBotPosition(LinkedList<Position> l, Position p,
			int height) {
	}

	public PositionManager() {

	}

	public int count() {
		return (mFlyList.size() + mTopUsed.size() + mBotUsed.size());
	}

	public void feed(Comment c) {
		if (mStageWidth < 0 || mStageHeight < 0) {
			switch (c.site) {
			case Comment.SITE_ACFUN: {
				mStageWidth = Comment.STAGE_WIDTH_ACFUN;
				mStageHeight = Comment.STAGE_HEIGHT_ACFUN;
				break;
			}
			case Comment.SITE_BILIBILI: {
				mStageWidth = Comment.STAGE_WIDTH_BILIBILI;
				mStageHeight = Comment.STAGE_HEIGHT_BILIBILI;
				break;
			}
			case Comment.SITE_ICHIBA: {
				mStageWidth = Comment.STAGE_WIDTH_ICHIBA;
				mStageHeight = Comment.STAGE_HEIGHT_ICHIBA;
				break;
			}
			default: {
				assert (false);
				break;
			}
			}
		}
		Position p = new Position();
		p.time = c.time;
		p.id = c.getHashString();
		p.duration = c.getDuration();
		p.width = c.getWidth();
		p.height = c.getHeight();
		switch (c.type) {
		case Comment.TYPE_FLY: {
			p.x = mStageWidth;
			setTopPosition(mFlyUsed, p, mStageHeight);
			mFlyList.add(p);
			break;
		}
		case Comment.TYPE_TOP: {
			p.x = (mStageWidth - p.width) / 2;
			setTopPosition(mTopUsed, p, mStageHeight);
			break;
		}
		case Comment.TYPE_BOT: {
			p.x = (mStageWidth - p.width) / 2;
			setBotPosition(mBotUsed, p, mStageHeight);
			break;
		}
		default: {
			break;
		}
		}
	}

	public void play(long time) {
		ListIterator<Position> it = null;
		// calculate and clean fly positions
		it = (ListIterator<Position>) mFlyUsed.iterator();
		while (it.hasNext()) {
			Position p = it.next();
			if ((time - p.time) * (p.width + mStageWidth) / p.duration > p.width) {
				it.remove();
				continue;
			}
		}
		it = (ListIterator<Position>) mFlyList.iterator();
		while (it.hasNext()) {
			Position p = it.next();
			if (p.time + p.duration < time) {
				it.remove();
				continue;
			}
			p.x = (int) (mStageWidth - (time - p.time)
					* (p.width + mStageWidth) / (p.duration));
		}
		// clean top and bottom positions
		it = (ListIterator<Position>) mTopUsed.iterator();
		while (it.hasNext()) {
			Position p = it.next();
			if (p.time + p.duration < time) {
				it.remove();
			}
		}
		it = (ListIterator<Position>) mBotUsed.iterator();
		while (it.hasNext()) {
			Position p = it.next();
			if (p.time + p.duration < time) {
				it.remove();
			}
		}
	}

	public void reset() {
		mFlyUsed.clear();
		mFlyList.clear();
		mTopUsed.clear();
		mBotUsed.clear();
	}

	public Bitmap snapshot() {
		if (mStageWidth < 0 || mStageHeight < 0) {
			return null;
		}
		Bitmap bitmap = Bitmap.createBitmap(mStageWidth, mStageHeight,
				Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		Log.d("faplayer", "==== begin snapshot ====");
		canvas.drawColor(0x00000000);
		for (Position p : mFlyList) {
			Log.d("faplayer",
					String.format("%d, %d: %s", p.x, p.y,
							Comment.getComment(p.id).toString()));
			Bitmap b = Comment.getBitmap(p.id);
			canvas.drawBitmap(b, p.x, p.y, null);
		}
		for (Position p : mTopUsed) {
			Log.d("faplayer",
					String.format("%d, %d: %s", p.x, p.y,
							Comment.getComment(p.id).toString()));
			Bitmap b = Comment.getBitmap(p.id);
			canvas.drawBitmap(b, p.x, p.y, null);
		}
		for (Position p : mBotUsed) {
			Log.d("faplayer",
					String.format("%d, %d: %s", p.x, p.y,
							Comment.getComment(p.id).toString()));
			Bitmap b = Comment.getBitmap(p.id);
			canvas.drawBitmap(b, p.x, p.y, null);
		}
		Log.d("faplayer", "====  end snapshot  ====");
		return bitmap;
	}

}
