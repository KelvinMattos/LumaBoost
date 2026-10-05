package com.kelvin.lumaboost;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.provider.Settings;

final class Permissions {
    static final int REQ_STORAGE = 41;

    private Permissions() {
    }

    @SuppressWarnings("deprecation")
    static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        } else {
            mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static void requestUsageAccess(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        if (!start(activity, intent)) {
            start(activity, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    static boolean hasAllFilesAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static void requestAllFilesAccess(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            if (!start(activity, intent)) {
                start(activity, new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            activity.requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_STORAGE);
        }
    }

    static boolean canWriteSettings(Context context) {
        return Settings.System.canWrite(context);
    }

    static void requestWriteSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        start(activity, intent);
    }

    static boolean hasSecureSettings(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean start(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static void openAppDetails(Activity activity, String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        start(activity, intent);
    }

    static void openSystemScreen(Activity activity, String action) {
        if (!start(activity, new Intent(action))) {
            start(activity, new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
