package com.autodetectimage.camera;

import android.support.annotation.Nullable;

import com.pixelnetica.imagesdk.Corners;

/**
 * Created by Denis on 02.06.2016.
 */
public class AutoShotDetector {

	private int mStableRadius = 7;
	private int mStableDelay = 1000;
	private int mStableCount = 3;

	private long mStartTime;
	private int mTotalCount;
	private final Corners mCornersAverage = new Corners();

	/**
	 *
	 * @param corners null mean bad corners. Cancel detection
	 */
	public synchronized boolean addDetectedCorners(@Nullable Corners corners) {
		// Check params
		if (mStableRadius <=0 || mStableDelay <= 0 || mStableCount <= 0) {
			return false;
		}

		// No corners detected. Reset detection
		if (corners == null) {
			reset();
			return false;
		}

		// First corners after reset
		if (mTotalCount == 0) {
			// Start new detection
			mStartTime = System.currentTimeMillis();
		} else {
			// Check new corners in specified area
			for (int i = 0; i < Corners.LENGTH; i++) {
				final double deltaX = mCornersAverage.points[i].x/(double)mTotalCount - corners.points[i].x;
				final double deltaY = mCornersAverage.points[i].y/(double)mTotalCount - corners.points[i].y;
				final double radius = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

				if (radius > mStableRadius) {
					reset();
					return false;
				}
			}

			final long time = System.currentTimeMillis();
			if (time - mStartTime > mStableDelay && mTotalCount > mStableCount) {
				// Start new cycle
				reset();
				// Corners are stable!
				return true;
			}
		}

		// Add current corners and continue
		mTotalCount++;
		for (int i = 0; i < Corners.LENGTH; i++) {
			mCornersAverage.points[i].x += corners.points[i].x;
			mCornersAverage.points[i].y += corners.points[i].y;
		}
		return false;
	}

	private void reset() {
		mTotalCount = 0;
		for (int i = 0; i < Corners.LENGTH; i++) {
			mCornersAverage.points[i].x = mCornersAverage.points[i].y = 0;
		}
	}

	public synchronized void setParams(int stableRadius, int stableDelay, int stableCount) {
		mStableRadius = stableRadius;
		mStableDelay = stableDelay;
		mStableCount = stableCount;
		reset();
	}
}
