package com.kelvin.lumaboost;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Execução de comandos como root (Magisk/KernelSU/SuperSU).
 *
 * Só usa comandos oficiais do Android (am, pm, sm, cmd, /proc/sys/vm): nada de alterar
 * governador de CPU, build.prop ou partições do sistema.
 */
final class RootShell {
    static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        boolean ok() {
            return exitCode == 0;
        }
    }

    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/sbin/su",
            "/vendor/bin/su", "/data/adb/magisk", "/data/adb/ksu", "/debug_ramdisk/su"
    };

    private static volatile Boolean granted;

    private RootShell() {
    }

    /** Há um binário su no aparelho? Não pede permissão a ninguém. */
    static boolean isSuPresent() {
        for (String path : SU_PATHS) {
            if (new File(path).exists()) {
                return true;
            }
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(":")) {
                if (new File(dir, "su").exists()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Root já foi concedido nesta execução do app (sem abrir o pedido do gerenciador de root). */
    static boolean isGrantedCached() {
        return Boolean.TRUE.equals(granted);
    }

    /** Pede/confirma acesso root. Bloqueante: chame fora da thread principal. */
    static boolean requestAccess() {
        if (!isSuPresent()) {
            granted = false;
            return false;
        }
        Result result = run(15, "id");
        granted = result.ok() && result.output.contains("uid=0");
        return granted;
    }

    /** Root disponível e habilitado pelo usuário no app. Bloqueante na primeira chamada. */
    static boolean available(android.content.Context context) {
        if (!Prefs.rootEnabled(context)) {
            return false;
        }
        if (granted == null) {
            requestAccess();
        }
        return Boolean.TRUE.equals(granted);
    }

    /** Executa os comandos em um único shell root, com tempo limite em segundos. */
    static Result run(long timeoutSeconds, String... commands) {
        Process process = null;
        try {
            process = new ProcessBuilder("su").redirectErrorStream(true).start();
            OutputStream stdin = process.getOutputStream();
            for (String command : commands) {
                stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            }
            stdin.write("exit\n".getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            stdin.close();

            final Process running = process;
            final StringBuilder output = new StringBuilder();
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            synchronized (output) {
                                if (output.length() < 64 * 1024) {
                                    output.append(line).append('\n');
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // Processo encerrado.
                    }
                }
            }, "luma-root-reader");
            reader.start();

            boolean finished;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            } else {
                finished = waitLegacy(process, timeoutSeconds * 1000L);
            }
            if (!finished) {
                process.destroy();
                return new Result(-2, "tempo esgotado");
            }
            reader.join(1000L);
            synchronized (output) {
                return new Result(process.exitValue(), output.toString());
            }
        } catch (Exception exception) {
            if (process != null) {
                process.destroy();
            }
            return new Result(-1, String.valueOf(exception.getMessage()));
        }
    }

    private static boolean waitLegacy(Process process, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException stillRunning) {
                Thread.sleep(100L);
            }
        }
        return false;
    }

    /** Aspas simples seguras para nomes de pacote vindos do sistema. */
    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
