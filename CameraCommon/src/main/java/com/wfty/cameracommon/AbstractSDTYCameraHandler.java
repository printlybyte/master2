
package com.wfty.cameracommon;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.wfty.dev.SDTYCamera;
import com.wfty.encoder.MediaAudioEncoder;
import com.wfty.encoder.MediaEncoder;
import com.wfty.encoder.MediaMuxerWrapper;
import com.wfty.encoder.MediaSurfaceEncoder;
import com.wfty.encoder.MediaVideoBufferEncoder;
import com.wfty.encoder.MediaVideoEncoder;
import com.wfty.dev.IFrameCallback;
import com.wfty.dev.DevMonitor;
import com.wfty.cameracommon.R;
import com.wfty.widget.CameraViewInterface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

abstract class AbstractSDTYCameraHandler extends Handler {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "AbsSDTYCameraHandler";

	public interface CameraCallback {
		public void onOpen();
		public void onClose();
		public void onStartPreview();
		public void onStopPreview();
		public void onStartRecording();
		public void onStopRecording();
		public void onError(final Exception e);
	}

	private static final int MSG_OPEN = 0;
	private static final int MSG_CLOSE = 1;
	private static final int MSG_PREVIEW_START = 2;
	private static final int MSG_PREVIEW_STOP = 3;
	private static final int MSG_CAPTURE_STILL = 4;
	private static final int MSG_CAPTURE_START = 5;
	private static final int MSG_CAPTURE_STOP = 6;
	private static final int MSG_MEDIA_UPDATE = 7;
	private static final int MSG_RELEASE = 9;

	private final WeakReference<AbstractSDTYCameraHandler.CameraThread> mWeakThread;
	private volatile boolean mReleased;

	protected AbstractSDTYCameraHandler(final CameraThread thread) {
		mWeakThread = new WeakReference<CameraThread>(thread);
	}

	public int getWidth() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getWidth() : 0;
	}

	public int getHeight() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getHeight() : 0;
	}

	public boolean isOpened() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isCameraOpened();
	}

	public boolean isPreviewing() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isPreviewing();
	}

	public boolean isRecording() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isRecording();
	}



	protected boolean isCameraThread() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && (thread.getId() == Thread.currentThread().getId());
	}

	protected boolean isReleased() {
		final CameraThread thread = mWeakThread.get();
		return mReleased || (thread == null);
	}

	protected void checkReleased() {
		if (isReleased()) {
			throw new IllegalStateException("already released");
		}
	}

	public void open(final DevMonitor.CameraControlBlock ctrlBlock) {
		checkReleased();
		sendMessage(obtainMessage(MSG_OPEN, ctrlBlock));
	}

	public void close() {
		if (DEBUG) Log.v(TAG, "close:");
		if (isOpened()) {
			stopPreview();
			sendEmptyMessage(MSG_CLOSE);
		}
		if (DEBUG) Log.v(TAG, "close:finished");
	}

	public void resize(final int width, final int height) {
		checkReleased();
		throw new UnsupportedOperationException("does not support now");
	}

	protected void startPreview(final Object surface) {
		checkReleased();
		if (!((surface instanceof SurfaceHolder) || (surface instanceof Surface) || (surface instanceof SurfaceTexture))) {
			throw new IllegalArgumentException("surface should be one of SurfaceHolder, Surface or SurfaceTexture");
		}
		sendMessage(obtainMessage(MSG_PREVIEW_START, surface));
	}

	public void stopPreview() {
		if (DEBUG) Log.v(TAG, "stopPreview:");
		removeMessages(MSG_PREVIEW_START);
		stopRecording();
		if (isPreviewing()) {
			final CameraThread thread = mWeakThread.get();
			if (thread == null) return;
			synchronized (thread.mSync) {
				sendEmptyMessage(MSG_PREVIEW_STOP);
				if (!isCameraThread()) {
					// wait for actually preview stopped to avoid releasing Surface/SurfaceTexture
					// while preview is still running.
					// therefore this method will take a time to execute
					try {
						thread.mSync.wait();
					} catch (final InterruptedException e) {
					}
				}
			}
		}
		if (DEBUG) Log.v(TAG, "stopPreview:finished");
	}

	protected void captureStill() {
		checkReleased();
		sendEmptyMessage(MSG_CAPTURE_STILL);
	}

	protected void captureStill(final String path) {
		checkReleased();
		sendMessage(obtainMessage(MSG_CAPTURE_STILL, path));
	}

	public void startRecording() {
		checkReleased();
		sendEmptyMessage(MSG_CAPTURE_START);
	}

	public void stopRecording() {
		sendEmptyMessage(MSG_CAPTURE_STOP);
	}

	public void release() {
		mReleased = true;
		close();
		sendEmptyMessage(MSG_RELEASE);
	}

	public void addCallback(final CameraCallback callback) {
		checkReleased();
		if (!mReleased && (callback != null)) {
			final CameraThread thread = mWeakThread.get();
			if (thread != null) {
				thread.mCallbacks.add(callback);
			}
		}
	}

	public void removeCallback(final CameraCallback callback) {
		if (callback != null) {
			final CameraThread thread = mWeakThread.get();
			if (thread != null) {
				thread.mCallbacks.remove(callback);
			}
		}
	}

	protected void updateMedia(final String path) {
		sendMessage(obtainMessage(MSG_MEDIA_UPDATE, path));
	}

	public boolean checkSupportFlag(final long flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.mSDTYCamera != null && thread.mSDTYCamera.checkSupportFlag(flag);
	}

	public int getValue(final int flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final SDTYCamera camera = thread != null ? thread.mSDTYCamera : null;
		if (camera != null) {
			if (flag == SDTYCamera.PU_BRIGHTNESS) {
				return camera.getBrightness();
			} else if (flag == SDTYCamera.PU_CONTRAST) {
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	public int setValue(final int flag, final int value) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final SDTYCamera camera = thread != null ? thread.mSDTYCamera : null;
		if (camera != null) {
			if (flag == SDTYCamera.PU_BRIGHTNESS) {
				camera.setBrightness(value);
				return camera.getBrightness();
			} else if (flag == SDTYCamera.PU_CONTRAST) {
				camera.setContrast(value);
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	public int resetValue(final int flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final SDTYCamera camera = thread != null ? thread.mSDTYCamera : null;
		if (camera != null) {
			if (flag == SDTYCamera.PU_BRIGHTNESS) {
				camera.resetBrightness();
				return camera.getBrightness();
			} else if (flag == SDTYCamera.PU_CONTRAST) {
				camera.resetContrast();
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	@Override
	public void handleMessage(final Message msg) {
		final CameraThread thread = mWeakThread.get();
		if (thread == null) return;
		switch (msg.what) {
		case MSG_OPEN:
			thread.handleOpen((DevMonitor.CameraControlBlock)msg.obj);
			break;
		case MSG_CLOSE:
			thread.handleClose();
			break;
		case MSG_PREVIEW_START:
			thread.handleStartPreview(msg.obj);
			break;
		case MSG_PREVIEW_STOP:
			thread.handleStopPreview();
			break;
		case MSG_CAPTURE_STILL:
			thread.handleCaptureStill((String)msg.obj);
			break;
		case MSG_CAPTURE_START:
			thread.handleStartRecording();
			break;
		case MSG_CAPTURE_STOP:
			thread.handleStopRecording();
			break;
		case MSG_MEDIA_UPDATE:
			thread.handleUpdateMedia((String)msg.obj);
			break;
		case MSG_RELEASE:
			thread.handleRelease();
			break;
		default:
			throw new RuntimeException("unsupported message:what=" + msg.what);
		}
	}

	static final class CameraThread extends Thread {
		private static final String TAG_THREAD = "CameraThread";
		private final Object mSync = new Object();
		private final Class<? extends AbstractSDTYCameraHandler> mHandlerClass;
		private final WeakReference<Activity> mWeakParent;
		private final WeakReference<CameraViewInterface> mWeakCameraView;
		private final int mEncoderType;
		private final Set<CameraCallback> mCallbacks = new CopyOnWriteArraySet<CameraCallback>();
		private int mWidth, mHeight, mPreviewMode;
		private float mBandwidthFactor;
		private boolean mIsPreviewing;
		private boolean mIsRecording;
		/**
		 * shutter sound
		 */
		private SoundPool mSoundPool;
		private int mSoundId;
		private AbstractSDTYCameraHandler mHandler;
		/**
		 * for accessing UVC camera
		 */
		private SDTYCamera mSDTYCamera;
		/**
		 * muxer for audio/video recording
		 */
		private MediaMuxerWrapper mMuxer;
		private MediaVideoBufferEncoder mVideoEncoder;

		/**
		 *
		 * @param clazz Class extends AbstractSDTYCameraHandler
		 * @param parent parent Activity
		 * @param cameraView for still capturing
		 * @param encoderType 0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
		 * @param width
		 * @param height
		 * @param format either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
		 * @param bandwidthFactor
		 */
		CameraThread(final Class<? extends AbstractSDTYCameraHandler> clazz,
			final Activity parent, final CameraViewInterface cameraView,
			final int encoderType, final int width, final int height, final int format,
			final float bandwidthFactor) {

			super("CameraThread");
			mHandlerClass = clazz;
			mEncoderType = encoderType;
			mWidth = width;
			mHeight = height;
			mPreviewMode = format;
			mBandwidthFactor = bandwidthFactor;
			mWeakParent = new WeakReference<Activity>(parent);
			mWeakCameraView = new WeakReference<CameraViewInterface>(cameraView);
			loadShutterSound(parent);
		}

		@Override
		protected void finalize() throws Throwable {
			Log.i(TAG, "CameraThread#finalize");
			super.finalize();
		}

		public AbstractSDTYCameraHandler getHandler() {
			if (DEBUG) Log.v(TAG_THREAD, "getHandler:");
			synchronized (mSync) {
				if (mHandler == null)
				try {
					mSync.wait();
				} catch (final InterruptedException e) {
				}
			}
			return mHandler;
		}

		public int getWidth() {
			synchronized (mSync) {
				return mWidth;
			}
		}

		public int getHeight() {
			synchronized (mSync) {
				return mHeight;
			}
		}

		public boolean isCameraOpened() {
			synchronized (mSync) {
				return mSDTYCamera != null;
			}
		}

		public boolean isPreviewing() {
			synchronized (mSync) {
				return mSDTYCamera != null && mIsPreviewing;
			}
		}

		public boolean isRecording() {
			synchronized (mSync) {
				return (mSDTYCamera != null) && (mMuxer != null);
			}
		}

		public void handleOpen(final DevMonitor.CameraControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG_THREAD, "handleOpen:");
			handleClose();
			try {
				final SDTYCamera camera = new SDTYCamera();
				camera.open(ctrlBlock);
				synchronized (mSync) {
					mSDTYCamera = camera;
				}
				callOnOpen();
			} catch (final Exception e) {
				callOnError(e);
			}
			if (DEBUG) Log.i(TAG, "supportedSize:" + (mSDTYCamera != null ? mSDTYCamera.getSupportedSize() : null));
		}

		public void handleClose() {
			if (DEBUG) Log.v(TAG_THREAD, "handleClose:");
			handleStopRecording();
			final SDTYCamera camera;
			synchronized (mSync) {
				camera = mSDTYCamera;
				mSDTYCamera = null;
			}
			if (camera != null) {
				camera.stopPreview();
				camera.destroy();
				callOnClose();
			}
		}

		public void handleStartPreview(final Object surface) {
			if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:");
			if ((mSDTYCamera == null) || mIsPreviewing) return;
			try {
				mSDTYCamera.setPreviewSize(mWidth, mHeight, 1, 31, mPreviewMode, mBandwidthFactor);
			} catch (final IllegalArgumentException e) {
				try {
					// fallback to YUV mode
					mSDTYCamera.setPreviewSize(mWidth, mHeight, 1, 31, SDTYCamera.DEFAULT_PREVIEW_MODE, mBandwidthFactor);
				} catch (final IllegalArgumentException e1) {
					callOnError(e1);
					return;
				}
			}
			if (surface instanceof SurfaceHolder) {
				mSDTYCamera.setPreviewDisplay((SurfaceHolder)surface);
			} if (surface instanceof Surface) {
				mSDTYCamera.setPreviewDisplay((Surface)surface);
			} else {
				mSDTYCamera.setPreviewTexture((SurfaceTexture)surface);
			}
			mSDTYCamera.startPreview();
			mSDTYCamera.updateCameraParams();
			synchronized (mSync) {
				mIsPreviewing = true;
			}
			callOnStartPreview();
		}

		public void handleStopPreview() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:");
			if (mIsPreviewing) {
				if (mSDTYCamera != null) {
					mSDTYCamera.stopPreview();
				}
				synchronized (mSync) {
					mIsPreviewing = false;
					mSync.notifyAll();
				}
				callOnStopPreview();
			}
			if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:finished");
		}

		public void handleCaptureStill(final String path) {
			if (DEBUG) Log.v(TAG_THREAD, "handleCaptureStill:");
			final Activity parent = mWeakParent.get();
			if (parent == null) return;
			mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);	// play shutter sound
			try {
				final Bitmap bitmap = mWeakCameraView.get().captureStillImage();
				if (DEBUG) Log.v("hjs", "bitmapbitmap:");
				// get buffered output stream for saving a captured still image as a file on external storage.
				// the file name is came from current time.
				// You should use extension name as same as CompressFormat when calling Bitmap#compress.
				final File outputFile = TextUtils.isEmpty(path)
					? MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png")
					: new File(path);
				final BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
				Log.e("hjs","bitmapgetByteCount"+bitmap.getByteCount());
				try {
					try {
						bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
						os.flush();
						mHandler.sendMessage(mHandler.obtainMessage(MSG_MEDIA_UPDATE, outputFile.getPath()));
					} catch (final IOException e) {
					}
				} finally {
					os.close();
				}
			} catch (final Exception e) {
				callOnError(e);
			}
		}

		public void handleStartRecording() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStartRecording:");
			try {
				if ((mSDTYCamera == null) || (mMuxer != null)) return;
				final MediaMuxerWrapper muxer = new MediaMuxerWrapper(".mp4");	// if you record audio only, ".m4a" is also OK.
				MediaVideoBufferEncoder videoEncoder = null;
				Log.e("hjs","mEncoderType"+mEncoderType);
				switch (mEncoderType) {
				case 1:	// for video capturing using MediaVideoEncoder
					new MediaVideoEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
					break;
				case 2:	// for video capturing using MediaVideoBufferEncoder
					videoEncoder = new MediaVideoBufferEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
					break;
				// case 0:	// for video capturing using MediaSurfaceEncoder
				default:
					new MediaSurfaceEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
					break;
				}
				if (true) {
					// for audio capturing
					new MediaAudioEncoder(muxer, mMediaEncoderListener);
				}
				muxer.prepare();
				muxer.startRecording();
				Log.e("hjs","videoEncoder");
				if (videoEncoder != null) {
					Log.e("hjs","mIFrameCallback");
					mSDTYCamera.setFrameCallback(mIFrameCallback, SDTYCamera.PIXEL_FORMAT_NV21);
				}
				synchronized (mSync) {
					mMuxer = muxer;
					mVideoEncoder = videoEncoder;
				}
				callOnStartRecording();
			} catch (final IOException e) {
				callOnError(e);
				Log.e(TAG, "startCapture:", e);
			}
		}

		public void handleStopRecording() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStopRecording:mMuxer=" + mMuxer);
			final MediaMuxerWrapper muxer;
			synchronized (mSync) {
				muxer = mMuxer;
				mMuxer = null;
				mVideoEncoder = null;
				if (mSDTYCamera != null) {
					mSDTYCamera.stopCapture();
				}
			}
			try {
				mWeakCameraView.get().setVideoEncoder(null);
			} catch (final Exception e) {
				// ignore
			}
			if (muxer != null) {
				muxer.stopRecording();
				mSDTYCamera.setFrameCallback(null, 0);
				// you should not wait here
				callOnStopRecording();
			}
		}

		private final IFrameCallback mIFrameCallback = new IFrameCallback() {
			@Override
			public void onFrame(final ByteBuffer frame) {
				Log.e("hjs","onFrame");
				final MediaVideoBufferEncoder videoEncoder;
				synchronized (mSync) {
					videoEncoder = mVideoEncoder;
				}
				if (videoEncoder != null) {
					videoEncoder.frameAvailableSoon();
					videoEncoder.encode(frame);
				}
			}
		};

		public void handleUpdateMedia(final String path) {
			if (DEBUG) Log.v(TAG_THREAD, "handleUpdateMedia:path=" + path);
			final Activity parent = mWeakParent.get();
			final boolean released = (mHandler == null) || mHandler.mReleased;
			if (parent != null && parent.getApplicationContext() != null) {
				try {
					if (DEBUG) Log.i(TAG, "MediaScannerConnection#scanFile");
					MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{ path }, null, null);
				} catch (final Exception e) {
					Log.e(TAG, "handleUpdateMedia:", e);
				}
				if (released || parent.isDestroyed())
					handleRelease();
			} else {
				Log.w(TAG, "MainActivity already destroyed");
				// give up to add this movie to MediaStore now.
				// Seeing this movie on Gallery app etc. will take a lot of time.
				handleRelease();
			}
		}

		public void handleRelease() {
			if (DEBUG) Log.v(TAG_THREAD, "handleRelease:mIsRecording=" + mIsRecording);
			handleClose();
			mCallbacks.clear();
			if (!mIsRecording) {
				mHandler.mReleased = true;
				Looper.myLooper().quit();
			}
			if (DEBUG) Log.v(TAG_THREAD, "handleRelease:finished");
		}

		private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
			@Override
			public void onPrepared(final MediaEncoder encoder) {
				if (DEBUG) Log.v(TAG, "onPrepared:encoder=" + encoder);
				mIsRecording = true;
				if (encoder instanceof MediaVideoEncoder)
				try {
					mWeakCameraView.get().setVideoEncoder((MediaVideoEncoder)encoder);
				} catch (final Exception e) {
					Log.e(TAG, "onPrepared:", e);
				}
				if (encoder instanceof MediaSurfaceEncoder)
				try {
					mWeakCameraView.get().setVideoEncoder((MediaSurfaceEncoder)encoder);
					mSDTYCamera.startCapture(((MediaSurfaceEncoder)encoder).getInputSurface());
				} catch (final Exception e) {
					Log.e(TAG, "onPrepared:", e);
				}
			}

			@Override
			public void onStopped(final MediaEncoder encoder) {
				if (DEBUG) Log.v(TAG_THREAD, "onStopped:encoder=" + encoder);
				if ((encoder instanceof MediaVideoEncoder)
					|| (encoder instanceof MediaSurfaceEncoder))
				try {
					mIsRecording = false;
					final Activity parent = mWeakParent.get();
					mWeakCameraView.get().setVideoEncoder(null);
					synchronized (mSync) {
						if (mSDTYCamera != null) {
							mSDTYCamera.stopCapture();
						}
					}
					final String path = encoder.getOutputPath();
					if (!TextUtils.isEmpty(path)) {
						mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_MEDIA_UPDATE, path), 1000);
					} else {
						final boolean released = (mHandler == null) || mHandler.mReleased;
						if (released || parent == null || parent.isDestroyed()) {
							handleRelease();
						}
					}
				} catch (final Exception e) {
					Log.e(TAG, "onPrepared:", e);
				}
			}
		};

		/**
		 * prepare and load shutter sound for still image capturing
		 */
		@SuppressWarnings("deprecation")
		private void loadShutterSound(final Context context) {
	    	// get system stream type using reflection
	        int streamType;
	        try {
	            final Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
	            final Field sseField = audioSystemClass.getDeclaredField("STREAM_SYSTEM_ENFORCED");
	            streamType = sseField.getInt(null);
	        } catch (final Exception e) {
	        	streamType = AudioManager.STREAM_SYSTEM;	// set appropriate according to your app policy
	        }
	        if (mSoundPool != null) {
	        	try {
	        		mSoundPool.release();
	        	} catch (final Exception e) {
	        	}
	        	mSoundPool = null;
	        }
	        // load shutter sound from resource
			mSoundPool = new SoundPool(2, streamType, 0);
		    mSoundId = mSoundPool.load(context, R.raw.camera_click, 1);

		}

		@Override
		public void run() {
			Looper.prepare();
			AbstractSDTYCameraHandler handler = null;
			try {
				final Constructor<? extends AbstractSDTYCameraHandler> constructor = mHandlerClass.getDeclaredConstructor(CameraThread.class);
				handler = constructor.newInstance(this);
			} catch (final NoSuchMethodException e) {
				Log.w(TAG, e);
			} catch (final IllegalAccessException e) {
				Log.w(TAG, e);
			} catch (final InstantiationException e) {
				Log.w(TAG, e);
			} catch (final InvocationTargetException e) {
				Log.w(TAG, e);
			}
			if (handler != null) {
				synchronized (mSync) {
					mHandler = handler;
					mSync.notifyAll();
				}
				Looper.loop();
				if (mSoundPool != null) {
					mSoundPool.release();
					mSoundPool = null;
				}
				if (mHandler != null) {
					mHandler.mReleased = true;
				}
			}
			mCallbacks.clear();
			synchronized (mSync) {
				mHandler = null;
				mSync.notifyAll();
			}
		}

		private void callOnOpen() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onOpen();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnClose() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onClose();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStartPreview() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStartPreview();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStopPreview() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStopPreview();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStartRecording() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStartRecording();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStopRecording() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStopRecording();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnError(final Exception e) {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onError(e);
				} catch (final Exception e1) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}
	}
}
