package com.autodetectimage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;


import com.autodetectimage.util.Utils;
import com.pixelnetica.imagesdk.Corners;
import com.pixelnetica.imagesdk.MetaImage;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by Denis on 28.10.2016.
 */

public class ProcessImageTask extends AsyncTask<Uri, Void, Integer> {

	public interface Listener {
		void onProcessImageComplete(@NonNull ProcessImageTask task, @Nullable MetaImage result, @CropDemoApp.ProcessingError int error);
	}
	@NonNull
	private final Context mContext;
	@NonNull
	private final DocImageRoutine.Factory mFactory;

	@CropDemoApp.ProcessingError
	private int mError = CropDemoApp.NOERROR;

	private final Corners mCorners;
	private final float mRotationAngle;
	private boolean mStrongShadows;
	@CropDemoApp.ProcessingProfile
	private int mProfile = CropDemoApp.OriginateImage;

	// Output
	@NonNull
	private final Listener mListener;
	MetaImage mTargetImage;

	ProcessImageTask(@NonNull Context context, @NonNull DocImageRoutine.Factory factory, @NonNull Corners corners, float rotationAngle, @CropDemoApp.ProcessingProfile int profile,
                     @NonNull Listener listener) {
		mContext = context;
		mFactory = factory;
		mCorners = corners;
		mRotationAngle = rotationAngle;
		mProfile = profile;
		mListener = listener;
	}

	@Override
	protected Integer doInBackground(Uri... params) {

		final Uri sourceUri = params[0];
		//MetaImage sourceImage = null;
		InputStream inputStream = null;
		final DocImageRoutine routine = mFactory.createRoutine(false);
		try {
			// Open and scale source image
			inputStream = mContext.getContentResolver().openInputStream(sourceUri);
			Bitmap inputBitmap = BitmapFactory.decodeStream(inputStream);
			if (inputBitmap == null) {
				return CropDemoApp.INVALIDFILE;
			}
			Utils.safeClose(inputStream);
			inputStream = null;

			Point sourceSize = new Point(inputBitmap.getWidth(), inputBitmap.getHeight());
			Point targetSize = routine.sdk.supportImageSize(sourceSize);
			if (targetSize == null || targetSize.x == -1 || targetSize.y == -1) {
				return CropDemoApp.INVALIDFILE;
			}

			Bitmap scaledBitmap = Bitmap.createScaledBitmap(inputBitmap, targetSize.x, targetSize.y, true);
			if (scaledBitmap == null) {
				return CropDemoApp.INVALIDFILE;
			} else if (!scaledBitmap.equals(inputBitmap)) {
				inputBitmap.recycle();
			}

			// Setup source image
			MetaImage sourceImage = new MetaImage(scaledBitmap, mContext.getContentResolver(), sourceUri);
			sourceImage.setStrongShadows(mStrongShadows);

			// Make original
			MetaImage originImage = routine.sdk.imageOriginal(sourceImage);
			MetaImage.safeRecycleBitmap(sourceImage, originImage);
			sourceImage = null;

			CropData cropData = new CropData(mCorners, originImage);
			cropData.setRotationAngle(mRotationAngle);
			originImage.setExifOrientation(cropData.getExifRotation());

			// Crop
			Corners corners = cropData.getRotatedCorners();
			MetaImage croppedImage = routine.sdk.correctDocument(originImage, corners);
			if (croppedImage == null) {
				return CropDemoApp.PROCESSING;
			}
			MetaImage.safeRecycleBitmap(originImage, croppedImage);
			originImage = null;

			MetaImage targetImage = null;
			switch (mProfile) {
				case CropDemoApp.OriginateImage:
					targetImage = routine.sdk.imageOriginal(croppedImage);
					break;
				case CropDemoApp.NoBinarization:
					targetImage = routine.sdk.imageOriginal(croppedImage);
					break;

				case CropDemoApp.BWBinarization:
					targetImage = routine.sdk.imageBWBinarization(croppedImage);
					break;

				case CropDemoApp.GrayBinarization:
					targetImage = routine.sdk.imageGrayBinarization(croppedImage);
					break;

				case CropDemoApp.ColorBinarization:
					targetImage = routine.sdk.imageColorBinarization(croppedImage);
					break;
			}
			MetaImage.safeRecycleBitmap(croppedImage, targetImage);
			croppedImage = null;

			// OK
			mTargetImage = targetImage;
		} catch (OutOfMemoryError e) {
			return CropDemoApp.OUTOFMEMORY;
		} catch (FileNotFoundException e) {
			return CropDemoApp.INVALIDFILE;
		} finally {
			Utils.safeClose(inputStream);
			routine.close();
		}

		return CropDemoApp.NOERROR;
	}

	@Override
	protected void onPostExecute(Integer result) {
		mListener.onProcessImageComplete(this, mTargetImage, result);
	}
}
