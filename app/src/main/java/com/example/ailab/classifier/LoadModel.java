package com.example.ailab.classifier;

import java.util.List;
import android.app.Activity;
import java.io.IOException;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.ByteBuffer;
import android.content.res.AssetFileDescriptor;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;


/**
 * @name loadModel Class
 * @Description load model file and label file from file path to memory buffer and list
 * @author tangger
 * */
public class LoadModel {

    /** Reads label list from Assets. */
    private List<String> loadLabelList(Activity activity, String path) {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(activity.getAssets().open(path)));
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return labelList;
    }

    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity, String path) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(path);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    /**
     * @description: get the memory-mapped model from Loadmodel Class.
     * @param activity Activity
     * @return model file
     * */
    public ByteBuffer getModel(Activity activity, String path) throws IOException {
        return loadModelFile(activity, path);
    }

    /**
     * @description: get the list of label file from Loadmodel Class.
     * @param activity Activity
     * @return label file
     * */
    public List<String> getLabels(Activity activity, String path) {
        return loadLabelList(activity, path);
    }

}
