package io.agora.rte.extension.histreamvc.example;

import static io.agora.rtc2.Constants.CLIENT_ROLE_BROADCASTER;
import static io.agora.rtc2.Constants.POSITION_BEFORE_MIXING;
import static io.agora.rtc2.Constants.POSITION_MIXED;
import static io.agora.rtc2.Constants.POSITION_PLAYBACK;
import static io.agora.rtc2.Constants.POSITION_RECORD;
import static io.agora.rtc2.Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

import io.agora.extension.histreamvc.R;
import io.agora.extension.histreamvc.ExtensionManager;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IAudioFrameObserver;
import io.agora.rtc2.IMediaExtensionObserver;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.audio.AudioParams;

import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements IMediaExtensionObserver {

    private static final String TAG = "MainActivity";

    private RtcEngine mRtcEngine;

//    private Button button;
//    private final ObservableBoolean enableExtension =
//            new ObservableBoolean(false);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Handler mWorkHandler;
    private Runnable mWorkHandlerRunnable;
    private HandlerThread mHandlerThread;
    private static final String STREAM_VC_ASSET = "stream_vc";
    private int myUid;
    private boolean joined;
    private Button btn_join;
    private EditText et_channel;

    public static void hideInputBoard(Activity activity, EditText editText)
    {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn_join = findViewById(R.id.btn_join);
        initUI();;
        initPermission();
    }

    private void initUI() {
        et_channel = findViewById(R.id.et_channel);
        btn_join.setOnClickListener(view -> {
            if (view.getId() == R.id.btn_join) {
                if (!joined) {
                    hideInputBoard(this, et_channel);
                    String channelId = et_channel.getText().toString();
                    if (AndPermission.hasPermissions(this, Permission.Group.STORAGE, Permission.Group.MICROPHONE)) {
                        joinChannel(channelId);
                        return;
                    }
                    AndPermission.with(this).runtime().permission(
                            Permission.Group.STORAGE,
                            Permission.Group.MICROPHONE
                    ).onGranted(permissions ->
                            joinChannel(channelId)).start();
                } else {
                    joined = false;
                    mRtcEngine.leaveChannel();
                    btn_join.setText(getString(R.string.join));
                }
            }
        });
//        button = findViewById(R.id.button_enable);
//        button.setOnClickListener(
//                view -> enableExtension.set(!enableExtension.get()));
        findViewById(R.id.init_extension).setOnClickListener(view -> initExtension());
        findViewById(R.id.button_start_vc)
                .setOnClickListener(view -> start_vc());
        findViewById(R.id.button_stop_vc)
                .setOnClickListener(view -> stop_vc());
        findViewById(R.id.button_start_vc).setEnabled(false);
        findViewById(R.id.button_stop_vc).setEnabled(false);

        try {
            ResourceUtils.copyFileOrDir(
                    MainActivity.this.getAssets(),
                    STREAM_VC_ASSET,
                    getExternalFilesDir(null).getAbsolutePath()
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void start_vc(){
        JSONObject jsonObject = new JSONObject();
        setExtensionProperty(ExtensionManager.KEY_START_VC, jsonObject.toString());
//        findViewById(R.id.button_start_vc).setEnabled(false);
//        findViewById(R.id.button_stop_vc).setEnabled(true);
    }

    private void stop_vc(){
        JSONObject jsonObject = new JSONObject();
        setExtensionProperty(ExtensionManager.KEY_STOP_VC, jsonObject.toString());
//        findViewById(R.id.button_start_vc).setEnabled(false);
//        findViewById(R.id.button_stop_vc).setEnabled(false);
    }


    private void initExtension() {
        try {
            JSONObject jsonObject = new JSONObject();
            // 传入在声网控制台激活插件后获取的 appKey
            jsonObject.put("appkey", getString(R.string.appKey));
            // 传入在声网控制台激活插件后获取的 appSecret
            jsonObject.put("secret", getString(R.string.secret));
            jsonObject.put("init_json",
                    Objects.requireNonNull(getApplicationContext()).getExternalFilesDir(STREAM_VC_ASSET + "/init.json"));
            jsonObject.put("init_dir",
                    Objects.requireNonNull(getApplicationContext()).getExternalFilesDir(STREAM_VC_ASSET));
            setExtensionProperty(ExtensionManager.KEY_INIT_FILTER, jsonObject.toString());
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
//        findViewById(R.id.init_extension).setEnabled(false);
    }

    private void setExtensionProperty(String key, String property) {
        mRtcEngine.setExtensionProperty(ExtensionManager.EXTENSION_VENDOR_NAME,
                ExtensionManager.EXTENSION_AUDIO_FILTER, key, property);
    }

    private void initPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO},
                    0);
        } else {
            initRtcEngine();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0) {
            if (Arrays.equals(grantResults, new int[]{0, 0})) {
                initRtcEngine();
            }
        }
    }

    private void initRtcEngine() {
        RtcEngineConfig config = new RtcEngineConfig();
        config.mContext = getApplicationContext();
        config.mAppId = getString(R.string.agora_app_id);
        config.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
        config.addExtension(ExtensionManager.EXTENSION_NAME);
        config.mExtensionObserver = this;
        config.mEventHandler = iRtcEngineEventHandler;
        config.mAreaCode = RtcEngineConfig.AreaCode.AREA_CODE_CN;

        try {
            mRtcEngine = RtcEngine.create(config);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        if (mRtcEngine == null) {
            Log.e(TAG, "RtcEngin.create return null");
            return;
        }
        mRtcEngine.setParameters("{"
                + "\"rtc.report_app_scenario\":"
                + "{"
                + "\"appScenario\":" + 100 + ","
                + "\"serviceType\":" + 11 + ","
                + "\"appVersion\":\"" + RtcEngine.getSdkVersion() + "\""
                + "}"
                + "}");
        mRtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);

        mRtcEngine.registerAudioFrameObserver(iAudioFrameObserver);

//        enableExtension.set(true);
        enableExtension(true);
        mRtcEngine.enableAudio();

    }
    private final IAudioFrameObserver iAudioFrameObserver = new IAudioFrameObserver() {

        private boolean initMixedOut = false;
        private boolean initPreMixOut = false;
        private boolean initRecordOut = false;
        private boolean initPlayBack = false;

        private OutputStream mixedOutput;
        private OutputStream preMixOutput;
        private OutputStream recordOutput;
        private OutputStream playBackOutput;

        @Override
        public boolean onRecordAudioFrame(String channel, int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, ByteBuffer byteBuffer, long renderTimeMs, int bufferLength) {
//            Log.i(TAG, "onRecordAudioFrame");
            if(!initRecordOut){
                try {
                    recordOutput = new FileOutputStream(
                            Environment.getExternalStorageDirectory().getPath() +
                                    "/Android/data/io.agora.extension.histreamvc/files/recordOutput.pcm");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                initRecordOut = true;
            }
            try {
                int length = byteBuffer.remaining();
                byte[] buffer = new byte[length];
                byteBuffer.get(buffer);
                byteBuffer.flip();
                recordOutput.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
            boolean isWriteBackAudio = false;
            if(isWriteBackAudio){
                int length = byteBuffer.remaining();
//                byteBuffer.flip();
//                byte[] buffer = readBuffer();
                byte[] origin = new byte[length];
                byteBuffer.get(origin);
                byteBuffer.flip();
//                byteBuffer.put(audioAggregate(origin, buffer), 0, byteBuffer.remaining());
                byteBuffer.put(origin, 0, byteBuffer.remaining());
                byteBuffer.flip();
            }
            return true;
        }


        @Override
        public boolean onPlaybackAudioFrame(String channel, int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, ByteBuffer byteBuffer, long renderTimeMs, int bufferLength) {
//            Log.i(TAG, "onPlaybackAudioFrame");
            if(!initPlayBack){
                try {
                    playBackOutput = new FileOutputStream(
                            Environment.getExternalStorageDirectory().getPath()
                                    + "/Android/data/io.agora.extension.histreamvc/files/playBackOutput.pcm");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                initPlayBack = true;
            }
            try {
                int length = byteBuffer.remaining();
                byte[] buffer = new byte[length];
                byteBuffer.get(buffer);
                byteBuffer.flip();
                playBackOutput.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        public boolean onMixedAudioFrame(String channel, int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, ByteBuffer byteBuffer, long renderTimeMs, int bufferLength) {
//            Log.i(TAG, "onMixedAudioFrame");

            if(!initMixedOut){
                try {
                    mixedOutput = new FileOutputStream(
                            Environment.getExternalStorageDirectory().getPath() +
                                    "/Android/data/io.agora.extension.histreamvc/files/mixedOut.pcm");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                initMixedOut = true;
            }
            try {
                int length = byteBuffer.remaining();
                byte[] buffer = new byte[length];
                byteBuffer.get(buffer);
                byteBuffer.flip();
                mixedOutput.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        public boolean onEarMonitoringAudioFrame(int type, int samplesPerChannel, int bytesPerSample, int channels, int samplesPerSec, ByteBuffer buffer, long renderTimeMs, int avsync_type) {
            return false;
        }

        @Override
        public boolean onPlaybackAudioFrameBeforeMixing(String channel, int uid, int audioFrameType, int samples, int bytesPerSample, int channels, int samplesPerSec, ByteBuffer byteBuffer, long renderTimeMs, int bufferLength) {
//            Log.i(TAG, "onPlaybackAudioFrameBeforeMixing");
            if(!initPreMixOut){
                try {
                    preMixOutput = new FileOutputStream(
                            Environment.getExternalStorageDirectory().getPath() +
                                    "/Android/data/io.agora.extension.histreamvc/files/preMixOut.pcm");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                initPreMixOut = true;
            }
            try {
                int length = byteBuffer.remaining();
                byte[] buffer = new byte[length];
                byteBuffer.get(buffer);
                byteBuffer.flip();
                preMixOutput.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        public int getObservedAudioFramePosition() {
            return POSITION_PLAYBACK | POSITION_RECORD |
                    POSITION_MIXED | POSITION_BEFORE_MIXING;
        }

        @Override
        public AudioParams getRecordAudioParams() {
            return new AudioParams(44100, 1, RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1024);
        }

        @Override
        public AudioParams getPlaybackAudioParams() {
            return new AudioParams(44100, 1, RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1024);
        }

        @Override
        public AudioParams getMixedAudioParams() {
            return new AudioParams(44100, 1, RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1024);
        }

        @Override
        public AudioParams getEarMonitoringAudioParams() {
            return new AudioParams(44100, 1, RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1024);
        }

    };

    private final IRtcEngineEventHandler iRtcEngineEventHandler = new IRtcEngineEventHandler() {

        @Override
        public void onError(int err) {
            Log.w(TAG, String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err)));
        }

        @Override
        public void onLeaveChannel(RtcStats stats) {
            super.onLeaveChannel(stats);
            Log.i(TAG, String.format("local user %d leaveChannel!", myUid));
            if(mRtcEngine == null){
                Log.e(TAG, "engine == null");
            }
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.i(TAG, String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            myUid = uid;
            joined = true;
            handler.post(() -> {
                btn_join.setEnabled(true);
                btn_join.setText(getString(R.string.leave));
            });
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            super.onUserJoined(uid, elapsed);
            Log.i(TAG, "onUserJoined->" + uid);
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            Log.i(TAG, String.format("user %d offline! reason:%d", uid, reason));
        }

        @Override
        public void onActiveSpeaker(int uid) {
            super.onActiveSpeaker(uid);
            Log.i(TAG, String.format("onActiveSpeaker:%d", uid));
        }
    };

    private void enableExtension(boolean enabled) {
        mRtcEngine.enableExtension(ExtensionManager.EXTENSION_VENDOR_NAME,
                ExtensionManager.EXTENSION_AUDIO_FILTER, enabled);
    }

    private void joinChannel(String channelId) {
        mRtcEngine.setClientRole(CLIENT_ROLE_BROADCASTER);
        TokenUtils.gen(getApplicationContext(), channelId, 0, accessToken -> {
            ChannelMediaOptions option = new ChannelMediaOptions();
            option.autoSubscribeAudio = true;
            Log.i(TAG, "accessToken: " + accessToken);
            int res = mRtcEngine.joinChannel(accessToken, channelId, 0, option);
            if (res != 0) {
                Toast.makeText(getApplicationContext(), RtcEngine.getErrorDescription(Math.abs(res)),
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, RtcEngine.getErrorDescription(Math.abs(res)));
                return;
            }
            btn_join.setEnabled(false);
        });
    }

    @Override
    public void onEvent(String provider, String extension, String key, String value) {
        Log.i(TAG, "onEvent vendor: " + provider + "  extension: " + extension + "  key: " + key + "  value: " + value);
        switch (key) {
            case "InitOk":
                handler.post(() -> {
                    findViewById(R.id.button_start_vc).setEnabled(true);
                    findViewById(R.id.button_stop_vc).setEnabled(false);
                    findViewById(R.id.init_extension).setEnabled(false);
                });
                break;
            case "InitError":
                Log.e(TAG, "stream_vc init failed, key: " + key + ", value: " + value);
                break;
            case "StopOk":
            case "StopError":
            case "StartError":
                handler.post(() -> {
                    findViewById(R.id.button_start_vc).setEnabled(false);
                    findViewById(R.id.button_stop_vc).setEnabled(false);
                    findViewById(R.id.init_extension).setEnabled(true);
                });
                break;
            case "StartOk":
                handler.post(() -> {
                    findViewById(R.id.button_start_vc).setEnabled(false);
                    findViewById(R.id.button_stop_vc).setEnabled(true);
                    findViewById(R.id.init_extension).setEnabled(false);
                });
                break;
        }
    }

    @Override
    public void onStarted(String provider, String extension) {

    }

    @Override
    public void onStopped(String provider, String extension) {

    }

    @Override
    public void onError(String provider, String extension, int error, String message) {

    }
}