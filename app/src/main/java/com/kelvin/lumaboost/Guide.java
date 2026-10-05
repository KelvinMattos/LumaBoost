package com.kelvin.lumaboost;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Guia de permissões: abre a tela certa do Android e, com o assistente ligado, mostra um
 * "Toque aqui" pulsando sobre a chave. Quando a permissão é liberada, o Luma volta sozinho.
 * Quem toca é sempre a pessoa: o assistente só aponta, nunca liga permissões por conta própria.
 */
final class Guide {
    static final int ASSISTANT = 0;
    static final int USAGE = 1;
    static final int FILES = 2;
    static final int SETTINGS = 3;
    static final int NOTIFICATIONS = 4;

    /** Passo em andamento fora do app (para voltar sozinho quando liberar). */
    static volatile int pending = -1;

    private Guide() {
    }

    static boolean needed(int step) {
        return true;
    }

    static boolean isGranted(Context context, int step) {
        switch (step) {
            case ASSISTANT:
                return ForceStopService.isEnabled(context);
            case USAGE:
                return Permissions.hasUsageAccess(context);
            case FILES:
                return Permissions.hasAllFilesAccess(context);
            case SETTINGS:
                return Permissions.canWriteSettings(context);
            case NOTIFICATIONS:
                return Prefs.monitorEnabled(context) && (Build.VERSION.SDK_INT < 33
                        || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
            default:
                return true;
        }
    }

    static boolean allGranted(Context context) {
        for (int step = ASSISTANT; step <= NOTIFICATIONS; step++) {
            if (needed(step) && !isGranted(context, step)) {
                return false;
            }
        }
        return true;
    }

    /** Frase curta que aparece ao abrir as configurações do Android. */
    static String hint(int step) {
        switch (step) {
            case ASSISTANT:
                return "Toque em \"Luma Boost\", ligue a chave e toque em Permitir";
            case USAGE:
                return "Ligue a chave do Luma Boost para mostrar o uso dos apps";
            case FILES:
                return "Ligue a chave para o Luma encontrar arquivos esquecidos";
            case SETTINGS:
                return "Ligue a chave para o Luma cuidar dos ajustes";
            default:
                return "";
        }
    }

    /** No Fire TV estas permissões não têm tela nas Configurações: só saem via ADB. */
    static boolean needsAdb(Context context, int step) {
        return FireTv.is(context) && (step == ASSISTANT || step == USAGE || step == SETTINGS);
    }

    /** Leva a pessoa até a tela da permissão e liga o guia visual quando possível. */
    static void open(Activity activity, int step) {
        if (needsAdb(activity, step)) {
            if (step == ASSISTANT) {
                Prefs.setAssistantConsent(activity, true);
            }
            if (step == ASSISTANT && ForceStopService.enableSelf(activity)) {
                Toast.makeText(activity, "Assistente do Luma ligado", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(activity, "No Fire TV, libere pelo computador com os comandos ADB desta tela", Toast.LENGTH_LONG).show();
            return;
        }
        if (step == NOTIFICATIONS) {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 52);
            }
            return;
        }
        pending = step;
        Toast.makeText(activity, hint(step), Toast.LENGTH_LONG).show();
        switch (step) {
            case ASSISTANT:
                openAccessibility(activity);
                break;
            case USAGE:
                Permissions.requestUsageAccess(activity);
                break;
            case FILES:
                Permissions.requestAllFilesAccess(activity);
                break;
            case SETTINGS:
                Permissions.requestWriteSettings(activity);
                break;
            default:
                break;
        }
        if (step != ASSISTANT) {
            ForceStopService.startGuide(step);
        }
    }

    private static void openAccessibility(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
            details.putExtra(Intent.EXTRA_COMPONENT_NAME, new ComponentName(activity, ForceStopService.class).flattenToString());
            if (Permissions.start(activity, details)) {
                return;
            }
        }
        Permissions.start(activity, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    /** Volta para o Luma depois que a permissão foi liberada fora do app. */
    static void returnToApp(Context context) {
        pending = -1;
        Intent back = new Intent(context, MainActivity.class)
                .setAction(MainActivity.ACTION_RETURN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            context.startActivity(back);
        } catch (RuntimeException ignored) {
            // A pessoa volta manualmente.
        }
    }
}
