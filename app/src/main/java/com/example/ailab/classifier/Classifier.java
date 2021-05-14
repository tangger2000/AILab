
package com.example.ailab.classifier;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.nnapi.NnApiDelegate;


/** Classifies images with Tensorflow Lite. */
public abstract class Classifier {
    /** Tag for the {@link Log}. */
    private static final String TAG = "Classifier Class";

    /** Define the number of threads*/
    private static final int NUM_THREADS = 4;
    /** Number of results to show in the UI. */
    protected static final int RESULTS_TO_SHOW = 3;
    // Display preferences
    private static final float GOOD_PROB_THRESHOLD = 0.3f;
    private static final int SMALL_COLOR = 0xffddaa88;

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    protected Interpreter tflite;
    protected ByteBuffer modelFile;
    protected List<String> labelFile;

    protected PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    (o1, o2) -> (o1.getValue()).compareTo(o2.getValue()));

    Classifier(ByteBuffer modelFile, List<String> labelFile) {
        this.modelFile = modelFile;
        this.labelFile = labelFile;

        Interpreter.Options options = (new Interpreter.Options());
        options.setNumThreads(NUM_THREADS);
        NnApiDelegate nnApiDelegate;
        // Initialize interpreter with NNAPI delegate for Android Pie or above
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            nnApiDelegate = new NnApiDelegate();
            options.addDelegate(nnApiDelegate);
        }

        /* init TFLite interpreter*/
        tflite = new Interpreter(modelFile, options);
        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.");
    }


    /** Classifies a frame from the preview stream.
     * @return builder*/
    public SpannableStringBuilder classifyFrame(ByteBuffer imageInput) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
            builder.append(new SpannableString("Uninitialized Classifier."));
        }

        // Here's where the magic happens!!!
        long startTime = SystemClock.uptimeMillis();
        runInference(imageInput);
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Timecost to run model inference: " + (endTime - startTime));

//        // Smooth the results across frames.
//        applyFilter();
        printTopKLabels(builder);
        // Print the results.
        long duration = endTime - startTime;
        SpannableString span = new SpannableString(duration + " ms");
        span.setSpan(new ForegroundColorSpan(android.graphics.Color.LTGRAY), 0, span.length(), 0);
        builder.append(span);
        return builder;
    }

    /** Prints top-K labels, to be shown in UI as the results. */
    private void printTopKLabels(SpannableStringBuilder builder) {
        List<Map.Entry<String, Float>>list = getTopKLabels(true);
        for(Map.Entry<String,Float> mapping:list){
            sortedLabels.add(mapping);
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }

        final int size = sortedLabels.size();
        for (int i = 0; i < size; i++) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            assert label != null;
            SpannableString span =
                    new SpannableString(String.format(Locale.CHINA,"%s:  %4.3f\n", label.getKey(), label.getValue()));
            int color;
            // Make it black when probability larger than threshold.
            if (label.getValue() > GOOD_PROB_THRESHOLD) {
                color = Color.BLACK;
            } else {
                color = SMALL_COLOR;
            }
            // Make first item bigger.
            if (i == size - 1) {
                float sizeScale = (i == size - 1) ? 1.75f : 0.8f;
                span.setSpan(new RelativeSizeSpan(sizeScale), 0, span.length(), 0);
            }
            span.setSpan(new ForegroundColorSpan(color), 0, span.length(), 0);
            builder.insert(0, span);
        }
    }

    protected float[] softMax(float[] params) {
        float sum = 0;

        float[] exp_a = new float[params.length];
        for (int i=0; i<params.length; i++) {
            exp_a[i] = (float) Math.exp(params[i]);
            sum += exp_a[i];
        }

        float[] result = new float[params.length];
        for (int i=0; i<params.length; i++) {
            result[i] = exp_a[i] / sum;
        }

        return result;
    }

    protected abstract List<Map.Entry<String, Float>> getTopKLabels(boolean softmax);

    protected abstract void runInference(ByteBuffer imageInput);

    protected int getNumLabels() {
        return labelFile.size();
    }

    /** Closes tflite to release resources. */
    public void close() {
        tflite.close();
        tflite = null;
    }

}
