package org.tensorflow.demo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.tensorflow.demo.env.ImageUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by O.Kuskov on 3/27/2018.
 */

public class VideoProcessorActivity extends Activity {

    private static final String TAG = VideoProcessorActivity.class.getSimpleName();
    private static final int EXTRACTOR_FRAMES_PER_SECOND = 1;
    private static final String TF_OD_API_MODEL_FILE = "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";
    private static final int TF_OD_API_INPUT_SIZE = 300;

    private Handler handler;
    private HandlerThread handlerThread;
    private VideoView mVideoView;
    private ImageView mFramePreviewView;
    private FrameLayout mReplacementLayer;
    private Classifier detector;
    private float mWidthFrameMultiplicator = 1f;
    private float mHeightFrameMultiplicator = 1f;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(null);

        setContentView(R.layout.activity_video_processor);

        mReplacementLayer = (FrameLayout) findViewById(R.id.flReplacementLayer);

        mVideoView = (VideoView)findViewById(R.id.vvPlayer);
        mVideoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.video_sample));

        mFramePreviewView = (ImageView) findViewById(R.id.framePreviewView);

        findViewById(R.id.btnProcessVideo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWidthFrameMultiplicator = mVideoView.getWidth() / (float)TF_OD_API_INPUT_SIZE;
                mHeightFrameMultiplicator = mVideoView.getHeight() / (float)TF_OD_API_INPUT_SIZE;

                processVideo();
            }
        });

        initDetector();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "Exception!");
        }

        super.onPause();
    }

    private void initDetector() {
        try {
            detector = TensorFlowObjectDetectionAPIModel.create(
                    getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
//            cropSize = TF_OD_API_INPUT_SIZE;
            Log.i(TAG, "detector successfully init");
        } catch (final IOException e) {
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
//            finish();
        }
    }

    private ArrayList<Bitmap> extractFrames() {
        ArrayList<Bitmap> frameList = new ArrayList<>();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.video_sample));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        int duration_millisec = Integer.parseInt(duration); //duration in millisec
        int duration_second = duration_millisec / 1000;  //millisec to sec.
        int numeroFrameCaptured = EXTRACTOR_FRAMES_PER_SECOND * duration_second;
        for (int i = 0; i < numeroFrameCaptured; i++) {
            final Bitmap frame = retriever.getFrameAtTime(1000000*i, MediaMetadataRetriever.OPTION_CLOSEST);
            frameList.add(frame);

            updateCurrentStatus("Extracted frames: " + (i * 100 / numeroFrameCaptured) + "%");

//            show preview of extracted frame
//            runOnUiThread(
//                    new Runnable() {
//                        @Override
//                        public void run() {
//                            mFramePreviewView.setImageBitmap(frame);
//
//                        }
//                    }
//            );
        }

        updateCurrentStatus("Frames extracted successfully! " + numeroFrameCaptured + " frames found.");
        retriever.release();
        return frameList;
    }

    private void updateCurrentStatus(final String text) {
        Log.i(TAG, text);
        runOnUiThread(new Runnable() {
            public void run() {
                ((TextView) findViewById(R.id.txtStatus)).setText(text);
            }
        });
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    private void playCompleteVideo(final ArrayList<RectF> objects) {
        Log.i(TAG, "play complete video");
        runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    mVideoView.start();
                }
            }
        );

        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    for (final RectF object : objects) {
                        Log.i(TAG, "sleep for a second");
                        Thread.sleep(1000 / EXTRACTOR_FRAMES_PER_SECOND);
                        replaceObject(object);
                    }
                } catch (Exception e) {
                    e.getLocalizedMessage();
                }
            }
        });
    }

    private void replaceObject(final RectF location) {
        Log.i(TAG, "replacing object");
        runOnUiThread(
            new Runnable() {
                @Override
                public void run() {

                    mReplacementLayer.removeAllViews();

                    ImageView iv = new ImageView(VideoProcessorActivity.this);
                    iv.setImageResource(R.drawable.user);
                    iv.setLayoutParams(new ViewGroup.LayoutParams(
                            (int)(location.right - location.left),
                            (int)(location.bottom - location.top)));
                    iv.setX(location.left);
                    iv.setY(location.top);

                    mReplacementLayer.addView(iv);


                    Log.i(TAG, "location top: " + location.top);
                    Log.i(TAG, "location left: " + location.left);
                    Log.i(TAG, "location bottom: " + location.bottom);
                    Log.i(TAG, "location right: " + location.right);
                }
            }
        );
    }

    private ArrayList<RectF> detectObjectsOnFrames(List<Bitmap> framesList) {
        ArrayList<RectF> objects = new ArrayList<>();

        for (int i =0; i < framesList.size(); i++) {
            final Bitmap frame = framesList.get(i);
            Bitmap croppedBitmap = Bitmap.createScaledBitmap(frame, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, false);

//            ImageUtils.saveBitmap(frame, "f_" + SystemClock.uptimeMillis() + ".png");
//            ImageUtils.saveBitmap(croppedBitmap, "c_" + SystemClock.uptimeMillis() + ".png");

//            ImageUtils.saveBitmap(frame);


            List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
            objects.add(getFirstPersonObject(results));

            updateCurrentStatus("Objects detected on " + (i * 100 / framesList.size()) + "% frames");
        }

        updateCurrentStatus("Objects detected successfully!");
        return objects;
    }

    private RectF getFirstPersonObject( List<Classifier.Recognition> objects) {
        RectF object = new RectF();

        for (Classifier.Recognition obj : objects) {
            if (obj.getTitle().equals("person")) {

                object.top = obj.getLocation().top * mHeightFrameMultiplicator;
                object.bottom = obj.getLocation().bottom * mHeightFrameMultiplicator;
                object.left = obj.getLocation().left * mWidthFrameMultiplicator;
                object.right = obj.getLocation().right * mWidthFrameMultiplicator;

                break;
            }
        }

        return object;
    }

    private void processVideo() {
        Log.i(TAG, "processing ....");
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final long startTime = SystemClock.uptimeMillis();

                        ArrayList<Bitmap> frames = extractFrames();
                        Log.i(TAG, "frames count: " + frames.size());

                        ArrayList<RectF> detectedObjects = detectObjectsOnFrames(frames);
                        Log.i(TAG, "detected objects: " + detectedObjects.size());

                        long lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        updateCurrentStatus("Processing time: " + (int)(lastProcessingTimeMs / 1000) + " seconds");
                        playCompleteVideo(detectedObjects);
                    }
                }
        );
    }
}
