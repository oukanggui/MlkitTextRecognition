package com.okg.textrecognition;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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

import java.util.List;

public class TextRecognitionActivity extends AppCompatActivity {
    private static final String TAG = "Mlkit-MainActivity";
    private static int CAMERA_PERMISSION_CODE = 100;
    private static final String STR_SPLIT = ":";
    private static final String KEY_IMEI = "imei";
    private static final String KEY_IMEI1 = "imei1";
    private static final String KEY_IMEI2 = "imei2";
    private static final String KEY_SN = "sn";
    private static final String KEY_SN_CHINESE = "序列号";

    private static final int TYPE_LAYOUT_CMD = 1;
    private static final int TYPE_LAYOUT_VERTICAL = 2;
    private static final int TYPE_LAYOUT_HORIZONTAL = 3;

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
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_text_recognition);
        // 隐藏导航键
        //hideNavKey(this);

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
                            Log.d(TAG, "onTakePicture , bitmap = " + bitmap + ", pictureRotationDegrees = " + pictureRotationDegrees + ",deviceRotationDegrees=" + deviceRotationDegrees);
                            analyzeImage(bitmap, pictureRotationDegrees);
                        }
                    });
                }
            }
        });
    }

    public void hideNavKey(Context context) {
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
            View v = ((Activity) context).getWindow().getDecorView();
            v.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            View decorView = ((Activity) context).getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
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
        Bitmap cropBitmap = cropBitmap(rotateBitmap(bitmap, rotationDegrees));
        InputImage inputImage = InputImage.fromBitmap(cropBitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(inputImage).addOnSuccessListener(new OnSuccessListener<Text>() {
            @Override
            public void onSuccess(Text result) {
                ivCrop.setImageBitmap(cropBitmap);
                int blockCount = result.getTextBlocks().size();
                if (blockCount == 0) {
                    Toast.makeText(TextRecognitionActivity.this, "No Text Found in image!", Toast.LENGTH_LONG).show();
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
                Log.d(TAG, "original text = " + stringBuffer.toString());
//                tvContent.setText(stringBuffer.toString());
                parseText(result);
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

    private Bitmap rotateBitmap(Bitmap sourceBitmap, int rotationDegrees) {
        if (sourceBitmap == null) {
            return null;
        }
        Matrix matrix = new Matrix();
        matrix.setRotate(rotationDegrees);
        return Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight(),
                matrix, true);
    }

    private Bitmap cropBitmap(Bitmap sourceBitmap) {
        if (sourceBitmap == null) {
            return null;
        }
        Log.d(TAG, "cropBitmap: width--" + sourceBitmap.getWidth());
        Log.d(TAG, "cropBitmap: height-" + sourceBitmap.getHeight());
        Rect rect = new Rect((int) frameView.getFrameLeft(), (int) frameView.getFrameTop(), (int) frameView.getFrameRight(), (int) frameView.getFrameBottom());
        Log.d(TAG, "cropBitmap: " + rect.toString());
        Log.d(TAG, "cropBitmap: rect width--" + rect.width());
        Log.d(TAG, "cropBitmap: rect height-" + rect.height());
        return Bitmap.createBitmap(sourceBitmap, rect.left, rect.top, rect.width(), rect.height());
    }

    private void parseText(Text result) {
        Log.d(TAG, "parseText: " + result.getText());
        int blockCount = result == null ? 0 : result.getTextBlocks().size();
        if (blockCount == 0) {
            Toast.makeText(TextRecognitionActivity.this, "No Text Found in image!", Toast.LENGTH_LONG).show();
            return;
        }
        int layoutType = detectTextLayout(result);
        if (layoutType <= 0) {
            Toast.makeText(TextRecognitionActivity.this, "没有检测到imei信息", Toast.LENGTH_LONG).show();
            return;
        }
        String resultStr = "";
        switch (layoutType) {
            case TYPE_LAYOUT_CMD:
                resultStr = parseCMDDeviceInfo(result);
                break;
            case TYPE_LAYOUT_VERTICAL:
                resultStr = parseVerticalDeviceInfo(result);
                break;
            case TYPE_LAYOUT_HORIZONTAL:
                resultStr = parseHorizontalDeviceInfo(result);
                break;
            default:
                break;
        }
        tvContent.setText(resultStr + "\n" + "layoutType:" + layoutType);
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
                if (lineText.startsWith(KEY_IMEI)) {
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
        for (int i = 0; i < blockCount; i++) {
            // 判断block是否包含imei关键字
            String blockText = resultText.getTextBlocks().get(i).getText();
            if (isImei(blockText)) {
                Rect blockRect = resultText.getTextBlocks().get(i).getBoundingBox();
                if (isRectContainer(imeiBlockRect, blockRect)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String parseCMDDeviceInfo(Text resultText) {
        Log.d(TAG, "=====parseCMDDeviceInfo=====");
        String imei1 = "";
        String imei2 = "";
        String sn = "";
        int blockCount = resultText.getTextBlocks().size();
        // 先遍历查找imei，并确定布局方向
        for (int i = 0; i < blockCount; i++) {
            Text.TextBlock textBlock = resultText.getTextBlocks().get(i);
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
                if (lineText.startsWith(KEY_IMEI)) {
                    Log.d(TAG, "检测到有imei关键字, 需要进一步判断是否为imei1和imei2");
                    String[] textArrays = lineText.split(STR_SPLIT);
                    if (textArrays != null && textArrays.length > 1) {
                        if (lineText.contains(KEY_IMEI2)) {
                            imei2 = textArrays[1];
                        } else {
                            imei1 = textArrays[1];
                        }
                    }
                } else if (lineText.startsWith(KEY_SN)) {
                    Log.d(TAG, "检测到有sn关键字");
                    String[] textArrays = lineText.split(STR_SPLIT);
                    if (textArrays != null && textArrays.length > 1) {
                        sn = textArrays[1];
                    }
                }
            }
            if (!TextUtils.isEmpty(imei1) && !TextUtils.isEmpty(imei2) && !TextUtils.isEmpty(sn)) {
                // 已全部获取，提前退出循环
                break;
            }
        }
        return "imei1 = " + imei1 + "\n" + "imei2 = " + imei2 + "\n" + "sn = " + sn;
    }

    private String parseVerticalDeviceInfo(Text resultText) {
        Log.d(TAG, "=====parseVerticalDeviceInfo=====");
        String imei1 = "";
        String imei2 = "";
        String sn = "";
        int blockCount = resultText.getTextBlocks().size();
        // 先遍历查找imei，并确定布局方向
        for (int i = 0; i < blockCount; i++) {
            Text.TextBlock textBlock = resultText.getTextBlocks().get(i);
            Log.d(TAG, "blockText = " + textBlock.getText());
            int lineCount = textBlock.getLines().size();
            if (lineCount == 0) {
                continue;
            }
            String firstLineText = textBlock.getLines().get(0).getText();
            Log.d(TAG, "LineCount=" + lineCount + " ,firstLineText:" + firstLineText);
            if (TextUtils.isEmpty(firstLineText)) {
                continue;
            }
            firstLineText = firstLineText.toLowerCase();
            if (firstLineText.startsWith(KEY_SN) || firstLineText.contains(KEY_SN_CHINESE)) {
                Log.d(TAG, "检测到有sn/序列号信息");
                // 下一行为序列号信息
                sn = getNextBlockLineText(resultText, i);
            } else if (firstLineText.contains(KEY_IMEI)) {
                Log.d(TAG, "检测到有imei信息");
                String nextLineOrBlockText = "";
                // 先判断本block中是否有imei信息
                if (lineCount > 1) {
                    for (int j = 1; j < lineCount; j++) {
                        String lineText = textBlock.getLines().get(j).getText();
                        if (isImei(lineText)) {
                            nextLineOrBlockText = lineText;
                            break;
                        }
                    }

                }
                if (TextUtils.isEmpty(nextLineOrBlockText)) {
                    nextLineOrBlockText = getNextBlockLineText(resultText, i);
                }
                Log.d(TAG, "nextLineOrBlockText = " + nextLineOrBlockText);
                if (!TextUtils.isEmpty(nextLineOrBlockText)) {
                    if (TextUtils.isEmpty(imei1)) {
                        imei1 = nextLineOrBlockText;
                    } else {
                        imei2 = nextLineOrBlockText;
                    }
                }
            }
            if (!TextUtils.isEmpty(imei1) && !TextUtils.isEmpty(imei2) && !TextUtils.isEmpty(sn)) {
                // 已全部获取，提前退出循环
                break;
            }
        }
        return "imei1 = " + imei1 + "\n" + "imei2 = " + imei2 + "\n" + "sn = " + sn;
    }

    private String parseHorizontalDeviceInfo(Text resultText) {
        Log.d(TAG, "=====parseHorizontalDeviceInfo=====");
        String imei1 = "";
        String imei2 = "";
        String sn = "";
        int blockCount = resultText.getTextBlocks().size();
        // 先遍历查找imei，并确定布局方向
        for (int i = 0; i < blockCount; i++) {
            Text.TextBlock textBlock = resultText.getTextBlocks().get(i);
            String blockText = textBlock.getText();
            Log.d(TAG, "blockText = " + blockText);
            if (TextUtils.isEmpty(blockText)) {
                continue;
            }
            blockText = blockText.toLowerCase();
            if (blockText.startsWith(KEY_SN) || blockText.contains(KEY_SN_CHINESE)) {
                Log.d(TAG, "检测到有sn/序列号信息");
                // 下一行为序列号信息
                sn = getHorizontalBlockText(resultText, textBlock.getBoundingBox());
            } else if (blockText.contains(KEY_IMEI)) {
                String imei = getHorizontalBlockText(resultText, textBlock.getBoundingBox());
                if (TextUtils.isEmpty(imei1)) {
                    imei1 = imei;
                } else {
                    imei2 = imei;
                }
            }
            if (!TextUtils.isEmpty(imei1) && !TextUtils.isEmpty(imei2) && !TextUtils.isEmpty(sn)) {
                // 已全部获取，提前退出循环
                break;
            }
        }
        return "imei1 = " + imei1 + "\n" + "imei2 = " + imei2 + "\n" + "sn = " + sn;
    }

    private String getNextBlockLineText(Text resultText, int currentBlockIndex) {
        String result = "";
        if (currentBlockIndex < resultText.getTextBlocks().size() - 1) {
            Text.TextBlock nextBlock = resultText.getTextBlocks().get((currentBlockIndex + 1));
            List<Text.Line> nextBlockLines = nextBlock.getLines();
            if (nextBlockLines != null && nextBlockLines.size() > 0) {
                result = nextBlockLines.get(0).getText();
            }
        }
        return result;
    }

    private String getHorizontalBlockText(Text resultText, Rect currentBlockRect) {
        int blockCount = resultText.getTextBlocks().size();
        for (int i = 0; i < blockCount; i++) {
            Rect blockRect = resultText.getTextBlocks().get(i).getBoundingBox();
            if (currentBlockRect != blockRect && isRectContainer(currentBlockRect, blockRect)) {
                return resultText.getTextBlocks().get(i).getText();
            }
        }
        Log.e(TAG, "判断为横向，但找不到横向对应的block，请检查程序逻辑");
        return "";
    }

    private boolean isRectContainer(Rect rect1, Rect rect2) {
        int top1 = rect1.top;
        int bottom1 = rect1.bottom;
        int centerY1 = rect1.centerY();
        int top2 = rect2.top;
        int bottom2 = rect2.bottom;
        int centerY2 = rect2.centerY();
        if ((centerY1 >= top2) && (centerY1 <= bottom2) && (centerY2 >= top1) && (centerY2 <= bottom1)) {
            return true;
        }
        if ((top1 > top2 && top1 < bottom2) || (bottom1 > top2 && bottom1 < bottom2) || (top2 > top1 && top2 < bottom1) || (bottom2 > top1 && bottom2 < bottom1)) {
            return true;
        }
        return false;
    }

    private boolean isImei(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        text = text.trim();
        return text.startsWith("86") || text.startsWith("35") || text.startsWith("01") || text.startsWith("99");
    }
}