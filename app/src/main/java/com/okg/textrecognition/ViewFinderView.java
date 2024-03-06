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
class ViewFinderView extends View {
    private static final int defaultRectLeft = 50;
    private static final int defaultRectTop = 300;
    private static final int defaultRectRight = 50;
    private static final int defaultRectBottom = 300;
    private static final int defaultRectCornerRadius = 20;
    private static final int defaultRectOutColor = Color.parseColor("#cccccc");

    private Context mContext;
    private Paint mPaint;
    private float mRectLeft, mRectRight, mRectTop, mRectBottom;
    private float mRectCornerRadius;
    private int mRectOutColor;
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
            mRectLeft = ta.getDimension(R.styleable.ViewFinderView_rectLeft, defaultRectLeft);
            mRectTop = ta.getDimension(R.styleable.ViewFinderView_rectTop, defaultRectTop);
            mRectRight = ta.getDimension(R.styleable.ViewFinderView_rectRight, defaultRectRight);
            mRectBottom = ta.getDimension(R.styleable.ViewFinderView_rectBottom, defaultRectBottom);
            mRectCornerRadius = ta.getDimension(R.styleable.ViewFinderView_rectCornerRadius, defaultRectCornerRadius);
            mRectOutColor = ta.getColor(R.styleable.ViewFinderView_rectOutColor, defaultRectOutColor);
        }
    }

    protected void onDraw(Canvas canvas) {
        //抗锯齿
        mPaint.setAntiAlias(true);
        mPaint.setColor(mRectOutColor);
        // 取景框和全屏
        RectF rect = new RectF(mRectLeft, mRectTop, screenWidth - mRectRight, screenHeight - mRectBottom);
        Path path = new Path();
        path.addRoundRect(rect, mRectCornerRadius, mRectCornerRadius, Path.Direction.CW);
        // 全屏
        Region region = new Region();
        region.setPath(path, new Region(0, 0, screenWidth, screenHeight));

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
}
