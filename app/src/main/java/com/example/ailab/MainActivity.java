package com.example.ailab;

import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 延迟闪屏时间
        new Thread(){
            @Override
            public void run() {
                super.run();
                try {
                    Thread.sleep(1000);
                    Intent intent = new Intent(MainActivity.this,CameraActivity.class);
                    startActivity(intent);
                    finish();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
