package com.okg.textrecognition;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;

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
        if (openLog) {
            Log.d(TAG, msg);
        }
    }
}
