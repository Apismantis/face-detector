package com.blueeagle.facedetector;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.IOException;

public class DecoderBitmap {

    // Scale bitmap
    public static Bitmap scaleBitmap(Bitmap bitmap, int reqWidth, int reqHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= reqWidth && height <= reqHeight)
            return bitmap;

        float scaleW = 1.0f * reqWidth / width;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleW, scaleW);

        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
        BitmapHelper.recycleBitmap(bitmap);

        return resizedBitmap;
    }

    // Decode bitmap to fit to image view
    public static Bitmap readBitmap(Context context, Uri selectedImage, int reqWidth, int reqHeight) {
        Bitmap bitmap = null;
        AssetFileDescriptor fileDescriptor = null;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(selectedImage.getPath(), options);
        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);

        try {
            fileDescriptor = context.getContentResolver().openAssetFileDescriptor(selectedImage, "r");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor.getFileDescriptor(), null, options);
                fileDescriptor.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return bitmap;
    }

    // Decode bitmap
    public static Bitmap decodeBitmap(String path, int reqWidth, int reqHeight) {
        // Decode image size
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        int scaleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);

        // Decode bitmap in sample size
        options.inJustDecodeBounds = false;
        options.inSampleSize = scaleSize;
        options.inPurgeable = true;

        return BitmapFactory.decodeFile(path, options);
    }

    // Calculate insample size
    public static int calculateInSampleSize(int inWidth, int inHeight, int reqWidth, int reqHeight) {
        int inSampleSize = 1;

        if (inHeight > reqHeight || inWidth > reqWidth) {
            final int halfHeight = inHeight / 2;
            final int halfWidth = inWidth / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
