package org.stagex.danmaku.comment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.stagex.danmaku.site.CommentParserFactory;

import android.graphics.Bitmap;
import android.util.Log;

public class CommentManager {

	private CPI mCallbackHandler = null;

	private boolean mExit = false;
	private boolean mPause = true;
	private boolean mSeek = true;
	private Object mSeekLock = new Object();

	private ReentrantLock mPauseLock = new ReentrantLock();
	private Condition mPauseCond = mPauseLock.newCondition();

	private int mCommentIndex = 0;
	private ArrayList<Comment> mCommentList = new ArrayList<Comment>();

	private PositionManager mPositionManager = new PositionManager();

	private long mTimeStart = -1;
	private long mTimeOffset = 0;

	private Thread mRendererThread = null;

	private class RendererThread extends Thread {
		@Override
		public void run() {
			mTimeStart = System.currentTimeMillis();
			while (!mExit) {
				// fps
				long rendererBegin = System.currentTimeMillis();
				// wait if it is paused
				boolean pauseTest = false;
				long pauseBegin = 0, pauseEnd = 0;
				mPauseLock.lock();
				pauseTest = mPause;
				if (pauseTest) {
					pauseBegin = System.currentTimeMillis();
				}
				while (mPause) {
					try {
						mPauseCond.await();
					} catch (InterruptedException e) {
					}
				}
				if (pauseTest) {
					pauseEnd = System.currentTimeMillis();
					mTimeStart += (pauseEnd - pauseBegin);
				}
				mPauseLock.unlock();
				// check whether exit is requested
				if (mExit) {
					break;
				}
				// check whether seek is requested
				boolean seekTest = false;
				synchronized (mSeekLock) {
					seekTest = mSeek;
					if (mSeek) {
						mTimeStart = System.currentTimeMillis() - mTimeOffset;
						mTimeOffset = 0;
						mSeek = false;
					}
				}
				// current play time
				long currentTime = System.currentTimeMillis() - mTimeStart;
				// process positions
				if (seekTest) {
					mPositionManager.reset();
				}
				int start = seekTest ? 0 : mCommentIndex;
				for (int i = start; i < mCommentList.size(); i++) {
					Comment comment = mCommentList.get(i);
					if (comment.time > currentTime) {
						mCommentIndex = i;
						break;
					}
					if (currentTime >= comment.time
							&& currentTime < comment.time
									+ comment.getDuration()) {
						mPositionManager.feed(comment);
					}
				}
				mPositionManager.play(currentTime);
				// let's draw the whole screen
				Log.d("faplayer", String.format("snapshot: %d", currentTime));
				Bitmap bitmap = mPositionManager.snapshot();
				if (bitmap != null && mCallbackHandler != null) {
					mCallbackHandler.onCommentSnapshotReady(bitmap);
				}
				// done, and check whether we can sleep
				long rendererEnd = System.currentTimeMillis();
				long rendererEclipsed = (rendererEnd - rendererBegin);
				if (pauseTest) {
					rendererEclipsed -= (pauseEnd - pauseBegin);
				}
				Log.d("faplayer",
						String.format("rendered in %d ms", rendererEclipsed));
				long timeToSleep = (1000 / 16) - rendererEclipsed;
				if (timeToSleep > 0) {
					try {
						Thread.sleep(timeToSleep);
					} catch (InterruptedException e) {
					}
				}
			}
		}
	}

	public CommentManager() {

	}

	public void setCallbackHandler(CPI handler) {
		mCallbackHandler = handler;
	}

	public void open(String uri) {
		close();
		ArrayList<Comment> yeah = CommentParserFactory.parse(uri);
		if (yeah == null || yeah.size() == 0) {
			mCallbackHandler.onCommentLoadComplete(false);
			return;
		}
		mCommentList = yeah;
		Comparator<Comment> comparator = new Comparator<Comment>() {
			@Override
			public int compare(Comment c1, Comment c2) {
				return (int) (c1.time - c2.time);
			}
		};
		Collections.sort(mCommentList, comparator);
		// for (Comment c : mCommentList) {
		// Log.d("faplayer", c.toString());
		// }
		mCallbackHandler.onCommentLoadComplete(true);
		mRendererThread = new RendererThread();
		mRendererThread.start();
	}

	public void seek(long time) {
		synchronized (mSeekLock) {
			mSeek = true;
			mTimeOffset = time;
		}
	}

	public void pause() {
		mPauseLock.lock();
		mPause = true;
		mPauseCond.signal();
		mPauseLock.unlock();
	}

	public void play() {
		mPauseLock.lock();
		mPause = false;
		mPauseCond.signal();
		mPauseLock.unlock();
	}

	public void close() {
		if (mRendererThread != null) {
			mExit = true;
			play();
			if (mRendererThread.isAlive()) {
				try {
					mRendererThread.join();
				} catch (InterruptedException e) {
				}
			}
			mRendererThread = null;
		}
	}
}
