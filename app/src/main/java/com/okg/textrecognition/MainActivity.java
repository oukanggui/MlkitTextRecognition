package com.okg.textrecognition;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private static final int CODE_REQUEST_TEXT_RECOGNITION = 50;
    private Button btnTextRecognition, btnQRCodeScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnTextRecognition = findViewById(R.id.btn_text_recognition);
        btnQRCodeScan = findViewById(R.id.btn_qrcode_scan);
        btnTextRecognition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityForResult(new Intent(MainActivity.this, TextRecognitionActivity.class)
                        , CODE_REQUEST_TEXT_RECOGNITION);
            }
        });
        btnQRCodeScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, QRCodeScanActivity.class));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CODE_REQUEST_TEXT_RECOGNITION && resultCode == RESULT_OK && data != null) {
            String imei1 = data.getStringExtra(Constant.KEY_IMEI1);
            String imei2 = data.getStringExtra(Constant.KEY_IMEI2);
            String sn = data.getStringExtra(Constant.KEY_SN);
            CommonUtil.log("Main-onActivityResult == :" + "imei1 = " + imei1 + "\n" +
                    "imei2 = " + imei2 + "\n" +
                    "sn = " + sn);
        }
    }
}