package com.example.covid_19monitor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "MainActivity";
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 1;
    private Button button_symptoms;
    private Button upload_signs_button;
    private String heartRate = "0";
    private String respiratoryRate = "0";
    private HashMap<String, String> symptomsHashMap;
    DatabaseHelper myDB;

    private final HeartRateVideoPreview videoPreview = new HeartRateVideoPreview(this);
    private DataCollection mCollection;

    private int detectedValleys = 0;
    private int ticksPassed = 0;

    private CopyOnWriteArrayList<Long> valleys;

    private CountDownTimer timer;
    private SensorManager accelManage;
    private Sensor senseAccel;
    final private int MAX_COUNT = 220;
    float accelValuesX[] = new float[MAX_COUNT];
    float accelValuesY[] = new float[MAX_COUNT];
    float accelValuesZ[] = new float[MAX_COUNT];
    long time1;
    long time2;
    int total_time = 0;
    int index =0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //Initialize DB instance
        myDB = new DatabaseHelper(this);

        initialize_HashMap();

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                MY_PERMISSIONS_REQUEST_READ_CONTACTS);

        //changing view from MainActivity to Symptoms Activity
        button_symptoms = (Button)findViewById(R.id.button_symptoms);
        button_symptoms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSymptomsView();
            }
        });

        //receiving and processing the symptoms from Symptoms Activity
        Intent intent = getIntent();
        String symptomsString = (String) intent.getStringExtra("EXTRA_TEXT");
        if(symptomsString != null){
            symptomsString = symptomsString.substring(1);
            symptomsString = symptomsString.substring(0, symptomsString.length() - 1);
            Log.d(TAG, "symptoms"+ symptomsString);
            String[] pairs = symptomsString.split(",");
            for (int i=0;i<pairs.length;i++) {
                String pair = pairs[i].trim();
                String[] keyValue = pair.split("=");
                symptomsHashMap.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }

        Button heart_rate_button = (Button) findViewById(R.id.heart_rate_button);


        if(!hasCamera()){
            heart_rate_button.setEnabled(false);
        }

        final String finalSymptomsString1 = symptomsString;
        heart_rate_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(finalSymptomsString1!= null){
                    //startRecording();
                    startHeartRateVideoPreview();
                }
                else {
                    Toast.makeText(getApplicationContext(), "Please record symptoms first", Toast.LENGTH_LONG).show();
                }

            }
        });

        Button resp_button = (Button) findViewById(R.id.respiratory_button);
        resp_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(finalSymptomsString1 != null){
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    accelManage = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                    senseAccel = accelManage.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                    accelManage.registerListener(MainActivity.this, senseAccel, SensorManager.SENSOR_DELAY_NORMAL);

                }
                else {
                    Toast.makeText(getApplicationContext(), "Please record symptoms first", Toast.LENGTH_LONG).show();
                }

            }
        });



        upload_signs_button = (Button)findViewById(R.id.upload_signs_button);
        final String finalSymptomsString = symptomsString;
        upload_signs_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(finalSymptomsString !=null){
                    symptomsHashMap.put("Heart Rate", heartRate);
                    symptomsHashMap.put("Respiratory Rate", respiratoryRate);
                    boolean flag = myDB.insertData(symptomsHashMap);
                    if (flag)
                        Toast.makeText(getApplicationContext(), "Successfully inserted data", Toast.LENGTH_LONG).show();
                }

            }
        });

    }

    private void initialize_HashMap(){
        symptomsHashMap = new HashMap<>();
        symptomsHashMap.put("Heart Rate","");
        symptomsHashMap.put("Respiratory Rate","");
        symptomsHashMap.put("Nausea", "");
        symptomsHashMap.put("Headache", "");
        symptomsHashMap.put("Diarrhoea", "");
        symptomsHashMap.put("Soar Throat", "");
        symptomsHashMap.put("Fever", "");
        symptomsHashMap.put("Muscle Ache", "");
        symptomsHashMap.put("Loss of smell or taste", "");
        symptomsHashMap.put("Cough", "");
        symptomsHashMap.put("Shortness of breath", "");
        symptomsHashMap.put("Feeling Tired", "");
    }
    private boolean hasCamera() {
        if (getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY)){
            return true;
        } else {
            return false;
        }
    }


    public void openSymptomsView(){
        Intent intent = new Intent(this, SymptomsActivity.class);
        startActivity(intent);
    }



    private boolean detectValley() {
        final int valleyDetectionWindowSize = 13;
        CopyOnWriteArrayList<PixelData<Integer>> subList = mCollection.getLastStdValues(valleyDetectionWindowSize);
        if (subList.size() < valleyDetectionWindowSize) {
            return false;
        } else {
            Integer referenceValue = subList.get((int) Math.ceil(valleyDetectionWindowSize / 2)).measurement;

            for (PixelData<Integer> measurement : subList) {
                if (measurement.measurement < referenceValue) return false;
            }

            // filter out consecutive measurements due to too high measurement rate
            return (!subList.get((int) Math.ceil(valleyDetectionWindowSize / 2)).measurement.equals(
                    subList.get((int) Math.ceil(valleyDetectionWindowSize / 2) - 1).measurement));
        }
    }

    void measurePulse(final TextureView textureView, final HeartRateVideoPreview cameraService) {
        mCollection = new DataCollection();

        detectedValleys = 0;

        timer = new CountDownTimer(50000, 45) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (3500 > (++ticksPassed * 45)) return;
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run(){
                        Bitmap currentBitmap = textureView.getBitmap();
                        int pixelCount = textureView.getWidth() * textureView.getHeight();
                        int measurement = 0;
                        int[] pixels = new int[pixelCount];

                        currentBitmap.getPixels(pixels, 0, textureView.getWidth(), 0, 0, textureView.getWidth(), textureView.getHeight());


                        for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
                            measurement += (pixels[pixelIndex] >> 16) & 0xff;
                        }

                        mCollection.add(measurement);

                        if (detectValley()) {
                            detectedValleys = detectedValleys + 1;
                            valleys.add(mCollection.getLastTimestamp().getTime());
                        }
                    }
                });
                thread.start();
            }

            @Override
            public void onFinish() {
                heartRate = String.valueOf((int)(60f * (detectedValleys - 1) / (Math.max(1, (valleys.get(valleys.size() - 1) - valleys.get(0)) / 1000f))));

                String currentValue = String.format(
                        Locale.getDefault(), heartRate);
                ((TextView) findViewById(R.id.heart_rate_text)).setText("Heart Rate: " + currentValue);

                cameraService.stop();
            }
        };

        timer.start();
    }

    void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private void startHeartRateVideoPreview() {

        valleys = new CopyOnWriteArrayList<>();

        TextureView cameraTextureView = findViewById(R.id.textureView2);

        SurfaceTexture previewSurfaceTexture = cameraTextureView.getSurfaceTexture();
        if (previewSurfaceTexture != null) {
            Surface previewSurface = new Surface(previewSurfaceTexture);

            videoPreview.start(previewSurface);
            measurePulse(cameraTextureView, videoPreview);
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor mySensor = event.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (index==0)
                time1 = System.currentTimeMillis();
            index++;
            accelValuesX[index] = event.values[0];
            accelValuesY[index] = event.values[1];
            accelValuesZ[index] = event.values[2];
            if(index >= MAX_COUNT-1){
                time2 = System.currentTimeMillis();
                index = 0;
                total_time = (int) ((time2-time1)/1000);
                accelManage.unregisterListener(this);

                int breathRate = calculateBreathRate();

                respiratoryRate = String.valueOf((int)breathRate);
                String currentValue = String.format( Locale.getDefault(),"Respiratory Rate: " + respiratoryRate);
                ((TextView) findViewById(R.id.breath_rate_text)).setText(currentValue);
            }
        }
    }
    public int calculateBreathRate(){
        if (total_time==0)
            return 0;
        float xSum=0, ySum=0, zSum=0;
        for (int i =0; i<MAX_COUNT; i++){
            xSum += accelValuesX[i];
            ySum += accelValuesY[i];
            zSum += accelValuesZ[i];
        }
        float xAvg = xSum/MAX_COUNT, yAvg = ySum/MAX_COUNT, zAvg = zSum/MAX_COUNT;
        int xCount=0, yCount=0, zCount =0;
        for (int i =1; i<MAX_COUNT; i++){
            if ((accelValuesX[i-1] <= xAvg && xAvg<= accelValuesX[i]) || (accelValuesX[i-1] >= xAvg && xAvg>= accelValuesX[i]))
                xCount++;
            if ((accelValuesY[i-1] <= yAvg && yAvg<= accelValuesY[i]) || (accelValuesY[i-1] >= yAvg && yAvg>= accelValuesY[i]))
                yCount++;
            if ((accelValuesZ[i-1] <= zAvg && zAvg<= accelValuesZ[i]) || (accelValuesZ[i-1] >= zAvg && zAvg>= accelValuesZ[i]))
                zCount++;
        }
        int max_count = Math.max(xCount, Math.max(yCount, zCount));
        return max_count*30/total_time;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
