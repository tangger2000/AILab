package com.example.ailab;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class ImageActivity extends AppCompatActivity {
    private static final String TAG = CameraActivity.class.getName();

    //将图片的宽按比例缩放ratio = scalePixel / width
    private final int scalePixel = 485;

    /** Define the Size of Image*/
    private int targetHeight = 224;
    private int targetWidth = 224;

    /**Define normalizeOp params*/
    private float mean = 0;
    private float stddev = 255;

    private ImageView imageView;
    private TextView textView;

    private GeneralClassifier classifier;
    /** model file and label file*/
    protected ByteBuffer modelFile;
    protected List<String> labelFile;

    /** preProcessUtils Class*/
    protected preProcessUtils imageData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_image);

        // my_child_toolbar is defined in the layout file
        Toolbar myChildToolbar =
                (Toolbar) findViewById(R.id.toolbar2);
        setSupportActionBar(myChildToolbar);

        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();

        // Enable the Up button
        ab.setDisplayHomeAsUpEnabled(true);

        // 获取传递的参数
        Intent intent = getIntent();
        String imagePath = intent.getStringExtra("imagePath");
        String modelPath = intent.getStringExtra("modelPath");
        String labelPath = intent.getStringExtra("labelPath");
        targetHeight = intent.getIntExtra("targetH", 224);
        targetWidth = intent.getIntExtra("targetW", 224);
        mean = intent.getFloatExtra("mean",0);
        stddev = intent.getFloatExtra("stddev", 255);

        // 加载模型和标签
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

        // 加载模型和标签
        classifier = GeneralClassifier.getInstance(modelFile, labelFile);
//        try {
//            classifier = new GeneralClassifier(modelFile, labelFile);
//            Toast.makeText(ImageActivity.this, "模型加载成功！", Toast.LENGTH_SHORT).show();
//        } catch (Exception e) {
//            Toast.makeText(ImageActivity.this, "模型加载失败！", Toast.LENGTH_SHORT).show();
//            e.printStackTrace();
//            finish();
//        }

        //获取控件，指定操作
        imageView = findViewById(R.id.image_view);
        textView = findViewById(R.id.result_text);

        try {
            //图像预处理（不同于tflite模型的预处理，这里的预处理是缩放、裁剪、旋转意义上的）
            FileInputStream fis = new FileInputStream(imagePath);
            Bitmap bitmap = BitmapFactory.decodeStream(fis);
            //旋转图像，当图像是横向时旋转成竖向
            bitmap = Utils.tryRotateBitmap(bitmap);
            //按比例缩放图片，将位图按比例缩放
            bitmap = Utils.scaleBitmap(bitmap, scalePixel);
            //再将图片中心裁剪
            bitmap = Utils.cropBitmap(bitmap, 640, 480);
            //显示图像
            imageView.setImageBitmap(bitmap);

            ByteBuffer imageInput = imageData.getFloat32ImageWithNormOp(bitmap, targetHeight, targetWidth, mean, stddev).getBuffer();
            SpannableStringBuilder builder = classifier.classifyFrame(imageInput);
            textView.setText(builder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
