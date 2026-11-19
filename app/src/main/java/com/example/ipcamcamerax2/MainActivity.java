package com.example.ipcamcamerax2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Size;
import android.view.ScaleGestureDetector;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private MJPEGServer mjpegServer;
    private Camera camera;
    private ScaleGestureDetector scaleGestureDetector;

    private final ExecutorService analyzerExecutor =
            Executors.newSingleThreadExecutor();

    private static final float MIN_ZOOM = 1.0f;

    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreviewView previewView = findViewById(R.id.previewView);

        scaleGestureDetector =
                new ScaleGestureDetector(
                        this,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {

                            @Override
                            public boolean onScale(ScaleGestureDetector detector) {

                                if (camera == null) {
                                    return false;
                                }

                                ZoomState zoomState =
                                        camera.getCameraInfo()
                                                .getZoomState()
                                                .getValue();

                                if (zoomState == null) {
                                    return false;
                                }

                                float currentZoom =
                                        zoomState.getZoomRatio();

                                float newZoom =
                                        currentZoom * detector.getScaleFactor();

                                float maxZoom =
                                        zoomState.getMaxZoomRatio();

                                newZoom = Math.max(
                                        MIN_ZOOM,
                                        Math.min(newZoom, maxZoom)
                                );

                                camera.getCameraControl()
                                        .setZoomRatio(newZoom);

                                return true;
                            }
                        });

        previewView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });

        requestPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        result -> {
                            Boolean cameraPermission =
                                    result.getOrDefault(
                                            Manifest.permission.CAMERA,
                                            false
                                    );

                            if (Boolean.TRUE.equals(cameraPermission)) {
                                initializeCamera(previewView);
                            }
                        });

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            initializeCamera(previewView);

        } else {

            requestPermissionLauncher.launch(
                    new String[]{Manifest.permission.CAMERA}
            );
        }
    }

    private void initializeCamera(PreviewView previewView) {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {

            try {

                ProcessCameraProvider cameraProvider =
                        cameraProviderFuture.get();

                cameraProvider.unbindAll();

                Preview preview = new Preview.Builder().build();

                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setTargetResolution(new Size(640, 480))
                                .setOutputImageFormat(
                                        ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                                )
                                .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build();

                imageAnalysis.setAnalyzer(
                        analyzerExecutor,
                        image -> {

                            try {

                                Bitmap bitmap =
                                        Bitmap.createBitmap(
                                                image.getWidth(),
                                                image.getHeight(),
                                                Bitmap.Config.ARGB_8888
                                        );

                                bitmap.copyPixelsFromBuffer(
                                        image.getPlanes()[0].getBuffer()
                                );

                                ByteArrayOutputStream jpegStream =
                                        new ByteArrayOutputStream();

                                bitmap.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        40,
                                        jpegStream
                                );

                                bitmap.recycle();

                                if (mjpegServer != null) {
                                    mjpegServer.updateJpeg(
                                            jpegStream.toByteArray()
                                    );
                                }

                            } finally {
                                image.close();
                            }
                        });

                camera = cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                );

                camera.getCameraControl().setZoomRatio(1.0f);

                if (mjpegServer == null) {
                    mjpegServer = new MJPEGServer();
                    mjpegServer.start();
                }

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }

        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        analyzerExecutor.shutdown();

        if (mjpegServer != null) {
            mjpegServer.stopServer();
        }
    }
}