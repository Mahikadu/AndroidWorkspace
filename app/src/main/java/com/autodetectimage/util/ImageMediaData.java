package com.autodetectimage.util;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Some image data from media store
 * Created by Denis on 21.10.2016.
 */

public class ImageMediaData {
	@NonNull
	public final String path;
	@NonNull
	public final int orientation;

	ImageMediaData(String path, int orientation) {
		this.path = path;
		this.orientation = orientation;
	}

	@Nullable
	public static ImageMediaData create(@NonNull ContentResolver cr, @NonNull Uri contentUri) {
		Cursor cursor = null;
		try {
			String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.ImageColumns.ORIENTATION};
			cursor = cr.query(contentUri, projection, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				final String path = cursor.getString(0);
				final int degree = cursor.getInt(1);

				// Define Exif orientation
				int exifOrientation;
				switch (degree) {
					case 0:
						exifOrientation = ExifInterface.ORIENTATION_NORMAL;
						break;
					case 90:
						exifOrientation = ExifInterface.ORIENTATION_ROTATE_90;
						break;
					case 180:
						exifOrientation = ExifInterface.ORIENTATION_ROTATE_180;
						break;
					case 270:
						exifOrientation = ExifInterface.ORIENTATION_ROTATE_270;
						break;
					default:
						exifOrientation = ExifInterface.ORIENTATION_UNDEFINED;
				}

				return new ImageMediaData(path, exifOrientation);
			} else {
				return null;
			}

		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

}
