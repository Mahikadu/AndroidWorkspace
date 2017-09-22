package com.autodetectimage.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.util.Log;

import com.autodetectimage.AppLog;
import com.autodetectimage.CropDemoApp;
import com.autodetectimage.DocImageRoutine;
import com.autodetectimage.SdkFactory;
import com.autodetectimage.util.SequentialThread;

import com.pixelnetica.imagesdk.Corners;
import com.pixelnetica.imagesdk.ImageProcessing;
import com.pixelnetica.imagesdk.ImageSdkLibrary;
import com.pixelnetica.imagesdk.MetaImage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Find document corners by SDK
 * Created by Denis on 22.03.2015.
 */
public class FindDocCornersThread extends SequentialThread {

	private static final int PROCESS_CORNERS = EXIT_THREAD + 1;

	// Stuff
	private final Context mContext;
	private DocImageRoutine mRoutine;
	private final AutoShotDetector mShotDetector = new AutoShotDetector();

	interface FindDocCornersListener {
		void documentCornersFound(Task task);
	}

	@IntDef({CORNERS_DETECTED, CORNERS_UNCERTAIN, CORNERS_SMALL_AREA, CORNERS_DISTORTED, CORNERS_SHOW})
	@interface CornersFail {}
	static final int CORNERS_SHOW = -1;  // special pseudo-error to debug
	static final int CORNERS_DETECTED = 0;
	public static final int CORNERS_UNCERTAIN = 1;
	public static final int CORNERS_SMALL_AREA = 2;
	public static final int CORNERS_DISTORTED = 3;

	class Task {

		// Using weak reference to allow camera be released
		final WeakReference<Camera> refCamera;

		final byte [] pictureBuffer;
		final int pictureFormat;
		final Point pictureSize;

		// Output parameters
		Corners documentCorners;
		Corners documentBounds;
		@CornersFail
		int cornerFail;
		float failedRate;
		float areaAspect;
		boolean shotReady;

		Task(Camera camera, byte [] buffer, int format, Point size) {
			this.refCamera = new WeakReference<Camera>(camera);
			this.pictureBuffer = buffer;
			this.pictureFormat = format;
			this.pictureSize = size;
		}

		FindDocCornersThread getThread() {
			return FindDocCornersThread.this;
		}

		YuvImage createImage() {
			if (pictureFormat == ImageFormat.NV21 || pictureFormat == ImageFormat.YUY2) {
				return new YuvImage(pictureBuffer, pictureFormat, pictureSize.x, pictureSize.y, null);
			} else {
				// Unsupported format
				return null;
			}
		}

		Bitmap decodeBuffer() {
			YuvImage image = createImage();
			if (image == null) {
				return null;
			}

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			image.compressToJpeg(new Rect(0, 0, pictureSize.x, pictureSize.y), 100, out);

			ByteArrayInputStream input = new ByteArrayInputStream(out.toByteArray());
			return BitmapFactory.decodeStream(input);
		}
	}

	private List<FindDocCornersListener> listeners = new ArrayList<FindDocCornersListener>();

	FindDocCornersThread(Context context) {
		super("FindDocCornersThread");    // No runnable object
		this.mContext = context;
	}

	void addListener(FindDocCornersListener obj) {
		assert obj != null;
		if (!listeners.contains(obj)) {
			listeners.add(obj);
		}
	}

	void removeListener(FindDocCornersListener obj) {
		assert obj != null;
		listeners.remove(obj);
	}

	Task createTask(Camera camera, byte [] buffer, int format, Point size) {
		return new Task(camera, buffer, format, size);
	}

	void processDocumentCorners(Task task) {
		addThreadTask(PROCESS_CORNERS, task, SINGLE_TASK, false);
	}

	void setShotDetectorParams(int stableRadius, int stableDelay, int stableCount) {
		mShotDetector.setParams(stableRadius, stableDelay, stableCount);
	}

	@Override
	protected void onThreadStarted() {
		// Create SDK instance in worker thread
		// NOTE: DocImageSDK.load() MUST be caller early
		final CropDemoApp theApp = (CropDemoApp)mContext.getApplicationContext();
		mRoutine = theApp.createRoutine(false);
	}

	@Override
	protected Runnable handleThreadTask(int what, Object arg) {
		// Main processing
		final Task task = (Task) arg;
		process(mRoutine.sdk, mRoutine.params, task);
		if (task.cornerFail == CORNERS_DETECTED) {
			task.shotReady = mShotDetector.addDetectedCorners(task.documentCorners);
		} else {
			task.shotReady = mShotDetector.addDetectedCorners(null);
		}
		return new Runnable() {
			@Override
			public void run() {
				complete(task);
			}
		};
	}

	protected void onThreadComplete() {
		if (mRoutine != null) {
			mRoutine.close();
			mRoutine = null;
		}
	}

	static boolean validThreshold(float value) {
		return (value > 0 && value < 1.0f);
	}

	// Main task
	private void process(ImageProcessing sdk, Bundle cornerParams, Task task) {
		// First, decode picture to bitmap
		Bitmap previewBitmap = task.decodeBuffer();
		if (previewBitmap == null) {
			Log.d(AppLog.TAG, "Cannot decode camera preview frame buffer");
			return;
		}

		if (!sdk.validate()) {
			// Something wrong
			return;
		}

		Bitmap sourceBitmap;
		// Need scale input?
		Point inputSize = sdk.supportImageSize(task.pictureSize);
		if (inputSize == task.pictureSize) {
			// No scale need. Check picture format
			if (previewBitmap.getConfig() == Bitmap.Config.ARGB_8888) {
				sourceBitmap = previewBitmap;
			} else {
				sourceBitmap = previewBitmap.copy(Bitmap.Config.ARGB_8888, true);
			}
		} else {
			// Scale bitmap
			sourceBitmap = Bitmap.createScaledBitmap(previewBitmap.copy(Bitmap.Config.ARGB_8888, true), inputSize.x, inputSize.y, true);
		}

		try {
			MetaImage image = new MetaImage(sourceBitmap);
			// Perform corners check
			cornerParams.putBoolean(ImageSdkLibrary.SDK_CHECK_DOCUMENT_AREA, true);
			cornerParams.putBoolean(ImageSdkLibrary.SDK_CHECK_DOCUMENT_DISTORTION, true);

			task.documentCorners = sdk.detectDocumentCorners(image, cornerParams);
			task.cornerFail = CORNERS_DETECTED;

			if (!cornerParams.getBoolean(ImageSdkLibrary.SDK_IS_SMART_CROP)) {
				// No corners if no smart crop
				task.documentCorners = null;
				task.documentBounds = null;
				task.cornerFail = CORNERS_UNCERTAIN;
			} else if (!cornerParams.getBoolean(ImageSdkLibrary.SDK_DOCUMENT_AREA_CHECKED)) {
				task.cornerFail = CORNERS_SMALL_AREA;
				task.failedRate = SdkFactory.queryDocumentAreaRate(cornerParams);
				task.areaAspect = SdkFactory.queryDocumentAreaAspect(cornerParams);
			} else if (!cornerParams.getBoolean(ImageSdkLibrary.SDK_DOCUMENT_DISTORTION_CHECKED)) {
				task.cornerFail = CORNERS_DISTORTED;
				task.failedRate = SdkFactory.queryDocumentDistortionRate(cornerParams);
			}
		} catch (Throwable e) {
			Log.e(AppLog.TAG, "DocImage SDK internal error", e);
		}
	}

	// Notify task complete
	private void complete(Task task) {
		// Notify all listeners
		for (FindDocCornersListener obj : listeners) {
			obj.documentCornersFound(task);
		}
	}
}
