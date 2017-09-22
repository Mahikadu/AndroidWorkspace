package com.autodetectimage.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;


import com.autodetectimage.util.Utils;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Load picture asynchronous and set it to ImageView
 * Created by Denis on 02.05.2015.
 */
public class LoadPictureTask extends AsyncTask<Uri, Void, Bitmap> {

    public interface Callbacks {
        Bitmap doRemakePicture(LoadPictureTask sender, Bitmap bitmap);
        void onPictureComplete(LoadPictureTask sender, Bitmap bitmap);
    }

	@NonNull
    private final Context mContext;

	@NonNull
    private final Callbacks mCallbacks;

    public LoadPictureTask(@NonNull Context context, @NonNull Callbacks callbacks) {
        super();
	    this.mContext = context;
        this.mCallbacks = callbacks;
    }

    @Override
    protected Bitmap doInBackground(Uri... params) {
	    final Uri pictureFile = params[0];
	    Bitmap source = Utils.loadPicture(mContext, pictureFile);

	    // Transform if need
		return mCallbacks.doRemakePicture(this, source);
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        mCallbacks.onPictureComplete(this, bitmap);
    }
}
