package com.autodetectimage;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.pixelnetica.imagesdk.Corners;
import com.pixelnetica.imagesdk.MetaImage;

import java.lang.ref.WeakReference;

public class CropDocImageTask extends AsyncTask<MetaImage, Integer, MetaImage> {
	
	public interface Listener {
		void onCropDocImageComplete(CropDocImageTask task, @CropDemoApp.ProcessingError int error, MetaImage result);
	}

	@NonNull
	private final DocImageRoutine.Factory mFactory;

	final private Corners mDocumentCorners;
	@CropDemoApp.ProcessingProfile
	final private int mProcessingProfile;
	@CropDemoApp.ProcessingError
	private int mProcessingError = CropDemoApp.NOERROR;

	WeakReference<Listener> mListener;
	
	public CropDocImageTask(@NonNull DocImageRoutine.Factory factory, Corners corners, @CropDemoApp.ProcessingProfile int profile, Listener listener)	{
		this.mFactory = factory;
		this.mDocumentCorners = corners;
		this.mProcessingProfile = profile;
		this.mListener = new WeakReference<Listener>(listener);
	}
	
	// No real processing. Typical use as manual crop
	public boolean isOriginal()	{
		return mProcessingProfile == CropDemoApp.OriginateImage;
	}

	@Override
	protected MetaImage doInBackground(MetaImage... params) {
		MetaImage sourceImage = params[0];
		MetaImage targetImage = null;
		try
		{
			final DocImageRoutine routine = mFactory.createRoutine(false);
			try {
				if (mProcessingProfile == CropDemoApp.OriginateImage) {
					targetImage = routine.sdk.imageOriginal(sourceImage);
				} else {
					MetaImage croppedImage = routine.sdk.correctDocument(sourceImage, mDocumentCorners);
					/*if (BuildConfig.DEBUG) {
						targetImage = croppedImage;
					} else*/ {
						if (croppedImage != null) {
							switch (mProcessingProfile) {
								case CropDemoApp.OriginateImage:
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
						}
					}
				}				
			} finally {
				routine.close();
				System.gc();
			}
		} catch(OutOfMemoryError e) {
			e.printStackTrace();
			mProcessingError = CropDemoApp.OUTOFMEMORY;
		} catch(Exception e) {
			e.printStackTrace();
			mProcessingError = CropDemoApp.PROCESSING;
		} catch(Error e) {
			e.printStackTrace();
			mProcessingError = CropDemoApp.PROCESSING;
		}

		if (targetImage == null) {
			mProcessingError = CropDemoApp.PROCESSING;
		}
		return targetImage;
	}

	@Override
	protected void onPostExecute(MetaImage result) {
		if (mListener != null && mListener.get() != null) {
			mListener.get().onCropDocImageComplete(this, mProcessingError, result);
		}
	}
}
