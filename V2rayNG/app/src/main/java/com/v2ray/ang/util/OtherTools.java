package com.v2ray.ang.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.util.regex.Pattern;

/**
 * Created by ingra on 2017-08-22.
 */

public class OtherTools {

    Activity activity;
    Context context;

    public OtherTools(Activity activity, Context context) {
        this.activity = activity;
        this.context = context;
    }

    private static final String TAG = "MyTools";

    //获取状态栏宽度pixel
    public int getStatusBarSize() {
        int statusBarHeight = -1;
        int resourceId = context.getResources().getIdentifier("status_bar_height",
                "dimen", "android");//获取status_bar_height资源的ID
        if (resourceId > 0) {
            //根据资源ID获取响应的尺寸值
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        Log.d("MyTools", " 状态栏高度:" + px2dip(statusBarHeight) + "dp");
        return statusBarHeight;
    }

    //获取虚拟导航栏宽度
    public int getNavigationBarSize() {
        int navigationBarHeight = -1;
        int resourceId = context.getResources().getIdentifier("navigation_bar_height",
                "dimen", "android");//获取navigation_bar_height资源的ID
        if (resourceId > 0) {
            //根据资源ID获取响应的尺寸值
            navigationBarHeight = context.getResources().getDimensionPixelSize
                    (resourceId);
        }
        Log.d("MyTools", " 导航栏高度:" + px2dip(navigationBarHeight) + "dp");
        return navigationBarHeight;
    }

    //判断字符串是否为浮点数（Double和Float）
    public boolean isDouble(String str) {
        if (null == str || "".equals(str)) {
            return false;
        }
        Pattern pattern = Pattern.compile("^[-\\+]?[.\\d]*$");
        return pattern.matcher(str).matches();
    }

    //判断字符串是否为整型
    public boolean isInteger(String str) {
        if (null == str || "".equals(str)) {
            return false;
        }
        Pattern pattern = Pattern.compile("^[-\\+]?[\\d]*$");
        return pattern.matcher(str).matches();
    }

    //判断字符串是否是数字
    public boolean isNum(String str) {
        if (isInteger(str) || isDouble(str)) {
            return true;
        } else {
            return false;
        }
    }

    //获取屏幕分辨率（width，height）,更新使用最新方法
    public int[] getResolution() {
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getRealSize(point);
        int mScreenH = point.y;
        int mScreenW = point.x;
        Log.d(TAG, "getResolution: " + "width:" + mScreenW + "height:" + mScreenH);
        return new int[]{mScreenH, mScreenW};
    }

    //查看系统当前是否已经显示虚拟导航按键
    public boolean isVitrualButtonOpened() {
        WindowManager windowManager = activity.getWindowManager();
        Display d = windowManager.getDefaultDisplay();
        DisplayMetrics realDisplayMetrics = new DisplayMetrics();
        d.getRealMetrics(realDisplayMetrics);
        int realHeight = realDisplayMetrics.heightPixels;
        int realWidth = realDisplayMetrics.widthPixels;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        d.getMetrics(displayMetrics);
        int displayHeight = displayMetrics.heightPixels;
        int displayWidth = displayMetrics.widthPixels;
        boolean result = (realWidth - displayWidth) > 0 || (realHeight - displayHeight)
                > 0;
        Log.d(TAG, "isVitrualButtonOpened: " + result);
        return result;
    }

    //计算PPI
    public int calculatePpi(int width, int height, double size) {
        double ppi = (Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2))) / size;
        if (ppi - (int) ppi >= 0.5) {
            Log.d(TAG, "calculatePpi: " + ppi);
            return (int) ppi + 1;
        } else {
            Log.d(TAG, "calculatePpi: " + ppi);
            return (int) ppi;
        }
    }

    //获取屏幕物理尺寸，标准获取方法
    //已经无效
    public double getDeviceSize() {
        int[] pixels=getResolution();
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        double x = Math.pow(pixels[0]/ dm.xdpi, 2);
        double y = Math.pow(pixels[1] / dm.ydpi, 2);
        double screenInches = Math.sqrt(x + y);
        return screenInches;
    }

    //获取当前屏幕ppi
    public int getDevicePpi() {
        int[] a = getResolution();
        int ppi = calculatePpi(a[0], a[1], getDeviceSize());
        Log.d(TAG, "getDevicePpi: " + a[0] + "+++++" + a[1]);
        return ppi;
    }

    //pixel转换成dp数
    public float px2dip(float pxValue) {
        float m = context.getResources().getDisplayMetrics().density;
        return pxValue / m + 0.5f;
    }

    //dp转换成px
    public int dp2px(float dpValue) {
        float m = context.getResources().getDisplayMetrics().density;
        return (int) ((dpValue - 0.5f) * m);
    }
}