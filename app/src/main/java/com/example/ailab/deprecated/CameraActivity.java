package com.example.ailab.deprecated;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.example.ailab.R;
import com.example.ailab.activity.ImageActivity;
import com.example.ailab.classifier.GeneralClassifier;
import com.example.ailab.classifier.LoadModel;
import com.example.ailab.utils.Utils;
import com.example.ailab.utils.preProcessUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CameraActivity extends AppCompatActivity {
    private static final String TAG = CameraActivity.class.getName();
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;

    private HandlerThread mCaptureThread;
    private Handler mCaptureHandler;
    private HandlerThread mInferThread;
    private Handler mInferHandler;

    private ImageReader mImageReader;
    private Size mPreviewSize;
    private boolean mCapturing;

    private final Object lock = new Object();
    private boolean runClassifier = false;
    private ArrayList<String> classNames;
    private GeneralClassifier classifier;

    private AutoFitTextureView mTextureView;
    private TextView textView;
    private ImageButton select_img_button;
    private ImageButton function_button;
    private ImageButton capture_img_button;

    /** Define the Size of Image*/
    private static final int targetHeight = 224;
    private static final int targetWidth = 224;

    /**Define normalizeOp params*/
    private static final float mean = 0;
    private static final float stddev = 255;

    /** file path*/
    private static final String modelPath = "mobilenet_v3.tflite";
    private static final String labelPath = "labels_list.txt";

    /** model file and label file*/
    protected ByteBuffer modelFile;
    protected List<String> labelFile;

    /** preProcessUtils Class*/
    protected preProcessUtils imageData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera_deprecated);
        if (!hasPermission()) {
            requestPermission();
        }
        // ?????????????????????
        /* init LoadModel class*/
        // load model and label
        LoadModel loadModel = new LoadModel();
        try{
            modelFile = loadModel.getModel(this, modelPath);
            labelFile = loadModel.getLabels(this, labelPath);
        } catch (IOException e) {
            Log.d(TAG, "load Model and Label failed!");
            e.printStackTrace();
        }

        // init imageData Class
        try{
            imageData = new preProcessUtils();
        } catch (Exception e) {
            Log.d(TAG, "cannot get ImageData from preProcessUtils Class!");
            e.printStackTrace();
        }

        // ?????????????????????
        try {
            classifier = GeneralClassifier.getInstance(modelFile, labelFile);
            Toast.makeText(CameraActivity.this, "?????????????????????", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(CameraActivity.this, "?????????????????????", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            finish();
        }

        //???????????????????????????
        mTextureView = findViewById(R.id.texture_view);
        textView = findViewById(R.id.result_text);
        capture_img_button = findViewById(R.id.capture_img_button);
        select_img_button = findViewById(R.id.select_img_button);
        function_button = findViewById(R.id.function_button);

    }

    @Override
    protected void onResume() {
        initStatus();
        super.onResume();
        //????????????,????????????
        select_img_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ????????????
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 1);
            }
        });
    }

    @Override
    protected void onPause() {
        stopInfer();
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopInfer();
        super.onStop();
    }


    // ??????????????????
    private final Runnable periodicClassify =
            new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        if (runClassifier) {
                            // ???????????????????????????????????????????????????
                            if (getApplicationContext() != null && mCameraDevice != null && classifier != null) {
                                predict();
                            }
                        }
                    }
                    if (mInferThread != null && mInferHandler != null && mCaptureHandler != null && mCaptureThread != null) {
                        mInferHandler.post(periodicClassify);
                    }
                }
            };


    // ????????????????????????
    private void predict() {
        // ???????????????????????????
        Bitmap bitmap = mTextureView.getBitmap();
        try {
            ByteBuffer imageInput = imageData.getFloat32ImageWithNormOp(bitmap, targetHeight, targetWidth, mean, stddev).getBuffer();
            SpannableStringBuilder leftBuilder = new SpannableStringBuilder();
            SpannableStringBuilder rightBuilder = new SpannableStringBuilder();
            classifier.classifyFrame(imageInput, leftBuilder, rightBuilder, false);
//            textView.setText(builder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ??????????????????????????????
    private void initStatus() {
        // ????????????
        startCaptureThread();
        startInferThread();

        // ??????SurfaceTexture????????????????????????????????????????????????
        if (mTextureView.isAvailable()) {
            startCapture();
        } else {
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    startCapture();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
        }
    }

    // ??????????????????
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startCapture() {
        // ??????????????????????????????????????????
        if (mCapturing) return;
        mCapturing = true;

        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        String cameraIdAvailable = null;
        try {
            assert manager != null;
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                // ??????????????????????????????????????????
                boolean isFont = false;
                if (isFont) {
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        cameraIdAvailable = cameraId;
                        break;
                    }
                } else {
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraIdAvailable = cameraId;
                        break;
                    }
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "???????????????????????? ", e);
        }

        try {
            assert cameraIdAvailable != null;
            final CameraCharacteristics characteristics =
                    manager.getCameraCharacteristics(cameraIdAvailable);

            final StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            mPreviewSize = Utils.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    mTextureView.getWidth(),
                    mTextureView.getHeight());
            Log.d("mPreviewSize", String.valueOf(mPreviewSize));
            mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            manager.openCamera(cameraIdAvailable, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    mCameraDevice = camera;
                    createCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    mCameraDevice = null;
                    mCapturing = false;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, final int error) {
                    Log.e(TAG, "?????????????????? =  " + error);
                    camera.close();
                    mCameraDevice = null;
                    mCapturing = false;
                }
            }, mCaptureHandler);
        } catch (CameraAccessException | SecurityException e) {
            mCapturing = false;
            Log.e(TAG, "???????????????????????? ", e);
        }
    }

    // ??????????????????session
    private void createCaptureSession() {
        try {
            final SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            final Surface surface = new Surface(texture);
            final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            mImageReader = ImageReader.newInstance(
                    mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 10);

            mCameraDevice.createCaptureSession(
                    Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (null == mCameraDevice) {
                                return;
                            }

                            mCaptureSession = cameraCaptureSession;
                            try {
                                captureRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                captureRequestBuilder.set(
                                        CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                CaptureRequest previewRequest = captureRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(
                                        previewRequest, new CameraCaptureSession.CaptureCallback() {
                                            @Override
                                            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                                                super.onCaptureProgressed(session, request, partialResult);
                                            }

                                            @Override
                                            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                                                super.onCaptureFailed(session, request, failure);
                                                Log.d(TAG, "onCaptureFailed = " + failure.getReason());
                                            }

                                            @Override
                                            public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                                                super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                                                Log.d(TAG, "onCaptureSequenceCompleted");
                                            }
                                        }, mCaptureHandler);
                            } catch (final CameraAccessException e) {
                                Log.e(TAG, "onConfigured exception ", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull final CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "onConfigureFailed ");
                        }
                    },
                    null);
        } catch (final CameraAccessException e) {
            Log.e(TAG, "??????????????????session?????? ", e);
        }
    }

    //????????????????????????
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String imagePath;
        if(requestCode == 1) {
            if (data == null) {
                Log.w("onActivityResult", "user photo data is null");
                return;
            }
            Uri imageUri = data.getData();
            imagePath = getPathFromURI(CameraActivity.this, imageUri);
            // ????????????????????????????????????intent???????????????????????????
            Intent intent = new Intent(CameraActivity.this, ImageActivity.class);
            //????????????
            intent.putExtra("imagePath", imagePath);
            intent.putExtra("modelPath", modelPath);
            intent.putExtra("labelPath", labelPath);
            intent.putExtra("targetH", targetHeight);
            intent.putExtra("targetW", targetWidth);
            intent.putExtra("mean", mean);
            intent.putExtra("stddev", stddev);

            startActivity(intent);
        }
    }

    // ????????????
    private void closeCamera() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        mCapturing = false;
    }

    // ????????????????????????
    private void stopCaptureThread() {
        try {
            if (mCaptureThread != null) {
                mCaptureThread.quitSafely();
                mCaptureThread.join();
            }
            mCaptureThread = null;
            mCaptureHandler = null;
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    // ??????????????????
    private void stopInferThread() {
        try {
            if (mInferThread != null) {
                mInferThread.quitSafely();
                mInferThread.join();
            }
            mInferThread = null;
            mInferHandler = null;
            synchronized (lock) {
                runClassifier = false;
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    // ??????????????????
    private void stopInfer() {
        // ?????????????????????
        closeCamera();
        stopCaptureThread();
        stopInferThread();
    }

    // ????????????????????????
    private void startCaptureThread() {
        mCaptureThread = new HandlerThread("capture");
        mCaptureThread.start();
        mCaptureHandler = new Handler(mCaptureThread.getLooper());
    }

    // ??????????????????
    private void startInferThread() {
        mInferThread = new HandlerThread("inference");
        mInferThread.start();
        mInferHandler = new Handler(mInferThread.getLooper());
        synchronized (lock) {
            runClassifier = true;
        }
        mInferHandler.post(periodicClassify);
    }

    // ???????????????Uri?????????????????????
    public static String getPathFromURI(Context context, Uri uri) {
        String result;
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            result = uri.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    // check had permission
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    // request permission
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }
}