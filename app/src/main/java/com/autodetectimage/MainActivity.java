package com.autodetectimage;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;


import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.autodetectimage.camera.CameraActivity;
import com.autodetectimage.util.Utils;
import com.example.android.basicrenderscript.ScriptC_saturation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    // Main application
    CropDemoApp theApp;

    CropImageView imageView;
    ProgressBar progressWait;

    boolean cropclick = false;

    // Crop button bar
    ViewGroup cropButtonBar;

    private static final int TAKE_PHOTO = 101;
    public RuntimePermissions RuntimePermission;

    private Bitmap[] mBitmapsOut;
    private final int NUM_BITMAPS = 2;
    private int mCurrentBitmap = 0;
    Bitmap displayBitmap;

    private Allocation mInAllocation;
    private Allocation[] mOutAllocations;
    private ScriptC_saturation mScript;
    private RenderScriptTask mCurrentTask;

    Button camera,crop,manualcrop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

       // Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));

        theApp = (CropDemoApp) getApplication();
        theApp.mainActivity = this;

        RuntimePermission = new RuntimePermissions();

         camera = (Button) findViewById(R.id.camera);
         manualcrop = (Button) findViewById(R.id.manualcrop);
         crop = (Button) findViewById(R.id.crop);

        // Setup display
        imageView = (CropImageView) findViewById(R.id.image_holder);
        progressWait = (ProgressBar) findViewById(R.id.progress_wait);

        View root = findViewById(R.id.root);
        cropButtonBar = (ViewGroup) root.findViewById(R.id.crop_button_bar);
        cropButtonBar.findViewById(R.id.btn_rotate_left).setOnClickListener(this);
        cropButtonBar.findViewById(R.id.btn_rotate_right).setOnClickListener(this);
        cropButtonBar.findViewById(R.id.btn_revert_selection).setOnClickListener(this);
        cropButtonBar.findViewById(R.id.btn_expand_selection).setOnClickListener(this);

        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTakePhoto();
            }
        });

        manualcrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                 theApp.performManualCrop();
                 crop.setVisibility(View.VISIBLE);
                manualcrop.setVisibility(View.GONE);

            }
        });
        crop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCropImage();
                cropclick = true;
                manualcrop.setVisibility(View.GONE);
            }
        });
    }


    void onCropImage() {
        // Start crop pipeline
        theApp.startCropImage(imageView.getCropData());
    }

    void updateUI() {
        setButtonsState();
        setDisplayImage();
        setWaitMode();
    }

    public void setWaitMode() {
        if (theApp.waitMode) {
            // TODO: setup a better color filter
            imageView.setColorFilter(Color.rgb(128, 128, 128), PorterDuff.Mode.LIGHTEN);
            progressWait.setVisibility(View.VISIBLE);
        } else {
            imageView.setColorFilter(0, PorterDuff.Mode.DST);
            progressWait.setVisibility(View.INVISIBLE);
        }
    }

    public void setButtonsState() {
        final boolean manualCropAvailable = theApp.imageMode == CropDemoApp.CropOrigin && theApp.processImage != null && !theApp.waitMode;
        // final boolean processingAvailable = (theApp.imageMode == CropDemoApp.Source || theApp.imageMode == CropDemoApp.CropOrigin) && theApp.processImage != null && !theApp.waitMode;
        //final boolean resultAvailable = theApp.imageMode == CropDemoApp.Target && theApp.processImage != null && !theApp.waitMode;

       /* if (buttonBar != null)
        {
            // Open & Camera buttons are always available

            // Crop button is enabled when we have a source image and no target image
            // and no current processing
            setupButtonVisible(buttonBar, R.id.btn_crop_image, processingAvailable);

            // Save button is enabled when we have a target image
            setupButtonVisible(buttonBar, R.id.btn_save_image, resultAvailable);
        }*/

        // Show or hide crop button bar
        setupButtonVisible(findViewById(R.id.root), R.id.crop_button_bar, manualCropAvailable);
    }

    private static void setupButtonVisible(@NonNull View container, int id, boolean visible) {

        View button = (View) container.findViewById(id);
        if (button == null) {
            throw new IllegalStateException("Button is null!");
        }

        if (visible) {
            button.setVisibility(View.VISIBLE);
        } else {
            button.setVisibility(View.GONE);
        }
    }



    public void setDisplayImage() {
        float displayAngle = 0;
        Bitmap displayBitmap = null;

        if (theApp.processImage != null) {

            displayBitmap = theApp.processImage.getBitmap();

            if(cropclick) {


                mBitmapsOut = new Bitmap[NUM_BITMAPS];
                for (int i = 0; i < NUM_BITMAPS; ++i) {
                    mBitmapsOut[i] = Bitmap.createBitmap(displayBitmap.getWidth(),
                            displayBitmap.getHeight(), displayBitmap.getConfig());
                }

            }

            switch (theApp.processImage.getExifOrientation()) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    displayAngle = 90f;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    displayAngle = 180f;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    displayAngle = 270f;
                    break;
                default:
                    displayAngle = 0;
            }
        }

        if(cropclick) {
            imageView.setImageBitmap(mBitmapsOut[mCurrentBitmap]);
            mCurrentBitmap += (mCurrentBitmap + 1) % NUM_BITMAPS;

            //mImageView.setImageBitmap(mBitmapIn);
            createScript(displayBitmap);
            updateImage(2.0f);
        }else {
            imageView.setImageBitmap(displayBitmap);
        }
        //imageView.setScaleFactor(1.0f);
        if (theApp.documentCrop == null) {
            imageView.rotateImageToFit(displayAngle);
            imageView.setCropData(null);
        } else {
            imageView.setCropData(theApp.documentCrop);
        }
        //SetupImageViewBitmapTask.setImageBitmap(imageView, displayBitmap, displayAngle, theApp.manualCropZoom, theApp.documentCrop);
    }

    private void createScript(Bitmap bitmap) {
        // Initialize RS
        RenderScript rs = RenderScript.create(this);

        // Allocate buffers
        mInAllocation = Allocation.createFromBitmap(rs, bitmap);
        mOutAllocations = new Allocation[NUM_BITMAPS];
        for (int i = 0; i < NUM_BITMAPS; ++i) {
            mOutAllocations[i] = Allocation.createFromBitmap(rs, mBitmapsOut[i]);
        }

        // Load script
        mScript = new ScriptC_saturation(rs);
    }

    private class RenderScriptTask extends AsyncTask<Float, Void, Integer> {
        Boolean issued = false;
        private Context mContext;

        private ProgressDialog mProgressDialog;

        public RenderScriptTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setMessage("Please Wait Saving Image");
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        protected Integer doInBackground(Float... values) {
            int index = -1;
            if (!isCancelled()) {
                issued = true;
                index = mCurrentBitmap;

                // Set global variable in RS
                mScript.set_saturationValue(values[0]);

                // Invoke saturation filter kernel
                mScript.forEach_saturation(mInAllocation, mOutAllocations[index]);

                // Copy to bitmap and invalidate image view
                mOutAllocations[index].copyTo(mBitmapsOut[index]);
                mCurrentBitmap = (mCurrentBitmap + 1) % NUM_BITMAPS;
            }
            return index;
        }

        void updateView(Integer result) {
            if (result != -1) {

                // Request UI update
                displayBitmap = mBitmapsOut[result];
                imageView.setImageBitmap(displayBitmap);
                imageView.invalidate();


               /* */
            }
        }

        protected void onPostExecute(Integer result) {

            updateView(result);

            cropclick = false;
            //save image on locally
            final File filecrop = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "CropImage");

            if (!filecrop.exists()) {
                if (!filecrop.mkdirs()) {
                    Log.d("Autodetectimage", "failed to create directory");

                }
            }

            // Get system time to use as picture file name and sequence counter
            final long sequenceCounter = System.currentTimeMillis();
            // Build picture name from
            String fileName = String.format("crop-%016X.jpg", sequenceCounter);
            final File pictureFile = new File(filecrop, fileName);

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(pictureFile);
                displayBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                //fos.write(bitmapToByteArray(displayBitmap));
                fos.flush();
                fos.close();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        protected void onCancelled(Integer result) {
            if (issued) {
                updateView(result);
            }
        }
    }

    private void updateImage(final float f) {
        if (mCurrentTask != null) {
            mCurrentTask.cancel(false);
        }
        mCurrentTask = new RenderScriptTask(this);
        mCurrentTask.execute(f);

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case TAKE_PHOTO:
                if (resultCode == RESULT_OK) {
                    Uri selectedImage = data.getData();
                    theApp.onOpenImage(selectedImage);
                    //theApp.onOpenCropPage(selectedImage, mPageListener);
                }
                break;
        }
    }


    void onTakePhoto() {

        final File fileSink = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "SimpleImage");

        // Common routine to start camera
        final Runnable start = new Runnable() {
            @Override
            public void run() {
                Intent intent = CameraActivity.newIntent(MainActivity.this,
                        fileSink.getAbsolutePath(),
                        "camera-prefs",
                        true);
                startActivityForResult(intent, TAKE_PHOTO);
            }
        };

        if (fileSink.exists()) {
            // Simple start
            start.run();
        } else {
            // Query permissions and create directories
            RuntimePermission.runWithPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    R.string.permission_query_write_storage,
                    new RuntimePermissions.Callback() {
                        @Override
                        public void permissionRun(String permission, boolean granted) {
                            if (granted && fileSink.mkdirs()) {
                                start.run();
                            }
                        }
                    });
        }
    }


    public void showProcessingError(@CropDemoApp.ProcessingError int error) {
        // Show error toast
        switch (error) {
            case CropDemoApp.NOERROR:
                Toast.makeText(getApplicationContext(), R.string.msg_processing_complete, Toast.LENGTH_SHORT).show();
                break;
            case CropDemoApp.PROCESSING:
                Toast.makeText(getApplicationContext(), R.string.msg_processing_error, Toast.LENGTH_LONG).show();
                break;
            case CropDemoApp.OUTOFMEMORY:
                Toast.makeText(getApplicationContext(), R.string.msg_out_of_memory, Toast.LENGTH_LONG).show();
                break;
            case CropDemoApp.NODOCCORNERS:
                Toast.makeText(getApplicationContext(), R.string.msg_no_doc_corners, Toast.LENGTH_LONG).show();
                break;
            case CropDemoApp.INVALIDCORNERS:
                Toast.makeText(getApplicationContext(), R.string.msg_invalid_corners, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Setup App's main activity
        theApp.mainActivity = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set buttons state
        updateUI();
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
           /* case R.id.btn_open_image:
                onOpenImage();
                break;
            case R.id.btn_take_photo:
                onTakePhoto();
                break;
            case R.id.btn_crop_image:
                onCropImage();
                break;
            case R.id.btn_save_image:
                onSaveImage();
                break;*/
            case R.id.btn_rotate_left:
                imageView.rotateLeft();
                break;
            case R.id.btn_rotate_right:
                imageView.rotateRight();
                break;
            case R.id.btn_revert_selection:
                imageView.revertSelection(theApp.documentCorners);
                break;
            case R.id.btn_expand_selection:
                imageView.expandSelection();
                break;
        }
    }

   /* public static byte[] bitmapToByteArray(Bitmap bmp) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return byteArray;
    }*/
}
