package com.example.ailab;

import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

/***
 * @className: preProcessUtils
 * @description: Convert the bitmap taken by the camera into a floating-point image and
 * return a ByteBuffer
 * @apiNote : getImgData
 */
public class preProcessUtils {


    /**
     * Writes Image data into a {@code ByteBuffer}.
     * @description:  Convert the bitmap to a float32 image,
     * resize the image size to target size and normalize the image
     * */
    private TensorImage convertBitmapToFloat32ImageWithNormOp(Bitmap bitmap, int targetHeight, int targetWidth, float mean, float stddev) {
        // Initialization code
        // Create an ImageProcessor with all ops required.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(targetHeight, targetWidth, ResizeOp.ResizeMethod.BILINEAR))
                        .add(new NormalizeOp(mean, stddev))
                        .build();

        // Create a TensorImage object. This creates the tensor of the corresponding
        // tensor type (Float32 in this case) that the TensorFlow Lite interpreter needs.
        TensorImage tImage = new TensorImage(DataType.FLOAT32);

        // Analysis code for every frame
        // Preprocess the image
        tImage.load(bitmap);
        return imageProcessor.process(tImage);
    }

    /**
     * Writes Image data into a {@code ByteBuffer}.
     * @description:  Convert the bitmap to a float32 image,
     * resize the image size to target size and normalize the image
     * */
    private TensorImage convertBitmapToUint8ImageWithNormOp(Bitmap bitmap, int targetHeight, int targetWidth, float mean, float stddev) {
        // Initialization code
        // Create an ImageProcessor with all ops required.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(targetHeight, targetWidth, ResizeOp.ResizeMethod.BILINEAR))
                        .add(new NormalizeOp(mean, stddev))
                        .build();

        // Create a TensorImage object. This creates the tensor of the corresponding
        // tensor type (Float32 in this case) that the TensorFlow Lite interpreter needs.
        TensorImage tImage = new TensorImage(DataType.UINT8);

        // Analysis code for every frame
        // Preprocess the image
        tImage.load(bitmap);
        return imageProcessor.process(tImage);
    }

    /**
     * Writes Image data into a {@code ByteBuffer}.
     * @description:  Convert the bitmap to a float32 image, resize the image size to target size
     * */
    private TensorImage convertBitmapToFloat32Image(Bitmap bitmap, int targetHeight, int targetWidth) {
        // Initialization code
        // Create an ImageProcessor with all ops required.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(targetHeight, targetWidth, ResizeOp.ResizeMethod.BILINEAR))
                        .build();

        // Create a TensorImage object. This creates the tensor of the corresponding
        // tensor type (Float32 in this case) that the TensorFlow Lite interpreter needs.
        TensorImage tImage = new TensorImage(DataType.FLOAT32);

        // Analysis code for every frame
        // Preprocess the image
        tImage.load(bitmap);
        return imageProcessor.process(tImage);
    }

    /**
     * Writes Image data into a {@code ByteBuffer}.
     * @description:  Convert the bitmap to a float32 image, resize the image size to target size
     * */
    private TensorImage convertBitmapToUint8Image(Bitmap bitmap, int targetHeight, int targetWidth) {
        // Initialization code
        // Create an ImageProcessor with all ops required.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(targetHeight, targetWidth, ResizeOp.ResizeMethod.BILINEAR))
                        .build();

        // Create a TensorImage object. This creates the tensor of the corresponding
        // tensor type (Float32 in this case) that the TensorFlow Lite interpreter needs.
        TensorImage tImage = new TensorImage(DataType.UINT8);

        // Analysis code for every frame
        // Preprocess the image
        tImage.load(bitmap);
        return imageProcessor.process(tImage);
    }

    /**
     * @param bitmap bitmap
     * @description: Get a normalized float32 image and feed it to TFLite as input
     * @return ByteBuffer
     * */
    public TensorImage getFloat32ImageWithNormOp(Bitmap bitmap, int targetHeight, int targetWidth, float mean, float stddev){
        return convertBitmapToFloat32ImageWithNormOp(bitmap, targetHeight, targetWidth, mean, stddev);
    }

    /**
     * @param bitmap bitmap
     * @description: Get a normalized uint8 image and feed it to TFLite as input
     * @return ByteBuffer
     * */
    public TensorImage getUint8ImageWithNormOp(Bitmap bitmap, int targetHeight, int targetWidth, float mean, float stddev){
        return convertBitmapToUint8ImageWithNormOp(bitmap, targetHeight, targetWidth, mean, stddev);
    }

    /**
     * @param bitmap bitmap
     * @description: Get a float32 image and feed it to TFLite as input
     * @return ByteBuffer
     * */
    public TensorImage getFloat32Image(Bitmap bitmap, int targetHeight, int targetWidth){
        return convertBitmapToFloat32Image(bitmap, targetHeight, targetWidth);
    }

    /**
     * @param bitmap bitmap
     * @description: Get a uint8 image and feed it to TFLite as input
     * @return ByteBuffer
     * */
    public TensorImage getUint8Image(Bitmap bitmap, int targetHeight, int targetWidth){
        return convertBitmapToUint8Image(bitmap, targetHeight, targetWidth);
    }
}
