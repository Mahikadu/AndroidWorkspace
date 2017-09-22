package com.autodetectimage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;


import com.autodetectimage.widget.ZoomableImageView;
import com.pixelnetica.imagesdk.Corners;

import java.util.ArrayList;
import java.util.List;

public class CropImageView extends ZoomableImageView {

	// Crop points inside original image
	private CropData cropData;

	// Initial tap
	private PointF initPoint;
	private PointF movePoint;
	private PointF deltaMove;
	
	// Edges lines
	Path edgeLines;
	
	// Corners screen (!) coordinates
	boolean cornersValid;
	PointF [] cornersPos;
	List<DrawSector> cornersThumbs;
	
	// edges lines paint
	Paint edgesPaint;
	// invalid lines paint
	Paint invalidEdgesPaint;
	// corners inactive paint
	Paint cornersPaint;
	// active corner paint
	Paint activeCornerPaint;
	// Corner grip radius
	float mCornerGripRadius;

	// Corners numbers paint
	Paint mNumberPaint;
	
	// Current drag corner index
	int activeCornerIndex = -1;	

	public CropImageView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public CropImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onTransformChanged() {
		update();
	}

	public void setCropData(CropData cropData) {
		this.cropData = cropData;
		if (cropData != null) {
			// update() inside
			rotateImageToFit(this.cropData.getRotationAngle());
		} else {
			update();
		}
	}
	
	public CropData getCropData() {
		return cropData;
	}
		
	public void setScaleFactor(float value) {
		//scaleFactor = value;
		//update();
	}
	
	public void rotateLeft() {
		if (cropData != null) {
			cropData.rotateTo(-90f);			
			rotateImageToFit(cropData.getRotationAngle());
			//update();
		}
	}
	
	public void rotateRight() {
		if (cropData != null) {
			cropData.rotateTo(+90);
			rotateImageToFit(cropData.getRotationAngle());
			//update();
		}
	}

	public void rotateImageToFit(float angle) {

		// Create rotation matrix
		Matrix matrix = new Matrix();
		matrix.setRotate(angle);

		setBaseMatrix(matrix);
	}
	
	public void revertSelection(Corners corners) {
		if (cropData != null) {
			cropData.setCorners(corners);
			update();
		}
	}
	
	public void expandSelection() {
		if (cropData != null) {
			cropData.expand();
			update();
		}
	}
	
	@Override
	public void setImageBitmap(Bitmap bitmap) {
		super.setImageBitmap(bitmap);

		// Reset view
		//rotateImageToFit(0);
		adjustPicture(null, null);
	}
	

	// Prepare all members to draw
	private void update() {
		// Setup painters

		// Edges paint
		if (edgesPaint == null) {
			edgesPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			edgesPaint.setColor(Color.GREEN);
			edgesPaint.setStyle(Style.STROKE);
			edgesPaint.setStrokeWidth(pxFromDp(2));
			edgesPaint.setPathEffect(new DashPathEffect(new float[]{pxFromDp(5), pxFromDp(5)}, 0));
		}
		
		if (invalidEdgesPaint == null) {
			// Same as edges but red
			invalidEdgesPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			invalidEdgesPaint.setColor(Color.RED);
			invalidEdgesPaint.setStyle(Style.STROKE);
			invalidEdgesPaint.setStrokeWidth(pxFromDp(2));
			invalidEdgesPaint.setPathEffect(new DashPathEffect(new float[]{pxFromDp(5), pxFromDp(5)}, 0));
			
		}
		
		// Corners paint
		if (cornersPaint == null) {
			cornersPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			cornersPaint.setColor(Color.GREEN);
			cornersPaint.setStyle(Style.FILL_AND_STROKE);
		}
		
		// Active corner paint
		if (activeCornerPaint == null) {
			activeCornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			activeCornerPaint.setColor(Color.RED);
			activeCornerPaint.setStyle(Style.FILL_AND_STROKE);
		}

		// Setup corner grip
		if (mCornerGripRadius == 0) {
			mCornerGripRadius = pxFromDp(50f);
		}

		// Setup numbers
		if (BuildConfig.DEBUG && mNumberPaint == null) {
			mNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			mNumberPaint.setColor(Color.BLUE);
			mNumberPaint.setTextSize(pxFromDp(18.0f));
		}
		
		// Draw cropper
		if (cropData != null && isZoomValid()) {
			// Recalculate crop data to corners						
			PointF pf = new PointF();
			cornersPos = new PointF[4];  
			for (int i = 0; i < 4; i++) {
				// convert to display bitmap
				pf.x = cropData.points[i].x ;//* displayWidth / cropData.totalWidth;
				pf.y = cropData.points[i].y ;//* displayHeight / cropData.totalHeight;
				cornersPos[i] = getDisplayPoint(pf);
						//getBitmapCoord(pf);
			}
						
			// Check margins
			RectF bounds = getDisplayBounds();
			//bounds.offset(getScrollX(), getScrollY());
			/*new RectF(0, 0, displayWidth, displayHeight);
			getImageMatrix().mapRect(bounds);*/

			if (deltaMove != null && activeCornerIndex >= 0) {
				cornersPos[activeCornerIndex].offset(deltaMove.x, deltaMove.y);
				cornersPos[activeCornerIndex].x = Math.max(bounds.left, Math.min(bounds.right, cornersPos[activeCornerIndex].x));
				cornersPos[activeCornerIndex].y = Math.max(bounds.top, Math.min(bounds.bottom, cornersPos[activeCornerIndex].y));
			}
			
			// Check corners are valid
			cornersValid = validateCornersPos(cornersPos, bounds);

			// Corners edges
			if (edgeLines == null) {
				edgeLines = new Path();
			}		
			edgeLines.reset();
			// Top-left start point
			edgeLines.moveTo(cornersPos[0].x, cornersPos[0].y);
			// Top-right
			edgeLines.lineTo(cornersPos[1].x, cornersPos[1].y);
			// Bottom-RIGHT
			edgeLines.lineTo(cornersPos[3].x, cornersPos[3].y);
			// Bottom-left
			edgeLines.lineTo(cornersPos[2].x, cornersPos[2].y);
			// Return to start point
			edgeLines.lineTo(cornersPos[0].x, cornersPos[0].y);
			// finish
			edgeLines.close();
			
			// Corners thumbs
			if (cornersThumbs == null) {
				cornersThumbs = new ArrayList<DrawSector>();
			}
			
			final float radius = pxFromDp(10f);
			cornersThumbs.clear();
			cornersThumbs.add(new DrawSector(cornersPos[0], radius, calcAngle(cornersPos[0], cornersPos[2]), calcAngle(cornersPos[0], cornersPos[1]), cornersPaint));
			cornersThumbs.add(new DrawSector(cornersPos[1], radius, calcAngle(cornersPos[1], cornersPos[0]), calcAngle(cornersPos[1], cornersPos[3]), cornersPaint));
			cornersThumbs.add(new DrawSector(cornersPos[2], radius, calcAngle(cornersPos[2], cornersPos[3]), calcAngle(cornersPos[2], cornersPos[0]), cornersPaint));
			cornersThumbs.add(new DrawSector(cornersPos[3], radius, calcAngle(cornersPos[3], cornersPos[1]), calcAngle(cornersPos[3], cornersPos[2]), cornersPaint));
			
			if (activeCornerIndex >= 0) {
				cornersThumbs.get(activeCornerIndex).setPainter(activeCornerPaint);			
			}
		} else {
			edgeLines = null;
			cornersThumbs = null;
			cornersPos = null;
		}

		invalidate();
	}

	private boolean validateCornersPos(PointF [] corners, RectF bounds) {
		final Point [] points = new Point[corners.length];
		for (int i = 0; i < corners.length; ++i) {
			points[i] = new Point(
					Math.round(corners[i].x - bounds.left),
					Math.round(corners[i].y - bounds.top));
		}

		final Point size = new Point(
				Math.round(bounds.width()),
				Math.round(bounds.height()));
		return CropData.validateCorners(getContext(), points, size);
	}

	// Angle relative X-axis
	private final float calcAngle(PointF p1, PointF p2) {
		float angle = (float) (180/Math.PI * Math.atan2(p2.y - p1.y, p2.x - p1.x));
		
		// Normalize angle to [0..360]
		if (angle < 0) {
			angle += 360f;
		}
		
		return angle;
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);		

		if (edgeLines != null) {
			Paint p = cornersValid ? edgesPaint : invalidEdgesPaint;
			canvas.drawPath(edgeLines, p);
		}
		
		if (cornersThumbs != null) {
			for (DrawSector s : cornersThumbs) {
				s.draw(canvas);
			}
			
		}

		// Show corners number to debug
		if (BuildConfig.DEBUG && cornersPos != null) {
			for (int i = 0; i < cornersPos.length; ++i) {
				PointF p = cornersPos[i];
				canvas.drawText(Integer.toString(i), p.x - 10, p.y - 10, mNumberPaint);
			}
		}
	}
	
	private float pxFromDp(float dp) {
	    return dp * getResources().getDisplayMetrics().density;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (super.onTouchEvent(event)) {
			return true;
		}
		
		switch (event.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
			// Store initial point
			initPoint = new PointF(event.getX(), event.getY());

			if (cropData != null) {
				// Store image matrix
				/*originalMatrix = new Matrix( getImageMatrix() );
				originalScaleType = getScaleType();
				
				if (scaleFactor > 0)*/ {
					// Detect active corner to move
					// Select closest corner
					activeCornerIndex = -1;
					double dist = mCornerGripRadius; //Double.MAX_VALUE;
					final PointF checkPoint = new PointF(initPoint.x + getScrollX(), initPoint.y + getScrollY());
					for (int i = 0; i < cornersPos.length; i++) {
						double dx = checkPoint.x - cornersPos[i].x;
						double dy = checkPoint.y - cornersPos[i].y;
						double d = Math.sqrt(dx * dx + dy * dy);
						if (d < dist) {
							dist = d;
							activeCornerIndex = i;
						}
					}
					if (activeCornerIndex < 0) {
						break;
					} else if (activeCornerIndex >= cornersPos.length) {
						throw new IllegalStateException("Invalid activeCornerIndex " + activeCornerIndex);

					}

					// update() inside
					PointF focus = new PointF(
							cornersPos[activeCornerIndex].x - getScrollX(),
							cornersPos[activeCornerIndex].y - getScrollY());
					setZoomScale(1.0f, focus);
					//update();

					/*setScaleType(ScaleType.MATRIX);
					editorMatrix = new Matrix( originalMatrix );
					// Scale to tap point
					editorMatrix.postScale(scaleFactor, scaleFactor, initPoint.x, initPoint.y);
					setImageMatrix(editorMatrix);*/
				}
			} else {
				update();
			}

			// Notify listeners
			performClick();
			break;
			
		case MotionEvent.ACTION_MOVE:
			movePoint = new PointF(event.getX(), event.getY());
			deltaMove = new PointF(movePoint.x - initPoint.x, movePoint.y - initPoint.y);
			update();
			break;
			
		case MotionEvent.ACTION_UP:
			// Store corner position
			if (cornersPos != null && activeCornerIndex >= 0) {
				if (cropData == null) {
					throw new IllegalStateException("cropData is null");
				}

				// Calculate coordinates in display bitmap
				PointF picturePoint = getPicturePoint(cornersPos[activeCornerIndex]);
						//getPointerCoord(cornersPos[activeCornerIndex]);
				
				// Calculate coordinates in source bitmap
				int imgX = Math.round(picturePoint.x); //(int) (displayPoint.x * cropData.totalWidth / displayWidth);
				int imgY = Math.round(picturePoint.y); //(int) (displayPoint.y * cropData.totalHeight / displayHeight);
				
				cropData.points[activeCornerIndex].set(imgX, imgY);				
			}			
			// NOTE: No break! It is not an error.
			
		case MotionEvent.ACTION_CANCEL:
			initPoint = null;
			movePoint = null;	
			deltaMove = null;
			activeCornerIndex = -1;

			/*if (originalScaleType != null) {
				setScaleType(originalScaleType);
			}
			if (originalMatrix != null) {
				setImageMatrix(originalMatrix);
			}
			update();*/
			setScaleToFit();
			break;
		}
		
		return true;
	}
	
	/*private PointF getPointerCoord(@NonNull PointF viewCoord) {
		final float [] coord = new float [] {viewCoord.x, viewCoord.y};
		Matrix matrix = new Matrix();
		getImageMatrix().invert(matrix);
		matrix.postTranslate(getScrollX(), getScrollY());
		matrix.mapPoints(coord);
		
		return new PointF(coord[0], coord[1]);
	}
	
	private PointF getBitmapCoord(@NonNull PointF bitmapCoord) {
		final float [] coord = new float [] {bitmapCoord.x, bitmapCoord.y};
		Matrix matrix = new Matrix(getImageMatrix());
		matrix.preTranslate(-getScrollX(), -getScrollY());	// ????
		getImageMatrix().mapPoints(coord);
		
		return new PointF(coord[0], coord[1]);
	}*/
	
}
