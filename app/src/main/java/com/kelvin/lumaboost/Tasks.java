package com.kelvin.lumaboost;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Executa trabalho pesado fora da thread principal e entrega o resultado na UI. */
final class Tasks {
    interface Job<T> {
        T run() throws Exception;
    }

    interface Done<T> {
        void done(T result, Exception error);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Tasks() {
    }

    static <T> void run(final Job<T> job, final Done<T> done) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                T result = null;
                Exception error = null;
                try {
                    result = job.run();
                } catch (Exception exception) {
                    android.util.Log.e("LumaBoost", "Tarefa em segundo plano falhou", exception);
                    error = exception;
                }
                final T finalResult = result;
                final Exception finalError = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        done.done(finalResult, finalError);
                    }
                });
            }
        });
    }

    static void main(Runnable runnable) {
        MAIN.post(runnable);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
