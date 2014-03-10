package org.stagex.danmaku.player;

import java.util.Timer;
import java.util.TimerTask;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;

public class DefMediaPlayer extends AbsMediaPlayer implements
		MediaPlayer.OnBufferingUpdateListener,
		MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener,
		MediaPlayer.OnInfoListener, MediaPlayer.OnPreparedListener,
		MediaPlayer.OnSeekCompleteListener,
		MediaPlayer.OnVideoSizeChangedListener {

	private static final String LOGTAG = "DANMAKU-DefMediaPlayer";

	private MediaPlayer mMediaPlayer = null;

	private Timer mTimer = null;
	private TimerTask mTimerTask = new TimerTask() {
		@Override
		public void run() {
			if (mMediaPlayer == null || mOnProgressUpdateListener == null)
				return;
			if (mMediaPlayer.isPlaying()) {
				int time = mMediaPlayer.getCurrentPosition();
				int length = mMediaPlayer.getDuration();
				mOnProgressUpdateListener.onProgressUpdate(
						(AbsMediaPlayer) DefMediaPlayer.this, time, length);
			}
		}
	};

	private AbsMediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener = null;
	private AbsMediaPlayer.OnCompletionListener mOnCompletionListener = null;
	private AbsMediaPlayer.OnErrorListener mOnErrorListener = null;
	private AbsMediaPlayer.OnInfoListener mOnInfoListener = null;
	private AbsMediaPlayer.OnPreparedListener mOnPreparedListener = null;
	private AbsMediaPlayer.OnProgressUpdateListener mOnProgressUpdateListener = null;

	protected DefMediaPlayer() {
		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setOnBufferingUpdateListener(this);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnErrorListener(this);
		mMediaPlayer.setOnInfoListener(this);
		mMediaPlayer.setOnPreparedListener(this);
		mMediaPlayer.setOnSeekCompleteListener(this);
		mMediaPlayer.setOnVideoSizeChangedListener(this);
	}

	@Override
	public int getCurrentPosition() {
		Log.d(LOGTAG, "DefMediaPlayer getCurrentPosition() called");
		int ret = -1;
		try {
			ret = mMediaPlayer.getCurrentPosition();
		} catch (Exception e) {
			Log.e(LOGTAG, "getCurrentPosition()");
		}
		return ret;
	}

	@Override
	public int getDuration() {
		Log.d(LOGTAG, "DefMediaPlayer getDuration() called");
		int ret = -1;
		try {
			ret = mMediaPlayer.getDuration();
		} catch (Exception e) {
			Log.e(LOGTAG, "getDuration()");
		}
		return ret;
	}

	@Override
	public int getVideoHeight() {
		Log.d(LOGTAG, "DefMediaPlayer getVideoHeight() called");
		int ret = -1;
		try {
			ret = mMediaPlayer.getVideoHeight();
		} catch (Exception e) {
			Log.e(LOGTAG, "getVideoHeight()");
		}
		return ret;
	}

	@Override
	public int getVideoWidth() {
		Log.d(LOGTAG, "DefMediaPlayer getVideoWidth() called");
		int ret = -1;
		try {
			ret = mMediaPlayer.getVideoWidth();
		} catch (Exception e) {
			Log.e(LOGTAG, "getVideoWidth()");
		}
		return ret;
	}

	@Override
	public boolean isLooping() {
		Log.d(LOGTAG, "DefMediaPlayer isLooping() called");
		try {
			return mMediaPlayer.isLooping();
		} catch (Exception e) {
			Log.e(LOGTAG, "isLooping()");
			return false;
		}
	}

	@Override
	public boolean isPlaying() {
		Log.d(LOGTAG, "DefMediaPlayer isPlaying() called");
		try {
			return mMediaPlayer.isPlaying();
		} catch (Exception e) {
			Log.e(LOGTAG, "isPlaying()");
			return false;
		}
	}

	@Override
	public void pause() {
		Log.d(LOGTAG, "DefMediaPlayer pause() called");
		try {
			mMediaPlayer.pause();
		} catch (Exception e) {
			Log.e(LOGTAG, "pause()");
		}
	}

	@Override
	public void prepare() {
		Log.d(LOGTAG, "DefMediaPlayer prepare() called");
		try {
			mMediaPlayer.prepare();
		} catch (Exception e) {
			Log.e(LOGTAG, "prepare()");
		}
	}

	@Override
	public void prepareAsync() {
		Log.d(LOGTAG, "DefMediaPlayer prepareAsync() called");
		try {
			mMediaPlayer.prepareAsync();
		} catch (Exception e) {
			Log.e(LOGTAG, "prepareAsync()");
		}
	}

	@Override
	public void release() {
		Log.d(LOGTAG, "DefMediaPlayer release() called");
		try {
			mMediaPlayer.release();
		} catch (Exception e) {
			Log.e(LOGTAG, "release()");
		}
		sDefMediaPlayer = null;
	}

	@Override
	public void reset() {
		Log.d(LOGTAG, "DefMediaPlayer reset() called");
		stop();
		try {
			mMediaPlayer.reset();
		} catch (Exception e) {
			Log.e(LOGTAG, "reset()");
		}
	}

	@Override
	public void seekTo(int msec) {
		Log.d(LOGTAG, "DefMediaPlayer seekTo() called");
		try {
			mMediaPlayer.seekTo(msec);
		} catch (Exception e) {
			Log.e(LOGTAG, "seekTo()");
		}
	}

	@Override
	public void setDataSource(String uri) {
		Log.d(LOGTAG, "DefMediaPlayer setDataSource() called");
		try {
			if (uri.startsWith("file://"))
				uri = uri.substring(7);
			mMediaPlayer.setDataSource(uri);
			mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		} catch (Exception e) {
			Log.e(LOGTAG, "setDataSource()");
		}
	}

	@Override
	public void setDisplay(SurfaceHolder holder) {
		Log.d(LOGTAG, "DefMediaPlayer setDisplay() called");
		try {
			mMediaPlayer.setDisplay(holder);
		} catch (Exception e) {
			Log.e(LOGTAG, "setDisplay()");
		}
	}

	@Override
	public void setLooping(boolean looping) {
		Log.d(LOGTAG, "DefMediaPlayer setLooping() called");
		try {
			mMediaPlayer.setLooping(looping);
		} catch (Exception e) {
			Log.e(LOGTAG, "setLooping()");
		}
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
		Log.d(LOGTAG, "DefMediaPlayer start() called");
		try {
			mMediaPlayer.start();
			if (mTimer != null) {
				mTimer.purge();
			} else {
				mTimer = new Timer();
			}
			mTimer.schedule(mTimerTask, 0, 250);
		} catch (Exception e) {
			Log.e(LOGTAG, "start()");
		}
	}

	@Override
	public void stop() {
		Log.d(LOGTAG, "DefMediaPlayer stop() called");
		try {
			if (mTimer != null) {
				mTimer.purge();
				mTimer = null;
			}
			mMediaPlayer.stop();
		} catch (Exception e) {
			Log.e(LOGTAG, "stop()");
		}
	}

	@Override
	public int getAudioTrackCount() {
		Log.d(LOGTAG, "DefMediaPlayer getAudioTrackCount() called");
		return -1;
	}

	@Override
	public int getAudioTrack() {
		return -1;
	}

	@Override
	public void setAudioTrack(int index) {
		Log.d(LOGTAG, "DefMediaPlayer setAudioTrack() called");
	}

	@Override
	public int getSubtitleTrackCount() {
		Log.d(LOGTAG, "DefMediaPlayer getSubtitleTrackCount() called");
		return -1;
	}

	@Override
	public int getSubtitleTrack() {
		return -1;
	}

	@Override
	public void setSubtitleTrack(int index) {
		Log.d(LOGTAG, "DefMediaPlayer setSubtitleTrack() called");
	}

	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		Log.d(LOGTAG,
				String.format("MediaPlayer onBufferingUpdate %d", percent));
		if (mOnBufferingUpdateListener != null) {
			mOnBufferingUpdateListener.onBufferingUpdate(this, percent);
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		Log.d(LOGTAG, "MediaPlayer onCompletion");
		if (mOnCompletionListener != null) {
			mOnCompletionListener.onCompletion(this);
		}
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.d(LOGTAG, String.format("MediaPlayer onError %d %d", what, extra));
		if (mOnErrorListener != null) {
			return mOnErrorListener.onError(this, what, extra);
		}
		return false;
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		Log.d(LOGTAG, String.format("MediaPlayer onInfo %d %d", what, extra));
		if (mOnInfoListener != null) {
			return mOnInfoListener.onInfo(this, what, extra);
		}
		return true;
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		Log.d(LOGTAG, "MediaPlayer onPrepared");
		if (mOnPreparedListener != null) {
			mOnPreparedListener.onPrepaired(this);
		}
	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		Log.d(LOGTAG, "MediaPlayer onSeekComplete");
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		Log.d(LOGTAG, String.format("MediaPlayer onVideoSizeChanged %d %d",
				width, height));
	}

}
