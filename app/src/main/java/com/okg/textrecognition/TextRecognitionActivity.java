package com.okg.textrecognition;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import org.json.JSONObject;

public class TextRecognitionActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "Mlkit-TextRecognitionActivity";
    private static int CAMERA_PERMISSION_CODE = 100;

    private FrameLayout textureViewContainer;
    private TextView tvContent;

    private ViewFinderView frameView;
    private ImageView ivCrop, ivBack, ivTakePicture, ivInput, ivTorch;

    private CameraHelper mCameraHelper;
    /**
     * 记录是否打开了闪光灯
     */
    private boolean isTorchOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置全屏
        CommonUtil.setFullScreen(this);

        setContentView(R.layout.activity_text_recognition);
        // 隐藏导航键
        CommonUtil.hideNavigationBar(this);

        initView();
        requestPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraHelper != null) {
            mCameraHelper.releaseCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera();
            } else {
                CommonUtil.showToast(this, "相机权限被拒绝");
            }
        }
    }

    private void initView() {
        tvContent = findViewById(R.id.tv_content);
        ivTakePicture = findViewById(R.id.iv_take_picture);
        ivBack = findViewById(R.id.iv_back);
        ivInput = findViewById(R.id.iv_input);
        ivTorch = findViewById(R.id.iv_torch);
        frameView = findViewById(R.id.view_frame);
        ivCrop = findViewById(R.id.iv_crop);
        textureViewContainer = findViewById(R.id.container);
        ivBack.setOnClickListener(this);
        ivInput.setOnClickListener(this);
        ivTorch.setOnClickListener(this);
        ivTakePicture.setOnClickListener(this);
    }

    /**
     * 申请相机权限
     */
    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            setupCamera();
        }
    }

    /**
     * 设置相机
     */
    private void setupCamera() {
        mCameraHelper = new CameraHelper(this);
        mCameraHelper.setUpWithTextureView(createTextureView());
    }

    private TextureView createTextureView() {
        TextureView textureView = new TextureView(this);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        textureView.setLayoutParams(lp);
        textureViewContainer.addView(textureView, 0, lp);
        return textureView;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_back:
                finish();
                break;
            case R.id.iv_input:
                showImeiInputDialog();
                break;
            case R.id.iv_torch:
                if (mCameraHelper == null) {
                    return;
                }
                isTorchOpen = !isTorchOpen;
                if (isTorchOpen) {
                    mCameraHelper.openTorch();
                } else {
                    mCameraHelper.closeTorch();
                }
                break;
            case R.id.iv_take_picture:
                if (mCameraHelper != null) {
                    mCameraHelper.takePicture(new CameraHelper.OnTakePictureListener() {
                        @Override
                        public void onTakePicture(Bitmap bitmap, int pictureRotationDegrees, int deviceRotationDegrees) {
                            CommonUtil.log(TAG, "onTakePicture , bitmap = " + bitmap + ", pictureRotationDegrees = " + pictureRotationDegrees + ",deviceRotationDegrees=" + deviceRotationDegrees);
                            // 如果打开了手电筒，则拍完照后立即关闭手电筒
                            if (isTorchOpen && mCameraHelper != null) {
                                mCameraHelper.closeTorch();
                                isTorchOpen = false;
                            }
                            analyzeImage(bitmap, pictureRotationDegrees);
                        }
                    });
                }
                break;
            default:
                break;
        }
    }

    /**
     * 解析文本
     */
    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeImage(Bitmap bitmap, int rotationDegrees) {
        if (bitmap == null) {
            CommonUtil.log(TAG, "analyzeImage , bitmap is null !!!!!!!");
            return;
        }
        Bitmap resultBitmap = handleBitmap(bitmap, rotationDegrees);
        InputImage inputImage = InputImage.fromBitmap(resultBitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(inputImage).addOnSuccessListener(new OnSuccessListener<Text>() {
            @Override
            public void onSuccess(Text result) {
                ivCrop.setImageBitmap(resultBitmap);
                int blockCount = result.getTextBlocks().size();
                if (blockCount == 0 || TextUtils.isEmpty(result.getText())) {
                    CommonUtil.showToast(TextRecognitionActivity.this, "识别不出内容，请对准拍摄");
                    return;
                }
                JSONObject jsonObject = OCRHelper.getInstance().parseImeiAndSnInfo(result);
                tvContent.setText(jsonObject.toString());
                String imei1 = jsonObject.optString(OCRHelper.KEY_IMEI1);
                String imei2 = jsonObject.optString(OCRHelper.KEY_IMEI2);
                String sn = jsonObject.optString(OCRHelper.KEY_SN);
                if (TextUtils.isEmpty(imei1) && TextUtils.isEmpty(imei2) && TextUtils.isEmpty(sn)) {
                    CommonUtil.showToast(TextRecognitionActivity.this, "没有找到imei/sn信息，请调整拍照角度或范围继续拍摄");
                }
            }
        }).addOnCompleteListener(new OnCompleteListener<Text>() {
            @Override
            public void onComplete(@NonNull Task<Text> task) {
                bitmap.recycle();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // 处理识别过程中的错误
                e.printStackTrace();
                bitmap.recycle();
            }
        });
    }

    /**
     * 对bitmap进行旋转、缩放及裁剪处理
     *
     * @param sourceBitmap
     * @param rotationDegrees
     * @return
     */
    private Bitmap handleBitmap(Bitmap sourceBitmap, int rotationDegrees) {
        if (sourceBitmap == null || sourceBitmap.getWidth() <= 0 || sourceBitmap.getWidth() <= 0) {
            return sourceBitmap;
        }
        // step1 对bitmap进行旋转
        Bitmap rotateBitmap = CommonUtil.rotateBitmap(sourceBitmap, rotationDegrees);
        // step2 对bitmap按屏幕宽度进行等比例缩放
        float scaleRatio = CommonUtil.getRealScreenWidth(this) / rotateBitmap.getWidth();
        Bitmap scaleBitmap = CommonUtil.scaleBitmap(rotateBitmap, scaleRatio);
        // step3 对bitmap进行裁剪，需要重新调整裁剪的矩形框
        int bitmapWidth = scaleBitmap.getWidth();
        int bitmapHeight = scaleBitmap.getHeight();
        float rectScaleHeight = frameView.getFrameHeight() * scaleRatio;
        float cropRectLeft = frameView.getFrameLeft();
        float cropRectTop = bitmapHeight / 2 - rectScaleHeight / 2;
        float cropRectRight = bitmapWidth - frameView.getFrameMarginRight();
        float cropRectBottom = bitmapHeight / 2 + rectScaleHeight / 2;
        Rect cropRect = new Rect((int) cropRectLeft, (int) cropRectTop, (int) cropRectRight, (int) cropRectBottom);
        return CommonUtil.cropBitmap(scaleBitmap, cropRect);
    }

    /**
     * 显示imei输入框
     */
    private void showImeiInputDialog() {
        Dialog dialog = new Dialog(this, R.style.CommonDialog);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.dialog_input);
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            WindowManager.LayoutParams params = dialogWindow.getAttributes();
            params.width = CommonUtil.getRealScreenWidth(this);
            dialogWindow.setGravity(Gravity.BOTTOM);
            dialogWindow.setAttributes(params);
        }
        View ivClose = dialog.findViewById(R.id.iv_close);
        View btnComplete = dialog.findViewById(R.id.btn_complete);
        EditText etImei = dialog.findViewById(R.id.et_imei);
        ivClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CommonUtil.dismissDialog(TextRecognitionActivity.this, dialog);
            }
        });
        btnComplete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String strInputImei = etImei.getText().toString();
                if (TextUtils.isEmpty(strInputImei)) {
                    CommonUtil.showToast(TextRecognitionActivity.this, "输入IMEI不能为空");
                    return;
                }
                CommonUtil.dismissDialog(TextRecognitionActivity.this, dialog);
                // 数据回调
            }
        });
        CommonUtil.showDialog(this, dialog);
    }
}