package com.blueeagle.facedetector;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FaceRectangle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageButton imbGallery, imbCamera;
    private ImageView imvPhoto;
    private ProgressDialog progressDialog;

    static String TAG = "MainActivity";
    static final int RC_HANDLE_CAMERA_PERM = 1;
    static final int REQUEST_IMAGE_CAPTURE = 2;
    static final int REQUEST_PICK_IMAGE = 3;

    private String mCurrentPhotoPath;
    private Bitmap imageBitmap = null;

    // Face client for detector
    private FaceServiceClient faceServiceClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init view
        initView();

        // Check permission
        checkPermissions();

        // Face client for detector
        String APIKey = getResources().getString(R.string.microsoft_oxford_api_subscription_key);
        faceServiceClient = new FaceServiceRestClient(APIKey);

        imbGallery.setOnClickListener(this);
        imbCamera.setOnClickListener(this);

        // Config progress dialog
        progressDialog = new ProgressDialog(this);
    }

    public void initView() {
        imbCamera = (ImageButton) findViewById(R.id.imbCamera);
        imbGallery = (ImageButton) findViewById(R.id.imbGallery);
        imvPhoto = (ImageView) findViewById(R.id.imvPhoto);
    }

    public void checkPermissions() {
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc != PackageManager.PERMISSION_GRANTED)
            requestCameraPermission();
    }

    public void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission...");

        final String[] cameraPermission = new String[]{Manifest.permission.CAMERA};
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, cameraPermission, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Request camera permission onClick",
                        Toast.LENGTH_LONG).show();

                ActivityCompat.requestPermissions(thisActivity, cameraPermission, RC_HANDLE_CAMERA_PERM);
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission is granted.");
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();

        switch (viewId) {
            case R.id.imbCamera:
                dispatchTakePictureIntent();
                break;

            case R.id.imbGallery:
                pickImageFromGallery();
                break;
        }
    }

    /**
     * Detect gender and age of all people in photo
     *
     * @param imageBitmap: Bitmap image
     */
    public void detectFace(Bitmap imageBitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        new DetectAsyntask().execute(inputStream);
    }

    public void pickImageFromGallery() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickPhotoIntent.setType("image/*");
        startActivityForResult(pickPhotoIntent, REQUEST_PICK_IMAGE);
    }

    // Open camera then take a photo
    public void dispatchTakePictureIntent() {

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;

            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "Can't create photo file. " + ex.getMessage());
            }

            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(
                        this,
                        "com.example.android.fileprovider",
                        photoFile);

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    imageBitmap = decodeBitmap();
                    if (imageBitmap != null) {
                        imvPhoto.setImageBitmap(imageBitmap);
                        detectFace(imageBitmap);
                    }
                }
                break;

            case REQUEST_PICK_IMAGE:
                if (resultCode != RESULT_OK)
                    break;

                Uri uri = data.getData();

                try {
                    imageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    imvPhoto.setImageBitmap(imageBitmap);
                    detectFace(imageBitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                break;
        }

    }

    public File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storgeDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storgeDir);

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    // Decode bitmap to fit to image view
    public Bitmap decodeBitmap() {
        int targetW = imvPhoto.getWidth();
        int targetH = imvPhoto.getHeight();

        BitmapFactory.Options bmpOption = new BitmapFactory.Options();
        bmpOption.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmpOption);

        int photoW = bmpOption.outWidth;
        int photoH = bmpOption.outHeight;

        // Caculate scale ratio
        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        // Decode the image file into a Bitmap sized to fill the view
        bmpOption.inJustDecodeBounds = false;
        bmpOption.inSampleSize = scaleFactor;
        bmpOption.inPurgeable = true;

        return BitmapFactory.decodeFile(mCurrentPhotoPath, bmpOption);
    }

    public Bitmap drawInfoToBitmap(Bitmap originBitmap, Face[] faces) {
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

        return bitmap;
    }

    class DetectAsyntask extends AsyncTask<InputStream, String, Face[]> {

        @Override
        protected Face[] doInBackground(InputStream... params) {
            try {
                Log.d(TAG, "Detecting...");
                FaceServiceClient.FaceAttributeType types[] = new FaceServiceClient.FaceAttributeType[2];
                types[0] = FaceServiceClient.FaceAttributeType.Age;
                types[1] = FaceServiceClient.FaceAttributeType.Gender;

                Face[] result = faceServiceClient.detect(
                        params[0],
                        true,         // returnFaceId
                        false,        // returnFaceLandmarks
                        types          // returnFaceAttributes: a string like "age, gender"
                );

                if (result == null) {
                    Toast.makeText(getApplicationContext(),
                            "Detection finished. Nothing detected", Toast.LENGTH_LONG).show();
                    return null;
                }

                Log.d(TAG, String.format("Detection Finished. %d face(s) detected", result.length));
                return result;

            } catch (Exception e) {
                Toast.makeText(getApplicationContext(),
                        "Detect failed. Please try again!", Toast.LENGTH_LONG).show();
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Please wait! Detecting...");
            progressDialog.show();
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            //TODO: update progress
        }

        @Override
        protected void onPostExecute(Face[] result) {
            // Hide progress dialog
            progressDialog.dismiss();

            // Nothing detected or detect failed
            if (result == null)
                return;

            Bitmap bitmap = drawInfoToBitmap(imageBitmap, result);
            imvPhoto.setImageBitmap(bitmap);
        }
    }
}
