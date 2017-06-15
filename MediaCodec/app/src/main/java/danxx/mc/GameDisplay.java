package danxx.mc;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


/**
 * SurfaceTexture是从Android3.0（API 11）加入的一个新类。这个类跟SurfaceView很像，
 * 可以从camera preview或者video decode里面获取图像流（image stream）。
 * 但是，和SurfaceView不同的是，SurfaceTexture在接收图像流之后，不需要显示出来。
 * 有做过Android camera开发的人都知道，比较头疼的一个问题就是，
 * 从camera读取到的预览（preview）图像流一定要输出到一个可见的（Visible）SurfaceView上，
 * 然后通过Camera.PreviewCallback的
 * public void onPreviewFrame(byte[] data, Camera camera)函数来获得图像帧数据的拷贝。
 * 这就存在一个问题，比如我希望隐藏摄像头的预览图像或者对每一帧进行一些处理再显示到手机显示屏上，
 * 那么在Android3.0之前是没有办法做到的，或者说你需要用一些小技巧，比如用其他控件把SurfaceView给挡住，
 * 注意这个显示原始camera图像流的SurfaceView其实是依然存在的，也就是说被挡住的SurfaceView依然在接收从camera传过来的图像，
 * 而且一直按照一定帧率去刷新，这是消耗cpu的，而且如果一些参数设置的不恰当，后面隐藏的SurfaceView有可能会露出来，因此这些小技巧并不是好办法。
 * 但是，有了SurfaceTexture之后，就好办多了，因为SurfaceTexture不需要显示到屏幕上，
 * 因此我们可以用SurfaceTexture接收来自camera的图像流，然后从SurfaceTexture中取得图像帧的拷贝进行处理，
 * 处理完毕后再送给另一个SurfaceView用于显示即可。
 * @author jiangwei1-g
 *
 */

@SuppressWarnings("deprecation")
public class GameDisplay extends SurfaceView implements SurfaceHolder.Callback,Camera.PreviewCallback{

    public final static String TAG="GameDisplay";

    private static final int MAGIC_TEXTURE_ID = 10;
    public static final int DEFAULT_WIDTH=800;
    public static final int DEFAULT_HEIGHT=480;
    public static final int BLUR = 0;
    public static final int CLEAR = BLUR + 1;
    //public static final int PAUSE = PLAY + 1;
    //public static final int EXIT = PAUSE + 1;
    public SurfaceHolder gHolder;
    public  SurfaceTexture gSurfaceTexture;
    public Camera gCamera;
    public byte gBuffer[];
    public int textureBuffer[];
    public Thread gProcessThread;
    private int bufferSize;
    private Camera.Parameters parameters;
    public int previewWidth, previewHeight;
    public int screenWidth, screenHeight;
    public Bitmap gBitmap;
    private Rect gRect;
    // timer
    private Timer sampleTimer;
    private TimerTask sampleTask;

    @SuppressLint("NewApi")
    public GameDisplay(Context context,int screenWidth,int screenHeight) {
        super(context);
        gHolder=this.getHolder();
        gHolder.addCallback(this);
        gHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        gSurfaceTexture=new SurfaceTexture(MAGIC_TEXTURE_ID);
        this.screenWidth=screenWidth;
        this.screenHeight=screenHeight;
        gRect=new Rect(0,0,screenWidth,screenHeight);
        Log.v(TAG, "GameDisplay initialization completed");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.v(TAG, "GameDisplay surfaceChanged");
        parameters = gCamera.getParameters();
        List<Size> preSize = parameters.getSupportedPreviewSizes();
        previewWidth = preSize.get(0).width;
        previewHeight = preSize.get(0).height;
        for (int i = 1; i < preSize.size(); i++) {
            double similarity = Math
                    .abs(((double) preSize.get(i).height / screenHeight)
                            - ((double) preSize.get(i).width / screenWidth));
            if (similarity < Math.abs(((double) previewHeight / screenHeight)
                    - ((double) previewWidth / screenWidth))) {
                previewWidth = preSize.get(i).width;
                previewHeight = preSize.get(i).height;
            }
        }
        gBitmap= Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        parameters.setPreviewSize(previewWidth, previewHeight);
        gCamera.setParameters(parameters);
        bufferSize = previewWidth * previewHeight;
        textureBuffer=new int[bufferSize];
        bufferSize  = bufferSize * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
        gBuffer = new byte[bufferSize];
        gCamera.addCallbackBuffer(gBuffer);
        gCamera.setPreviewCallbackWithBuffer(this);
        gCamera.startPreview();
        //gProcessThread = new ProcessThread(surfaceView,handler,null,previewWidth,previewHeight);
        //processThread.start();
    }

    @SuppressLint("NewApi")
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "GameDisplay surfaceCreated");
        if (gCamera == null) {
            gCamera = Camera.open();
        }
        try {
            gCamera.setPreviewTexture(gSurfaceTexture);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //sampleStart();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "GameDisplay surfaceDestroyed");
        //gProcessThread.isRunning=false;
        //sampleTimer.cancel();
        //sampleTimer = null;
        //sampleTask.cancel();
        //sampleTask = null;
        gCamera.stopPreview();
        gCamera.release();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Log.v(TAG, "GameDisplay onPreviewFrame");
        //gProcessThread.raw_data=data;    
        camera.addCallbackBuffer(gBuffer);
        for(int i=0;i<textureBuffer.length;i++)
            textureBuffer[i]=0xff000000|data[i];
        gBitmap.setPixels(textureBuffer, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        synchronized (gHolder)
        {
            Canvas canvas = this.getHolder().lockCanvas();
            canvas.drawBitmap(gBitmap, null,gRect, null);
            //canvas.drawBitmap(textureBuffer, 0, screenWidth, 0, 0, screenWidth, screenHeight, false, null);
            this.getHolder().unlockCanvasAndPost(canvas);
        }

    }

    public void sampleStart() {
        Log.v(TAG, "GameDisplay sampleStart");
        sampleTimer = new Timer(false);
        sampleTask = new TimerTask() {
            @Override
            public void run() {
                //gProcessThread.timer=true;
            }
        };
        sampleTimer.schedule(sampleTask,0, 80);
    }
}
