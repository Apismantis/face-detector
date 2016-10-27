package com.blueeagle.facedetector;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
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
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageButton imbGallery, imbCamera;
    private ImageView imvPhoto;
    private ProgressDialog progressDialog;

    static String TAG = "MainActivity";
    static final int RC_HANDLE_ALL_PERM = 1;
    static boolean RS_PERM = false;

    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_PICK_IMAGE = 2;

    private String mCurrentPhotoPath;
    private Bitmap imageBitmap = null;

    // Face client for detector
    private FaceServiceClient faceServiceClient;

    // Detect leak memory
    private RefWatcher refWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Leak-canary is in analyzing
        if (LeakCanary.isInAnalyzerProcess(this)) {
            Log.d(TAG, "Leak-canary is in analyzing...");
            return;
        }

        // Install Leak-canary
        refWatcher = LeakCanary.install(getApplication());

        // Init view
        initView();

        // Face client for detector
        String APIKey = getResources().getString(R.string.microsoft_oxford_api_subscription_key);
        faceServiceClient = new FaceServiceRestClient(APIKey);

        imbGallery.setOnClickListener(this);
        imbCamera.setOnClickListener(this);
    }

    // Init view
    public void initView() {
        imbCamera = (ImageButton) findViewById(R.id.imbCamera);
        imbGallery = (ImageButton) findViewById(R.id.imbGallery);
        imvPhoto = (ImageView) findViewById(R.id.imvPhoto);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check permission
        RS_PERM = checkPermissions();
    }

    public boolean checkNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null;
    }

    // Check all permission
    public boolean checkPermissions() {
        String[] requestPermission = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE};

        List<String> permissionNeedRequest = new ArrayList<>();
        int rs;

        for (String permission : requestPermission) {
            rs = ActivityCompat.checkSelfPermission(this, permission);
            if (rs != PackageManager.PERMISSION_GRANTED)
                permissionNeedRequest.add(permission);
        }

        if (!permissionNeedRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionNeedRequest.toArray(new String[permissionNeedRequest.size()]),
                    RC_HANDLE_ALL_PERM);

            Log.d(TAG, "All permission is not granted...");
            return false;
        }

        return true;
    }

    // Handle result of permission request
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        switch (requestCode) {
            case RC_HANDLE_ALL_PERM:
                if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "All permission is granted.");
                    RS_PERM = true;
                } else {
                    RS_PERM = false;
                }

                break;

            default:
                break;
        }
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();

        if (!RS_PERM && (viewId == R.id.imbCamera || viewId == R.id.imbGallery)) {
            Toast.makeText(this,
                    "Face Detector has no permission!. Please grant permission in " +
                            "Settings -> Application -> Face Detector -> Permissions",
                    Toast.LENGTH_LONG).show();
        }

        Log.d(TAG, RS_PERM + "");

        switch (viewId) {
            case R.id.imbCamera:
                if (RS_PERM)
                    takePhoto();

                break;

            case R.id.imbGallery:
                if (RS_PERM)
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
        // Check network
        if (!checkNetwork()) {
            Toast.makeText(this, "Network is off. Please turn on " +
                    "network connection to continue detecting.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Create input stream from bitmap
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
            progressDialog.setMessage("Please wait! Detecting...");
            progressDialog.show();

            // Detect face from input stream
            new DetectAsyncTask().execute(inputStream);

            // Close output stream
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Pick an image from gallery
    public void pickImageFromGallery() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickPhotoIntent.setType("image/*");
        startActivityForResult(pickPhotoIntent, REQUEST_PICK_IMAGE);
    }

    // Open camera then take a photo
    public void takePhoto() {

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;

            try {
                photoFile = createImageFile();

                if (photoFile != null) {
                    Uri photoUri = FileProvider.getUriForFile(
                            this,
                            "com.example.android.fileprovider",
                            photoFile);

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Can't create photo file. " + ex.getMessage());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    if (imageBitmap != null)
                        BitmapHelper.recycleBitmap(imageBitmap);

                    imageBitmap = ExifUtil.rotateBitmap(
                            mCurrentPhotoPath,
                            DecoderBitmap.decodeBitmap(mCurrentPhotoPath, imvPhoto.getWidth(), imvPhoto.getHeight()));

                    if (imageBitmap != null) {
                        imvPhoto.setImageBitmap(imageBitmap);
                        detectFace(imageBitmap);
                    }
                }
                break;

            case REQUEST_PICK_IMAGE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();

                    if (imageBitmap != null)
                        BitmapHelper.recycleBitmap(imageBitmap);

                    imageBitmap = DecoderBitmap.scaleBitmap(
                            DecoderBitmap.readBitmap(this, uri, imvPhoto.getWidth(), imvPhoto.getHeight()),
                            imvPhoto.getWidth(),
                            imvPhoto.getHeight());

                    imvPhoto.setImageBitmap(imageBitmap);
                    detectFace(imageBitmap);
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
        Log.d(TAG, "Current photo path: " + mCurrentPhotoPath);
        return image;
    }

    class DetectAsyncTask extends AsyncTask<InputStream, String, Face[]> {

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
                        types         // returnFaceAttributes: a string like "age, gender"
                );

                if (result == null) {
                    showToast("Detection finished. Nothing detected");
                    return null;
                }

                showToast(String.format(Locale.US, "Detection Finished. %d face(s) detected", result.length));
                return result;

            } catch (Exception e) {
                showToast("Detect failed. Please try again!");
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            //TODO: .......
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            //TODO: update progress
        }

        @Override
        protected void onPostExecute(Face[] result) {
            // Hide progress dialog
            progressDialog.setCancelable(true);
            progressDialog.dismiss();

            // Nothing detected or detect failed
            if (result == null)
                return;

            Bitmap bitmap = BitmapHelper.drawFaceInfoToBitmap(imageBitmap, result);
            imvPhoto.setImageBitmap(bitmap);
        }

        public void showToast(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (imageBitmap != null)
            BitmapHelper.recycleBitmap(imageBitmap);

        // Watch leak memory
        // refWatcher.watch(this);

        super.onDestroy();
    }
}
