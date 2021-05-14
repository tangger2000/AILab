package com.example.ailab.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.SpannableStringBuilder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.ailab.R;
import com.example.ailab.utils.Utils;
import com.example.ailab.classifier.GeneralClassifier;
import com.example.ailab.classifier.LoadModel;
import com.example.ailab.utils.preProcessUtils;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * author：tangger
 * date：2021/5/13
 */
public class CameraActivity extends AppCompatActivity {
    /** 常量参数*/
    private static final String[] PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int  PERMISSIONS_REQUEST_CODE = 10;
    private static final double RATIO_4_3_VALUE = 4.0 / 3.0;
    private static final double  RATIO_16_9_VALUE = 16.0 / 9.0;
    /** Model Params
     *  Define the Size of Image
     * */
    private static final int targetHeight = 224;
    private static final int targetWidth = 224;
    /**Define normalizeOp params*/
    private static final float mean = 0;
    private static final float stddev = 255;
    /** file path*/
    private static final String modelPath = "mobilenet_v3.tflite";
    private static final String labelPath = "labels_list.txt";

    private final ArrayList<String> deniedPermission = new ArrayList<>();
    private final String TAG = this.getClass().getSimpleName();
    private String outputFilePath;

    /** 控件*/
    private PreviewView mPreviewView;
    private ImageButton mRecordView, mBtnSelectImg;
    private ImageButton mBtnCameraSwitch;
    private Preview mPreview;
    protected TextView textView;
    /**线程*/
    private ExecutorService mExecutorService;
    private Handler handler;
    /**照相*/
    private ImageCapture mImageCapture;
    private ImageAnalysis mImageAnalysis;
    protected Camera mCamera;
    /**可以将一个camera跟任意的LifecycleOwner绑定的一个单例类*/
    private ProcessCameraProvider mCameraProvider;
    /**摄像头朝向 默认向后*/
    private int mLensFacing = CameraSelector.LENS_FACING_BACK;
    /**是否是照相*/
    protected boolean takingPicture;

    /** preProcessUtils Class*/
    protected preProcessUtils imageData;
    /** classifier*/
    protected GeneralClassifier classifier;

    /** 全局变量传递模型*/
    public static class Transmitter {
        public static ByteBuffer modelFile;
        public static List<String> labelFile;
    }

    public static boolean hasPermission(Context context){
        for (String permission : PERMISSIONS) {
            boolean res = ContextCompat.checkSelfPermission(context,permission) == PackageManager.PERMISSION_DENIED;
            if(res){
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == PERMISSIONS_REQUEST_CODE){
            deniedPermission.clear();
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grant = grantResults[i];
                if(grant == PackageManager.PERMISSION_DENIED){
                    deniedPermission.add(permission);
                }
            }
            if(deniedPermission.isEmpty()){
                setUpCamera();
            }else {
                new AlertDialog.Builder(this)
                        .setMessage("有权限没有授权，无法使用")
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setPositiveButton("好的", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String[] denied = new String[deniedPermission.size()];
                                ActivityCompat.requestPermissions(CameraActivity.this, deniedPermission.toArray(denied), PERMISSIONS_REQUEST_CODE);
                            }
                        }).create().show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera);
        mExecutorService = Executors.newSingleThreadExecutor();

        initModel();

        // 检查权限
        if(!hasPermission(this)){
            ActivityCompat.requestPermissions(this,PERMISSIONS,PERMISSIONS_REQUEST_CODE);
        }else {
            setUpCamera();
        }

        mPreviewView = findViewById(R.id.view_finder);
        mRecordView = findViewById(R.id.take_photo);
        mBtnCameraSwitch = findViewById(R.id.camera_switch_button);
        textView = findViewById(R.id.result_text);
        mBtnSelectImg = findViewById(R.id.select_img);

        updateCameraUi();
        setRecordListener();
        setSelectListener();
        mBtnCameraSwitch.setOnClickListener(v -> {
            if (CameraSelector.LENS_FACING_FRONT == mLensFacing){
                mLensFacing = CameraSelector.LENS_FACING_BACK;
            }else {
                mLensFacing = CameraSelector.LENS_FACING_FRONT;
            }
            bindCameraUseCases();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(classifier==null) {
            initModel();
        }

        HandlerThread handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

    }

    @Override
    protected void onStop() {
        super.onStop();
//
//        if(!mExecutorService.isShutdown()){
//            mExecutorService.shutdown();
//        }
    }

    private void setRecordListener() {
        mRecordView.setOnClickListener(v -> {
            //拍照
            takingPicture = true;
            //创建图片保存的文件地址
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath(),
                    System.currentTimeMillis() + ".jpeg");
            ImageCapture.Metadata metadata = new ImageCapture.Metadata();
            metadata.setReversedHorizontal(mLensFacing == CameraSelector.LENS_FACING_FRONT);

            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture
                    .OutputFileOptions.Builder(file)
                    .setMetadata(metadata)
                    .build();
            mImageCapture.takePicture(outputFileOptions,mExecutorService , new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    Uri savedUri = outputFileResults.getSavedUri();
                    if(savedUri == null){
                        savedUri = Uri.fromFile(file);
                    }
                    outputFilePath = file.getAbsolutePath();
                    onFileSaved(savedUri);
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    Log.e(TAG, "Photo capture failed: "+exception.getMessage(), exception);
                }
            });
        });
    }

    private void onFileSaved(Uri savedUri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            sendBroadcast(new Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri));
        }
        String mimeTypeFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap
                .getFileExtensionFromUrl(savedUri.getPath()));
        MediaScannerConnection.scanFile(getApplicationContext(),
                new String[]{new File(savedUri.getPath()).getAbsolutePath()},
                new String[]{mimeTypeFromExtension}, (path, uri) -> Log.d(TAG, "Image capture scanned into media store: $uri"+uri));
//        PreviewActivity.start(this, outputFilePath, !takingPicture);
    }

    private void setSelectListener(){
        //打开相册,选择图像
        mBtnSelectImg.setOnClickListener(v -> {
            // 打开相册
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, 1);
        });
    }

    //响应选择图片控件
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
            imagePath = Utils.getPathFromURI(CameraActivity.this, imageUri);
            // 跳转到处理页面，同时利用intent传递图像的文件路径
            Intent intent = new Intent(CameraActivity.this, ImageActivity.class);
            //传递参数
            intent.putExtra("imagePath", imagePath);
//            intent.putExtra("modelPath", modelPath);
//            intent.putExtra("labelPath", labelPath);
//            intent.putExtra("targetH", targetHeight);
//            intent.putExtra("targetW", targetWidth);
//            intent.putExtra("mean", mean);
//            intent.putExtra("stddev", stddev);
            startActivity(intent);
        }
    }


    private void updateCameraUi() {
        //必须先remove在add这样视频流画面才能正确的显示出来
        ViewGroup parent = (ViewGroup) mPreviewView.getParent();
        parent.removeView(mPreviewView);
        parent.addView(mPreviewView,0);
    }

    private void setUpCamera() {
        //Future表示一个异步的任务，ListenableFuture可以监听这个任务，当任务完成的时候执行回调
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    mCameraProvider = cameraProviderFuture.get();
                    //选择摄像头的朝向
                    mLensFacing = getLensFacing();
                    if(mLensFacing == -1){
                        Toast.makeText(getApplicationContext(),"无可用的设备cameraId!,请检查设备的相机是否被占用",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 构建并绑定照相机用例
                    bindCameraUseCases();

                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("RestrictedApi")
    private void bindCameraUseCases() {
        //获取屏幕的分辨率
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mPreviewView.getDisplay().getRealMetrics(displayMetrics);
        //获取宽高比
        int screenAspectRatio = aspectRatio(displayMetrics.widthPixels, displayMetrics.heightPixels);

        int rotation = mPreviewView.getDisplay().getRotation();

        if(mCameraProvider == null){
            Toast.makeText(getApplicationContext(),"相机初始化失败",Toast.LENGTH_SHORT).show();
            return;
        }

        ProcessCameraProvider cameraProvider = mCameraProvider;
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(mLensFacing).build();
        Preview.Builder pBuilder = new Preview.Builder();

        mPreview = pBuilder
                //设置宽高比
                .setTargetAspectRatio(screenAspectRatio)
                //设置当前屏幕的旋转
                .setTargetRotation(rotation)
                .build();

        ImageCapture.Builder builder = new ImageCapture.Builder();

        mImageCapture = builder
                //优化捕获速度，可能降低图片质量
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                //设置宽高比
                .setTargetAspectRatio(screenAspectRatio)
                //设置初始的旋转角度
                .setTargetRotation(rotation)
                .build();

        mImageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                // 非阻塞模式
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        mImageAnalysis.setAnalyzer(mExecutorService, new ImageAnalysis.Analyzer() {
            @SuppressLint("UnsafeExperimentalUsageError")
            @Override
            public void analyze(@NonNull ImageProxy image) {
                // insert your code here.
                Log.d("分析","start");
                Bitmap bitmap = Utils.toBitmap(image);
                predict(bitmap);
                image.close();
            }
        });

        //重新绑定之前必须先取消绑定
        cameraProvider.unbindAll();

        mCamera = cameraProvider.bindToLifecycle(CameraActivity.this,
                cameraSelector,mPreview,mImageCapture,mImageAnalysis);
        mPreview.setSurfaceProvider(mPreviewView.getSurfaceProvider());
    }

    private void initModel()
    {
        // load model and label
        LoadModel loadModel = new LoadModel();
        try{
            Transmitter.modelFile = loadModel.getModel(CameraActivity.this, modelPath);
            Transmitter.labelFile = loadModel.getLabels(CameraActivity.this, labelPath);
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

        // init classifier
        try {
            classifier = GeneralClassifier.getInstance(Transmitter.modelFile, Transmitter.labelFile);
            Toast.makeText(CameraActivity.this, "模型加载成功！", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "模型加载成功！");
        } catch (Exception e) {
            Toast.makeText(CameraActivity.this, "模型加载失败！", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "模型加载失败！");
            e.printStackTrace();
        }
    }


    protected void predict(Bitmap bitmap){
        runInBackground(new Runnable() {
            @Override
            public void run() {
                ByteBuffer imageInput = imageData.getFloat32ImageWithNormOp(bitmap, targetHeight, targetWidth, mean, stddev).getBuffer();
                SpannableStringBuilder builder = classifier.classifyFrame(imageInput);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(builder);
                    }
                });
            }
        });
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    private int aspectRatio(int widthPixels, int heightPixels) {
        double previewRatio = (double) Math.max(widthPixels, heightPixels) / (double) Math.min(widthPixels, heightPixels);
        if (Math.abs(previewRatio - RATIO_4_3_VALUE) <= Math.abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }

    private int getLensFacing() {
        if(hasBackCamera()){
            return CameraSelector.LENS_FACING_BACK;
        }
        if(hasFrontCamera()){
            return CameraSelector.LENS_FACING_FRONT;
        }
        return -1;
    }

    /**
     * 是否有后摄像头
     */
    private boolean hasBackCamera(){
        if(mCameraProvider == null){
            return false;
        }
        try {
            return mCameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
        return false;
    }
    /**
     * 是否有前摄像头
     */
    private boolean hasFrontCamera(){
        if(mCameraProvider == null){
            return false;
        }
        try {
            return mCameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        mExecutorService.shutdown();
        super.onDestroy();
    }
}
