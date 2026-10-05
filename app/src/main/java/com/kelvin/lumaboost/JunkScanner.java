package com.kelvin.lumaboost;

import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Varre o armazenamento compartilhado em busca de arquivos que podem ser removidos com segurança. */
final class JunkScanner {
    static final String CACHE = "cache";
    static final String TEMP = "temp";
    static final String TRASH = "trash";
    static final String APK = "apk";
    static final String EMPTY = "empty";
    static final String APP_CACHE = "appcache";
    static final String OLD_DOWNLOADS = "downloads";
    static final String LARGE = "large";

    private static final long LARGE_FILE = 100L * 1024L * 1024L;
    private static final long OLD_DOWNLOAD_MS = 60L * AppCatalog.DAY_MS;
    private static final int MAX_DEPTH = 14;

    private static final Set<String> CACHE_DIRS = set(".thumbnails", ".thumbs", ".cache", "cache", ".tmp", "tmp", ".temp", "temp");
    private static final Set<String> TEMP_EXTENSIONS = set("tmp", "temp", "log", "part", "partial", "crdownload", "dmp", "chck");
    private static final Set<String> APK_EXTENSIONS = set("apk", "apks", "xapk", "apkm");
    private static final Set<String> STANDARD_DIRS = set("android", "dcim", "pictures", "music", "movies", "download",
            "documents", "alarms", "notifications", "podcasts", "ringtones", "audiobooks", "recordings");

    static final class Item {
        final File file;
        final long size;
        final boolean directory;
        boolean selected;

        Item(File file, long size, boolean directory, boolean selected) {
            this.file = file;
            this.size = size;
            this.directory = directory;
            this.selected = selected;
        }
    }

    static final class Category {
        final String id;
        final String title;
        final String description;
        final boolean safe;
        final List<Item> items = new ArrayList<>();

        Category(String id, String title, String description, boolean safe) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.safe = safe;
        }

        long size() {
            long total = 0L;
            for (Item item : items) {
                total += item.size;
            }
            return total;
        }

        long selectedSize() {
            long total = 0L;
            for (Item item : items) {
                if (item.selected) {
                    total += item.size;
                }
            }
            return total;
        }

        int selectedCount() {
            int count = 0;
            for (Item item : items) {
                if (item.selected) {
                    count++;
                }
            }
            return count;
        }

        void selectAll(boolean selected) {
            for (Item item : items) {
                item.selected = selected;
            }
        }
    }

    final AtomicInteger scannedFiles = new AtomicInteger();
    private volatile boolean cancelled;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private File root;

    JunkScanner() {
        add(new Category(CACHE, "Miniaturas e restos de apps", "Cópias pequenas de fotos e arquivos de apoio. Os apps refazem quando precisarem.", true));
        add(new Category(TEMP, "Arquivos temporários", "Downloads que não terminaram e anotações que os apps deixam para trás.", true));
        add(new Category(TRASH, "Lixeira de fotos e vídeos", "O que você já mandou para a lixeira e ainda está ocupando espaço.", true));
        add(new Category(APP_CACHE, "Restos de apps", "Arquivos temporários que os apps deixaram no armazenamento.", true));
        add(new Category(APK, "Instaladores de apps", "Arquivos usados para instalar apps. Depois de instalados, não servem mais.", true));
        add(new Category(EMPTY, "Pastas vazias", "Pastas que ficaram para trás, sem nada dentro.", true));
        add(new Category(OLD_DOWNLOADS, "Downloads antigos", "Coisas que você baixou há mais de 2 meses. Dê uma olhada antes de apagar.", false));
        add(new Category(LARGE, "Arquivos grandes", "Arquivos com mais de 100 MB. Veja um por um antes de apagar.", false));
    }

    private void add(Category category) {
        categories.put(category.id, category);
    }

    void cancel() {
        cancelled = true;
    }

    /** Executa a varredura (bloqueante). Retorna só as categorias com itens. */
    List<Category> scan() {
        root = Environment.getExternalStorageDirectory();
        walk(root);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            scanLegacyAppCaches();
        }
        List<Category> result = new ArrayList<>();
        for (Category category : categories.values()) {
            if (!category.items.isEmpty()) {
                Collections.sort(category.items, new Comparator<Item>() {
                    @Override
                    public int compare(Item a, Item b) {
                        return Long.compare(b.size, a.size);
                    }
                });
                result.add(category);
            }
        }
        return result;
    }

    private void walk(File start) {
        Deque<File> stack = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        stack.push(start);
        depths.push(0);
        long now = System.currentTimeMillis();
        File downloads = new File(root, Environment.DIRECTORY_DOWNLOADS);

        while (!stack.isEmpty() && !cancelled) {
            File dir = stack.pop();
            int depth = depths.pop();
            File[] children = dir.listFiles();
            if (children == null) {
                continue;
            }
            if (children.length == 0) {
                if (isRemovableEmptyDir(dir)) {
                    categories.get(EMPTY).items.add(new Item(dir, 0L, true, true));
                }
                continue;
            }
            for (File child : children) {
                if (cancelled) {
                    return;
                }
                String name = child.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (child.isDirectory()) {
                    if (FileUtils.isSymlink(child) || isRestrictedDir(child)) {
                        continue;
                    }
                    if (CACHE_DIRS.contains(lower) && !child.getParentFile().equals(root)) {
                        long size = FileUtils.size(child);
                        if (size > 0L) {
                            categories.get(CACHE).items.add(new Item(child, size, true, true));
                        }
                        continue;
                    }
                    if (depth < MAX_DEPTH) {
                        stack.push(child);
                        depths.push(depth + 1);
                    }
                    continue;
                }

                scannedFiles.incrementAndGet();
                long size = child.length();
                String extension = extension(lower);
                if (lower.startsWith(".trashed-")) {
                    categories.get(TRASH).items.add(new Item(child, size, false, true));
                } else if (lower.startsWith(".pending-")) {
                    continue;
                } else if (TEMP_EXTENSIONS.contains(extension) || lower.startsWith("thumbdata")) {
                    categories.get(TEMP).items.add(new Item(child, size, false, true));
                } else if (APK_EXTENSIONS.contains(extension)) {
                    categories.get(APK).items.add(new Item(child, size, false, true));
                } else if (size >= LARGE_FILE) {
                    categories.get(LARGE).items.add(new Item(child, size, false, false));
                } else if (isInside(child, downloads) && now - child.lastModified() > OLD_DOWNLOAD_MS) {
                    categories.get(OLD_DOWNLOADS).items.add(new Item(child, size, false, false));
                }
            }
        }
    }

    /** Android 10 ou anterior: cache que os apps gravam em Android/data/&lt;pacote&gt;/cache. */
    private void scanLegacyAppCaches() {
        File data = new File(root, "Android/data");
        File[] packages = data.listFiles();
        if (packages == null) {
            return;
        }
        for (File pkg : packages) {
            File cache = new File(pkg, "cache");
            long size = FileUtils.size(cache);
            if (size > 0L) {
                categories.get(APP_CACHE).items.add(new Item(cache, size, true, true));
            }
        }
    }

    private boolean isRestrictedDir(File dir) {
        File parent = dir.getParentFile();
        if (parent == null || !"Android".equals(parent.getName())) {
            return false;
        }
        String name = dir.getName();
        // Android/data e Android/obb pertencem aos apps (e são bloqueados no Android 11+).
        return "data".equals(name) || "obb".equals(name);
    }

    private boolean isRemovableEmptyDir(File dir) {
        if (dir.equals(root)) {
            return false;
        }
        String path = dir.getAbsolutePath();
        String rootPath = root.getAbsolutePath();
        if (path.startsWith(rootPath + "/Android")) {
            return false;
        }
        File parent = dir.getParentFile();
        return !(parent != null && parent.equals(root) && STANDARD_DIRS.contains(dir.getName().toLowerCase(Locale.ROOT)));
    }

    private static boolean isInside(File file, File dir) {
        return file.getAbsolutePath().startsWith(dir.getAbsolutePath() + "/");
    }

    private static String extension(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        return dot < 0 ? "" : lowerName.substring(dot + 1);
    }

    private static Set<String> set(String... values) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, values);
        return set;
    }

    /** Apaga os itens selecionados e retorna os bytes liberados de fato. */
    static long delete(List<Category> categories) {
        long freed = 0L;
        for (Category category : categories) {
            List<Item> remaining = new ArrayList<>();
            for (Item item : category.items) {
                if (!item.selected) {
                    remaining.add(item);
                    continue;
                }
                if (item.directory && item.size == 0L) {
                    item.file.delete();
                } else {
                    freed += FileUtils.deleteRecursively(item.file);
                }
                if (item.file.exists()) {
                    remaining.add(item);
                }
            }
            category.items.clear();
            category.items.addAll(remaining);
        }
        return freed;
    }

    /** Limpeza rápida usada pela otimização automática: só categorias 100% seguras. */
    static long quickClean() {
        JunkScanner scanner = new JunkScanner();
        List<Category> found = scanner.scan();
        List<Category> safe = new ArrayList<>();
        for (Category category : found) {
            if (CACHE.equals(category.id) || TEMP.equals(category.id) || APP_CACHE.equals(category.id)) {
                category.selectAll(true);
                safe.add(category);
            }
        }
        return delete(safe);
    }
}
