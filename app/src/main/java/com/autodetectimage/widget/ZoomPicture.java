package com.autodetectimage.widget;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.autodetectimage.util.Utils;


/**
 * Helper class to handle picture zoom operation
 * Created by Denis on 22.04.2015.
 */
class ZoomPicture {

	/**
	 * Initial transform matrix. E.g. rotation.
	 * Cannot be null!
	 */
	@NonNull
	private final Matrix mInitMatrix = new Matrix();

	/**
	 * Current scale. Negative or zero value means 1
	 */
	private float mZoomScale;

	/**
	 * Managed picture size
	 */
	private final Rect mPictureBounds = new Rect();

	/**
	 * Pivot is point on the PICTURE (in relative coordinates)
	 * than to apply all transitions around
	 */
	private final PointF mPivot = new PointF(0, 0);


	/**
	 * Absolute shift matrix in DISPLAY coordinates
	 */
	private final PointF mShift = new PointF(0, 0);

	/**
	 * Current worker matrix
	 */
	private Matrix mMatrix = new Matrix();

	/**
	 * if some params modified we need to rebuild matrix
	 */
	private boolean mComplete;

	/**
	 * Inverse matrix, creates by request
	 */
	private Matrix mInverse;

	/**
	 * Image bounds on display
	 */
	private RectF mBounds;

	public boolean isValid()
	{
		if (mPictureBounds.width() <= 0 && mPictureBounds.height() <= 0) {
			return false;
		}

		return true;
	}

	protected final float [] getPicturePivotF()
	{
		return new float[] {
				mPictureBounds.left + (mPictureBounds.width() * mPivot.x),
				mPictureBounds.top + (mPictureBounds.height() * mPivot.y)
		};
	}

	public void setPictureBounds(@Nullable Rect bounds)
	{
		if (bounds != null) {
			mPictureBounds.set(bounds);
		} else {
			mPictureBounds.set(0, 0, 0, 0);
		}
		mComplete = false;
	}

	public void setTransform(@NonNull Matrix matrix, float scale)
	{
		if (matrix == null) {
			throw new IllegalArgumentException("Null initial matrix for ZoomPicture");
		}

		if (!mInitMatrix.equals(matrix)) {
			mInitMatrix.set(matrix);
			mComplete = false;
		}

		if (mZoomScale != scale) {
			mZoomScale = scale;
			mComplete = false;
		}

		// Reset origin
		if (mShift.x != 0 || mShift.y != 0) {
			mShift.set(0, 0);
			mComplete = false;
		}
	}

	/**
	 *
	 * @return current pivot point in display coordinates
	 */
	public PointF getDisplayPivot()
	{
		validate();

		final float [] pf = getPicturePivotF();
		ensureMatrix().mapPoints(pf);
		return new PointF(pf[0], pf[1]);
	}

	/**
	 *
	 * @return current pivot point in picture coordinates
	 */
	public final PointF getPicturePivot()
	{
		final float [] pf = getPicturePivotF();
		return new PointF(pf[0], pf[1]);
	}

	public RectF getPictureBounds() {
		return new RectF(mPictureBounds);
	}

	public RectF getDisplayBounds()
	{
		ensureMatrix();
		if (mBounds == null) {
			mBounds = new RectF(mPictureBounds);
			mMatrix.mapRect(mBounds);
		}
		return mBounds;
	}

	/**
	 * Translate display coordinates to ABSOLUTE picture coordinates
	 * @param x
	 * @param y
	 */
	public PointF getPicturePoint(float x, float y) {
		validate();

		final float [] pf = {x, y};
		ensureInverse().mapPoints(pf);
		return new PointF(pf[0], pf[1]);
	}

	/**
	 * Translate display coordinates to RELATIVE picture coordinates
	 * @param x
	 * @param y
	 * @return
	 */
	public PointF getPictureFocus(float x, float y) {
		final PointF point = getPicturePoint(x, y);
		return new PointF(
				(point.x - (float) mPictureBounds.left) / (float) mPictureBounds.width(),
				(point.y - (float) mPictureBounds.top) / (float) mPictureBounds.height());
	}

	/**
	 * Translate ABSOLUTE picture coordinates to display
	 * @param x
	 * @param y
	 * @return
	 */
	public PointF getDisplayPoint(float x, float y) {
		validate();

		final float [] pf = {x, y};
		ensureMatrix().mapPoints(pf);
		return new PointF(pf[0], pf[1]);
	}

	/**
	 * Translate RELATIVE picture coordinates to absolute display coordinates
	 * @param fx
	 * @param fy
	 * @return
	 */
	public PointF getDisplayFocus(float fx, float fy) {
		return getDisplayPoint(
				mPictureBounds.left + fx * mPictureBounds.width(),
				mPictureBounds.top + fy * mPictureBounds.height());
	}

	/**
	 * Move picture to 0:0 display axis
	 */
	public PointF snapToOrigin()
	{
		// Reset display focus and shift
		mShift.set(0, 0);
		mComplete = false;

		// Setup display shift
		// (ensureMatrix() inside getBounds())
		final RectF bounds = getDisplayBounds();
		mShift.set(-bounds.left, -bounds.top);
		mComplete = false;

		return new PointF(bounds.width(), bounds.height());
	}


	/**
	 * Setup all params to scale to fit. No scroll need
	 */
	public float scaleToFit(@NonNull PointF displaySize)
	{
		validate();

		if (displaySize == null) {
			throw new IllegalArgumentException("displayBounds is null for s.caleToFit()");
		}

		// Calculate scale ratio relative natural picture size
		mZoomScale = 1.0f;
		mComplete = false;

		// Change current scale to fit
		RectF bounds = getDisplayBounds();

		// Define ratio to fit
		if (displaySize.x * bounds.height() < displaySize.y * bounds.width()) {
			mZoomScale = displaySize.x / bounds.width();
		} else {
			mZoomScale = displaySize.y / bounds.height();
		}

		// Reset origin
		mShift.set(0, 0);

		mComplete = false;
		return mZoomScale;
	}

	/**
	 *  Compute only scale-to-fit ratio
	 * @param displaySize
	 * @return
	 */
	public float computeScaleToFit(@NonNull PointF displaySize) {
		validate();

		if (displaySize == null) {
			throw new IllegalArgumentException("displaySize is null for computeScaleToFit()");
		}

		// Save current zoom scale
		final float savedScale = mZoomScale;

		// Rebuild matrix for 1:1
		mZoomScale = 1.0f;
		mComplete = false;

		// Change current scale to fit
		final RectF bounds = getDisplayBounds();

		// Define ratio to fit
		float scale;
		if (displaySize.x * bounds.height() < displaySize.y * bounds.width())
		{
			scale = displaySize.x / bounds.width();
		}
		else
		{
			scale = displaySize.y / bounds.height();
		}

		// Restore scale value
		mZoomScale = savedScale;
		mComplete = false;

		return scale;
	}

	private Matrix ensureMatrix()
	{
		validate();

		if (mComplete) {
			return mMatrix;
		}

		// Reset dependencies
		mInverse = null;
		mBounds = null;

		// Translate pivot point to axis center
		final float [] pivot = getPicturePivotF();

		mMatrix.set(mInitMatrix);
		mMatrix.mapPoints(pivot);
		mMatrix.postTranslate(-pivot[0], -pivot[1]);

		// Scale to specified ratio
		mMatrix.postScale(ratio(), ratio());

		// Move picture to specified absolute offset
		mMatrix.postTranslate(mShift.x, mShift.y);

		// Matrix complete
		mComplete = true;
		return mMatrix;
	}

	protected void validate()
	{
		if (!isValid()) {
			throw new IllegalStateException("Picture size is not defined");
		}
	}

	public Matrix ensureInverse()
	{
		ensureMatrix();
		if (mInverse == null) {
			mInverse = new Matrix();
			if (!mMatrix.invert(mInverse)) {
				throw new IllegalStateException("Irreversible matrix");
			}
		}
		return mInverse;
	}

	public float ratio()
	{
		return (mZoomScale > 0) ? mZoomScale : 1.0f;
	}

	public Matrix getMatrix()
	{
		ensureMatrix();
		return new Matrix(mMatrix);
	}

	public void baseTransform(@NonNull final PointF [] points, boolean snapToOrigin) {
		validate();

		if (points == null) {
			throw new IllegalArgumentException("Null points");
		}

		// Check simple case
		if( mInitMatrix.isIdentity()) {
			return;
		}

		Matrix matrix;
		if (snapToOrigin) {
			matrix = new Matrix(mInitMatrix);
			RectF rf = new RectF(mPictureBounds);
			matrix.mapRect(rf);
			matrix.postTranslate(-rf.left, -rf.top);
		} else {
			matrix = mInitMatrix;
		}

		Matrix inverse = new Matrix();
		matrix.invert(inverse);
		Utils.matrixMapPoints(inverse, points);
	}
}
