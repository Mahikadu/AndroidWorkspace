package com.autodetectimage;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import com.pixelnetica.imagesdk.Corners;
import com.pixelnetica.imagesdk.ImageSdkLibrary;
import com.pixelnetica.imagesdk.MetaImage;


public class DetectDocCornersTask extends
		AsyncTask<MetaImage, Integer, DetectDocCornersTask.DocCornersResult> {

	public static class DocCornersResult {
		@NonNull
		final Corners cropData;
		final boolean isSmartCrop;
		DocCornersResult(@NonNull Corners cropData, boolean isSmartCrop) {
			this.cropData = cropData;
			this.isSmartCrop = isSmartCrop;
		}

	};
	
	public interface Listener {
		void onDocCornersComplete(DetectDocCornersTask task, @CropDemoApp.ProcessingError int error, DocCornersResult result);
	}

	private @CropDemoApp.ProcessingError int mProcessingError = CropDemoApp.NOERROR;

	@NonNull
	private final DocImageRoutine.Factory mFactory;
	@NonNull
	private final Listener mListener;

	public DetectDocCornersTask(@NonNull DocImageRoutine.Factory factory, @NonNull Listener listener) {
		this.mFactory = factory;
		this.mListener = listener;
	}

	@Override
	protected DocCornersResult doInBackground(MetaImage... params) {
		MetaImage inputImage = params[0];	// always one param
		final DocImageRoutine routine = mFactory.createRoutine(false);
		try {
			// Debug bounding box
			if (BuildConfig.DEBUG) {
				routine.params.putBoolean(ImageSdkLibrary.SDK_CHECK_DOCUMENT_AREA, true);
				routine.params.putBoolean(ImageSdkLibrary.SDK_CHECK_DOCUMENT_DISTORTION, true);
			}

			Corners corners = routine.sdk.detectDocumentCorners(inputImage, routine.params);
			if (corners == null) {
				mProcessingError = CropDemoApp.NODOCCORNERS;
			} else {
				if (BuildConfig.DEBUG) {
					//Log.v(AppLog.TAG, "OBB check: " + )
				}
				// Read and copy result
				routine.corners.setCorners(corners);
				routine.isSmartCropMode = routine.params.getBoolean("isSmartCropMode");
				Log.d("CropDemo", String.format(
						"Document (%d %d) [%d] corners (%d %d) (%d %d) (%d %d) (%d %d)",
						inputImage.getBitmap().getWidth(), inputImage.getBitmap().getHeight(),
						inputImage.getExifOrientation(),
						routine.corners.points[0].x, routine.corners.points[0].y,
						routine.corners.points[1].x, routine.corners.points[1].y,
						routine.corners.points[2].x, routine.corners.points[2].y,
						routine.corners.points[3].x, routine.corners.points[3].y));
			}
		} catch(OutOfMemoryError e) {
			e.printStackTrace();
			mProcessingError = CropDemoApp.OUTOFMEMORY;
		} catch(Exception|Error e) {
			e.printStackTrace();
			mProcessingError = CropDemoApp.PROCESSING;
		} finally {
			routine.close();
		}

		return new DocCornersResult(routine.corners.clone(), routine.isSmartCropMode);
	}

	@Override
	protected void onPostExecute(DocCornersResult result) {
		if (mListener != null) {
			mListener.onDocCornersComplete(this, mProcessingError, result);
		}
	}
}
