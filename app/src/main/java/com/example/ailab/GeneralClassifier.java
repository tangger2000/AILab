package com.example.ailab;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GeneralClassifier extends Classifier{

    private static final String TAG = "ImageClassifier Class";
    /** file path*/
    public static final String modelPath = "mobilenet_v3.tflite";
    public static final String labelPath = "labels_list.txt";

    /** Define the Size of Image*/
    private static final int targetHeight = 224;
    private static final int targetWidth = 224;

    /**Define normalizeOp params*/
    private static final float mean = 0;
    private static final float stddev = 255;

    /** Labels corresponding to the output of the vision model. */
    private final TensorBuffer probabilityBuffer;

    private preProcessUtils imageData;

    GeneralClassifier(Activity activity) {
        super(activity);
        // init imageData Class
        try{
            imageData = new preProcessUtils();
        } catch (Exception e) {
            Log.d(TAG, "cannot get ImageData from preProcessUtils Class!");
            e.printStackTrace();
        }

        // Create a container for the result and specify that this is a quantized model.
        // Hence, the 'DataType' is defined as UINT8 (8-bit unsigned integer)
        probabilityBuffer = TensorBuffer.createFixedSize(new int[]{1, getNumLabels()}, DataType.FLOAT32);
    }

    @Override
    protected void runInference(Bitmap bitmap) {
        ByteBuffer imageInput = imageData.getFloat32ImageWithNormOp(bitmap, targetHeight, targetWidth, mean, stddev).getBuffer();
        tflite.run(imageInput, probabilityBuffer.getBuffer().rewind());
    }

    @Override
    protected List<Map.Entry<String, Float>> getTopKLabels(boolean softmax){
        Map<String, Float> floatMap = null;
        // Post-processor which dequantize the result
        TensorProcessor probabilityProcessor =
                new TensorProcessor.Builder().build();

        if (softmax)
        {
            float[] logits = probabilityBuffer.getFloatArray();
            float[] probability = softMax(logits);
            probabilityBuffer.loadArray(probability);
        }

        // mapping labels with probability
        if (null != labelFile) {
            // Map of labels and their corresponding probability
            TensorLabel labels = new TensorLabel(labelFile,
                    probabilityProcessor.process(probabilityBuffer));

            // Create a map to access the result based on label
            floatMap = labels.getMapWithFloatValue();
        }

        //将map.entrySet()转换成list
        assert floatMap != null;
        List<Map.Entry<String,Float>> list = new ArrayList<>(floatMap.entrySet());
        //然后通过比较器来实现排序
        //升序排序
        Collections.sort(list, (o1, o2) -> -o1.getValue().compareTo(o2.getValue()));

        // 检查是否成功归一化，约为1就是成功的
//        float sum=0;
//        for (int i =0;i<list.size();i++)
//        {
//            sum += list.get(i).getValue();
//        }
        return list;
    }
}
