package org.stagex.danmaku.activity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.stagex.danmaku.R;
import org.stagex.danmaku.comment.CPI;
import org.stagex.danmaku.comment.CommentDrawable;
import org.stagex.danmaku.comment.CommentManager;
import org.stagex.danmaku.helper.SystemUtility;
import org.stagex.danmaku.player.AbsMediaPlayer;
import org.stagex.danmaku.player.DefMediaPlayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class PlayerActivity extends Activity implements
		AbsMediaPlayer.OnBufferingUpdateListener,
		AbsMediaPlayer.OnCompletionListener, AbsMediaPlayer.OnErrorListener,
		AbsMediaPlayer.OnInfoListener, AbsMediaPlayer.OnPreparedListener,
		AbsMediaPlayer.OnProgressUpdateListener, OnTouchListener,
		OnClickListener, OnSeekBarChangeListener {

	static final String LOGTAG = "DANMAKU-PlayerActivity";

	/* */
	private static final int DEF_SURFACE_CREATED = 0x1001;
	private static final int DEF_SURFACE_CHANGED = 0x1002;
	private static final int DEF_SURFACE_DESTROYED = 0x1003;
	private static final int VLC_SURFACE_CREATED = 0x2001;
	private static final int VLC_SURFACE_CHANGED = 0x2002;
	private static final int VLC_SURFACE_DESTROYED = 0x2003;

	private static final int MEDIA_PLAYER_BUFFERING_UPDATE = 0x4001;
	private static final int MEDIA_PLAYER_COMPLETION = 0x4002;
	private static final int MEDIA_PLAYER_ERROR = 0x4003;
	private static final int MEDIA_PLAYER_INFO = 0x4004;
	private static final int MEDIA_PLAYER_PREPARED = 0x4005;
	private static final int MEDIA_PLAYER_PROGRESS_UPDATE = 0x4006;

	/* the media player */
	private AbsMediaPlayer mMediaPlayer = null;

	/**/
	private ArrayList<String> mPlayListArray = null;
	private int mPlayListSelected = -1;

	/* GUI evnet handler */
	private Handler mEventHandler;

	/* player misc */
	private ProgressBar mProgressBarPreparing;

	/* player controls */
	private TextView mTextViewTime;
	private SeekBar mSeekBarProgress;
	private TextView mTextViewLength;
	private ImageButton mImageButtonToggleMessage;
	private ImageButton mImageButtonSwitchAudio;
	private ImageButton mImageButtonSwitchSubtitle;
	private ImageButton mImageButtonPrevious;
	private ImageButton mImageButtonTogglePlay;
	private ImageButton mImageButtonNext;
	private ImageButton mImageButtonSwitchAspectRatio;
	private ImageButton mImageButtonToggleFullScreen;

	private LinearLayout mLinearLayoutControlBar;

	/* player video */
	private View mViewMessage;
	private SurfaceView mSurfaceViewDef;
	private SurfaceHolder mSurfaceHolderDef;
	private SurfaceView mSurfaceViewVlc;
	private SurfaceHolder mSurfaceHolderVlc;

	private int mTime = -1;
	private int mLength = -1;
	private boolean mCanPause = true;
	private boolean mCanSeek = true;

	private boolean mFullScreen = false;
	private int mAspectRatio = 0;

	private int mVideoWidth = -1;
	private int mVideoHeight = -1;

	private int mVideoSurfaceWidth = -1;
	private int mVideoSurfaceHeight = -1;

	private int mVideoPlaceX = 0;
	private int mVideoPlaceY = 0;
	private int mVideoPlaceW = 0;
	private int mVideoPlaceH = 0;

	private int mAudioTrackIndex = 0;
	private int mAudioTrackCount = 0;
	private int mSubtitleTrackIndex = 0;
	private int mSubtitleTrackCount = 0;

	protected void initializeEvents() {
		mEventHandler = new Handler() {
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case DEF_SURFACE_CREATED: {
					Log.d(LOGTAG, "def surface created ");
					createMediaPlayer(true,
							mPlayListArray.get(mPlayListSelected),
							mSurfaceHolderDef);
					break;
				}
				case DEF_SURFACE_CHANGED: {
					Log.d(LOGTAG, "def surface changed");
					break;
				}
				case DEF_SURFACE_DESTROYED: {
					Log.d(LOGTAG, "def surface destroyed");
					destroyMediaPlayer(true);
					break;
				}
				case VLC_SURFACE_CREATED: {
					Log.d(LOGTAG, "vlc surface created ");
					createMediaPlayer(false,
							mPlayListArray.get(mPlayListSelected),
							mSurfaceHolderVlc);
					break;
				}
				case VLC_SURFACE_CHANGED: {
					Log.d(LOGTAG, "vlc surface changed");
					break;
				}
				case VLC_SURFACE_DESTROYED: {
					Log.d(LOGTAG, "vlc surface destroyed");
					destroyMediaPlayer(false);
					break;
				}
				case MEDIA_PLAYER_BUFFERING_UPDATE: {
					mProgressBarPreparing
							.setVisibility(msg.arg1 < 100 ? View.VISIBLE
									: View.GONE);
					break;
				}
				case MEDIA_PLAYER_COMPLETION: {
					break;
				}
				case MEDIA_PLAYER_ERROR: {
					/* fallback to VlcMediaPlayer if possible */
					if (mMediaPlayer.getClass().getName()
							.equals(DefMediaPlayer.class.getName())) {
						selectMediaPlayer(
								mPlayListArray.get(mPlayListSelected), true);
					} else {
						/* really won't play */
					}
					break;
				}
				case MEDIA_PLAYER_INFO: {
					if (msg.arg1 == MediaPlayer.MEDIA_INFO_NOT_SEEKABLE) {
						mCanSeek = false;
					}
					break;
				}
				case MEDIA_PLAYER_PREPARED: {
					mMediaPlayer.start();
					break;
				}
				case MEDIA_PLAYER_PROGRESS_UPDATE: {
					int length = msg.arg2;
					if (length >= 0) {
						mLength = length;
						mTextViewLength.setText(SystemUtility
								.getTimeString(mLength));
						mSeekBarProgress.setMax(mLength);
					}
					int time = msg.arg1;
					if (time >= 0) {
						mTime = time;
						mTextViewTime.setText(SystemUtility
								.getTimeString(mTime));
						mSeekBarProgress.setProgress(mTime);
					}
					break;
				}
				default:
					break;
				}
			}
		};
	}

	protected void initializeControls() {
		mSurfaceViewVlc = (SurfaceView) findViewById(R.id.player_surface_vlc);
		mSurfaceHolderVlc = mSurfaceViewVlc.getHolder();
		mSurfaceHolderVlc.addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				Message msg = new Message();
				msg.what = VLC_SURFACE_CREATED;
				msg.obj = holder;
				mEventHandler.dispatchMessage(msg);
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				Message msg = new Message();
				msg.what = VLC_SURFACE_CHANGED;
				msg.obj = holder;
				msg.arg1 = width;
				msg.arg2 = height;
				mEventHandler.dispatchMessage(msg);
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				Message msg = new Message();
				msg.what = VLC_SURFACE_DESTROYED;
				msg.obj = holder;
				mEventHandler.dispatchMessage(msg);
			}
		});
		mSurfaceViewVlc.setOnTouchListener(this);
		mSurfaceViewDef = (SurfaceView) findViewById(R.id.player_surface_def);
		mSurfaceViewDef.setOnTouchListener(this);
		mSurfaceHolderDef = mSurfaceViewDef.getHolder();
		mSurfaceHolderDef.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mSurfaceHolderDef.addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				Message msg = new Message();
				msg.what = DEF_SURFACE_CREATED;
				msg.obj = holder;
				mEventHandler.dispatchMessage(msg);
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				Message msg = new Message();
				msg.what = DEF_SURFACE_CHANGED;
				msg.obj = holder;
				msg.arg1 = width;
				msg.arg2 = height;
				mEventHandler.dispatchMessage(msg);
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				Message msg = new Message();
				msg.what = DEF_SURFACE_DESTROYED;
				msg.obj = holder;
				mEventHandler.dispatchMessage(msg);
			}
		});
		mViewMessage = (View) findViewById(R.id.player_view_message);
		mViewMessage.setBackgroundDrawable(new CommentDrawable());
		mViewMessage.setOnTouchListener(this);

		mTextViewTime = (TextView) findViewById(R.id.player_text_position);
		mSeekBarProgress = (SeekBar) findViewById(R.id.player_seekbar_progress);
		mSeekBarProgress.setOnSeekBarChangeListener(this);
		mTextViewLength = (TextView) findViewById(R.id.player_text_length);
		mImageButtonToggleMessage = (ImageButton) findViewById(R.id.player_button_toggle_message);
		mImageButtonToggleMessage.setOnClickListener(this);
		mImageButtonSwitchAudio = (ImageButton) findViewById(R.id.player_button_switch_audio);
		mImageButtonSwitchAudio.setOnClickListener(this);
		mImageButtonSwitchSubtitle = (ImageButton) findViewById(R.id.player_button_switch_subtitle);
		mImageButtonSwitchSubtitle.setOnClickListener(this);
		mImageButtonPrevious = (ImageButton) findViewById(R.id.player_button_previous);
		mImageButtonPrevious.setOnClickListener(this);
		mImageButtonTogglePlay = (ImageButton) findViewById(R.id.player_button_toggle_play);
		mImageButtonTogglePlay.setOnClickListener(this);
		mImageButtonNext = (ImageButton) findViewById(R.id.player_button_next);
		mImageButtonNext.setOnClickListener(this);
		mImageButtonSwitchAspectRatio = (ImageButton) findViewById(R.id.player_button_switch_aspect_ratio);
		mImageButtonSwitchAspectRatio.setOnClickListener(this);
		mImageButtonToggleFullScreen = (ImageButton) findViewById(R.id.player_button_toggle_full_screen);
		mImageButtonToggleFullScreen.setOnClickListener(this);

		mLinearLayoutControlBar = (LinearLayout) findViewById(R.id.player_control_bar);

		mProgressBarPreparing = (ProgressBar) findViewById(R.id.player_prepairing);
	}

	protected void initializeData() {
		Intent intent = getIntent();
		String action = intent.getAction();
		if (action != null && action.equals(Intent.ACTION_VIEW)) {
			String one = intent.getDataString();
			mPlayListSelected = 0;
			mPlayListArray = new ArrayList<String>();
			mPlayListArray.add(one);
		} else {
			mPlayListSelected = intent.getIntExtra("selected", 0);
			mPlayListArray = intent.getStringArrayListExtra("playlist");
		}
		if (mPlayListArray == null || mPlayListArray.size() == 0) {
			Log.e(LOGTAG, "initializeData(): empty");
			finish();
			return;
		}
	}

	protected void initializeInterface() {
		/* */
		mSurfaceViewVlc.setVisibility(View.GONE);
		mSurfaceViewDef.setVisibility(View.GONE);
		mViewMessage.setVisibility(View.GONE);
		/* */
		mImageButtonToggleMessage.setVisibility(View.GONE);
		mImageButtonSwitchAudio.setVisibility(View.GONE);
		mImageButtonSwitchSubtitle.setVisibility(View.GONE);
		mImageButtonPrevious
				.setVisibility((mPlayListArray.size() == 1) ? View.GONE
						: View.VISIBLE);
		mImageButtonTogglePlay.setVisibility(View.VISIBLE);
		mImageButtonNext.setVisibility((mPlayListArray.size() == 1) ? View.GONE
				: View.VISIBLE);
		/* not implemented yet */
		mImageButtonSwitchAspectRatio.setVisibility(View.GONE);
		mImageButtonToggleFullScreen.setVisibility(View.GONE);
		/* */
		mLinearLayoutControlBar.setVisibility(View.GONE);
		/* */
		mProgressBarPreparing.setVisibility(View.GONE);
	}

	protected void selectMediaPlayer(String uri, boolean forceVlc) {
		/* TODO: do this through configuration */
		boolean useDefault = true;
		int indexOfDot = uri.lastIndexOf('.');
		if (indexOfDot != -1) {
			String extension = uri.substring(indexOfDot).toLowerCase();
			if (extension.compareTo(".flv") == 0
					|| extension.compareTo(".hlv") == 0
					|| extension.compareTo(".m3u8") == 0
					|| extension.compareTo(".mkv") == 0
					|| extension.compareTo(".rm") == 0
					|| extension.compareTo(".rmvb") == 0) {
				useDefault = false;
			}
		}
		if (forceVlc) {
			useDefault = false;
		}
		mSurfaceViewDef.setVisibility(useDefault ? View.VISIBLE : View.GONE);
		mSurfaceViewVlc.setVisibility(useDefault ? View.GONE : View.VISIBLE);
	}

	protected void createMediaPlayer(boolean useDefault, String uri,
			SurfaceHolder holder) {
		Log.d(LOGTAG, "createMediaPlayer() " + uri);
		mMediaPlayer = AbsMediaPlayer.getMediaPlayer(useDefault);
		mMediaPlayer.setOnBufferingUpdateListener(this);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnErrorListener(this);
		mMediaPlayer.setOnInfoListener(this);
		mMediaPlayer.setOnPreparedListener(this);
		mMediaPlayer.setOnProgressUpdateListener(this);
		mMediaPlayer.reset();
		mMediaPlayer.setDisplay(holder);
		mMediaPlayer.setDataSource(uri);
		mMediaPlayer.prepareAsync();
	}

	protected void destroyMediaPlayer(boolean isDefault) {
		if (mMediaPlayer == null) {
			return;
		}
		boolean testDefault = mMediaPlayer.getClass().getName()
				.equals(DefMediaPlayer.class.getName());
		if (isDefault == testDefault) {
			Log.d(LOGTAG, "destroyMediaPlayer()");
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initializeEvents();
		setContentView(R.layout.player);
		initializeControls();
		initializeData();
		initializeInterface();
		selectMediaPlayer(mPlayListArray.get(mPlayListSelected), false);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer.release();
		}
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onStop() {
		super.onStop();
		if (mMediaPlayer != null) {
			mMediaPlayer.pause();
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		/* TODO: enable this after prepared */
		int action = event.getAction();
		if (action == MotionEvent.ACTION_DOWN) {
			int visibility = mLinearLayoutControlBar.getVisibility();
			if (visibility != View.VISIBLE) {
				mLinearLayoutControlBar.setVisibility(View.VISIBLE);
			} else {
				mLinearLayoutControlBar.setVisibility(View.GONE);
			}
			return true;
		}
		return false;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		switch (id) {
		case R.id.player_button_toggle_message: {
			int visibility = mViewMessage.getVisibility();
			mViewMessage.setVisibility(visibility == View.VISIBLE ? View.GONE
					: View.VISIBLE);
			break;
		}
		case R.id.player_button_switch_audio: {

			break;
		}
		case R.id.player_button_switch_subtitle: {

			break;
		}
		case R.id.player_button_previous: {

			break;
		}
		case R.id.player_button_toggle_play: {
			if (mCanPause) {
				boolean playing = mMediaPlayer.isPlaying();
				if (playing) {
					mMediaPlayer.pause();
				} else {
					mMediaPlayer.start();
				}
				String name = String.format("btn_play_%d", !playing ? 1 : 0);
				int resouce = SystemUtility.getDrawableId(name);
				mImageButtonTogglePlay.setBackgroundResource(resouce);
			}
			break;
		}
		case R.id.player_button_next: {
			break;
		}
		case R.id.player_button_switch_aspect_ratio: {

			break;
		}
		case R.id.player_button_toggle_full_screen: {

			break;
		}
		default:
			break;
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {

	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		int id = seekBar.getId();
		switch (id) {
		case R.id.player_seekbar_progress: {
			if (mCanSeek && mLength > 0) {
				int position = seekBar.getProgress();
				mMediaPlayer.seekTo(position);
			}
			break;
		}
		default:
			break;
		}
	}

	@Override
	public void onBufferingUpdate(AbsMediaPlayer mp, int percent) {
		Message msg = new Message();
		msg.what = MEDIA_PLAYER_BUFFERING_UPDATE;
		msg.arg1 = percent;
		mEventHandler.sendMessage(msg);
	}

	@Override
	public void onCompletion(AbsMediaPlayer mp) {
		Message msg = new Message();
		msg.what = MEDIA_PLAYER_COMPLETION;
		mEventHandler.sendMessage(msg);
	}

	@Override
	public boolean onError(AbsMediaPlayer mp, int what, int extra) {
		Message msg = new Message();
		msg.what = MEDIA_PLAYER_ERROR;
		msg.arg1 = what;
		msg.arg2 = extra;
		mEventHandler.sendMessage(msg);
		return true;
	}

	@Override
	public boolean onInfo(AbsMediaPlayer mp, int what, int extra) {
		Message msg = new Message();
		msg.what = MEDIA_PLAYER_INFO;
		msg.arg1 = what;
		msg.arg2 = extra;
		mEventHandler.sendMessage(msg);
		return true;
	}

	@Override
	public void onPrepaired(AbsMediaPlayer mp) {
		Message msg = new Message();
		msg.what = MEDIA_PLAYER_PREPARED;
		mEventHandler.sendMessage(msg);
	}

	@Override
	public void onProgressUpdate(AbsMediaPlayer mp, int time, int length) {
		Message msg = new Message();
		msg.what = MEDIA_PLAYER_PROGRESS_UPDATE;
		msg.arg1 = time;
		msg.arg2 = length;
		mEventHandler.sendMessage(msg);
	}

}
