package com.okg.textrecognition;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * @author okg
 * @date 2024-03-10
 * 描述：Imei选择Adapter
 */
public class ImeiSelectAdapter extends RecyclerView.Adapter<ImeiSelectAdapter.ViewHolder> {
    private List<ImeiSelectBean> imeiList;

    private OnItemListener mListener;

    public interface OnItemListener {
        void onItemSelectChange(int position);
    }

    public ImeiSelectAdapter(List<ImeiSelectBean> imeiList) {
        this.imeiList = imeiList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_imei_select, null, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        //获取当前位置的子项对象
        final ImeiSelectBean imeiSelectBean = imeiList.get(position);
        //从当前子项对象中获取数据，绑定在viewHolder对象中
        holder.ivSelect.setImageResource(imeiSelectBean.isSelect ? R.mipmap.ic_launcher : R.mipmap.ic_close);
        holder.etImei.setText(imeiSelectBean.imei);
        holder.ivSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onItemSelectChange(holder.getAdapterPosition());
                }
            }
        });
        holder.mTxtWatcher.buildWatcher(position, imeiSelectBean);
        /**
         * RecyclerView 在滑动的时候会使EditText失去焦点，这样可以触发OnFocusChangeListener，
         * 这样可以更准确的绑定和解绑TxtWatcher
         */
        holder.etImei.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    holder.etImei.addTextChangedListener(holder.mTxtWatcher);
                } else {
                    holder.etImei.removeTextChangedListener(holder.mTxtWatcher);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return imeiList != null ? imeiList.size() : 0;
    }

    public void setNewData(List<ImeiSelectBean> imeiList) {
        this.imeiList.clear();
        this.imeiList.addAll(imeiList);
        notifyDataSetChanged();
    }

    public void setOnItemListener(OnItemListener listener) {
        mListener = listener;
    }

    //ViewHolder类将子项布局中所有控件绑定为一个对象，该对象包含子项布局的所有控件
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivSelect;
        EditText etImei;
        private TxtWatcher mTxtWatcher;

        public ViewHolder(View view) {
            //父类构造函数
            super(view);
            //获取RecyclerView布局的子项布局中的所有控件id
            ivSelect = view.findViewById(R.id.iv_select);
            etImei = view.findViewById(R.id.et_imei);
            mTxtWatcher = new TxtWatcher();
        }
    }

    static class TxtWatcher implements TextWatcher {

        private int mPosition;
        private ImeiSelectBean bean;

        public void buildWatcher(int position, ImeiSelectBean bean) {
            this.mPosition = position;
            this.bean = bean;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            bean.imei = s.toString();
        }
    }
}
