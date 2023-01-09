package com.dc.speech2text;

import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.ibm.cloud.sdk.core.http.HttpMediaType;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import com.ibm.watson.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResults;
import org.json.*;
import java.io.*;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final String[] permissions = {Manifest.permission.RECORD_AUDIO};
    private boolean audioRecordingPermissionGranted,isRecording;
    private Button startRecordingButton;
    private TextView displayText;
    private MediaRecorder mediaRecorder;
    private String recordedFileName,convertedFileName;
    private Handler mainHandler;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainHandler = new Handler();
        isRecording = false;
        mediaRecorder = new MediaRecorder();
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        startRecordingButton= findViewById(R.id.button);
        displayText = findViewById(R.id.textView);
        startRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRecording) {
                    if (audioRecordingPermissionGranted) {
                        try {
                            startAudioRecording();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                convertSpeech();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    thread.start();
                }
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            audioRecordingPermissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        if (!audioRecordingPermissionGranted) {
            finish();
        }
    }
    private void startAudioRecording() throws IOException {
        toggleRecording();
        recordedFileName = getFilesDir().getPath() + "/" + "audioFile" + ".3gp";
        convertedFileName = getFilesDir().getPath() + "/" + "audioFile" + ".mp3";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(recordedFileName);
        mediaRecorder.prepare();
        mediaRecorder.start();
    }
    private void convertSpeech() throws FileNotFoundException {
        toggleRecording();
        mediaRecorder.stop();
        mediaRecorder.reset();
        mediaRecorder.release();
        int rc = FFmpeg.execute(String.format("-i %s -c:a libmp3lame %s", recordedFileName, convertedFileName));
        if (rc == RETURN_CODE_SUCCESS) {
            IamAuthenticator authenticator = new IamAuthenticator("0iu8FI_Ug-hKuyQvYoaGoapwnAdAM6kkMtfjEnfpYC6h");
            SpeechToText speechToText = new SpeechToText(authenticator);
            speechToText.setServiceUrl("https://api.au-syd.speech-to-text.watson.cloud.ibm.com/instances/20d50a05-2ef7-4984-b86a-96df05079446");
            File audioFile = new File(convertedFileName);
            RecognizeOptions options = new RecognizeOptions.Builder()
                    .audio(audioFile)
                    .contentType(HttpMediaType.AUDIO_MP3)
                    .model("en-US_NarrowbandModel")
                    .build();
            final SpeechRecognitionResults transcript = speechToText.recognize(options).execute().getResult();
            System.out.println(transcript);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject jsonObject = new JSONObject(transcript.toString());
                        JSONArray resultsArray = jsonObject.getJSONArray("results");
                        for (int i = 0; i < resultsArray.length(); i++) {
                            JSONArray alternativesArray = resultsArray.getJSONObject(i).getJSONArray("alternatives");
                            for (int j = 0; j < alternativesArray.length(); j++) {
                                JSONObject resultObject = alternativesArray.getJSONObject(j);
                                displayText.append("\n"+resultObject.getString("transcript")+"\n");
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } else if (rc == RETURN_CODE_CANCEL) {
            Log.i(Config.TAG, "Command execution cancelled by user.");
        } else {
            Log.i(Config.TAG, String.format("Command execution failed with rc=%d and the output below.", rc));
            Config.printLastCommandOutput(Log.INFO);
        }
    }
    private void toggleRecording() {
        isRecording = !isRecording;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isRecording){
                    startRecordingButton.setText("Convert to Text");
                }
                else{
                    startRecordingButton.setText("Start Speech");
                }
            }
        });
    }
}