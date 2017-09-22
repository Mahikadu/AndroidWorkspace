package com.autodetectimage;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;


import com.autodetectimage.camera.FindDocCornersThread;
import com.pixelnetica.imagesdk.ImageProcessing;
import com.pixelnetica.imagesdk.ImageSdkLibrary;
import com.pixelnetica.imagesdk.ImageWriter;
import com.pixelnetica.imagesdk.ImageWriterException;

/**
 * Created by Denis on 26.11.2016.
 */

public class SdkFactory extends SdkFactoryBase {

	SdkFactory(@NonNull Application application) {
		super(application);
	}

	ImageProcessing createSDK() {
		// Default processing
		return getLibrary().newProcessingInstance();
	}

	public ImageWriter createImageWriter(@ImageSdkLibrary.ImageWriterType int type) throws ImageWriterException {
		return getLibrary().newImageWriterInstance(type);
	}

	Bundle createDocCorners() {
		// All-defaults params
		return new Bundle();
	}

	void loadPreferences() {
		// Dummy
	}

	public static float queryDocumentAreaRate(@NonNull Bundle bundle) {
		return Float.MIN_VALUE;
	}

	public static float queryDocumentAreaAspect(@NonNull Bundle bundle) {
		return Float.MIN_VALUE;
	}

	public static float queryDocumentDistortionRate(@NonNull Bundle bundle) {
		return Float.MIN_VALUE;
	}

	public static String verboseDetectionFailure(@NonNull Context context, int failure, float failedRate, float areaAspect) {
		switch (failure) {
			case FindDocCornersThread.CORNERS_UNCERTAIN:
				return context.getString(R.string.camera_uncertain_detection);
			case FindDocCornersThread.CORNERS_SMALL_AREA:
				return context.getString(R.string.camera_small_area);
			case FindDocCornersThread.CORNERS_DISTORTED:
				return context.getString(R.string.camera_distorted);
			default:
				return null;
		}
	}

}
