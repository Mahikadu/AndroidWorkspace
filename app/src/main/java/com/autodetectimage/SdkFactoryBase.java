package com.autodetectimage;

import android.app.Application;

import com.pixelnetica.imagesdk.ImageSdkLibrary;

/**
 * Created by Denis on 15.07.2017.
 */

class SdkFactoryBase {
	private final Application mApplication;
	private ImageSdkLibrary mLibrary;   // Using lazy initialization

	SdkFactoryBase(Application application) {
		mApplication = application;
	}

	ImageSdkLibrary getLibrary() {
		ImageSdkLibrary library = mLibrary;
		if (library == null) {
			synchronized (this) {
				library = new ImageSdkLibrary();
				mLibrary = library;
			}
		}
		return library;
	}

	Application getApplication() {
		return mApplication;
	}
}
