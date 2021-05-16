package com.example.ailab.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.SpannableStringBuilder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * author：tangger
 * date：2021/5/13
 */
public class CameraActivity extends AppCompatActivity implements HorizontalScrollTabStrip.TagChangeListener{
    /** 常量参数*/
    private static final String[] PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int  PERMISSIONS_REQUEST_CODE = 10;
    private static final double RATIO_4_3_VALUE = 4.0 / 3.0;
    private static final double  RATIO_16_9_VALUE = 16.0 / 9.0;
    private final List<String> mTitiles = Arrays.asList("扫码","花卉","通用", "鸟类", "食物");
    private HorizontalScrollTabStrip id_horizontal_view;
    private View id_line;
    /**
     * 指示器下的红线的参数
     */
    private LinearLayout.LayoutParams lineLp;
    /**
     * 红线当前的位置
     */
    private int mLineLocation_X = 0;

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
    /** bitmap params*/
    //将图片的宽按比例缩放ratio = scalePixel / width
    private final int scalePixel = 485;
    private final int cropHeight = 640;
    private final int cropWeight = 480;

    private final ArrayList<String> deniedPermission = new ArrayList<>();
    private final String TAG = this.getClass().getSimpleName();
    private String outputFilePath;

    /** 控件*/
    private PreviewView mPreviewView;
    private ImageButton mRecordView, mBtnSelectImg, mBtnCameraSwitch, mBtnLight;
    boolean lightOff = true;
    private Preview mPreview;
    protected TextView leftTextView, rightTextView;
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
        leftTextView = findViewById(R.id.result_key_text);
        rightTextView = findViewById(R.id.result_value_text);
        mBtnSelectImg = findViewById(R.id.select_img);
        mBtnLight = findViewById(R.id.turn_on_torch);
        id_horizontal_view = (HorizontalScrollTabStrip) findViewById(R.id.id_horizontal_view);
        initLineParams();
        id_line = findViewById(R.id.id_line);
        id_line.setLayoutParams(lineLp);
        id_horizontal_view.setTags(mTitiles);
        id_horizontal_view.setOnTagChangeListener(this);

        updateCameraUi();
        setRecordListener();
        setSelectListener();
        setLightListener();
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
    public void changeLine(int location_x, boolean isClick) {
        TranslateAnimation animation = new TranslateAnimation(mLineLocation_X,
                location_x, 0f, 0f);
        animation.setInterpolator(new LinearInterpolator());
        int duration = 0;
        if (isClick) {
            duration = 200 * (Math.abs(location_x - mLineLocation_X) / 100);
            duration = duration > 400 ? 400 : duration;
        } else
            duration = 0;
        animation.setDuration(duration);
        animation.setFillAfter(true);
        id_line.startAnimation(animation);
        mLineLocation_X = location_x;
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
            Bitmap bitmap = mPreviewView.getBitmap();
            try {
                bitmap = preprocess(bitmap, scalePixel, cropHeight, cropWeight);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            Intent intent = new Intent(CameraActivity.this, ImageActivity.class);
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            assert bitmap != null;
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs);
            byte[] bitmapByte = bs.toByteArray();
            intent.putExtra("bitmap", bitmapByte);
            startActivity(intent);
        });
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

    private void setLightListener(){
        mBtnLight.setOnClickListener(v -> {
            if ( mCamera.getCameraInfo().hasFlashUnit()) {
                if(lightOff) {
                    mCamera.getCameraControl().enableTorch(true);
                    lightOff = false;
                }else {
                    mCamera.getCameraControl().enableTorch(false);
                    lightOff = true;
                }
            }
        });
    }

    //响应选择图片控件
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String imagePath;
        Bitmap bitmap = null;
        if(requestCode == 1) {
            if (data == null) {
                Log.w("onActivityResult", "user photo data is null");
                return;
            }
            Uri imageUri = data.getData();
            imagePath = Utils.getPathFromURI(CameraActivity.this, imageUri);
            try {
                FileInputStream fis = new FileInputStream(imagePath);
                bitmap = BitmapFactory.decodeStream(fis);
                bitmap = preprocess(bitmap, scalePixel, cropHeight, cropWeight);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // 跳转到处理页面，同时利用intent传递图像的文件路径
            Intent intent = new Intent(CameraActivity.this, ImageActivity.class);
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            assert bitmap != null;
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs);
            byte[] bitmapByte = bs.toByteArray();
            intent.putExtra("bitmap", bitmapByte);
            startActivity(intent);
        }
    }

    /**
     * 初始化指示器“红线”参数
     */
    private void initLineParams() {
        lineLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lineLp.weight = 0f;
        WindowManager wm = (WindowManager) getSystemService(Service.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        lineLp.width = (int) (width / (id_horizontal_view.mDefaultShowTagCount + 0.7));
        lineLp.height = (int) (getResources().getDisplayMetrics().density * 1 + 0.5f);
    }

    private Bitmap preprocess(Bitmap bitmap, int scalePixel, int cropH, int cropW) throws FileNotFoundException {
        //图像预处理（不同于tflite模型的预处理，这里的预处理是缩放、裁剪、旋转意义上的）
        //旋转图像，当图像是横向时旋转成竖向
        bitmap = Utils.tryRotateBitmap(bitmap);
        //按比例缩放图片，将位图按比例缩放
        bitmap = Utils.scaleBitmap(bitmap, scalePixel);
        //再将图片中心裁剪
        assert bitmap != null;
        bitmap = Utils.cropBitmap(bitmap, cropH, cropW);
        return bitmap;
    }


    private void updateCameraUi() {
        //必须先remove再add这样视频流画面才能正确的显示出来
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
                SpannableStringBuilder leftBuilder = new SpannableStringBuilder();
                SpannableStringBuilder rightBuilder = new SpannableStringBuilder();
                classifier.classifyFrame(imageInput, leftBuilder, rightBuilder);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        leftTextView.setText(leftBuilder);
                        rightTextView.setText(rightBuilder);
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
