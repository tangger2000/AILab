package com.example.ailab.classifier;

import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GeneralClassifier extends Classifier{

    private static final String TAG = "ImageClassifier Class";
    /** Labels corresponding to the output of the vision model. */
    private final TensorBuffer probabilityBuffer;

    /** 单例实现 **/
    private static GeneralClassifier mInstance;

    public static GeneralClassifier getInstance(ByteBuffer modelFile, List<String> labelFile) {
        if (mInstance == null){
            synchronized (GeneralClassifier.class){
                if (mInstance == null){
                    Log.e(TAG, "GeneralClassifier init");
                    mInstance = new GeneralClassifier(modelFile, labelFile);
                }
            }
        }
        return mInstance;
    }

    private GeneralClassifier(ByteBuffer modelFile, List<String> labelFile) {
        super(modelFile, labelFile);
        // Create a container for the result and specify that this is a quantized model.
        // Hence, the 'DataType' is defined as UINT8 (8-bit unsigned integer)
        probabilityBuffer = TensorBuffer.createFixedSize(new int[]{1, getNumLabels()}, DataType.UINT8);
    }

    @Override
    protected void runInference(ByteBuffer imageInput) {
        tflite.run(imageInput, probabilityBuffer.getBuffer().rewind());
    }

    @Override
    protected List<Map.Entry<String, Float>> getTopKLabels(boolean softmax){
        Map<String, Float> floatMap = null;
        // Post-processor which dequantize the result
        TensorProcessor probabilityProcessor =
                new TensorProcessor.Builder().add(new NormalizeOp(0, 255)).build();

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
