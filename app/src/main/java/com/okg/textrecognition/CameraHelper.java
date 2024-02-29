package com.okg.textrecognition;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author oukanggui
 * @date 2020-06-19
 * 描述：相机操作辅助类
 */
public class CameraHelper {
    private static final String TAG = "Mlkit-CameraHelper";
    private Activity mActivity;
    private TextureView mTextureView;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mCaptureRequest;
    private CameraDevice.StateCallback mCameraDeviceStateCallback;
    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback;
    private CameraCaptureSession.CaptureCallback mCameraCaptureSessionCaptureCallback;
    private CameraCaptureSession mCameraCaptureSession;
    private String mCurrentCameraId;
    /**
     * 图片保存的Size
     */
    private Size mCurrentSelectSize;
    /**
     * 预览的Size
     */
    private Size mPreviewSize;
    private Handler mCameraBackgroundHandler;
    private Surface mSurface;

    private OnTakePictureListener mOnTakePictureListener;

    private int mAngle = 0;
    private int mRotationDegrees = 0;

    /**
     * 拍照回调监听器
     */
    public interface OnTakePictureListener {
        /**
         * 拍照完成回调,回调于子线程
         *
         * @param bitmap 图片保存的图片文件
         */
        void onTakePicture(Bitmap bitmap, int angle, int rotationDegrees);
    }

    public CameraHelper(Activity activity) {
        mActivity = activity;
    }

    public void setUpWithTextureView(TextureView textureView) {
        mTextureView = textureView;
        init();
    }

    private void init() {
        initCameraBackgroundThread();
        initCameraManager();
        initSelectCamera();
        // 根据选中的相机，初始化图片显示的Size
        initHandlerMatchingSize();
        //initImageReader();
        // 初始化各种监听器
        initTextureViewListener();
        initCameraDeviceStateCallbackListener();
        initCameraCaptureSessionStateCallbackListener();
        initCameraCaptureSessionCaptureCallbackListener();
    }

    /**
     * 初始化相机后台线程Handler，相机初始化、图片保存耗时，需要绑定子Handler在后台线程执行
     */
    private void initCameraBackgroundThread() {
        HandlerThread handlerThread = new HandlerThread("faceCamera");
        handlerThread.start();
        mCameraBackgroundHandler = new Handler(handlerThread.getLooper());
    }


    /**
     * 初始化相机管理
     */
    private void initCameraManager() {
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
    }

    /**
     * 初始化选择摄像头
     * 选择后置摄像头
     */
    private void initSelectCamera() {
        try {
            String[] cameraIdArray = mCameraManager.getCameraIdList();
            if (cameraIdArray == null || cameraIdArray.length == 0) {
                Toast.makeText(mActivity, "没有可用相机", Toast.LENGTH_SHORT).show();
                return;
            }
            for (String itemId : cameraIdArray) {
                CameraCharacteristics itemCharacteristics = mCameraManager.getCameraCharacteristics(itemId);
                Integer facing = itemCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
//                    StreamConfigurationMap map = itemCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//                    //根据TextureView的尺寸设置预览尺寸
//                   mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                    mCurrentCameraId = itemId;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (mCurrentCameraId == null) {
            Toast.makeText(mActivity, "此设备不支持前摄像头", Toast.LENGTH_SHORT).show();
            mActivity.finish();
        }

    }

    // 选择sizeMap中大于并且最接近width和height的size
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }

    /**
     * 初始化计算适合当前屏幕分辨率的拍照分辨率
     *
     * @return
     */
    private void initHandlerMatchingSize() {
        try {
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCurrentCameraId);
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            DisplayMetrics displayMetrics = mActivity.getResources().getDisplayMetrics();
            int deviceWidth = displayMetrics.widthPixels;
            int deviceHeight = displayMetrics.heightPixels;
            Log.d(TAG, "当前屏幕密度宽度=" + deviceWidth + "，高度=" + deviceHeight);
            for (int j = 1; j < 81; j++) {
                for (int i = 0; i < sizes.length; i++) {
                    Size itemSize = sizes[i];
                    if (itemSize.getHeight() < (deviceWidth + j * 5) && itemSize.getHeight() > (deviceWidth - j * 5)) {
                        if (mCurrentSelectSize != null) { //如果之前已经找到一个匹配的宽度
                            if (Math.abs(deviceHeight - itemSize.getWidth()) < Math.abs(deviceHeight - mCurrentSelectSize.getWidth())) { //求绝对值算出最接近设备高度的尺寸
                                mCurrentSelectSize = itemSize;
                                continue;
                            }
                        } else {
                            mCurrentSelectSize = itemSize;
                        }

                    }
                }

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "当前预览宽度=" + mCurrentSelectSize.getWidth() + "，高度=" + mCurrentSelectSize.getHeight());
    }

    /**
     * 初始化图片Reader，用于保存图片,在后台线程进行图片处理
     */
    private void initImageReader() {
        Log.d(TAG, "初始化图片ImageReader的宽=" + mCurrentSelectSize.getWidth() + "高=" + mCurrentSelectSize.getHeight());
//        mImageReader = ImageReader.newInstance(mCurrentSelectSize.getWidth()
//                , mCurrentSelectSize.getHeight()
//                , ImageFormat.JPEG
//                , 2);
        Log.d(TAG, "initImageReader, " + mPreviewSize.toString());
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth()
                , mPreviewSize.getHeight()
                , ImageFormat.JPEG
                , 2);

        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "onImageAvailable,保存图片，thread=" + Thread.currentThread().getName());
                Image image = reader.acquireLatestImage();
                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                // 不管成功与否，保证均回调
                if (mOnTakePictureListener != null) {
                    mOnTakePictureListener.onTakePicture(bitmap, mAngle, mRotationDegrees);
                }
                if (image != null) {
                    image.close();
                }
                startPreview();
            }
        }, mCameraBackgroundHandler);
    }

    private void initTextureViewListener() {
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                //当SurfaceTexture可用的时候，设置相机参数并打开相机
                Log.d(TAG, "onSurfaceTextureAvailable, w= " + width + ",h=" + height);
                openCamera();
                mPreviewSize = new Size(width, height);
                initImageReader();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    private void initCameraDeviceStateCallbackListener() {
        mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Log.d(TAG, "相机开启");
                //相机开启
                mCameraDevice = camera;
                try {
                    SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
                    // TODO 是指预览图大小，设置不好会导致预览出现拉伸的情况
                    Log.d(TAG, "onOpened:" + mPreviewSize.toString());
                    surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    mSurface = new Surface(surfaceTexture);
                    // 创建预览请求
                    mCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    mCaptureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    mCaptureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);//自动爆光
                    mCaptureRequest.addTarget(mSurface);
                    mCameraDevice.createCaptureSession(Arrays.asList(mSurface, mImageReader.getSurface())
                            , mCameraCaptureSessionStateCallback
                            , mCameraBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {

            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                // TODO 需要检测这里
                //mActivity.finish();
                //Toast.makeText(mActivity, "相机打开失败", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "CameraDevice.StateCallback onError : 相机异常 error code=" + error);
                releaseCamera();
            }
        };
    }

    private void initCameraCaptureSessionStateCallbackListener() {
        mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                mCameraCaptureSession = session;
                startPreview();
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                mActivity.finish();
                Toast.makeText(mActivity, "相机打开失败", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "CameraCaptureSession.StateCallback onConfigureFailed : CameraCaptureSession会话通道创建失败");
            }
        };
    }

    private void initCameraCaptureSessionCaptureCallbackListener() {
        mCameraCaptureSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
                //获取开始
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                super.onCaptureProgressed(session, request, partialResult);
                //获取中
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                //获取结束
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                //获取失败
                //Toast.makeText(mActivity, "拍照失败", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "失败报告Reason=" + failure.getReason());
            }
        };
    }

    /**
     * 开启相机，相机开启属于耗时操作，需要放在子线程Handler中处理
     */
    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            if (mCameraManager == null) {
                initCameraManager();
            }
            //打开相机
            // 第一个参数指示打开哪个摄像头，第二个参数stateCallback为相机的状态回调接口，第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
            mCameraManager.openCamera(mCurrentCameraId, mCameraDeviceStateCallback, mCameraBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 开始预览
     */
    private void startPreview() {
        try {
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequest.build(), mCameraCaptureSessionCaptureCallback, mCameraBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止预览
     */
    private void stopPreview() {
        try {
            mCameraCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * 拍照
     *
     * @param onTakePictureListener 拍照成功后回调
     */
    public void takePicture(OnTakePictureListener onTakePictureListener) {
        mOnTakePictureListener = onTakePictureListener;
        try {
            CaptureRequest.Builder takePictureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            takePictureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);//自动对焦
            //takePictureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);//自动爆光
            mRotationDegrees = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            mAngle = getJpegOrientation(mCameraManager.getCameraCharacteristics(mCurrentCameraId), mRotationDegrees);
            Log.d(TAG, "人脸拍照 照片角度angle=" + mAngle);
            Log.d(TAG, "人脸拍照 rotation=" + mRotationDegrees);
            takePictureRequest.set(CaptureRequest.JPEG_ORIENTATION, mAngle);
            Surface surface = mImageReader.getSurface();
            takePictureRequest.addTarget(surface);
            CaptureRequest request = takePictureRequest.build();
            stopPreview();
            mCameraCaptureSession.capture(request, null, mCameraBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * 官方提供的JPEG图片方向算法
     *
     * @param c
     * @param deviceOrientation
     * @return
     */
    private int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 0;
        }
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);//获取传感器方向

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // Reverse device orientation for front-facing cameras
        boolean facingFront = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;//判断摄像头面向
        if (facingFront) {
            deviceOrientation = -deviceOrientation;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }

    /**
     * 释放相机资源
     */
    public void releaseCamera() {
        Log.d(TAG, "releaseCamera========");
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mCameraCaptureSession != null) {
//            stopPreview();
//            try {
//                mCameraCaptureSession.abortCaptures();
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }
            try {
                mCameraCaptureSession.close();
            } catch (Exception e) {

            }
            mCameraCaptureSession = null;
        }
        if (mCaptureRequest != null) {
            mCaptureRequest.removeTarget(mSurface);//注意释放mSurface
            mCaptureRequest = null;
        }
        if (mSurface != null) {
            mSurface.release();//注意释放mSurface
            mSurface = null;
        }
        //也可以用onSurfaceTextureDestroyed这种方式释放SurfaceTexture 但是在上面的public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) 回调中你需要返回true或者自己执行 surface.release(); 这步资源释放很重要
        mTextureView.getSurfaceTextureListener().onSurfaceTextureDestroyed(mTextureView.getSurfaceTexture());
        mCameraDeviceStateCallback = null;
        mCameraCaptureSessionStateCallback = null;
        mCameraCaptureSessionCaptureCallback = null;
        mCameraManager = null;
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mCameraBackgroundHandler != null) {
            mCameraBackgroundHandler.removeCallbacksAndMessages(null);
            mCameraBackgroundHandler = null;
        }
    }
}
