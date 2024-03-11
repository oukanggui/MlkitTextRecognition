package com.okg.textrecognition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
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

import java.util.ArrayList;

public class TextRecognitionActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "Mlkit-TextRecognitionActivity";
    private static int CODE_CAMERA_PERMISSION = 100;
    private static int CODE_REQUEST_IMEI_SELECT_ACTIVITY = 101;

    private FrameLayout textureViewContainer;
    private TextView tvContent;

    private ViewFinderView frameView;
    private ImageView ivCrop, ivBack, ivTakePicture, ivInput, ivTorch;

    private CameraHelper mCameraHelper;
    /**
     * 记录是否打开了闪光灯
     */
    private boolean isTorchOpen = false;

    private ArrayList<String> mTextLineList;

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
        if (requestCode == CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera();
            } else {
                CommonUtil.showToast(this, "相机权限被拒绝");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CODE_REQUEST_IMEI_SELECT_ACTIVITY && resultCode == RESULT_OK && data != null) {
            String imei1 = data.getStringExtra(Constant.KEY_IMEI1);
            CommonUtil.log(TAG, "onActivityResult == :" + "imei1 = " + imei1);
            setResultAndFinish(imei1, null, null);
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
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CODE_CAMERA_PERMISSION);
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
                if (isTorchOpen) {
                    closeTorch();
                } else {
                    openTorch();
                }
                break;
            case R.id.iv_take_picture:
                if (mCameraHelper != null) {
                    mCameraHelper.takePicture(new CameraHelper.OnTakePictureListener() {
                        @Override
                        public void onTakePicture(Bitmap bitmap, int pictureRotationDegrees, int deviceRotationDegrees) {
                            CommonUtil.log(TAG, "onTakePicture , bitmap = " + bitmap + ", pictureRotationDegrees = " + pictureRotationDegrees + ",deviceRotationDegrees=" + deviceRotationDegrees);
                            // 如果打开了手电筒，则拍完照后立即关闭手电筒
                            if (isTorchOpen) {
                                closeTorch();
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
                if (!TextUtils.isEmpty(imei1) || !TextUtils.isEmpty(imei2) || !TextUtils.isEmpty(sn)) {
                    // 已找到imei/sn信息，则直接返回
                    setResultAndFinish(imei1, imei2, sn);
                    return;
                }
                CommonUtil.log(TAG, "没有识别找到imei/sn信息，则跳转编辑选择编辑界面");
                if (mTextLineList == null) {
                    mTextLineList = new ArrayList<>();
                }
                mTextLineList.clear();
                for (int i = 0; i < blockCount; i++) {
                    Text.TextBlock textBlock = result.getTextBlocks().get(i);
                    if (textBlock == null || textBlock.getLines() == null || textBlock.getLines().size() == 0) {
                        continue;
                    }
                    int lineCount = textBlock.getLines().size();
                    for (int j = 0; j < lineCount; j++) {
                        String lineText = textBlock.getLines().get(j).getText();
                        if (!TextUtils.isEmpty(lineText)) {
                            mTextLineList.add(lineText);
                        }
                    }
                }
                Intent intent = new Intent(TextRecognitionActivity.this, ImeiSelectActivity.class);
                intent.putStringArrayListExtra(Constant.KEY_IMEI_LIST, mTextLineList);
                startActivityForResult(intent, CODE_REQUEST_IMEI_SELECT_ACTIVITY);
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

    private void openTorch() {
        isTorchOpen = true;
        ivTorch.setImageResource(R.mipmap.ic_torch_open);
        if (mCameraHelper != null) {
            mCameraHelper.openTorch();
        }
    }

    private void closeTorch() {
        isTorchOpen = false;
        ivTorch.setImageResource(R.mipmap.ic_torch_close);
        if (mCameraHelper != null) {
            mCameraHelper.closeTorch();
        }
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
                setResultAndFinish(strInputImei, null, null);
            }
        });
        CommonUtil.showDialog(this, dialog);
    }

    /**
     * 设置Result并回调结果返回上一级页面
     *
     * @param imei1
     * @param imei2
     * @param sn
     */
    private void setResultAndFinish(String imei1, String imei2, String sn) {
        Intent dataIntent = new Intent();
        dataIntent.putExtra(Constant.KEY_IMEI1, imei1);
        dataIntent.putExtra(Constant.KEY_IMEI2, imei2);
        dataIntent.putExtra(Constant.KEY_SN, sn);
        setResult(RESULT_OK, dataIntent);
        finish();
    }
}