package com.example.ailab.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;


import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.ailab.deprecated.CameraActivity;
import com.example.ailab.R;
import com.example.ailab.classifier.GeneralClassifier;
import com.example.ailab.utils.preProcessUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.nio.ByteBuffer;
import java.util.List;

public class ImageActivity extends AppCompatActivity {
    private static final String TAG = CameraActivity.class.getName();
    /** Define the Size of Image*/
    private final int targetHeight = 224;
    private final int targetWidth = 224;

    /**Define normalizeOp params*/
    private final float mean = 0.0f;
    private final float stddev = 1.0f;

    private ImageView imageView;
    private TextView leftTextView, rightTextView;
    private View bottomSheet;
    private BottomSheetBehavior behavior;

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
        leftTextView = findViewById(R.id.result_key_text);
        rightTextView = findViewById(R.id.result_value_text);
        bottomSheet = findViewById(R.id.bottom_sheet_layout);
        behavior = BottomSheetBehavior.from(bottomSheet);
        ImageView bottom_sheet_arrow = findViewById(R.id.bottom_sheet_arrow);

        try {
            //显示图像
            imageView.setImageBitmap(bitmap);

            ByteBuffer imageInput = imageData.getUint8ImageWithNormOp(bitmap, targetHeight, targetWidth, mean, stddev).getBuffer();
            SpannableStringBuilder leftBuilder = new SpannableStringBuilder();
            SpannableStringBuilder rightBuilder = new SpannableStringBuilder();
            classifier.classifyFrame(imageInput, leftBuilder, rightBuilder, false);
            leftTextView.setText(leftBuilder);
            rightTextView.setText(rightBuilder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
