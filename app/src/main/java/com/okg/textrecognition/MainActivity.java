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
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
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

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Mlkit-MainActivity";
    private static int CAMERA_PERMISSION_CODE = 100;
    private static final String STR_SPLIT = ":";
    private static final String KEY_IMEI = "imei";
    private static final String KEY_IMEI1 = "imei1";
    private static final String KEY_IMEI2 = "imei2";
    private static final String KEY_SN = "sn";

    private static final int TYPE_LAYOUT_CMD = 1;
    private static final int TYPE_LAYOUT_VERTICAL = 2;
    private static final int TYPE_LAYOUT_HORIZONTAL = 3;

    private Button btnTakePicture;
    private FrameLayout textureViewContainer;
    private TextView tvContent;

    private CameraHelper mCameraHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
        //textureView = findViewById(R.id.texture_view);
        textureViewContainer = findViewById(R.id.container);
        btnTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCameraHelper != null) {
                    mCameraHelper.takePicture(new CameraHelper.OnTakePictureListener() {
                        @Override
                        public void onTakePicture(Bitmap bitmap, int angle, int rotationDegrees) {
                            Log.d("CameraMlkit", "onTakePicture , bitmap = " + bitmap + ", angle = " + angle + ",rotationDegrees=" + rotationDegrees);
                            analyzeImage(bitmap, rotationDegrees);
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
            Log.e(TAG, "analyzeImage , bitmap is null !!!!!!!");
            return;
        }
        InputImage inputImage = InputImage.fromBitmap(bitmap, 90);
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(inputImage).addOnSuccessListener(new OnSuccessListener<Text>() {
            @Override
            public void onSuccess(Text result) {
                int blockCount = result.getTextBlocks().size();
                if (blockCount == 0) {
                    Toast.makeText(MainActivity.this, "No Text Found in image!", Toast.LENGTH_LONG).show();
                    return;
                }
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
                tvContent.setText(stringBuffer.toString());
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

    private void parseText(Text result) {
        int blockCount = result == null ? 0 : result.getTextBlocks().size();
        if (blockCount == 0) {
            Toast.makeText(MainActivity.this, "No Text Found in image!", Toast.LENGTH_LONG).show();
            return;
        }
        int layoutType = detectTextLayout(result);
        if (layoutType <= 0) {
            Toast.makeText(MainActivity.this, "没有检测到imei信息", Toast.LENGTH_LONG).show();
            return;
        }
        switch (layoutType) {
            case TYPE_LAYOUT_CMD:
                tvContent.setText(parseCMDDeviceInfo(result));
                break;
            case TYPE_LAYOUT_VERTICAL:
                tvContent.setText(parseVerticalDeviceInfo(result));
                break;
            case TYPE_LAYOUT_HORIZONTAL:
                tvContent.setText(parseHorizontalDeviceInfo(result));
                break;
            default:
                break;
        }
    }


    /**
     * 通过imei关键字来探测排行方向
     *
     * @return
     */
    private int detectTextLayout(Text result) {
        int blockCount = result.getTextBlocks().size();
        // 先遍历查找imei，并确定布局方向
        for (int i = 0; i < blockCount; i++) {
            Text.TextBlock textBlock = result.getTextBlocks().get(i);
            Rect rect = textBlock.getBoundingBox();
            Log.d(TAG, "blockNum: " + i + " ,top=" + rect.top + " ,bottom=" + rect.bottom + " ,centerY=" + rect.centerY() + "\n");
            int lineCount = textBlock.getLines().size();
            if (lineCount == 0) {
                continue;
            }
            for (int j = 0; j < lineCount; j++) {
                Text.Line textLine = textBlock.getLines().get(j);
                String lineText = textLine.getText();
                // 文本转换为小写
                lineText = lineText.toLowerCase();
                Log.d(TAG, "lineNum: " + j + ", lineText = " + textLine.getText() + "\n");
                if (lineText.contains(KEY_IMEI)) {
                    Log.d(TAG, "=======检测到有imei关键字=======");
                    // step1 该行文本包含imei关键字，进一步探测
                    if (lineText.contains(STR_SPLIT)) {
                        // 包含“:”，证明是通过*#06#命令查看的方式
                        Log.d(TAG, "检测到有imei关键字且包含:，判定为通过命令行输入方式");
                        return TYPE_LAYOUT_CMD;
                    } else {
                        // step2 检测是否为横向的
                        boolean isHorizontal = detectIsHorizontal(rect, result);
                        if (isHorizontal) {
                            Log.d(TAG, "判断为横向排版");
                            return TYPE_LAYOUT_HORIZONTAL;
                        } else {
                            Log.d(TAG, "判断为垂直排版");
                            return TYPE_LAYOUT_VERTICAL;
                        }
                    }
                }
            }
        }
        // 没有检测到
        return -1;
    }

    private boolean detectIsHorizontal(Rect imeiBlockRect, Text resultText) {
        int blockCount = resultText.getTextBlocks().size();
        int imeiRectTop = imeiBlockRect.top;
        int imeiRectBottom = imeiBlockRect.bottom;
        int imeiRectCenterY = imeiBlockRect.centerY();
        for (int i = 0; i < blockCount; i++) {
            Rect blockRect = resultText.getTextBlocks().get(i).getBoundingBox();
            int blockCenterY = blockRect.centerY();
            // 检测是否是否存在top、bottom存在区域重叠
            if ((imeiRectCenterY >= blockRect.top) && (imeiRectCenterY <= blockRect.bottom) && (blockCenterY >= imeiRectTop) && (blockCenterY <= imeiRectBottom)) {
                return true;
            }
        }
        return false;
    }

    private String parseCMDDeviceInfo(Text resultText) {
        return "";
    }

    private String parseVerticalDeviceInfo(Text resultText) {
        return "";
    }

    private String parseHorizontalDeviceInfo(Text resultText) {
        return "";
    }
}