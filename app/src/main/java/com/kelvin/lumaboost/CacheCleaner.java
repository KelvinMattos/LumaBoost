package com.kelvin.lumaboost;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Limpeza de cache: do próprio Luma, cache excedente do sistema e cache de todos os apps. */
final class CacheCleaner {
    private CacheCleaner() {
    }

    static long cleanOwnCaches(Context context) {
        long freed = FileUtils.deleteContents(context.getCacheDir());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            freed += FileUtils.deleteContents(context.getCodeCacheDir());
        }
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File[] externalCaches = context.getExternalCacheDirs();
            if (externalCaches != null) {
                for (File cache : externalCaches) {
                    freed += FileUtils.deleteContents(cache);
                }
            }
        }
        return freed;
    }

    /**
     * Pede ao Android para reservar todo o espaço alocável. Para atender o pedido, o sistema apaga
     * os arquivos de cache dos outros apps (começando pelos que mais excedem a cota). Nada é gravado:
     * a reserva é só uma garantia de espaço livre.
     */
    static long trimSystemCache(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return 0L;
        }
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        File dataDir = context.getFilesDir();
        long before = dataDir.getUsableSpace();
        try {
            UUID uuid = storageManager.getUuidForPath(dataDir);
            long allocatable = storageManager.getAllocatableBytes(uuid);
            if (allocatable > before) {
                storageManager.allocateBytes(uuid, allocatable);
            }
        } catch (IOException | RuntimeException ignored) {
            // O sistema recusou liberar mais; mede o que já foi limpo.
        }
        return Math.max(0L, dataDir.getUsableSpace() - before);
    }

    /** Diálogo oficial do Android 11+ que limpa o cache de todos os apps de uma vez. */
    static boolean canClearAllAppsCache(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Permissions.hasAllFilesAccess(context);
    }

    static Intent clearAllAppsCacheIntent() {
        return new Intent(StorageManager.ACTION_CLEAR_APP_CACHE);
    }
}
