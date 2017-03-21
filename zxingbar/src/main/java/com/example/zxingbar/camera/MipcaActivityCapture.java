package com.example.zxingbar.camera;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import com.example.zxingbar.R;
import com.example.zxingbar.decoding.CaptureActivityHandler;
import com.example.zxingbar.decoding.InactivityTimer;
import com.example.zxingbar.view.ViewfinderView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.io.IOException;
import java.util.Vector;

/**
 * Initial the camera
 * @author Ryan.Tang
 */
public abstract class MipcaActivityCapture extends Activity implements Callback {

	private CaptureActivityHandler handler;
	private ViewfinderView viewfinderView;
	private boolean hasSurface;
	private Vector<BarcodeFormat> decodeFormats;
	private String characterSet;
	private InactivityTimer inactivityTimer;
	private MediaPlayer mediaPlayer;
	private boolean playBeep;
	private static final float BEEP_VOLUME = 0.10f;
	private boolean vibrate;

	//扫描框外的阴影颜色，默认颜色
	private int outShadowColor = Color.parseColor("#44aaaaaa");
	private View rootView;
	private View topView;

	/**
	 * rootView
	 */
	public abstract View getRootView();

	/**
	 * top View
	 */
	public abstract View getTopView();

	/**
	 * 扫码结果返回
	 */
	public abstract void resultCode(Result result,Bitmap bitmap);

	/**
	 * 返回ViewfinderView
	 */
	public abstract ViewfinderView toSetViewfinderView(View topView) ;

	/**
	 * 返回扫描限surfaceView
	 */
	public abstract SurfaceView getSurfaceView(View rootView);

	/**
	 * 设置扫描框外的阴影颜色
	 * @param color
	 */
	public void setOutShadowColor(int color) {
		this.outShadowColor = color;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		rootView = getRootView();
		topView = getTopView();
		setContentView(rootView);
		this.addContentView(topView,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
				, ViewGroup.LayoutParams.MATCH_PARENT));
		
		Camera camera = null;
		try {
			camera = Camera.open();
		} catch (Exception e) {
			// TODO: handle exception
			Toast.makeText(this, "请启动相机相关权限", Toast.LENGTH_SHORT).show();
		}finally {
			if(camera != null) {
				camera.release();
			}
		}
		
		inactivityTimer = new InactivityTimer(this);
		CameraManager.init(getApplication());
//		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		viewfinderView = toSetViewfinderView(topView);
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		// TODO Auto-generated method stub
		super.onWindowFocusChanged(hasFocus);
		if(!hasFocus) {
			return;
		}
		//CameraManager.init(getApplication());
		int originX = viewfinderView.getLeft();
		int originY = viewfinderView.getTop();
		int width = viewfinderView.getWidth();
		int height = viewfinderView.getHeight();

		int rootWidth = viewfinderView.getRootView().getWidth();
		int rootHeight = viewfinderView.getRootView().getHeight();
		
		Rect rect = new Rect(originX, originY, originX + width, originY + height);
		CameraManager.get().setFramingRect(rect);
		//为周边添加影音
		FrameLayout outShadowView = new FrameLayout(this);

		//顶部
		View topShaowdowView = new View(this);
		FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(rootWidth,originY);
		topParams.topMargin = 0;
		topParams.leftMargin = 0;
		topShaowdowView.setBackgroundColor(outShadowColor);
		outShadowView.addView(topShaowdowView,topParams);

		//左边
		View leftShaowdowView = new View(this);
		FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(originX,height);
		leftParams.topMargin = originY;
		leftParams.leftMargin = 0;
		leftShaowdowView.setBackgroundColor(outShadowColor);
		outShadowView.addView(leftShaowdowView,leftParams);

		//底部
		View bottomShaowdowView = new View(this);
		FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(rootWidth,rootHeight - viewfinderView.getBottom());
		bottomParams.topMargin = viewfinderView.getBottom();
		bottomParams.leftMargin = 0;
		bottomShaowdowView.setBackgroundColor(outShadowColor);
		outShadowView.addView(bottomShaowdowView,bottomParams);

		//右边
		View rightShaowdowView = new View(this);
		FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(rootWidth - viewfinderView.getRight(),height);
		rightParams.topMargin = viewfinderView.getTop();
		rightParams.leftMargin = viewfinderView.getRight();
		rightShaowdowView.setBackgroundColor(outShadowColor);
		outShadowView.addView(rightShaowdowView,rightParams);

		this.addContentView(outShadowView,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
				, ViewGroup.LayoutParams.MATCH_PARENT));
		topView.bringToFront();



		hasSurface = false;
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurfaceView surfaceView = getSurfaceView(rootView);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			initCamera(surfaceHolder);
		} else {
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		decodeFormats = null;
		characterSet = null;

		playBeep = true;
		AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
			playBeep = false;
		}
		initBeepSound();
		vibrate = true;
		
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		CameraManager.get().closeDriver();
	}

	@Override
	protected void onDestroy() {
		if(inactivityTimer != null) {
			inactivityTimer.shutdown();
		}
		super.onDestroy();
	}
	
	/**
	 * 处理扫描结果
	 * @param result
	 * @param barcode
	 */
	public void handleDecode(Result result, Bitmap barcode) {
		inactivityTimer.onActivity();
		playBeepSoundAndVibrate();
		resultCode(result,barcode);
	}
	
	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get().openDriver(surfaceHolder);
		} catch (IOException ioe) {
			return;
		} catch (RuntimeException e) {
			return;
		}
		if (handler == null) {
			handler = new CaptureActivityHandler(this, decodeFormats,
					characterSet);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;

	}

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();

	}

	private void initBeepSound() {
		if (playBeep && mediaPlayer == null) {
			// The volume on STREAM_SYSTEM is not adjustable, and users found it
			// too loud,
			// so we now play on the music stream.
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mediaPlayer.setOnCompletionListener(beepListener);

			AssetFileDescriptor file = getResources().openRawResourceFd(
					R.raw.beep);
			try {
				mediaPlayer.setDataSource(file.getFileDescriptor(),
						file.getStartOffset(), file.getLength());
				file.close();
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
				mediaPlayer.prepare();
			} catch (IOException e) {
				mediaPlayer = null;
			}
		}
	}

	private static final long VIBRATE_DURATION = 200L;

	private void playBeepSoundAndVibrate() {
		if (playBeep && mediaPlayer != null) {
			mediaPlayer.start();
		}
		if (vibrate) {
			Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			vibrator.vibrate(VIBRATE_DURATION);
		}
	}

	/**
	 * When the beep has finished playing, rewind to queue up another one.
	 */
	private final OnCompletionListener beepListener = new OnCompletionListener() {
		public void onCompletion(MediaPlayer mediaPlayer) {
			mediaPlayer.seekTo(0);
		}
	};
}