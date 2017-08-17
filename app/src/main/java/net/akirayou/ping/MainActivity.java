package net.akirayou.ping;

import android.Manifest;
import android.content.DialogInterface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoPointCloudManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Math.min;

public class MainActivity extends AppCompatActivity {
    private final String TAG="MainActivity";




    //Camera request for Tango preview
    private void requestCameraPermission() {
        final int REQUEST_CODE_CAMERA_PERMISSION = 0x01;
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            new AlertDialog.Builder(this)
                    .setTitle("パーミッションの追加説明")
                    .setMessage("このアプリで写真を撮るにはパーミッションが必要です")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CODE_CAMERA_PERMISSION);
                        }
                    })
                    .create()
                    .show();
            return;
        }
        ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA
                },
                REQUEST_CODE_CAMERA_PERMISSION);
        return;
    }




    private boolean tangoEnabled=false;
    private Tango mTango=null;
    private TangoConfig mConfig=null;
    TangoPointCloudManager tpcm=new TangoPointCloudManager();

    private TangoConfig setupTangoConfig(Tango tango) {
        // Create a new Tango Configuration and enable the HelloMotionTrackingActivity API.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE,false);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA,true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH,true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        config.putInt(TangoConfig.KEY_INT_RUNTIME_DEPTH_FRAMERATE,5);

        // Tango Service should automatically attempt to recover when it enters an invalid state.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);
        return config;
    }
    private void startTango(){
        Log.i(TAG,"===============START TANGO==============");
        if(tangoEnabled){
            Log.e(TAG,"ignore double resume for tango");
            return; //Ha?
        }

        Log.i(TAG,"tangoStart on resume");
        mTango = new Tango(MainActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready; this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there are no
            // UI thread changes involved.
            @Override
            public void run() {
                synchronized (MainActivity.this) {
                    try {
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        attachTango();
                        //showsToastAndFinishOnUiThread("Tango Enabled");
                        tangoEnabled=true;
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, "out of data", e);
                        showsToastAndFinishOnUiThread("out of date");
                    } catch (TangoErrorException e) {
                        Log.e(TAG, "tango error", e);
                        showsToastAndFinishOnUiThread("tango error");
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, "tango invalid", e);
                        showsToastAndFinishOnUiThread("tango invalid");
                    }
                }
            }
        });


    }
    private void stopTango(){
        Log.i(TAG,"===============STOP TANGO==============");
        synchronized (MainActivity.this) {
            try {
                if(tangoEnabled) {
                    mTango.disconnect();
                    tangoEnabled = false;
                }else{

                    Log.e(TAG,"tango double disable");
                }
            } catch (TangoErrorException e) {
                Log.e(TAG, "tango error", e);
            }
        }
    }
    private void attachTango() {
        // Lock configuration and connect to Tango.
        // Select coordinate frame pair.
        final ArrayList<TangoCoordinateFramePair> framePairs =
                new ArrayList<>();

        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        // Listen for new Tango data.
        mTango.connectListener(framePairs, new Tango.TangoUpdateCallback() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {

            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                Log.i("TANGO","================-point cloud");
                tpcm.updatePointCloud(pointCloud);
            }
            @Override
            public void onTangoEvent(final TangoEvent event) {
            }
            @Override
            public void onFrameAvailable(int cameraId) {
            }
        });
    }
    private Timer timer ;
    private AudioTrack player ;
    private final int chunkLen=44100/3;
    private byte[][] wav=new byte[5][];
    private MonitorView monitorView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        InputStream input;
        int[] rr={R.raw.a,R.raw.i,R.raw.u,R.raw.e,R.raw.o};
        for(int i=0;i<5;i++) {
            try {
                input = getResources().openRawResource(rr[i]);
                wav[i] = new byte[(int) input.available()];
                input.read(wav[i]);
                input.close();
                for(int j=0;j<wav[i].length;j++)wav[i][j]+=128;
            }
            catch (IOException e){
                Log.e(TAG,"Io exception onCreate");
            }
        }



        player = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(chunkLen*2)
                .build();
        /*
        if (PermissionChecker.checkSelfPermission(
                MainActivity.this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        }*/
        player.play();

        monitorView = (MonitorView) this.findViewById(R.id.monitor_view);
    }


    private float lenToHz(float len){
        final float maxHz=500;
        final float minHz=60;
        final float maxLen=2.0f;
        final float minLen=0.2f;
        if(len<minLen)return maxHz;
        if(maxLen<len)return minHz;
        float  hz=(float)Math.exp(Math.log(maxHz/minHz)*(maxLen-len)/(maxLen-minLen))*minHz;
        return hz;
    }
    final int nofLen=4;
    private float[] getLen(){
        final float hmin=-0.51f;
        final float hmax=0.51f;
        TangoPointCloudData pc = tpcm.getLatestPointCloud();

        if (pc == null) return null; //no data yet

        int dataLen = pc.numPoints;
        float d[] = new float[4];
        float minLen[] = new float[nofLen];
        for(int i=0;i<nofLen;i++)minLen[i]=9999;

        pc.points.rewind();
        for (int i = 0; i < dataLen; i++) {
            pc.points.get(d);
            if (d[3] < 0.5) continue; // not good data
            float vang = (float) Math.atan2(d[1], d[2]);
            if(Math.abs(vang)> 30.0/180*Math.PI)continue;
            float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            float hang = (float) Math.atan2(d[0], d[2]);

            int id=(int)Math.floor(nofLen*(hang-hmin)/(hmax-hmin));
            if(id<0)id=0;
            if(nofLen<=id)id=nofLen-1;
            minLen[id]=min(len,minLen[id]);
        }
        return minLen;
    }
    private byte[] audioBuff =new byte[chunkLen];
    @Override
    protected void onStart(){
        super.onStart();
        startTango();
        timer= new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                final int noteLen=7*chunkLen/8;

                //Log.i("TIMERLOOP","kick");
                final float[] len=getLen();
                if(len==null)return;
                int frameLen = player.getBufferSizeInFrames();
                if (frameLen < chunkLen) return;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        monitorView.data=len;
                        monitorView.invalidate();
                    }
                });

                float[] hz= new float[nofLen];
                for(int i=0;i<nofLen;i++)hz[i]=lenToHz(len[i]);
/*                Log.i("TIMER LOOP",
                        String.valueOf(len[0]) +" "+String.valueOf(len[1]) +" "+String.valueOf(len[2]) +
                                String.valueOf(len[3]) +" "+String.valueOf(len[4]) +" "+String.valueOf(len[5]) +
                                String.valueOf(len[6]) +" "+String.valueOf(len[7])
                );*/

                float phase=0;
                int oldId=-1;
                float pos=0;
                for(int i=0;i<noteLen;i++) {
                    int id=nofLen*i/noteLen;
                    if(oldId!=id) {//newData
                        pos=0;
                        oldId=id;
                    }
                    /*
                    audioBuff[i]=(byte)((phase<0.5)?-50:50);
                    phase+=1.0f/44100*hz[id];
                    phase-=Math.floor(phase);
                    */
                    audioBuff[i]=wav[id][(int)pos];
                    pos+=hz[id]/100.0f;
                    if(wav[id].length<=pos)pos=0;
                }
                for(int i=noteLen;i<chunkLen;i++) audioBuff[i]=0;

                player.write(audioBuff,0,chunkLen);

            }
        },500,100);
    }

    @Override
    protected void onStop() {
        timer.cancel();
        timer.purge();
        timer=null;
        stopTango();
        super.onStop();
    }
    private void showsToastAndFinishOnUiThread(final String res) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,res, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
