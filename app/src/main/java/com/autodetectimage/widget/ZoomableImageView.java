package com.autodetectimage.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.ImageView;


import com.autodetectimage.util.Utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by Denis on 21.04.2015.
 */
public class ZoomableImageView extends AsyncLoadImageView {

	private static final float EPS = 0.05f;
	private static final int SCALE_ZOOM = 0;    //
	private static final int SCALE_TO_FIT = 1;
	private static final int SCALE_NORMAL = 2;  // 1:1
	private static final int SCALE_TO_MAX = 3;

	private int mScaleMode = SCALE_TO_FIT;

	private float mCurrentScale = 1.0f;

	private PointF mPictureFocus;

	/**
	 * Picture focus only for scale operations
	 */
	private PointF mScaleFocus;

	/**
	 * Scale to fit value
	 */
	private float mFitScale;

	private float mNormalScale = 1.0f;

	/**
	 * Scale limit
	 */
	private float mMaxScale = 2.0f;

	/**
	 * Zoom holder
	 */
	private final ZoomPicture mZoomPicture = new ZoomPicture();

	/**
	 * Focus point to zoom
	 */
	private PointF mCenterPoint;

	private PointF mDisplaySize;

	private final Matrix mBaseMatrix = new Matrix();

	public ZoomableImageView(Context context) {
		super(context);
   }

	public ZoomableImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ZoomableImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr,
							 int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	protected void onLoadPicture(Object task, Bitmap bitmap) {
		super.onLoadPicture(task, bitmap);
		adjustPicture(null, mPictureFocus);
	}

	private boolean isTouchScrollEnabled() {
		return false;
	}

	private static class ScaleItem {
		public int mode;
		public float scale;

		public ScaleItem(int mode, float scale) {
			this.mode = mode;
			this.scale = scale;
		}
	}

	private static class ScaleComparator implements Comparator<ScaleItem> {
		@Override
		public int compare(ScaleItem lhs, ScaleItem rhs) {
			return Float.compare(lhs.scale, rhs.scale);
		}
	}

	private int defineScaleMode(float scale) {
		// Build scale array
		ScaleItem scales [] = {
				new ScaleItem(SCALE_TO_FIT, mFitScale),
				new ScaleItem(SCALE_NORMAL, mNormalScale),
				new ScaleItem(SCALE_TO_MAX, mMaxScale)
		};
		Arrays.sort(scales, new ScaleComparator());

		// Define scale mode
		// Complex case: fit scale may be less or great than normal scale

		// Check higher and lower values
		if (scale <= scales[0].scale) {
			return scales[0].mode;
		} else if (scale >= scales[scales.length-1].scale) {
			return scales[scales.length-1].mode;
		} else {
			// Check values
			for (ScaleItem item : scales) {
				if (Utils.checkFloat(scale, item.scale, EPS)) {
					return item.mode;
				}
			}
			// Not found
			return SCALE_ZOOM;
		}
	}

	@Override
	public void scrollTo(int x, int y) {
		super.scrollTo(x, y);

		if (mZoomPicture.isValid()) {
			mPictureFocus = getPictureFocus(mCenterPoint);
		}
	}

	@Override
	public void setImageBitmap(Bitmap bitmap) {
		super.setImageBitmap(bitmap);

		// Store source image dimensions
		if (bitmap != null) {
			mZoomPicture.setPictureBounds(new Rect(0, 0, bitmap.getWidth(),bitmap.getHeight()));
		} else {
			mZoomPicture.setPictureBounds(null);
		}
	}

	@Override
	public void setImageDrawable(Drawable drawable) {
		super.setImageDrawable(drawable);

		// Store source drawable dimensions
		if (drawable != null) {
			mZoomPicture.setPictureBounds(drawable.getBounds());
		} else {
			mZoomPicture.setPictureBounds(null);
		}
	}

	@Override
	public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		// Get picture focus BEFORE size changing
		PointF pictureFocus = null;
		if (mDisplaySize != null && mZoomPicture.isValid()) {
			pictureFocus = getPictureFocus(null);
		}

		super.onSizeChanged(width, height, oldWidth, oldHeight);

		// Setup display
		mDisplaySize = new PointF(width, height);
		mCenterPoint = new PointF(width / 2f, height / 2f);
		adjustPicture(mCenterPoint, pictureFocus);
	}

	/**
	 * More efficient method than {@link ImageView#computeHorizontalScrollRange()}
	 * @return
	 */
	@Override
	public int computeHorizontalScrollRange() {
		if (isTouchScrollEnabled()&& mZoomPicture.isValid()) {
			return Math.round(mZoomPicture.getDisplayBounds().width());
		} else {
			return super.computeHorizontalScrollRange();
		}
	}

	/**
	 * More efficieent method than {@link ImageView#computeVerticalScrollRange()}
	 * @return
	 */
	@Override
	public int computeVerticalScrollRange() {
		if (isTouchScrollEnabled() && mZoomPicture.isValid()) {
			return Math.round(mZoomPicture.getDisplayBounds().height());
		} else {
			return super.computeVerticalScrollRange();
		}
	}

	public void setCurrentScale(float scale) {
		mCurrentScale = scale;
		adjustPicture(null, null);
	}

	public void setScaleToFit() {
		mScaleMode = SCALE_TO_FIT;
		adjustPicture(null, null);
	}


	public void setZoomScale(float scale, PointF focus) {
		mCurrentScale = scale;
		mScaleMode = defineScaleMode(mCurrentScale);
		adjustPicture(focus, null);
	}

	public boolean isScaleToFit() {
		return mScaleMode == SCALE_TO_FIT;
	}

	public void setBaseMatrix(Matrix matrix) {
		mBaseMatrix.set(matrix);
		adjustPicture(null, null);
	}

	public Matrix getBaseMatrix() {
		return mBaseMatrix;
	}

	public PointF getPicturePoint(@NonNull PointF displayPoint) {
		final PointF pf = new PointF(displayPoint.x, displayPoint.y);
		//pf.offset(getScrollX(), getScrollY());
		return mZoomPicture.getPicturePoint(pf.x, pf.y);
	}

	public PointF getDisplayPoint(@NonNull PointF picturePoint){
		final PointF pf = mZoomPicture.getDisplayPoint(picturePoint.x, picturePoint.y);
		//pf.offset(-getScrollX(), -getScrollY());
		return pf;
	}

	public RectF getPictureBounds() {
		return mZoomPicture.getPictureBounds();
	}

	public RectF getDisplayBounds() {
		return mZoomPicture.getDisplayBounds();
	}

	/**
	 * Compute RELATIVE picture coordinates (focus)
	 * under specified display {@code #point}
	 * @param point
	 * @return
	 */
	public PointF getPictureFocus(PointF point) {
		if (point == null) {
			point = mCenterPoint;
		}

		return mZoomPicture.getPictureFocus(
				getScrollX() + point.x,
				getScrollY() + point.y);
	}

	/**
	 * Scroll to combine RELATIVE picture point {@code #focus}
	 * with display {@code #point}
	 * @param focus
	 * @param point
	 */
	public void setPictureFocus(@NonNull PointF focus, @Nullable PointF point) {
		if (focus == null) {
			throw new IllegalArgumentException("null pivot for setPictureFocus()");
		}

		if (point == null) {
			point = mCenterPoint;
		}

		if (!isScaleToFit()) {
			final PointF pf = mZoomPicture.getDisplayFocus(focus.x, focus.y);
			scrollTo(Math.round(pf.x - point.x), Math.round(pf.y - point.y));
		}
	}

	public boolean isZoomValid() {
		return mDisplaySize != null && mZoomPicture.isValid();
	}

	protected void adjustPicture(PointF screenPoint, PointF pictureFocus) {
		if (isZoomValid()) {
			// Set scale type
			if (getScaleType() != ScaleType.MATRIX) {
				setScaleType(ScaleType.MATRIX);
			}

			// Use display center if no focus specified
			if (screenPoint == null) {
				screenPoint = mCenterPoint;
			}

			// Rotate, zoom etc around specified focus point.
			// Get it before new transform!
			if (pictureFocus == null) {
				pictureFocus = getPictureFocus(screenPoint);
			}

			// Restrict minimal zoom
			mFitScale = mZoomPicture.computeScaleToFit(mDisplaySize);

			// Apply matrix
			if (mScaleMode == SCALE_TO_FIT) {
				mZoomPicture.setTransform(mBaseMatrix, 0);
				mFitScale = mZoomPicture.scaleToFit(mDisplaySize);
				mCurrentScale = mFitScale;
				// Scale to fit around center image
				pictureFocus = new PointF(0.5f, 0.5f);
			} else if (mScaleMode == SCALE_NORMAL) {
				mCurrentScale = 1.0f;
				mZoomPicture.setTransform(mBaseMatrix, mCurrentScale);
			} else if (mScaleMode == SCALE_TO_MAX) {
				mCurrentScale = mMaxScale;
				mZoomPicture.setTransform(mBaseMatrix, mCurrentScale);
			} else {
				mZoomPicture.setTransform(mBaseMatrix, mCurrentScale);
			}

			//mZoomPicture.snapToOrigin();

			final RectF bounds = mZoomPicture.getDisplayBounds();
			final PointF offset = mZoomPicture.getDisplayFocus(pictureFocus.x, pictureFocus.y);

			// Save current picture focus (to save/restore state)
			mPictureFocus = getPictureFocus(mCenterPoint);

			Point scrollPos = new Point();
			Rect scrollRect = new Rect(
					Math.round(bounds.left),
					Math.round(bounds.top),
					0, 0);

			if (bounds.width() <= mDisplaySize.x) {
				scrollRect.right = scrollRect.left;
				scrollRect.offset(Math.round((bounds.width() - mDisplaySize.x)*0.5f), 0);
				scrollPos.x = scrollRect.left;
			} else {
				scrollRect.right = Math.round(bounds.right - mDisplaySize.x);
				scrollPos.x = Math.round(offset.x - screenPoint.x);
			}

			if (bounds.height() <= mDisplaySize.y) {
				scrollRect.bottom = scrollRect.top;
				scrollRect.offset(0, Math.round((bounds.height() - mDisplaySize.y)*0.5f));
				scrollPos.y = scrollRect.top;
			} else {
				scrollRect.bottom = Math.round(bounds.bottom - mDisplaySize.y);
				scrollPos.y = Math.round(offset.y - screenPoint.y);
			}

			//setScrollBounds(scrollRect);
			setImageMatrix(mZoomPicture.getMatrix());
			scrollTo(scrollPos.x, scrollPos.y);
			onTransformChanged();
		}
	}

	public void onTransformChanged() {

	}

	// Save/Restore instance state
	static class SavedState extends BaseSavedState {
		final static int VERSION = 1;
		int scaleMode;
		float currentScale;
		PointF pictureFocus;
		final Matrix baseMatrix = new Matrix();

		SavedState(Parcelable superState) {
			super(superState);
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			super.writeToParcel(out, flags);
			out.writeInt(VERSION);
			out.writeInt(scaleMode);
			out.writeFloat(currentScale);
			if (pictureFocus == null) {
				out.writeInt(0);
			} else {
				out.writeInt(1);
				out.writeFloat(pictureFocus.x);
				out.writeFloat(pictureFocus.y);
			}
			float [] values = new float[9];
			baseMatrix.getValues(values);
			out.writeFloatArray(values);
		}

		@SuppressWarnings("hiding")
		public static final Parcelable.Creator<SavedState> CREATOR
				= new Parcelable.Creator<SavedState>() {
			@Override
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}
			@Override
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};

		private SavedState(Parcel in) {
			super(in);
			final int version = in.readInt();
			if (version <= 1) {
				scaleMode = in.readInt();
				currentScale = in.readFloat();
				if (in.readInt() != 0) {
					pictureFocus = new PointF();
					pictureFocus.x = in.readFloat();
					pictureFocus.y = in.readFloat();
				}

				float [] values = new float[9];
				in.readFloatArray(values);
				baseMatrix.setValues(values);
			}
		}
	}

	@Override
	public Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		SavedState ss = new SavedState(superState);

		ss.scaleMode = mScaleMode;
		ss.currentScale = mCurrentScale;
		ss.pictureFocus = mPictureFocus;
		ss.baseMatrix.set(mBaseMatrix);

		return ss;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (!(state instanceof SavedState)) {
			super.onRestoreInstanceState(state);
			return;
		}

		SavedState ss = (SavedState)state;
		super.onRestoreInstanceState(ss.getSuperState());

		mScaleMode = ss.scaleMode;
		mCurrentScale = ss.currentScale;
		mPictureFocus = ss.pictureFocus;
		mBaseMatrix.set(ss.baseMatrix);
	}
}
