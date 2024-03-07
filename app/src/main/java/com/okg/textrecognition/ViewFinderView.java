package com.okg.textrecognition;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RegionIterator;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * @author okg
 * @date 2024-03-06
 * 描述：取景框自定义View
 */
public class ViewFinderView extends View {
    private static final int defaultFrameHeight = 300;
    private static final int defaultFrameMarginLeft = 60;
    private static final int defaultFrameMarginRight = 60;
    private static final int defaultFrameCornerRadius = 30;
    private static final int defaultFrameOutColor = Color.parseColor("#50000000");

    private Context mContext;
    private Paint mPaint;
    private float mFrameMarginLeft, mFrameMarginRight, mFrameHeight;
    private float mFrameCornerRadius;
    private int mFrameOutColor;
    private int screenWidth, screenHeight;


    public ViewFinderView(Context context) {
        this(context, null);
    }

    public ViewFinderView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewFinderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        mPaint = new Paint();
        screenWidth = CommonUtil.getRealScreenWidth(mContext);
        screenHeight = CommonUtil.getRealScreenHeight(mContext);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ViewFinderView);
        if (ta != null) {
            mFrameHeight = ta.getDimension(R.styleable.ViewFinderView_frameHeight, defaultFrameHeight);
            mFrameMarginLeft = ta.getDimension(R.styleable.ViewFinderView_frameMarginLeft, defaultFrameMarginLeft);
            mFrameMarginRight = ta.getDimension(R.styleable.ViewFinderView_frameMarginRight, defaultFrameMarginRight);
            mFrameCornerRadius = ta.getDimension(R.styleable.ViewFinderView_frameCornerRadius, defaultFrameCornerRadius);
            mFrameOutColor = ta.getColor(R.styleable.ViewFinderView_frameOutColor, defaultFrameOutColor);
        }
    }

    protected void onDraw(Canvas canvas) {
        //抗锯齿
        mPaint.setAntiAlias(true);
        mPaint.setColor(mFrameOutColor);
        // 取景框
        RectF rect = new RectF(mFrameMarginLeft, screenHeight / 2 - mFrameHeight / 2, screenWidth - mFrameMarginRight, screenHeight / 2 + mFrameHeight / 2);
        Path path = new Path();
        path.addRoundRect(rect, mFrameCornerRadius, mFrameCornerRadius, Path.Direction.CW);
        // 取景框Region
        Region region = new Region();
        region.setPath(path, new Region(0, 0, screenWidth, screenHeight));
        // 全屏Region
        Region region1 = new Region();
        Path path1 = new Path();
        path1.addRect(0, 0, screenWidth, screenHeight, Path.Direction.CW);
        region1.setPath(path1, new Region(0, 0, screenWidth, screenHeight));
        // 范围取异并集
        region.op(region1, Region.Op.XOR);

        RegionIterator iterator = new RegionIterator(region);
        Rect rec = new Rect();
        while (iterator.next(rec)) {
            canvas.drawRect(rec, mPaint);
        }
    }

    public float getFrameLeft() {
        return mFrameMarginLeft;
    }

    public float getFrameRight() {
        return screenWidth - mFrameMarginRight;
    }

    public float getFrameTop() {
        return screenHeight / 2 - mFrameHeight / 2;
    }

    public float getFrameBottom() {
        return screenHeight / 2 + mFrameHeight / 2;
    }
}
