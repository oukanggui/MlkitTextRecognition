package com.okg.textrecognition;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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

public class TextRecognitionActivity extends AppCompatActivity {
    private static final String TAG = "Mlkit-TextRecognitionActivity";
    private static int CAMERA_PERMISSION_CODE = 100;

    private Button btnTakePicture;
    private FrameLayout textureViewContainer;
    private TextView tvContent;

    private ViewFinderView frameView;
    private ImageView ivCrop;

    private CameraHelper mCameraHelper;

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
                Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initView() {
        btnTakePicture = findViewById(R.id.btn_take_picture);
        tvContent = findViewById(R.id.tv_content);
        frameView = findViewById(R.id.view_frame);
        ivCrop = findViewById(R.id.iv_crop);
        //textureView = findViewById(R.id.texture_view);
        textureViewContainer = findViewById(R.id.container);
        btnTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCameraHelper != null) {
                    mCameraHelper.takePicture(new CameraHelper.OnTakePictureListener() {
                        @Override
                        public void onTakePicture(Bitmap bitmap, int pictureRotationDegrees, int deviceRotationDegrees) {
                            CommonUtil.log(TAG, "onTakePicture , bitmap = " + bitmap + ", pictureRotationDegrees = " + pictureRotationDegrees + ",deviceRotationDegrees=" + deviceRotationDegrees);
                            analyzeImage(bitmap, pictureRotationDegrees);
                        }
                    });
                }
            }
        });
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


    /**
     * 解析文本
     */
    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeImage(Bitmap bitmap, int rotationDegrees) {
        if (bitmap == null) {
            CommonUtil.log(TAG, "analyzeImage , bitmap is null !!!!!!!");
            return;
        }
        Rect cropRect = new Rect((int) frameView.getFrameLeft(), (int) frameView.getFrameTop(), (int) frameView.getFrameRight(), (int) frameView.getFrameBottom());
        Bitmap cropBitmap = CommonUtil.cropBitmap(CommonUtil.rotateBitmap(bitmap, rotationDegrees), cropRect);
        InputImage inputImage = InputImage.fromBitmap(cropBitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(inputImage).addOnSuccessListener(new OnSuccessListener<Text>() {
            @Override
            public void onSuccess(Text result) {
                ivCrop.setImageBitmap(cropBitmap);
                int blockCount = result.getTextBlocks().size();
                StringBuffer stringBuffer = new StringBuffer();
                for (int i = 0; i < blockCount; i++) {
                    Text.TextBlock textBlock = result.getTextBlocks().get(i);
                    Rect rect = textBlock.getBoundingBox();
                    stringBuffer.append("blockNum: " + i + " ,top=" + rect.top + " ,bottom=" + rect.bottom + " ,centerY=" + rect.centerY() + "\n");
                    int lineCount = textBlock.getLines().size();
                    if (lineCount == 0) {
                        continue;
                    }
                    for (int j = 0; j < lineCount; j++) {
                        Text.Line textLine = textBlock.getLines().get(j);
                        stringBuffer.append("lineNum: " + j + ", lineText = " + textLine.getText() + "\n");
                    }
                    stringBuffer.append("\n");
                }
                CommonUtil.log(TAG, "original text = " + stringBuffer.toString());
                JSONObject jsonObject = OCRHelper.getInstance().parseText(result);
                tvContent.setText(jsonObject.toString());
                String imei1 = jsonObject.optString(OCRHelper.KEY_IMEI1);
                String imei2 = jsonObject.optString(OCRHelper.KEY_IMEI2);
                String sn = jsonObject.optString(OCRHelper.KEY_SN);
                if (TextUtils.isEmpty(imei1) && TextUtils.isEmpty(imei2) && TextUtils.isEmpty(sn)) {
                    Toast.makeText(TextRecognitionActivity.this,
                            "没有找到imei/sn信息，请调整拍照角度或范围继续拍摄",
                            Toast.LENGTH_LONG).show();
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
        float scaleRatio = CommonUtil.getRealScreenWidth(this) / sourceBitmap.getWidth();
        Bitmap scaleBitmap = CommonUtil.scaleBitmap(rotateBitmap, scaleRatio);
        // step3 对bitmap进行裁剪，需要重新调整裁剪的矩形框
        Rect cropRect = new Rect((int) frameView.getFrameLeft(), (int) frameView.getFrameTop(), (int) frameView.getFrameRight(), (int) frameView.getFrameBottom());
        return CommonUtil.cropBitmap(scaleBitmap, cropRect);

    }
}