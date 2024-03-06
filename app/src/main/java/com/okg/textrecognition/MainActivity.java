package com.okg.textrecognition;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
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
                startActivity(new Intent(MainActivity.this, TextRecognitionActivity.class));
            }
        });
        btnQRCodeScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, QRCodeScanActivity.class));
            }
        });
    }
}