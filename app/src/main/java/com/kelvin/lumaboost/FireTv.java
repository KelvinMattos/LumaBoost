package com.kelvin.lumaboost;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Particularidades do Fire TV (Fire OS): Configurações próprias da Amazon, sem telas de Acesso ao uso,
 * de Ajustes do sistema nem lista de serviços de acessibilidade de terceiros. Essas permissões só são
 * liberadas via ADB, e o cache de todos os apps sai de uma vez em Configurações → Aplicativos.
 */
final class FireTv {
    /** Abre Configurações → Aplicativos, onde fica "Limpar o cache de todos os aplicativos". */
    static final String ACTION_APPLICATIONS = "com.amazon.device.settings.action.APPLICATIONS";

    /** Apps da Amazon que a pessoa abre e que podem ser fechados; os demais pacotes da Amazon são serviços do sistema. */
    private static final String[] AMAZON_USER_APPS = {
            "com.amazon.avod", "com.amazon.firebat", "com.amazon.cloud9", "com.amazon.firetv.youtube",
            "com.amazon.bueller.photos", "com.amazon.bueller.music", "com.amazon.minitv.android.app",
            "com.amazon.shoptv.firetv.client", "com.amazon.gamehub", "com.amazon.mp3", "com.amazon.kindle"
    };

    private static Boolean cached;

    private FireTv() {
    }

    static boolean is(Context context) {
        if (cached == null) {
            PackageManager pm = context.getPackageManager();
            cached = pm.hasSystemFeature("amazon.hardware.fire_tv")
                    || (Build.MANUFACTURER.equalsIgnoreCase("Amazon") && Build.MODEL.startsWith("AFT"));
        }
        return cached;
    }

    /** Serviço interno da Amazon (perfis, protetor de tela, Alexa, loja): nunca é listado nem fechado. */
    static boolean isSystemService(ApplicationInfo info) {
        if (!info.packageName.startsWith("com.amazon.")) {
            return false;
        }
        for (String pkg : AMAZON_USER_APPS) {
            if (pkg.equals(info.packageName)) {
                return false;
            }
        }
        return (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    static Intent applicationsIntent() {
        return new Intent(ACTION_APPLICATIONS);
    }

    /** Comandos ADB que liberam o que o Fire TV não deixa ligar pela tela. */
    static String adbCommands(Context context) {
        String pkg = context.getPackageName();
        return "adb shell pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS\n"
                + "adb shell appops set " + pkg + " GET_USAGE_STATS allow\n"
                + "adb shell appops set " + pkg + " WRITE_SETTINGS allow";
    }
}
