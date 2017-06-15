package danxx.mc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;


@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SurfaceHolder.Callback, PreviewCallback {

	AvcEncoder avcCodec;
	public Camera m_camera;
	SurfaceView   m_prevewview;
	SurfaceHolder m_surfaceHolder;
	int width = 720;
	int height = 480;
	int framerate = 20;
	int bitrate = 2500000;

	private SurfaceView mTimestampSurfaceView;
	private SurfaceHolder mTimestampSurfaceHolder;

	private ImageView imgV;

	byte[] h264 = new byte[width*height*3/2];

	private int cameraId = 0;

	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		avcCodec = new AvcEncoder(width,height,framerate,bitrate);

		m_prevewview = (SurfaceView) findViewById(R.id.surface_view);
		m_surfaceHolder = m_prevewview.getHolder(); // 绑定SurfaceView，取得SurfaceHolder对象
		m_surfaceHolder.setFixedSize(width, height); // 预览大小設置
		m_surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		m_surfaceHolder.addCallback((Callback) this);

		mTimestampSurfaceView = (SurfaceView)findViewById(R.id.surface_view1);
		mTimestampSurfaceView.getHolder().addCallback(new TimestampSurfaceHolder());

		imgV = (ImageView)findViewById(R.id.img);

		imgV.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				/**
				 *  shutter：在按下快门的时候回调，这里可以播放一段声音。
				 raw：从Camera获取到未经处理的图像。
				 jpeg：从Camera获取到一个经过压缩的jpeg图片。

				 虽然raw、postview、jpeg都是Camera.PictureCallback回调，但是一般我们只需要获取jpeg，
				 其他传null即可，Camera.PictureCallback里需要实现一个方法onPictureTaken(byte[] data,Camera camera)，
				 data及为图像数据。值得注意的是，一般taskPicture()方法拍照完成之后，
				 SurfaceView都会停留在拍照的瞬间，需要重新调用startPreview()才会继续预览。
				 　　					如果直接使用taskPicture()进行拍照的话，Camera是不会进行自动对焦的，
				 这里需要使用Camera.autoFocus()方法进行对焦，它传递一个Camera.AutoFocusCallback参数，
				 用于自动对焦完成后回调，一般会在它对焦完成在进行taskPicture()拍照。
				 */

				m_camera.autoFocus(new AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success, Camera camera) {
						if(success){
							m_camera.takePicture(
									new ShutterCallback() {

										@Override
										public void onShutter() {
											//在这里播放按下拍照的声音啥的
										}
									}, RawDatamPicture, JpgmPicture);
						}
					}});
			}
		});

		findViewById(R.id.switch_camera).setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				CameraInfo cameraInfo = new CameraInfo();
				Log.i("jw", "number:"+Camera.getNumberOfCameras());
				for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
					Camera.getCameraInfo(i, cameraInfo);
					Log.i("jw", "oritation:"+cameraInfo.orientation+",facing:"+cameraInfo.facing);
					if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
					} else if(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK){
					}
				}

				cameraId = 1 - cameraId;
				destroyCamera();
				initCameara();
			}});

	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3)   {

	}

	@SuppressLint("NewApi")
	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		initCameara();
	}

	@SuppressLint("NewApi")
	private void initCameara(){
		try{
			m_camera = Camera.open(cameraId);
			m_camera.setPreviewDisplay(m_surfaceHolder);

			Camera.Parameters parameters = m_camera.getParameters();
			//设置预览尺寸
			parameters.setPreviewSize(width, height);
			//设置拍照图片尺寸
			parameters.setPictureSize(width, height);
			parameters.setPreviewFormat(ImageFormat.NV21);
			//设置预览方向
			//m_camera.setDisplayOrientation(90);
			//设置拍照之后图片方向
			//parameters.setRotation(90);

			//获取摄像头支持的数据格式
			List<Integer> list = parameters.getSupportedPreviewFormats();
			for(Integer val : list){
				Log.i("jw", "val:"+val);
			}

			// 选择合适的预览尺寸
			List<Camera.Size> sizeList = parameters.getSupportedPreviewSizes();
			// 如果sizeList只有一个我们也没有必要做什么了，因为就他一个别无选择
			if (sizeList.size() > 1) {
				Iterator<Camera.Size> itor = sizeList.iterator();
				while (itor.hasNext()) {
					Camera.Size cur = itor.next();
					Log.i("jw", "size==" + cur.width + " " + cur.height);
				}
			}

			m_camera.setParameters(parameters);
			m_camera.setPreviewCallback((PreviewCallback) this);
			m_camera.startPreview();

		} catch (Exception e){
			Log.i("jw", "camera error:"+Log.getStackTraceString(e));
		}
	}

	private void destroyCamera(){
		if(m_camera == null){
			return;
		}
		//！！这个必须在前，不然退出出错
		m_camera.setPreviewCallback(null);
		m_camera.stopPreview();
		m_camera.release();
		m_camera = null;

	}

	private PictureCallback JpgmPicture = new PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {

			File pictureFile = getOutputMediaFile();
			if (pictureFile == null){
				Log.d("jw", "Error creating media file, check storage permissions: ");
				return;
			}
			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				fos.write(data);
				fos.close();
			} catch (FileNotFoundException e) {
				Log.d("jw", "File not found: " + e.getMessage());
			} catch (IOException e) {
				Log.d("jw", "Error accessing file: " + e.getMessage());
			}

			//拍照之后就停留在原位置了
			m_camera.startPreview();

			String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "IMG.jpg";
			try {
				ExifInterface exifInterface = new ExifInterface(path);
				Log.i("jw", "rotation:"+exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION));
				Log.i("jw", "width:"+exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
						+ ",height:"+exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH));
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	};

	private PictureCallback RawDatamPicture = new PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.i("jw", "raw data:"+data);
		}
	};

	private File getOutputMediaFile() {
		File mediaStorageDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
		if (! mediaStorageDir.exists()){
			if (! mediaStorageDir.mkdirs()){
				Log.d("linc", "failed to create directory");
				return null;
			}
		}

		File mediaFile;
		mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG.jpg");
		return mediaFile;
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		destroyCamera();
		avcCodec.close();
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {

		Size size = camera.getParameters().getPreviewSize();
		Log.i("jw", "width:"+size.width+",height:"+size.height);
		try{

			/*//这张方式可以将NV21格式数据转化成Bitmap
        	int[] imgData = DataFormatUtils.NV21toARGB(data, size.width, size.height);
        	Bitmap bmp = Bitmap.createBitmap(imgData, size.width, size.height, Config.ARGB_8888);
        	imgV.setImageBitmap(BitmapUtils.addTimeToBitmap(bmp, System.currentTimeMillis()));*/

			//直接使用系统的YuvImage来进行转化图片，这里的支持ImageFormat.NV21和ImageFormat.YUY2,
			//但是YUY2的Camera是不支持的，所以这里会出现花屏现象
			YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
			Bitmap bmp = null;
			if(image!=null){
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);
				bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
				//图片旋转
				bmp = BitmapUtils.rotateBitmap(bmp, cameraId == 0 ? 90 : 270);
				//加水印
				bmp = BitmapUtils.addTimeToBitmap(bmp, System.currentTimeMillis());
				stream.close();
			}

			imgV.setImageBitmap(bmp);

			try{
				Canvas canvas = mTimestampSurfaceHolder.lockCanvas();
				canvas.drawBitmap(bmp, 0, 0, new Paint());
				mTimestampSurfaceHolder.unlockCanvasAndPost(canvas);
			}catch(Exception e){
				Log.i("jw", "canvas bitmap error:"+Log.getStackTraceString(e));
			}

		}catch(Exception ex){
			Log.e("Sys","Error:"+ex.getMessage());
		}
	}

	private class TimestampSurfaceHolder implements SurfaceHolder.Callback{
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			mTimestampSurfaceHolder = holder;
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
								   int height) {
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {

		}
	}

}
