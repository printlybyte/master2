/*
 *  SDTYCamera
 *
 *
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libdev, libvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.wfty.cameracommon;

import android.app.Activity;
import android.view.Surface;

import com.serenegiant.glutils.RendererHolder;
import com.wfty.dev.SDTYCamera;
import com.wfty.widget.CameraViewInterface;

public class SDTYCameraHandlerMultiSurface extends AbstractSDTYCameraHandler {
	/**
	 * create SDTYCameraHandlerMultiSurface, use MediaVideoEncoder, try MJPEG, default bandwidth
	 * @param parent
	 * @param cameraView
	 * @param width
	 * @param height
	 * @return
	 */
	public static final SDTYCameraHandlerMultiSurface createHandler(
			final Activity parent, final CameraViewInterface cameraView,
			final int width, final int height) {

		return createHandler(parent, cameraView, 1, width, height, SDTYCamera.FRAME_FORMAT_MJPEG, SDTYCamera.DEFAULT_BANDWIDTH);
	}

	/**
	 * create SDTYCameraHandlerMultiSurface, use MediaVideoEncoder, try MJPEG
	 * @param parent
	 * @param cameraView
	 * @param width
	 * @param height
	 * @param bandwidthFactor
	 * @return
	 */
	public static final SDTYCameraHandlerMultiSurface createHandler(
			final Activity parent, final CameraViewInterface cameraView,
			final int width, final int height, final float bandwidthFactor) {

		return createHandler(parent, cameraView, 1, width, height, SDTYCamera.FRAME_FORMAT_MJPEG, bandwidthFactor);
	}

	/**
	 * create SDTYCameraHandlerMultiSurface, try MJPEG, default bandwidth
	 * @param parent
	 * @param cameraView
	 * @param encoderType
	 * @param width
	 * @param height
	 * @return
	 */
	public static final SDTYCameraHandlerMultiSurface createHandler(
			final Activity parent, final CameraViewInterface cameraView,
			final int encoderType, final int width, final int height) {

		return createHandler(parent, cameraView, encoderType, width, height, SDTYCamera.FRAME_FORMAT_MJPEG, SDTYCamera.DEFAULT_BANDWIDTH);
	}

	/**
	 * create SDTYCameraHandlerMultiSurface, default bandwidth
	 * @param parent
	 * @param cameraView
	 * @param encoderType
	 * @param width
	 * @param height
	 * @param format
	 * @return
	 */
	public static final SDTYCameraHandlerMultiSurface createHandler(
			final Activity parent, final CameraViewInterface cameraView,
			final int encoderType, final int width, final int height, final int format) {

		return createHandler(parent, cameraView, encoderType, width, height, format, SDTYCamera.DEFAULT_BANDWIDTH);
	}

	/**
	 * create SDTYCameraHandlerMultiSurface
	 * @param parent
	 * @param cameraView
	 * @param encoderType 0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
	 * @param width
	 * @param height
	 * @param format either SDTYCamera.FRAME_FORMAT_YUYV(0) or SDTYCamera.FRAME_FORMAT_MJPEG(1)
	 * @param bandwidthFactor
	 * @return
	 */
	public static final SDTYCameraHandlerMultiSurface createHandler(
			final Activity parent, final CameraViewInterface cameraView,
			final int encoderType, final int width, final int height, final int format, final float bandwidthFactor) {

		final CameraThread thread = new CameraThread(SDTYCameraHandlerMultiSurface.class, parent, cameraView, encoderType, width, height, format, bandwidthFactor);
		thread.start();
		return (SDTYCameraHandlerMultiSurface)thread.getHandler();
	}

	private RendererHolder mRendererHolder;
	protected SDTYCameraHandlerMultiSurface(final CameraThread thread) {
		super(thread);
		mRendererHolder = new RendererHolder(thread.getWidth(), thread.getHeight(), null);
	}

	public synchronized void release() {
		if (mRendererHolder != null) {
			mRendererHolder.release();
			mRendererHolder = null;
		}
		super.release();
	}

	public synchronized void resize(final int width, final int height) {
		super.resize(width, height);
		if (mRendererHolder != null) {
			mRendererHolder.resize(width, height);
		}
	}

	public synchronized void startPreview() {
		checkReleased();
		if (mRendererHolder != null) {
			super.startPreview(mRendererHolder.getSurface());
		} else {
			throw new IllegalStateException();
		}
	}

	public synchronized void addSurface(final int surfaceId, final Surface surface, final boolean isRecordable) {
		checkReleased();
		mRendererHolder.addSurface(surfaceId, surface, isRecordable);
	}

	public synchronized void removeSurface(final int surfaceId) {
		if (mRendererHolder != null) {
			mRendererHolder.removeSurface(surfaceId);
		}
	}

	@Override
	public void captureStill() {
		checkReleased();
		super.captureStill();
	}

	@Override
	public void captureStill(final String path) {
		checkReleased();
		post(new Runnable() {
			@Override
			public void run() {
				synchronized (SDTYCameraHandlerMultiSurface.this) {
					if (mRendererHolder != null) {
						mRendererHolder.captureStill(path);
						updateMedia(path);
					}
				}
			}
		});
	}
}
