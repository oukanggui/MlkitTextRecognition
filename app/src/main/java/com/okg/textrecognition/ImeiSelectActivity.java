package com.okg.textrecognition;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class ImeiSelectActivity extends AppCompatActivity {
    public static final String KEY_IMEI_LIST = "keyImeiList";
    private RecyclerView rvImei;
    private View btnBack, btnComplete;
    private List<ImeiSelectBean> imeiList;
    private ImeiSelectAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CommonUtil.setFullScreen(this);
        setContentView(R.layout.activity_imei_select);
        initView();
        initData();
    }

    private void initView() {
        rvImei = findViewById(R.id.rv_imei);
        btnBack = findViewById(R.id.iv_back);
        btnComplete = findViewById(R.id.btn_complete);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        btnComplete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 获取选中的imei信息返回
                boolean isExitSelect = false;
                String selectImeiStr = null;
                for (int i = 0; i < imeiList.size(); i++) {
                    if (imeiList.get(i).isSelect) {
                        isExitSelect = true;
                        selectImeiStr = imeiList.get(i).imei;
                    }
                }
                if (!isExitSelect) {
                    CommonUtil.showToast(ImeiSelectActivity.this, "请选中需要的IMEI信息");
                    return;
                }
                if (TextUtils.isEmpty(selectImeiStr)) {
                    CommonUtil.showToast(ImeiSelectActivity.this, "IMEI信息不能为空");
                    return;
                }
                // TODO 选择的imei信息返回给上一个页面
            }
        });
    }

    private void initData() {
        if (getIntent() == null) {
            imeiList = new ArrayList<>();
        } else {
            List<String> dataList = getIntent().getStringArrayListExtra(KEY_IMEI_LIST);
            if (dataList == null) {
                imeiList = new ArrayList<>();
            } else {
                int size = dataList.size();
                for (int i = 0; i < size; i++) {
                    ImeiSelectBean imeiSelectBean = new ImeiSelectBean();
                    imeiSelectBean.imei = dataList.get(i);
                    // 默认选中第一个
                    imeiSelectBean.isSelect = i == 0;
                    imeiList.add(imeiSelectBean);
                }
            }
        }
        rvImei.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ImeiSelectAdapter(imeiList);
        rvImei.setAdapter(mAdapter);
        mAdapter.setOnItemListener(new ImeiSelectAdapter.OnItemListener() {
            @Override
            public void onItemSelectChange(int position) {
                if (imeiList == null || position < 0 || position >= imeiList.size()) {
                    return;
                }
                for (int i = 0; i < position; i++) {
                    ImeiSelectBean imeiSelectBean = imeiList.get(i);
                    if (i == position) {
                        imeiSelectBean.isSelect = !imeiSelectBean.isSelect;
                    } else {
                        // 其余未非选中状态
                        imeiSelectBean.isSelect = false;
                    }
                }
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onImeiTextChange(int position, String newImei) {
                if (imeiList == null || position < 0 || position >= imeiList.size()) {
                    return;
                }
                imeiList.get(position).imei = newImei;
                mAdapter.notifyItemChanged(position);
            }
        });
    }
}