package com.autodetectimage.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import com.autodetectimage.R;
import com.autodetectimage.util.MovingAverage;


/**
 * Created by Denis on 28.02.2015.
 */
public class CameraOverlay extends View  implements ICameraOverlay {

	private boolean mShowCorners;
	private boolean mAlertMode;

	private class Task implements Runnable {
		private final MovingAverage average = new MovingAverage(8, 20);
		private PointF [] documentCorners;

		/**
		 * Starts executing the active part of the class' code. This method is
		 * called when a thread is started that has been created with a class which
		 * implements {@code Runnable}.
		 */
		@Override
		public void run() {
			average.duplicate();
			buildCorners(mShowCorners);
		}

		void reset() {
			average.reset();
		}

		private void updateCorners(PointF [] points, boolean animate) {
			if (points != null) {
				final float [] vector = new float[points.length*2];
				for (int i = 0; i < points.length; ++i) {
					vector[i*2] = points[i].x;
					vector[i*2 + 1] = points[i].y;
				}
				average.append(vector);
			} else {
				average.append(null);
			}

			buildCorners(animate);
		}

		void buildCorners(boolean animate) {
			float [] avg = average.average(true);
			if (avg != null) {
				documentCorners = new PointF[avg.length/2];
				for (int i = 0; i < documentCorners.length; ++i) {
					documentCorners[i] = new PointF(Math.round(avg[i * 2]), Math.round(avg[i * 2 + 1]));
				}
			} else {
				documentCorners = null;
			}

			if (animate) {
				postDelayed(this, 50);
			}

			invalidate();
		}

		void setPoints(PointF [] points, boolean animate) {
			removeCallbacks(this);
			updateCorners(points, animate);
		}

		// Drawing
		private final Path edgeLines = new Path();
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

		void initPaint(Paint.Style style, float width, Paint.Join join, Paint.Cap cap) {
			paint.setStyle(style);
			paint.setStrokeWidth(width);
			paint.setStrokeJoin(join);
			paint.setStrokeCap(cap);
		}
		void drawEdges(Canvas canvas, int color, boolean fade) {
			if (mShowCorners && documentCorners != null) {
				edgeLines.reset();
				// Top-left start point
				edgeLines.moveTo(documentCorners[0].x, documentCorners[0].y);
				// Top-right
				edgeLines.lineTo(documentCorners[1].x, documentCorners[1].y);
				// Bottom-RIGHT
				edgeLines.lineTo(documentCorners[3].x, documentCorners[3].y);
				// Bottom-left
				edgeLines.lineTo(documentCorners[2].x, documentCorners[2].y);
				// Return to start point
				edgeLines.lineTo(documentCorners[0].x, documentCorners[0].y);
				// finish
				edgeLines.close();

				paint.setColor(color);
				if (fade) {
					paint.setAlpha(average.fullness(Color.alpha(color)));
				}

				canvas.drawPath(edgeLines, paint);
			}
		}

	}

	// Current corners
	private final Task mCornersTask = new Task();
	private final Task mBoundsTask = new Task();

	// Drawing
	private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private float mFrameWidth = 5;
	private int mFrameColor = Color.WHITE;
	private int mBoundsColor = Color.GRAY;
	private int mAlertColor = Color.BLUE;

	private final Runnable mCancelAlert = new Runnable() {
		@Override
		public void run() {
			mAlertMode = false;
			invalidate();
		}
	};

	public CameraOverlay(Context context) {
		super(context);
		initPaint(null, 0, 0);
	}

	public CameraOverlay(Context context, AttributeSet attrs) {
		super(context, attrs);
		initPaint(attrs, 0, 0);
	}

	public CameraOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initPaint(attrs, defStyleAttr, 0);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public CameraOverlay(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		initPaint(attrs, defStyleAttr, defStyleRes);
	}

	@Override
	public void onDraw(Canvas canvas) {
		mCornersTask.drawEdges(canvas, (mAlertMode) ? mAlertColor : mFrameColor, true);
		mBoundsTask.drawEdges(canvas, mBoundsColor, false);
	}

	@Override
	public void showCorners(boolean shown) {
		if (shown != mShowCorners) {
			mShowCorners = shown;
			if (mShowCorners) {
				mCornersTask.reset();
				mBoundsTask.reset();
			}
			invalidate();
		}
	}

	@Override
	public void showAlert(boolean alert, int delay) {
		removeCallbacks(mCancelAlert);
		mAlertMode = alert;
		invalidate();

		if (alert && delay > 0) {
			postDelayed(mCancelAlert, delay);
		}
	}

	@Override
	public void setDocumentCorners(PointF[] points) {
		mCornersTask.setPoints(points, mShowCorners);
	}

	@Override
	public void setDocumentBounds(PointF[] points) {
		mBoundsTask.setPoints(points, mShowCorners);
	}

	private void initPaint(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		final TypedArray ar = getContext().obtainStyledAttributes(attrs, R.styleable.CameraOverlay, defStyleAttr, defStyleRes);
		mFrameWidth = ar.getDimension(R.styleable.CameraOverlay_frameWidth, mFrameWidth);
		mFrameColor = ar.getColor(R.styleable.CameraOverlay_frameColor, mFrameColor);
		mBoundsColor = ar.getColor(R.styleable.CameraOverlay_boundsColor, mBoundsColor);
		mAlertColor = ar.getColor(R.styleable.CameraOverlay_alertColor, mAlertColor);
		ar.recycle();

		mCornersTask.initPaint(Paint.Style.STROKE, mFrameWidth, Paint.Join.ROUND, Paint.Cap.ROUND);
		mBoundsTask.initPaint(Paint.Style.STROKE, 5, Paint.Join.MITER, Paint.Cap.BUTT);
	}
}
