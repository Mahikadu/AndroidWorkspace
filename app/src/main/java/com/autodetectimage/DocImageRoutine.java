package com.autodetectimage;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.pixelnetica.imagesdk.Corners;
import com.pixelnetica.imagesdk.ImageProcessing;

/**
 * Created by Denis on 02.09.2016.
 */
public class DocImageRoutine {

	public interface Factory {
		DocImageRoutine createRoutine(boolean hasLooper);
	}

	@Nullable
	private final Handler handler;

	@NonNull
	public final ImageProcessing sdk;
	@NonNull
	public final Bundle params;

	public boolean isSmartCropMode;

	public final Corners corners = new Corners();

	public DocImageRoutine(@Nullable Handler handler, @NonNull ImageProcessing sdk, @NonNull Bundle params) {
		this.handler = handler;
		this.sdk = sdk;
		this.params = params;
	}

	public void reset() {
		// Reset output parameters
		isSmartCropMode = false;
		corners.reset();
	}

	private final Runnable onClose = new Runnable() {
		@Override
		public void run() {
			sdk.destroy();
		}
	};

	public void close() {
		if (handler != null) {
			handler.post(onClose);
		} else {
			onClose.run();
		}
	}
}
