package io.github.aw1y2z.sesame.util;

import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;

public class PermissionUtil {
    private static final String TAG = AntForestRpcCall.class.getSimpleName();

    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    private static final String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
    };

    public static Boolean checkOrRequestAllPermissions(AppCompatActivity activity) {
        return checkOrRequestFilePermissions(activity) && checkOrRequestAlarmPermissions(activity);
    }

    public static boolean checkFilePermissions(Context context) {
        // Android 10（API 29）起为 scoped storage：模块只读写自己的专属外部目录
        // （Android/media/<宿主包名>/sesame-M）与导出目录，不需要任何存储权限，
        // manifest 中也已不再申请"所有文件访问"（MANAGE_EXTERNAL_STORAGE）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        // Android 9 及以下写外部目录仍需运行时权限（manifest 中已带 maxSdkVersion=28）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : PERMISSIONS_STORAGE) {
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Boolean checkOrRequestFilePermissions(AppCompatActivity activity) {
        try {
            if (checkFilePermissions(activity)) {
                return true;
            }
            // 只有 Android 6~9 需要申请；Android 10+ 无需权限，也不会再跳"所有文件访问"设置页
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                activity.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return false;
    }

    public static boolean checkAlarmPermissions() {
        Context context;
        try {
            if (!ApplicationHook.isHooked()) {
                return false;
            }
            context = ApplicationHook.getContext();
            if (context == null) {
                return false;
            }
        } catch (Throwable e) {
            // 必须 catch Throwable：模块 App 自己的进程里没有 libxposed，触达 ApplicationHook
            // 抛的是 NoClassDefFoundError（Error），catch Exception 接不住会把界面搞崩
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            //判断是否有使用闹钟的权限
            AlarmManager systemService = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (systemService != null) {
                return systemService.canScheduleExactAlarms();
            }
            return true;
        }
        return true;
    }

    public static Boolean checkOrRequestAlarmPermissions(Context context) {
        try {
            if (checkAlarmPermissions()) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                //跳转到权限页，请求权限
                Intent appIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appIntent.setData(Uri.parse("package:" + ClassUtil.PACKAGE_NAME));
                //appIntent.setData(Uri.fromParts("package", ClassUtil.PACKAGE_NAME, null));
                try {
                    context.startActivity(appIntent);
                } catch (ActivityNotFoundException ex) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return false;
    }

    public static boolean checkBatteryPermissions() {
        Context context;
        try {
            if (!ApplicationHook.isHooked()) {
                return false;
            }
            context = ApplicationHook.getContext();
            if (context == null) {
                return false;
            }
        } catch (Throwable e) {
            // 同 checkAlarmPermissions：ApplicationHook 在 App 进程里会抛 Error，必须 catch Throwable
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //判断是否有始终在后台运行的权限
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(ClassUtil.PACKAGE_NAME);
            }
            return true;
        }
        return true;
    }

    public static Boolean checkOrRequestBatteryPermissions(Context context) {
        try {
            if (context == null) {
                return false;
            }
            if (checkBatteryPermissions()) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //跳转到权限页，请求权限
                Intent appIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appIntent.setData(Uri.parse("package:" + ClassUtil.PACKAGE_NAME));
                context.startActivity(appIntent);
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
}
