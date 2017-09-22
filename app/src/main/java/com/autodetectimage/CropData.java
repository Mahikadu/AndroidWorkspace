package com.autodetectimage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.support.annotation.NonNull;
import android.util.Log;

import com.pixelnetica.imagesdk.Corners;
import com.pixelnetica.imagesdk.MetaImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class CropData extends Corners {

	// Original image total size
	public final int totalWidth;
	public final int totalHeight;
	
	// Image rotation
	private float rotationAngle;
	
	public CropData(@NonNull Corners src, @NonNull MetaImage image) {
		super(src);
		final Bitmap bm = image.getBitmap();
		if (bm == null) {
			throw new IllegalArgumentException("MetaImage with null bitmap");
		}
		this.totalWidth = bm.getWidth();
		this.totalHeight = bm.getHeight();
		setupRotation(image);
	}
	
	public CropData(@NonNull CropData src) {
		super(src);
		this.totalWidth = src.totalWidth;
		this.totalHeight = src.totalHeight;
		this.rotationAngle = src.rotationAngle;
	}
	
	public void setCorners(@NonNull Corners src) {
		for (int i = 0; i < points.length; i++) {
			points[i].set(src.points[i].x, src.points[i].y);
		}
	}

	public Corners getRotatedCorners() {
		// Simple copy
		if (rotationAngle == 0) {
			return new Corners(this);
		}
		
		Matrix matrix = new Matrix();
		matrix.setRotate(getRotationAngle());
		
		// Calculate new image bounds
		RectF rf = new RectF(0, 0, totalWidth, totalHeight);
		matrix.mapRect(rf);
		matrix.postTranslate(-rf.left, -rf.top);
		
		// process corners
		float [] pts = new float [points.length*2];
		for (int i = 0; i < points.length; i++) {
			pts[i*2] = points[i].x;
			pts[i*2+1] = points[i].y;
		}
		matrix.mapPoints(pts);
		
		// Arrange points to order
		
		// Sort by Y
		ArrayList<PointF> ptfs = new ArrayList<PointF>(points.length);
		for (int i = 0; i < points.length; i++)
		{
			ptfs.add(new PointF(pts[i*2], pts[i*2+1]));
		}
		Collections.sort(ptfs, new Comparator<PointF>() {
			@Override
			public int compare(PointF p1, PointF p2)
			{				
				if (p1.y < p2.y) {
					return -1;
				} else if (p1.y > p2.y) {
					return +1;
				} else {
					return 0;
				}
			}
		});

		// Corner points
		Point lt, rt, lb, rb;
		// First pair is top corners indexed 0 and 1
		if (ptfs.get(0).x < ptfs.get(1).x) {
			lt = new Point((int)ptfs.get(0).x, (int)ptfs.get(0).y);
			rt = new Point((int)ptfs.get(1).x, (int)ptfs.get(1).y);
		} else {
			lt = new Point((int)ptfs.get(1).x, (int)ptfs.get(1).y);
			rt = new Point((int)ptfs.get(0).x, (int)ptfs.get(0).y);
		}
		
		// Second pair is bottom corners indexed 2 and 3
		if (ptfs.get(2).x < ptfs.get(3).x) {
			lb = new Point((int)ptfs.get(2).x, (int)ptfs.get(2).y);
			rb = new Point((int)ptfs.get(3).x, (int)ptfs.get(3).y);
		} else {
			lb = new Point((int)ptfs.get(3).x, (int)ptfs.get(3).y);
			rb = new Point((int)ptfs.get(2).x, (int)ptfs.get(2).y);
		}
		
		return new Corners(lt, rt, lb, rb);
	}
	
	public void rotateTo(float angle) {
		rotationAngle += angle;
	}
	
	public float getRotationAngle() {
		return normalizeAngle(rotationAngle);
	}

	public int getExifRotation() {
		if (rotationAngle == 90f) {
			return ExifInterface.ORIENTATION_ROTATE_90;
		} else if (rotationAngle == 180f) {
			return ExifInterface.ORIENTATION_ROTATE_180;
		} else if (rotationAngle == 270f) {
			return ExifInterface.ORIENTATION_ROTATE_270;
		} else {
			return ExifInterface.ORIENTATION_NORMAL;
		}
	}

	public void setRotationAngle(float value) {
		rotationAngle = normalizeAngle(value);
	}

	public void setupRotation(@NonNull MetaImage image) {
		switch (image.getExifOrientation()) {
			case ExifInterface.ORIENTATION_UNDEFINED:
			case ExifInterface.ORIENTATION_NORMAL:
				rotationAngle = 0;
				break;
			case ExifInterface.ORIENTATION_ROTATE_90:
				rotationAngle = 90;
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				rotationAngle = 180;
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
				rotationAngle = 270;
				break;
			default:
				// Unsupported orientation
				rotationAngle = 0;
				Log.d(AppLog.TAG, "Unsupported EXIF orientation " + image.getExifOrientation());
		}
	}
	
	public static float normalizeAngle(float angle) {
		float a = angle % 360f;
		if (a < 0) {
			a += 360f;
		}
		return a;
	}

	public static boolean validateCorners(@NonNull Context context, @NonNull Point [] points, @NonNull Point bounds) {
		// Don't validate corners to use SDK regulation
		//return true;

		// Using SDK validation
		final CropDemoApp theApp = (CropDemoApp) context.getApplicationContext();

		// Convert to Corners
		final Corners corners = new Corners(
				points[0], points[1],
				points[2], points[3]);


		// USE SDK
		// Bad way to newInstance SDK each time. This is DEMO application
		DocImageRoutine routine = theApp.createRoutine(true);
		try {
			return routine.sdk.validateDocumentCorners(corners, bounds);
		} finally {
			routine.close();
		}
	}

	public boolean validateCorners(@NonNull Context context) {
		// Convert to PointF
		final Point size = new Point(totalWidth, totalHeight);
		return validateCorners(context, points, size);
	}
		
	public void expand() {
		points[0].x = 0;
		points[0].y = 0;
		points[1].x = totalWidth;
		points[1].y = 0;
		points[2].x = 0;
		points[2].y = totalHeight;
		points[3].x = totalWidth;
		points[3].y = totalHeight;
	}
}
	
