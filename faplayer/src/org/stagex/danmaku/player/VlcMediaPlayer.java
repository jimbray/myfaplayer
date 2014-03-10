package org.stagex.danmaku.player;

import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

public class VlcMediaPlayer extends AbsMediaPlayer {

	static {
		System.loadLibrary("vlccore");
	}

	private static final String LOGTAG = "DANMAKU-VlcMediaPlayer";

	protected AbsMediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener = null;
	protected AbsMediaPlayer.OnCompletionListener mOnCompletionListener = null;
	protected AbsMediaPlayer.OnErrorListener mOnErrorListener = null;
	protected AbsMediaPlayer.OnInfoListener mOnInfoListener = null;
	protected AbsMediaPlayer.OnPreparedListener mOnPreparedListener = null;
	protected AbsMediaPlayer.OnProgressUpdateListener mOnProgressUpdateListener = null;

	/* used by the native side */
	protected int mLibVlcInstance = 0;
	protected int mLibVlcMediaPlayer = 0;
	protected int mLibVlcMedia = 0;

	/*  */
	protected native void nativeAttachSurface(Surface s);

	protected native void nativeDetachSurface();

	/* */
	protected native void nativeCreate();

	protected native void nativeRelease();

	protected native int nativeGetCurrentPosition();

	protected native int nativeGetDuration();

	protected native int nativeGetVideoHeight();

	protected native int nativeGetVideoWidth();

	protected native boolean nativeIsLooping();

	protected native boolean nativeIsPlaying();

	protected native void nativePause();

	protected native void nativePrepare();

	protected native void nativePrepareAsync();

	protected native void nativeSeekTo(int msec);

	protected native void nativeSetDataSource(String path);

	// protected native void nativeSetDisplay(SurfaceHolder holder);

	protected native void nativeSetLooping(boolean looping);

	protected native void nativeStart();

	protected native void nativeStop();

	@SuppressWarnings("unused")
	private class VlcEvent {
		/* see native side */
		public final static int MediaMetaChanged = 0;
		public final static int MediaSubItemAdded = 1;
		public final static int MediaDurationChanged = 2;
		public final static int MediaParsedChanged = 3;
		public final static int MediaFreed = 4;
		public final static int MediaStateChanged = 5;
		public final static int MediaPlayerMediaChanged = 256;
		public final static int MediaPlayerNothingSpecial = 257;
		public final static int MediaPlayerOpening = 258;
		public final static int MediaPlayerBuffering = 259;
		public final static int MediaPlayerPlaying = 260;
		public final static int MediaPlayerPaused = 261;
		public final static int MediaPlayerStopped = 262;
		public final static int MediaPlayerForward = 263;
		public final static int MediaPlayerBackward = 264;
		public final static int MediaPlayerEndReached = 265;
		public final static int MediaPlayerEncounteredError = 266;
		public final static int MediaPlayerTimeChanged = 267;
		public final static int MediaPlayerPositionChanged = 268;
		public final static int MediaPlayerSeekableChanged = 269;
		public final static int MediaPlayerPausableChanged = 270;
		public final static int MediaPlayerTitleChanged = 271;
		public final static int MediaPlayerSnapshotTaken = 272;
		public final static int MediaPlayerLengthChanged = 273;
		/* the variables we received */
		public int eventType = -1;
		public boolean booleanValue = false;
		public int intValue = -1;
		public long longValue = -1;
		public float floatValue = -1.0f;
		public String stringValue = null;
	}

	/* called by native side */
	private void onVlcEvent(VlcEvent ev) {
		Log.d(LOGTAG, String.format("received vlc event %d", ev.eventType));
		switch (ev.eventType) {
		case VlcEvent.MediaParsedChanged: {
			if (!ev.booleanValue) {
				if (mOnErrorListener != null) {
					mOnErrorListener.onError(this,
							MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
				}
			} else {
				if (mOnPreparedListener != null) {
					mOnPreparedListener.onPrepaired(this);
				}
			}
			break;
		}
		case VlcEvent.MediaPlayerOpening: {
			if (mOnBufferingUpdateListener != null) {
				mOnBufferingUpdateListener.onBufferingUpdate(this, 0);
			}
			break;
		}
		case VlcEvent.MediaPlayerBuffering: {
			if (mOnBufferingUpdateListener != null) {
				int percent = (int) (ev.floatValue * 100);
				mOnBufferingUpdateListener.onBufferingUpdate(this, percent);
			}
			break;
		}
		case VlcEvent.MediaPlayerEndReached: {
			if (mOnCompletionListener != null) {
				mOnCompletionListener.onCompletion(this);
			}
			break;
		}
		case VlcEvent.MediaPlayerEncounteredError: {
			if (mOnErrorListener != null) {
				mOnErrorListener.onError(this, MediaPlayer.MEDIA_ERROR_UNKNOWN,
						0);
			}
			break;
		}
		case VlcEvent.MediaPlayerTimeChanged: {
			if (mOnProgressUpdateListener != null) {
				mOnProgressUpdateListener.onProgressUpdate(this,
						(int) ev.longValue, -1);
			}
			break;
		}
		case VlcEvent.MediaPlayerSeekableChanged: {
			if (!ev.booleanValue) {
				if (mOnInfoListener != null) {
					mOnInfoListener.onInfo(this,
							MediaPlayer.MEDIA_INFO_NOT_SEEKABLE, 0);
				}
			}
			break;
		}
		case VlcEvent.MediaPlayerLengthChanged: {
			if (mOnProgressUpdateListener != null) {
				mOnProgressUpdateListener.onProgressUpdate(
						(AbsMediaPlayer) this, -1, (int) ev.longValue);
			}
			break;
		}
		}
	}

	protected VlcMediaPlayer() {
		nativeCreate();
	}

	@Override
	public int getCurrentPosition() {
		Log.d(LOGTAG, "VlcMediaPlayer getCurrentPosition() called");
		return nativeGetCurrentPosition();
	}

	@Override
	public int getDuration() {
		Log.d(LOGTAG, "VlcMediaPlayer getDuration() called");
		return nativeGetDuration();
	}

	@Override
	public int getVideoHeight() {
		Log.d(LOGTAG, "VlcMediaPlayer getVideoHeight() called");
		return nativeGetVideoHeight();
	}

	@Override
	public int getVideoWidth() {
		Log.d(LOGTAG, "VlcMediaPlayer getVideoWidth() called");
		return nativeGetVideoHeight();
	}

	@Override
	public boolean isLooping() {
		Log.d(LOGTAG, "VlcMediaPlayer isLooping() called");
		return nativeIsLooping();
	}

	@Override
	public boolean isPlaying() {
		Log.d(LOGTAG, "VlcMediaPlayer isPlaying() called");
		return nativeIsPlaying();
	}

	@Override
	public void pause() {
		Log.d(LOGTAG, "VlcMediaPlayer pause() called");
		nativePause();
	}

	@Override
	public void prepare() {
		Log.d(LOGTAG, "VlcMediaPlayer prepare() called");
		nativePrepare();
	}

	@Override
	public void prepareAsync() {
		Log.d(LOGTAG, "VlcMediaPlayer prepareAsync() called");
		nativePrepareAsync();
	}

	@Override
	public void release() {
		Log.d(LOGTAG, "VlcMediaPlayer release() called");
		nativeRelease();
		sVlcMediaPlayer = null;
	}

	@Override
	public void reset() {
		Log.d(LOGTAG, "VlcMediaPlayer reset() called");

	}

	@Override
	public void seekTo(int msec) {
		Log.d(LOGTAG, "VlcMediaPlayer seekTo() called");
		nativeSeekTo(msec);
	}

	@Override
	public void setDataSource(String path) {
		Log.d(LOGTAG, "VlcMediaPlayer setDataSource() called");
		nativeSetDataSource(path);
	}

	@Override
	public void setDisplay(SurfaceHolder holder) {
		Log.d(LOGTAG, "VlcMediaPlayer setDisplay() called");
		holder.addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				nativeAttachSurface(holder.getSurface());
			}

			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				nativeAttachSurface(holder.getSurface());
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				nativeDetachSurface();
			}
		});
		holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
		holder.setFormat(PixelFormat.RGB_565);
	}

	@Override
	public void setLooping(boolean looping) {
		Log.d(LOGTAG, "VlcMediaPlayer setLooping() called");
		nativeSetLooping(looping);
	}

	@Override
	public void setOnBufferingUpdateListener(
			AbsMediaPlayer.OnBufferingUpdateListener listener) {
		mOnBufferingUpdateListener = listener;
	}

	@Override
	public void setOnCompletionListener(
			AbsMediaPlayer.OnCompletionListener listener) {
		mOnCompletionListener = listener;
	}

	@Override
	public void setOnErrorListener(AbsMediaPlayer.OnErrorListener listener) {
		mOnErrorListener = listener;
	}

	@Override
	public void setOnInfoListener(AbsMediaPlayer.OnInfoListener listener) {
		mOnInfoListener = listener;
	}

	@Override
	public void setOnPreparedListener(AbsMediaPlayer.OnPreparedListener listener) {
		mOnPreparedListener = listener;
	}

	@Override
	public void setOnProgressUpdateListener(
			AbsMediaPlayer.OnProgressUpdateListener listener) {
		mOnProgressUpdateListener = listener;
	}

	@Override
	public void start() {
		Log.d(LOGTAG, "VlcMediaPlayer start() called");
		nativeStart();
	}

	@Override
	public void stop() {
		Log.d(LOGTAG, "VlcMediaPlayer stop() called");
		nativeStop();
	}

	@Override
	public int getAudioTrackCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getAudioTrack() {
		return 0;
	}

	@Override
	public void setAudioTrack(int index) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getSubtitleTrackCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getSubtitleTrack() {
		return 0;
	}

	@Override
	public void setSubtitleTrack(int index) {
		// TODO Auto-generated method stub

	}

}
