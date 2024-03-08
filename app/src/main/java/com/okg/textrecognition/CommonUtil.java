package com.okg.textrecognition;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * @author oukanggui
 * @date 2023/7/25
 * @desc 通用工具类
 */

public class CommonUtil {

    private static final String TAG = "Mlkit-CommonUtil";
    private static boolean openLog = true;

    /**
     * 获取屏幕的宽度
     *
     * @return 屏幕的宽度px
     */
    public static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    /**
     * 获取屏幕的高度，在虚拟机上，获取到的高度可能不包含虚拟导航键，导致获取到的屏幕高度偏低
     *
     * @return 屏幕的高度px
     */
    public static int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    /**
     * 获取当前屏幕的方向
     *
     * @return
     */
    public static int getScreenOrientation() {
        return Resources.getSystem().getConfiguration().orientation;
    }

    /**
     * 通过Activity获取真正的屏幕宽度
     *
     * @param context
     * @return
     */
    public static int getRealScreenWidth(Context context) {
        if (context == null || !(context instanceof Activity)) {
            return getScreenWidth();
        }
        Activity activity = (Activity) context;
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    /**
     * 通过Activity获取真正的屏幕高度
     *
     * @param context
     * @return
     */
    public static int getRealScreenHeight(Context context) {
        if (context == null || !(context instanceof Activity)) {
            return getScreenHeight();
        }
        Activity activity = (Activity) context;
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics.heightPixels;
    }

    /**
     * 简单的日志打印，用于调试
     *
     * @param msg
     */
    public static void log(String msg) {
        log(TAG, msg);
    }

    /**
     * 简单的日志打印，用于调试
     *
     * @param tag
     * @param msg
     */
    public static void log(String tag, String msg) {
        if (openLog) {
            Log.d(tag, msg);
        }
    }

    /**
     * 旋转图片
     *
     * @param originBitmap
     * @param rotationDegrees
     * @return
     */
    public static Bitmap rotateBitmap(Bitmap originBitmap, int rotationDegrees) {
        if (originBitmap == null) {
            return null;
        }
        Matrix matrix = new Matrix();
        matrix.setRotate(rotationDegrees);
        Bitmap newBitmap = Bitmap.createBitmap(originBitmap, 0, 0, originBitmap.getWidth(), originBitmap.getHeight(), matrix, true);
        if (newBitmap.equals(originBitmap)) {
            return newBitmap;
        }
        originBitmap.recycle();
        return newBitmap;
    }

    /**
     * 裁剪图片
     *
     * @param originBitmap
     * @param cropRect
     * @return
     */
    public static Bitmap cropBitmap(Bitmap originBitmap, Rect cropRect) {
        if (originBitmap == null) {
            return null;
        }
        if (cropRect == null) {
            return originBitmap;
        }
        Bitmap newBitmap = Bitmap.createBitmap(originBitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height());
        if (newBitmap.equals(originBitmap)) {
            return newBitmap;
        }
        originBitmap.recycle();
        return newBitmap;
    }

    /**
     * 按比例缩放图片
     *
     * @param originBitmap 原图
     * @param ratio        比例
     * @return 新的bitmap
     */
    public static Bitmap scaleBitmap(Bitmap originBitmap, float ratio) {
        if (originBitmap == null) {
            return null;
        }
        int width = originBitmap.getWidth();
        int height = originBitmap.getHeight();
        Matrix matrix = new Matrix();
        matrix.preScale(ratio, ratio);
        Bitmap newBitmap = Bitmap.createBitmap(originBitmap, 0, 0, width, height, matrix, false);
        log(TAG, "缩放前宽高：" + width + "-" + height);
        log(TAG, "缩放后宽高：" + newBitmap.getWidth() + "-" + newBitmap.getHeight());
        if (newBitmap.equals(originBitmap)) {
            return newBitmap;
        }
        originBitmap.recycle();
        return newBitmap;
    }

    /**
     * 设置全屏显示，需要在setContentView前调用
     *
     * @param activity
     */
    public static void setFullScreen(Activity activity) {
        if (activity == null) {
            return;
        }
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /**
     * 隐藏底部导航栏
     *
     * @param activity
     */
    public static void hideNavigationBar(Activity activity) {
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
            View v = activity.getWindow().getDecorView();
            v.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            View decorView = activity.getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }
}
