package com.example.ailab.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;


import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.ailab.deprecated.CameraActivity;
import com.example.ailab.R;
import com.example.ailab.utils.Utils;
import com.example.ailab.classifier.GeneralClassifier;
import com.example.ailab.classifier.LoadModel;
import com.example.ailab.utils.preProcessUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class ImageActivity extends AppCompatActivity {
    private static final String TAG = CameraActivity.class.getName();
    /** Define the Size of Image*/
    private final int targetHeight = 224;
    private final int targetWidth = 224;

    /**Define normalizeOp params*/
    private final float mean = 0;
    private final float stddev = 255;

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
        assert ab != null;
        ab.setDisplayHomeAsUpEnabled(true);

        // 获取传递的参数
        Intent intent = getIntent();
        byte[] byteTemp = intent.getByteArrayExtra("bitmap");
        Bitmap bitmap = BitmapFactory.decodeByteArray(byteTemp, 0, byteTemp.length);
        modelFile = com.example.ailab.activity.CameraActivity.Transmitter.modelFile;
        labelFile = com.example.ailab.activity.CameraActivity.Transmitter.labelFile;

        // init imageData Class
        try{
            imageData = new preProcessUtils();
        } catch (Exception e) {
            Log.d(TAG, "cannot get ImageData from preProcessUtils Class!");
            e.printStackTrace();
        }

        // 加载模型和标签
        classifier = GeneralClassifier.getInstance(modelFile, labelFile);

        //获取控件，指定操作
        imageView = findViewById(R.id.image_view);
        textView = findViewById(R.id.result_text);

        try {
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
