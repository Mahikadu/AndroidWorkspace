package com.autodetectimage;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;


import com.autodetectimage.util.Utils;
import com.pixelnetica.imagesdk.Corners;
import com.pixelnetica.imagesdk.ImageFileWriter;
import com.pixelnetica.imagesdk.ImageProcessing;
import com.pixelnetica.imagesdk.ImageSdkLibrary;
import com.pixelnetica.imagesdk.ImageWriter;
import com.pixelnetica.imagesdk.ImageWriterException;
import com.pixelnetica.imagesdk.MetaImage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class CropDemoApp extends Application
	implements
	DetectDocCornersTask.Listener,
	CropDocImageTask.Listener,
		DocImageRoutine.Factory
{

	// Preferences keys
	public static final String PREFS_FORCE_MANUAL_CROP = "FORCE_MANUAL_CROP";
	public static final String PREFS_STRONG_SHADOWS = "STRONG_SHADOWS";
	public static final String PREFS_PROCESSING_PROFILE = "PROCESSING_PROFILE";

	// Camera preferences
	public static final String PREFS_PREVIEW_SIZE = "preview_size";
	public static final String PREFS_SERIAL_SHOT = "serial_shot";
	public static final String PREFS_FLASH_MODE = "flash_mode";
	public static final String PREFS_CAMERA_CORNERS = "camera_corners";

	public static final String PREFS_SAVE_FORMAT = "save_format";
	public static final String PREFS_SIMULATE_PAGES = "simulate-pages";
	public static final String PREFS_SHARE_OUTPUT = "share-output";

	// Current activity
	MainActivity mainActivity;

	/**
	 * Application state
	 */
	@Retention(RetentionPolicy.SOURCE)
	@IntDef({InitNothing, Source, CropOrigin, Target})
	@interface ImageMode { };

	/**
	 * No image selected. Initial state
	 */
	static final int InitNothing = 0;

	/**
	 * Image is source, no processing
	 */
	static final int Source = 1;

	/**
	 * Image was rotated to original orientation and crop mode on
	 */
	static final int CropOrigin = 2;

	/**
	 * Processing complete, Save result available
	 */
	static final int Target = 3;

	/**
	 * Main application state
	 */
	@ImageMode int imageMode = InitNothing;

	/**
	 * Current image to process
	 */
    MetaImage processImage;

	// Processing tasks
	DetectDocCornersTask docCornersTask;
	CropDocImageTask cropImageTask;

    // Special mode for some processing
    private boolean mStrongShadows;

    // Always use manual crop
    private boolean mForceManualCrop;

	final SdkFactory SdkFactory = new SdkFactory(this);

	// Camera parameters
	private Point mPreviewSize;
	private boolean mCameraCorners;
	private boolean mSerialShot;
	private boolean mFlashMode;

	/**
	 * Processing profile
	 */
	@Retention(RetentionPolicy.SOURCE)
	@IntDef({OriginateImage, NoBinarization, BWBinarization, GrayBinarization, 	ColorBinarization})
	public @interface ProcessingProfile {};
	static final int OriginateImage = 0;
	static final int NoBinarization = 1;
	static final int BWBinarization = 2;
	static final int GrayBinarization = 3;
	static final int ColorBinarization = 4;

	@ProcessingProfile
    private int mProcessingProfile = NoBinarization;

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({NOERROR, OUTOFMEMORY, INVALIDFILE, PROCESSING, NODOCCORNERS, INVALIDCORNERS})
	public @interface ProcessingError {};
	static public final int NOERROR = 0;
	static public final int OUTOFMEMORY = 1;
	static public final int INVALIDFILE = 2;
	static public final int PROCESSING = 3;
	static public final int NODOCCORNERS = 4;
	static public final int INVALIDCORNERS = 5;

	// Save format
	@Retention(RetentionPolicy.SOURCE)
	@IntDef({SAVE_JPEG, SAVE_TIFF_G4, SAVE_PNG_MONO, SAVE_PDF, SAVE_PDF_PNG})
	@interface SaveFormat {}
	public static final int SAVE_JPEG = 0;
	public static final int SAVE_TIFF_G4 = 1;
	public static final int SAVE_PNG_MONO = 2;
	public static final int SAVE_PDF = 3;
	public static final int SAVE_PDF_PNG = 4;

	private @SaveFormat int mSaveFormat = SAVE_TIFF_G4;
	private boolean mSimulatePages = true;
	private boolean mShareOutput = true;    // by default

    // Current processing state
    boolean waitMode;

    // Document corners
    Corners documentCorners;
    // Document corners was detected
    boolean isSmartCrop;
	// enter to manual crop mode
    boolean manualCrop;
    // Modified document corners
    CropData documentCrop;
	float documentAngle;

    // Zoom in manual crop mode
    final float manualCropZoom = 2.0f;

	// Current page directory
	private File mPageDir;

	private Uri mSourceImageUri;

	@NonNull
	public final RuntimePermissions RuntimePermission = new RuntimePermissions();

	@Override
	public synchronized DocImageRoutine createRoutine(boolean hasLooper) {
		return new DocImageRoutine(hasLooper ? new Handler() : null,
				createSDK(),
				createDocCorners());
	}

	private ImageProcessing createSDK() {
		return SdkFactory.createSDK();
	}

	private Bundle createDocCorners() {
		return SdkFactory.createDocCorners();
	}

	@Override
	public void onCreate() {
		super.onCreate();

		// Initialize Image SDK
		ImageSdkLibrary.load(this, "M70A-HGST-141B-RSES-1FEP-5IOH-UH4V-36SJ", 1);

		// Setup page directory
		mPageDir = new File(getFilesDir(), "page");
	}

	synchronized void loadSettings() {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// Main params
		mForceManualCrop = prefs.getBoolean(PREFS_FORCE_MANUAL_CROP, mForceManualCrop);
		mStrongShadows = prefs.getBoolean(PREFS_STRONG_SHADOWS, mStrongShadows);
		//noinspection ResourceType
		mProcessingProfile = prefs.getInt(PREFS_PROCESSING_PROFILE, mProcessingProfile);

		// App params
		//noinspection ResourceType
		mSaveFormat = prefs.getInt(PREFS_SAVE_FORMAT, mSaveFormat);
		mSimulatePages = prefs.getBoolean(PREFS_SIMULATE_PAGES, mSimulatePages);
		mShareOutput = prefs.getBoolean(PREFS_SHARE_OUTPUT, mShareOutput);

		// SDK params
		SdkFactory.loadPreferences();
	}

	boolean isForceManualCrop() {
		return mForceManualCrop;
	}

	synchronized void setForceManualCrop(boolean value) {
		mForceManualCrop = value;

		// Store settings now!
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = preferences.edit();
    	editor.putBoolean(PREFS_FORCE_MANUAL_CROP, mForceManualCrop);
    	editor.apply();
    }

	boolean canPerformManualCrop() {
		return documentCorners != null && mSourceImageUri != null;
	}

	void performManualCrop() {
		if (documentCorners != null && mSourceImageUri != null) {

			InputStream imageStream = null;
			try {

				// Reload source image
				docCornersTask = null;
				cropImageTask = null;
				System.gc();

				final ContentResolver cr = getContentResolver();
				imageStream = cr.openInputStream(mSourceImageUri);
				final Bitmap inputBitmap = BitmapFactory.decodeStream(imageStream);

				processImage = createScaledImage(inputBitmap, mSourceImageUri);
				imageMode = Source;

				// Perform crop and done
				cropImageTask = new CropDocImageTask(this, documentCorners, OriginateImage, this);
				cropImageTask.execute(processImage);
			} catch (FileNotFoundException e) {
				Utils.safeClose(imageStream);
			}
		}
	}

	void performProcessing(@ProcessingProfile final int processingProfile) {
		if (documentCorners != null && mSourceImageUri != null && imageMode == Target) {
			ProcessImageTask task = new ProcessImageTask(this, this,
					documentCorners, documentAngle, processingProfile, new ProcessImageTask.Listener() {
				@Override
				public void onProcessImageComplete(@NonNull ProcessImageTask task, @Nullable MetaImage result, @ProcessingError int error) {
					if (result != null) {
						waitMode = false;
						imageMode = Target;
						processImage = result;
						if (mainActivity != null) {
							mainActivity.updateUI();
						}
					} else {
						processImage = null;
						imageMode = InitNothing;
						if (mainActivity != null) {
							mainActivity.updateUI();
						}
						showProcessingError(error, false);
					}
				}
			});
			task.execute(mSourceImageUri);

			waitMode = true;
			if (mainActivity != null) {
				mainActivity.updateUI();
			}
		}
	}

	public synchronized Point getPreviewSize() {
		return mPreviewSize;
	}

	private static boolean equalObject(Object a, Object b) {
		return (a == null) ? (b == null) : a.equals(b);
	}

	boolean isStrongShadows() {
		return mStrongShadows;
	}

    synchronized void setStrongShadows(boolean value) {
	    if (mStrongShadows != value) {
		    mStrongShadows = value;

		    // Store settings now!
		    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		    SharedPreferences.Editor editor = preferences.edit();
		    editor.putBoolean("STRONG_SHADOWS", mStrongShadows);
		    editor.apply();
	    }
    }

	@ProcessingProfile
	int getProcessingProfile() {
		return mProcessingProfile;
	}

    synchronized void setProcessingProfile(@ProcessingProfile int value) {
	    if (!equalObject(mProcessingProfile, value)) {
		    mProcessingProfile = value;

		    // Store settings now!
		    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		    SharedPreferences.Editor editor = preferences.edit();
		    editor.putInt(PREFS_PROCESSING_PROFILE, mProcessingProfile);
		    editor.apply();
	    }

	    // Apply processing if possible
	    performProcessing(mProcessingProfile);
    }

	public int getSaveFormat() {
		return mSaveFormat;
	}

	public void setSaveFormat(int value) {
		if (value != mSaveFormat) {
			mSaveFormat = value;

			// Store settings now!
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = preferences.edit();
			editor.putInt(PREFS_SAVE_FORMAT, mSaveFormat);
			editor.apply();
		}
	}

	public void setSimulatePages(boolean value) {
		if (mSimulatePages != value) {
			mSimulatePages = value;

			// Store settings now!
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = preferences.edit();
			editor.putBoolean(PREFS_SIMULATE_PAGES, mSimulatePages);
			editor.apply();
		}
	}

	public boolean getSimulatePages() {
		return mSimulatePages;
}

	public void setShareOutput(boolean value) {
		if (value != mShareOutput) {
			mShareOutput = value;

			// Store settings now!
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = preferences.edit();
			editor.putBoolean(PREFS_SHARE_OUTPUT, mShareOutput);
			editor.apply();
		}
	}

	public boolean getShareOutput() {
		return mShareOutput;
	}

	private static String displayUriPath(ContentResolver cr, Uri uri) {
		if (cr == null || uri == null) {
			return "";
		}

		String path = MetaImage.getRealPathFromURI(cr, uri);
		if (path == null) {
			path = uri.toString();
		}
		return path;
	}

	void onOpenImage(Uri selectedImage) {

		mSourceImageUri = selectedImage;

		InputStream imageStream = null;
		try
		{
	        // New processing cycle
			waitMode = false;
			imageMode = InitNothing;
			processImage = null;
			documentCrop = null;
			documentCorners = null;

			docCornersTask = null;
			cropImageTask = null;
			System.gc();

			final ContentResolver cr = getContentResolver();
            imageStream = cr.openInputStream(mSourceImageUri);
            final Bitmap inputBitmap = BitmapFactory.decodeStream(imageStream);

			// Show Toast with bitmap information
			if (inputBitmap != null) {
				final String msg = getString(R.string.msg_image_file_info);
				final String text = String.format(msg,
						inputBitmap.getWidth(),
						inputBitmap.getHeight(),
						displayUriPath(cr, mSourceImageUri));
				Toast.makeText(this, text, Toast.LENGTH_LONG).show();
			} else {
				final String msg = getString(R.string.msg_cannot_open_file);
				final String text = String.format(msg, displayUriPath(cr, selectedImage));
				Toast.makeText(this, text, Toast.LENGTH_LONG).show();
				throw new FileNotFoundException(text);
			}

            processImage = createScaledImage(inputBitmap, selectedImage);
            if (processImage != null) {
                imageMode = Source;
				startCropImage(null);
            }
       	} catch (FileNotFoundException e) {
    		// Dummy. Not an error
    	} catch (OutOfMemoryError e) {
    		System.gc();
    		showProcessingError(OUTOFMEMORY, waitMode);
    	} catch (Error|Exception e) {
    		e.printStackTrace();
		} finally {
			if (imageStream != null) {
				try {
					imageStream.close();
				} catch (IOException e) {

				}
			}
		}

		if (mainActivity != null) {
			mainActivity.updateUI();
		}
	}

	void startCropImage(CropData cropData) {
		waitMode = true;
		if (mainActivity != null) {
			mainActivity.setWaitMode();
			mainActivity.setButtonsState();
		}

		// Cleanup before execute
		System.gc();

		// Check crop source image or after manual crop
		if (imageMode == CropOrigin) {
			// Save document corners
			if (cropData == null) {
				throw new IllegalArgumentException("cropData is null");
			}

			// Validate crop rectangle
			if (!cropData.validateCorners(this)) {
				showProcessingError(INVALIDCORNERS, false);
				if (mainActivity != null) {
					// restore buttons
					mainActivity.setButtonsState();
				}
				// Do nothing
				return;
			}

			// Setup image parameters
			processImage.setStrongShadows(mStrongShadows);

			documentAngle = cropData.getRotationAngle();
			if (documentAngle == 90f) {
				processImage.setExifOrientation(ExifInterface.ORIENTATION_ROTATE_90);
			} else if (documentAngle == 180f){
				processImage.setExifOrientation(ExifInterface.ORIENTATION_ROTATE_180);
			} else if (documentAngle == 270f) {
				processImage.setExifOrientation(ExifInterface.ORIENTATION_ROTATE_270);
			} else {
				processImage.setExifOrientation(ExifInterface.ORIENTATION_NORMAL);
			}

			cropImageTask = new CropDocImageTask(this, cropData.getRotatedCorners(), mProcessingProfile, this);
			cropImageTask.execute(processImage);

			// Document corners used
			documentCorners = cropData;
			documentCrop = null;
		} else if (imageMode == Source) {
			// Setup image parameters
			processImage.setStrongShadows(mStrongShadows);

			// Get thresholds from preferences
			docCornersTask = new DetectDocCornersTask(this, this);
			docCornersTask.execute(processImage);
		} else {
			// WTF?! May be error
			waitMode = false;
			processImage = null;
			imageMode = InitNothing;
			if (mainActivity != null) {
				mainActivity.setWaitMode();
				mainActivity.setButtonsState();
			}
		}

		if (mainActivity != null) {
			mainActivity.setDisplayImage();
		}
	}

	@Override
	public void onDocCornersComplete(DetectDocCornersTask task, @ProcessingError int error, DetectDocCornersTask.DocCornersResult result) {

		if (task.equals(docCornersTask)) {
			docCornersTask = null;

			// check processing error
			if (error != NOERROR) {
				showProcessingError(error, false);
				return;
			}

			// Store document corners and mode
			documentCorners = result.cropData;
			isSmartCrop = result.isSmartCrop;
			documentAngle = 0;

			// Activate manual crop mode if no corners detected
			if (BuildConfig.DEBUG) {
				manualCrop = /*mForceManualCrop ||*/ !this.isSmartCrop;
			} else {
				manualCrop = !isSmartCrop;
			}

			@ProcessingProfile int profile;
			if (manualCrop) {
				profile = OriginateImage;
			} else {
				profile = mProcessingProfile;
			}

			// Perform crop and done
			cropImageTask = new CropDocImageTask(this, documentCorners, profile, this);
			cropImageTask.execute(processImage);
		}
	}

	@Override
	public void onCropDocImageComplete(CropDocImageTask task, @ProcessingError int error, MetaImage result) {

		if (task.equals(cropImageTask)) {
			cropImageTask = null;

			// Complete processing
			waitMode = false;
			imageMode = InitNothing;
			processImage = null;
			documentCrop = null;

			// check processing error
			if (error != NOERROR) {
				// Finita la comedia
				showProcessingError(error, false);
			} else if (task.isOriginal()) {
				// Show origin image and corners
				processImage = result;
				imageMode = CropOrigin;

				documentCrop = new CropData(documentCorners, processImage);
				documentCrop.setRotationAngle(documentAngle);
			} else {
				// Show result
				processImage = result;
				//documentCorners = task.getDocumentCorners();
				imageMode = Target;
			}

			System.gc();
			if (mainActivity != null) {
				mainActivity.updateUI();
			}
		}
	}

	private void showProcessingError(@ProcessingError int error, boolean waitMode) {
		final boolean waitModeChanged = waitMode != this.waitMode;
		this.waitMode = waitMode;
		if (mainActivity != null) {
			if (waitModeChanged) {
				//mainActivity.setWaitMode();
			}
			//mainActivity.showProcessingError(error);
		}
	}

	private MetaImage createScaledImage(Bitmap sourceBitmap, Uri sourceUri) {
		if( sourceBitmap == null) {
			return null;
		}

		ImageProcessing sdk = createSDK();
		try {
			if (!sdk.validate()) {
				Log.d(AppLog.TAG, "Cannot initialize ImageSDK");
				return null;
			}

			Point sourceSize = new Point(sourceBitmap.getWidth(), sourceBitmap.getHeight());
			Point targetSize = sdk.supportImageSize(sourceSize);

			// Returns same image if size is supported
			if (sourceSize.equals(targetSize)) {
				return new MetaImage(sourceBitmap, getContentResolver(), sourceUri);
			} else {
				Log.d(AppLog.TAG, String.format("Image (%d x %d) too large, scale to (%d x %d)",
						sourceSize.x, sourceSize.y,
						targetSize.x, targetSize.y));
				Bitmap scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, targetSize.x, targetSize.y, true);
				return new MetaImage(scaledBitmap, getContentResolver(), sourceUri);
			}
		} catch (Exception|Error e) {
			Log.e(AppLog.TAG, "Cannot newInstance scaled image", e);
			return null;
		} finally {
			sdk.destroy();
		}
	}

	/* Checks if external storage is available for read and write */
	boolean isExternalStorageWritable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	        return true;
	    }
	    return false;
	}

	private class OutputFormat {
		@SaveFormat final int format;
		@ImageSdkLibrary.ImageWriterType
		final int type;
		final OutputFormat subOutput;
		@NonNull
        final String extension;
		@NonNull
        final String mimeType;
		final int fakePages;
		@NonNull
        final Bundle params = new Bundle();

		// Initialization
		OutputFormat(@SaveFormat int format, @ImageSdkLibrary.ImageWriterType int type, @NonNull String extension, @NonNull String mimeType, int fakePages) {
			this(format, type, null, extension, mimeType, fakePages);
		}

		OutputFormat(@SaveFormat int format, @ImageSdkLibrary.ImageWriterType int type, OutputFormat subOutput, @NonNull String extension, @NonNull String mimeType, int fakePages) {
			this.format = format;
			this.type = type;
			this.subOutput = subOutput;
			this.extension = extension;
			this.mimeType = mimeType;
			this.fakePages = fakePages;
		}

		protected void configure(int cookie) {
		}

		// Processing
		ImageWriter writer;
		String createWriter() throws ImageWriterException {
			writer = SdkFactory.createImageWriter(this.type);

			// Build picture name
			final File folder = new File(getExternalFilesDir(null), "output");
			folder.mkdirs();
			final String fileName = getResources().getString(R.string.processed_images_file,
					System.currentTimeMillis(), this.extension);
			final String filePath = folder.getAbsolutePath() + File.separator + fileName;

			writer.open(filePath);

			if (this.subOutput != null) {
				this.subOutput.createWriter();
			}

			// To display
			return filePath;
		}

		String writePage(MetaImage image, int page) throws ImageWriterException {
			// Co
			configure(page);
			writer.configure(params);

			if (subOutput != null) {
				final String subFile = subOutput.writePage(image, page);
				if (!TextUtils.isEmpty(subFile)) {
					@ImageFileWriter.ImageType int imageType;
					switch (subOutput.type) {
						case ImageSdkLibrary.IMAGE_WRITER_JPEG:
							imageType = ImageFileWriter.IMAGE_TYPE_JPEG;
							break;
						case ImageSdkLibrary.IMAGE_WRITER_PNG:
						case ImageSdkLibrary.IMAGE_WRITER_PNG_EXT:
							imageType = ImageFileWriter.IMAGE_TYPE_PNG;
							break;
						default:
							throw new ImageWriterException("Sub-writer " + subOutput.type + " is not supported");
					}

					// Not all writers supports
					ImageFileWriter fileWriter = (ImageFileWriter) writer;
					final String output = fileWriter.writeFile(subFile, imageType, 1);
					return output;
				} else {
					return "";
				}
			} else {
				return writer.write(image);
			}
		}

		void closeWriter() throws ImageWriterException {
			if (subOutput != null) {
				subOutput.closeWriter();
			}

			writer.close();
		}

	}

	private final OutputFormat[] outputParams = {
			new OutputFormat(SAVE_JPEG, ImageSdkLibrary.IMAGE_WRITER_JPEG, "jpg", "image/jpeg", 1),
			new OutputFormat(SAVE_TIFF_G4, ImageSdkLibrary.IMAGE_WRITER_TIFF, "tif", "image/tiff", 3),
			new OutputFormat(SAVE_PNG_MONO, ImageSdkLibrary.IMAGE_WRITER_PNG_EXT, "png", "image/png", 1),
			new OutputFormat(SAVE_PDF, ImageSdkLibrary.IMAGE_WRITER_PDF, "pdf", "application/pdf", 3) {
				@Override
				protected void configure(int cookie) {
					params.clear(); // remove keys
					switch (cookie) {
						case 0:
							// Overwrite default
							params.putInt(ImageWriter.CONFIG_PAGE_PAPER, ImageWriter.PAPER_HALF_LETTER);
							break;
						case 1:
							// Setup custom page size (A5)
							params.putFloat(ImageWriter.CONFIG_PAGE_WIDTH, 148);
							params.putFloat(ImageWriter.CONFIG_PAGE_HEIGHT, 210);
							break;
						case 2:
							// Setup horizontal page size
							params.putFloat(ImageWriter.CONFIG_PAGE_WIDTH, ImageWriter.Extensible);
							params.putFloat(ImageWriter.CONFIG_PAGE_HEIGHT, 210);
							break;
					}
				}
			},
			new OutputFormat(SAVE_PDF_PNG, ImageSdkLibrary.IMAGE_WRITER_PDF,
					new OutputFormat(SAVE_PNG_MONO, ImageSdkLibrary.IMAGE_WRITER_PNG_EXT, "png", "image/png", 1),
					"pdf", "application/pdf", 3) {
				@Override
				protected void configure(int cookie) {
					params.clear(); // remove keys
					switch (cookie) {
						case 0:
							// Overwrite default
							params.putInt(ImageWriter.CONFIG_PAGE_PAPER, ImageWriter.PAPER_HALF_LETTER);
							break;
						case 1:
							// Setup custom page size (A5)
							params.putFloat(ImageWriter.CONFIG_PAGE_WIDTH, 148);
							params.putFloat(ImageWriter.CONFIG_PAGE_HEIGHT, 210);
							break;
						case 2:
							// Setup horizontal page size
							params.putFloat(ImageWriter.CONFIG_PAGE_WIDTH, ImageWriter.Extensible);
							params.putFloat(ImageWriter.CONFIG_PAGE_HEIGHT, 210);
							break;
					}
				}
			},
	};


    void onSaveImage() {
	    if (processImage == null) {
		    throw new IllegalStateException("Image to process is null");
	    }

    	if( !isExternalStorageWritable()) {
    		Toast toast = Toast.makeText(getApplicationContext(),
    				R.string.msg_bad_external_storage, Toast.LENGTH_LONG);
    		toast.show();
    	}

    	try {
		    // Select output format
		    OutputFormat format = outputParams[0];  // default
		    for (OutputFormat f : outputParams) {
			    if (f.format == mSaveFormat) {
				    format = f;
				    break;
			    }
		    }


		    String filePath;
		    try {
			    filePath = format.createWriter();
			    // Write 3 pages
			    for (int i = 0; i < format.fakePages; ++i) {
				    String outputPath = format.writePage(processImage, i);
				    if (TextUtils.isEmpty(outputPath)) {
					    throw new IllegalStateException("Cannot write page to file " + filePath);
				    }

				    // Write only one page if no flag specified
				    if (!mSimulatePages) {
					    break;
				    }
			    }
			    // Close file
			    format.closeWriter();
		    } catch (Exception e) {
			    Log.e(AppLog.TAG, e.getMessage(), e);
			    throw e;
		    }
		    //writer.writeTiffG4(processImage, filePath);
		    Log.d(AppLog.TAG, "Write image to file " + filePath);

		    if (mShareOutput && mainActivity != null) {
			    // Share output
			    Uri fileUri = Uri.fromFile(new File(filePath));
			    Intent shareIntent = new Intent();
			    shareIntent.setAction(Intent.ACTION_SEND);
			    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
			    shareIntent.setType(format.mimeType);
			    mainActivity.startActivity(Intent.createChooser(shareIntent, getString(R.string.share_output_title)));
		    } else {
			    // Simple notify
			    final String msg = getString(R.string.msg_write_image_file);
			    Toast.makeText(mainActivity, String.format(msg, filePath), Toast.LENGTH_LONG).show();
		    }


    	} catch (Exception e) {
    		Toast toast = Toast.makeText(mainActivity,
    				R.string.msg_cannot_save_file, Toast.LENGTH_LONG);
    		toast.show();
    	}
    }
}
