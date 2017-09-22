package com.autodetectimage.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import android.util.AttributeSet;
import android.widget.ImageView;


import java.io.File;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Created by Denis on 27.05.2015.
 */
public class AsyncLoadImageView extends ImageView {
    public interface LoadPictureListener {
        void onPictureFile(AsyncLoadImageView sender, Object task, boolean isReady);
        void onLoadPicture(AsyncLoadImageView sender, Object task, Bitmap bitmap);
    }

    private final ArrayList<LoadPictureListener> mListeners = new ArrayList<LoadPictureListener>();

	// Image cache
	private static WeakHashMap<Uri, Bitmap> sPictureCache = new WeakHashMap<>();

	// Special value to check failed bitmaps
	private static Bitmap sNullBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);

    private Uri mPictureUri;
	private Bitmap mPictureBitmap;
    private LoadPictureTask mLoadPictureTask;

    public AsyncLoadImageView(Context context) {
        super(context);
    }

    public AsyncLoadImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AsyncLoadImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AsyncLoadImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void addLoadPictureListener(LoadPictureListener listener) {
        mListeners.add(listener);
    }

    public void removeLoadPictureListener(LoadPictureListener listener) {
        mListeners.remove(listener);
    }

	private Bitmap getCachedPicture(Uri pictureUri) {
		return sPictureCache.get(pictureUri);
	}

	private void putCachedPicture(Uri pictureUri, Bitmap pictureBitmap) {
		if (pictureBitmap == null) {
			pictureBitmap = sNullBitmap;
		}
		sPictureCache.put(pictureUri, pictureBitmap);
	}

    // Asynchronous load picture
    public void loadPicture(final Uri pictureUri, final Object task, boolean forceReload) {
	    // Check image cache and do not reload picture
	    final Bitmap cachedPicture;
	    if (forceReload) {
		    cachedPicture = null;
	    } else {
		    cachedPicture = getCachedPicture(pictureUri);
	    }

	    if (cachedPicture != null) {
		    Bitmap pictureBitmap;
		    if (cachedPicture == sNullBitmap) {
			    pictureBitmap = null;
		    } else {
			    pictureBitmap = cachedPicture;
		    }

		    mPictureUri = pictureUri;
		    onPictureFile(task, pictureBitmap != null);
		    if (pictureBitmap != mPictureBitmap) {
			    setImageBitmap(pictureBitmap);
		    }
		    onLoadPicture(task, pictureBitmap);
	    } else {

		    // Load
		    mPictureUri = pictureUri;
		    onPictureFile(task, false);
		    mLoadPictureTask = new LoadPictureTask(getContext(),
				    new LoadPictureTask.Callbacks() {
			    @Override
			    public Bitmap doRemakePicture(LoadPictureTask sender, Bitmap bitmap) {
				    return AsyncLoadImageView.this.remakePicture(task, bitmap);
			    }

			    @Override
			    public void onPictureComplete(LoadPictureTask sender, Bitmap bitmap) {
				    if (sender == mLoadPictureTask) {
					    mLoadPictureTask = null;
					    // Store bitmap in the cache
					    putCachedPicture(pictureUri, bitmap);
					    setImageBitmap(bitmap);
					    AsyncLoadImageView.this.onLoadPicture(task, bitmap);
				    }
			    }
		    });
		    mLoadPictureTask.execute(pictureUri);
	    }
    }

    protected void onPictureFile(Object task, boolean isReady) {
        for (LoadPictureListener listener : mListeners) {
            listener.onPictureFile(this, task, isReady);
        }
    }

    protected void onLoadPicture(Object task, Bitmap bitmap) {
        for (LoadPictureListener listener : mListeners) {
            listener.onLoadPicture(this, task, bitmap);
        }
    }

	@Override
	public void setImageBitmap(Bitmap bm) {
		super.setImageBitmap(bm);
		mPictureBitmap = bm;
		if (mPictureBitmap != null) {
			onPictureSize(new Point(mPictureBitmap.getWidth(), mPictureBitmap.getHeight()));
		} else {
			onPictureSize(null);
		}
	}

	@Override
	public void setImageDrawable(Drawable drawable) {
		super.setImageDrawable(drawable);
		mPictureBitmap = null;  // unknown bitmap
		if (drawable != null) {
			final Rect bounds = drawable.getBounds();
			onPictureSize(new Point(bounds.width(), bounds.height()));
		} else {
			onPictureSize(null);
		}
	}

	protected void onPictureSize(Point size) {
		// Empty
	}

    protected Bitmap remakePicture(Object task, Bitmap bitmap) {
        // No transform by default
        return bitmap;
    }

	// Save/LoadState
	static class SavedState extends BaseSavedState {
		final static int VERSION = 1;
		Uri mPictureUri;

		public SavedState(Parcelable superState) {
			super(superState);
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			super.writeToParcel(out, flags);
			out.writeInt(VERSION);
			if (mPictureUri != null) {
				out.writeString(mPictureUri.toString());
			} else {
				out.writeString(null);
			}
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
				String path = in.readString();
				if (path != null) {
					mPictureUri = Uri.parse(path);
				}
			}
		}
	}

	@Override
	public Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		SavedState ss = new SavedState(superState);

		ss.mPictureUri = mPictureUri;
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

		if (ss.mPictureUri != null) {
			loadPicture(ss.mPictureUri, this, false);
		}
	}
}
