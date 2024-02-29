package com.okg.textrecognition;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Mlkit-MainActivity";
    private static int CAMERA_PERMISSION_CODE = 100;

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
                            Log.d("CameraMlkit", "onTakePicture , bitmap = " + bitmap +
                                    ", angle = " + angle + ",rotationDegrees=" + rotationDegrees);
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
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
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


    /**
     * 解析文本
     */
    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeImage(Bitmap bitmap, int rotationDegrees) {
        if (bitmap == null) {
            Log.e(TAG, "analyzeImage , bitmap is null !!!!!!!");
            return;
        }
        InputImage inputImage = InputImage.fromBitmap(bitmap, rotationDegrees);
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(inputImage).addOnSuccessListener(new OnSuccessListener<Text>() {
            @Override
            public void onSuccess(Text result) {
                tvContent.setText(result.getText());
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

    private TextureView createTextureView() {
        TextureView textureView = new TextureView(this);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        textureView.setLayoutParams(lp);
        textureViewContainer.addView(textureView, 0, lp);
        return textureView;
    }
}