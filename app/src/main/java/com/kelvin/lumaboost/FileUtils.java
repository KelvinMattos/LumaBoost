package com.kelvin.lumaboost;

import java.io.File;

final class FileUtils {
    private FileUtils() {
    }

    static long deleteContents(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return 0L;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return 0L;
        }
        long freed = 0L;
        for (File child : children) {
            freed += deleteRecursively(child);
        }
        return freed;
    }

    /** Apaga arquivo ou pasta e retorna quantos bytes realmente saíram do disco. */
    static long deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        long freed = 0L;
        if (file.isDirectory() && !isSymlink(file)) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    freed += deleteRecursively(child);
                }
            }
            file.delete();
            return freed;
        }
        long length = file.length();
        return file.delete() ? length : 0L;
    }

    static long size(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (!file.isDirectory()) {
            return file.length();
        }
        if (isSymlink(file)) {
            return 0L;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return 0L;
        }
        long total = 0L;
        for (File child : children) {
            total += size(child);
        }
        return total;
    }

    static boolean isSymlink(File file) {
        try {
            File canonicalParent = file.getParentFile() == null ? null : file.getParentFile().getCanonicalFile();
            File candidate = canonicalParent == null ? file : new File(canonicalParent, file.getName());
            return !candidate.getCanonicalFile().equals(candidate.getAbsoluteFile());
        } catch (Exception exception) {
            return true;
        }
    }
}
