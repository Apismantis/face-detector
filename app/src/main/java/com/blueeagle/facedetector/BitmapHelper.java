package com.blueeagle.facedetector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FaceRectangle;

public class BitmapHelper {

    // Draw face's data to bitmap
    public static Bitmap drawFaceInfoToBitmap(Bitmap originBitmap, Face[] faces) {
        Bitmap bitmap = originBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        if (faces != null) {
            for (Face face : faces) {
                FaceRectangle faceRectangle = face.faceRectangle;

                // Config paint to draw a rect
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.parseColor("#9C27B0"));
                paint.setStrokeWidth(3.0f);

                // Draw a rect
                canvas.drawRect(
                        faceRectangle.left,
                        faceRectangle.top,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.top + faceRectangle.height,
                        paint);

                // Draw another rect
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                canvas.drawRect(
                        faceRectangle.left,
                        faceRectangle.top - 15,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.top + 20,
                        paint);

                // Config paint to draw text
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(16.0f);

                String info = face.faceAttributes.gender + ", " + (int) face.faceAttributes.age;
                // Draw text
                canvas.drawText(info, faceRectangle.left + 10, faceRectangle.top + 5, paint);
            }
        }

        recycleBitmap(originBitmap);
        return bitmap;
    }

    public static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.recycle();
            System.gc();
        }
    }
}
