package com.kelvin.lumaboost;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Ajustes reais do sistema que reduzem consumo de CPU, GPU, rádio e bateria.
 * Todo valor alterado é guardado para que "Restaurar" volte exatamente ao estado anterior.
 */
final class SystemTweaks {
    static final int NEEDS_NOTHING = 0;
    static final int NEEDS_WRITE_SETTINGS = 1;
    static final int NEEDS_SECURE_SETTINGS = 2;

    abstract static class Tweak {
        final String id;
        final String title;
        final String description;
        final int requirement;
        final boolean recommended;

        Tweak(String id, String title, String description, int requirement, boolean recommended) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.requirement = requirement;
            this.recommended = recommended;
        }

        boolean available(Context context) {
            switch (requirement) {
                case NEEDS_WRITE_SETTINGS:
                    return Permissions.canWriteSettings(context);
                case NEEDS_SECURE_SETTINGS:
                    return Permissions.hasSecureSettings(context);
                default:
                    return true;
            }
        }

        abstract boolean isOptimized(Context context);

        abstract void optimize(Context context);

        abstract void restore(Context context);

        void set(Context context, boolean optimized) {
            if (optimized) {
                optimize(context);
            } else {
                restore(context);
            }
        }
    }

    private SystemTweaks() {
    }

    static List<Tweak> performance() {
        List<Tweak> list = new ArrayList<>();
        list.add(new AnimationTweak());
        list.add(new GlobalIntTweak("ble_scan", "ble_scan_always_enabled", 0, 1,
                "Bluetooth procurando sem parar",
                "Mesmo com o Bluetooth desligado, o aparelho fica procurando aparelhos por perto. Isso para.", true));
        return list;
    }

    static List<Tweak> resources(Context context) {
        List<Tweak> list = new ArrayList<>();
        if (FireTv.is(context)) {
            // Na TV não há bateria, sensor de luz, vibração nem rotação; e apagar a tela em 30 s faria a TV
            // dormir no meio do uso. Só a sincronização faz sentido.
            list.add(new SyncTweak());
            return list;
        }
        list.add(new SystemIntTweak("timeout", Settings.System.SCREEN_OFF_TIMEOUT, 30000, 60000,
                "Apagar a tela em 30 segundos",
                "A tela é o que mais gasta bateria. Ficar acesa sem você olhar é desperdício.", true) {
            @Override
            boolean isOptimized(Context context) {
                return Settings.System.getInt(context.getContentResolver(), key, 60000) <= 30000;
            }
        });
        list.add(new SystemIntTweak("brightness", Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                "Brilho automático", "O brilho se ajusta ao ambiente, sem ficar forte à toa.", true));
        list.add(new SystemIntTweak("sounds", Settings.System.SOUND_EFFECTS_ENABLED, 0, 1,
                "Sem som ao tocar na tela", "Menos barulho e um pouco menos de bateria.", true));
        list.add(new SystemIntTweak("haptic", Settings.System.HAPTIC_FEEDBACK_ENABLED, 0, 1,
                "Sem vibração ao tocar", "Cada vibradinha gasta um pouco de bateria.", true));
        list.add(new SystemIntTweak("rotation", Settings.System.ACCELEROMETER_ROTATION, 0, 1,
                "Tela sem girar sozinha", "O celular para de vigiar a posição o tempo todo.", false));
        list.add(new SyncTweak());
        return list;
    }

    static List<Tweak> all(Context context) {
        List<Tweak> list = performance();
        list.addAll(resources(context));
        return list;
    }

    /** Aplica todos os ajustes recomendados permitidos. Retorna quantos foram alterados. */
    static int applyRecommended(Context context) {
        int changed = 0;
        for (Tweak tweak : all(context)) {
            if (tweak.recommended && tweak.available(context) && !tweak.isOptimized(context)) {
                try {
                    tweak.optimize(context);
                    changed++;
                } catch (RuntimeException ignored) {
                    // Configuração bloqueada pelo fabricante.
                }
            }
        }
        return changed;
    }

    static int restoreAll(Context context) {
        int restored = 0;
        for (Tweak tweak : all(context)) {
            if (tweak.available(context) && Prefs.original(context, tweak.id) != null) {
                try {
                    tweak.restore(context);
                    restored++;
                } catch (RuntimeException ignored) {
                    // Configuração bloqueada pelo fabricante.
                }
            }
        }
        return restored;
    }

    private static final class AnimationTweak extends Tweak {
        private static final String[] KEYS = {
                Settings.Global.WINDOW_ANIMATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.ANIMATOR_DURATION_SCALE
        };

        AnimationTweak() {
            super("animations", "Animações mais rápidas",
                    "Os apps abrem e trocam de tela na metade do tempo. Dá a sensação de um aparelho novo.",
                    NEEDS_SECURE_SETTINGS, true);
        }

        @Override
        boolean isOptimized(Context context) {
            ContentResolver resolver = context.getContentResolver();
            for (String key : KEYS) {
                if (Settings.Global.getFloat(resolver, key, 1.0f) > 0.5f) {
                    return false;
                }
            }
            return true;
        }

        @Override
        void optimize(Context context) {
            ContentResolver resolver = context.getContentResolver();
            StringBuilder original = new StringBuilder();
            for (String key : KEYS) {
                original.append(Settings.Global.getFloat(resolver, key, 1.0f)).append(';');
            }
            Prefs.saveOriginal(context, id, original.toString());
            for (String key : KEYS) {
                Settings.Global.putFloat(resolver, key, 0.5f);
            }
        }

        @Override
        void restore(Context context) {
            ContentResolver resolver = context.getContentResolver();
            String original = Prefs.original(context, id);
            String[] values = original == null ? new String[0] : original.split(";");
            for (int i = 0; i < KEYS.length; i++) {
                float value = 1.0f;
                if (i < values.length) {
                    try {
                        value = Float.parseFloat(values[i]);
                    } catch (NumberFormatException ignored) {
                        value = 1.0f;
                    }
                }
                if (value <= 0.5f) {
                    value = 1.0f;
                }
                Settings.Global.putFloat(resolver, KEYS[i], value);
            }
            Prefs.clearOriginal(context, id);
        }
    }

    private static class GlobalIntTweak extends Tweak {
        final String key;
        final int optimizedValue;
        final int defaultValue;

        GlobalIntTweak(String id, String key, int optimizedValue, int defaultValue, String title, String description, boolean recommended) {
            super(id, title, description, NEEDS_SECURE_SETTINGS, recommended);
            this.key = key;
            this.optimizedValue = optimizedValue;
            this.defaultValue = defaultValue;
        }

        @Override
        boolean isOptimized(Context context) {
            return Settings.Global.getInt(context.getContentResolver(), key, defaultValue) == optimizedValue;
        }

        @Override
        void optimize(Context context) {
            ContentResolver resolver = context.getContentResolver();
            Prefs.saveOriginal(context, id, String.valueOf(Settings.Global.getInt(resolver, key, defaultValue)));
            Settings.Global.putInt(resolver, key, optimizedValue);
        }

        @Override
        void restore(Context context) {
            int value = parse(Prefs.original(context, id), defaultValue);
            if (value == optimizedValue) {
                value = defaultValue;
            }
            Settings.Global.putInt(context.getContentResolver(), key, value);
            Prefs.clearOriginal(context, id);
        }
    }

    private static class SystemIntTweak extends Tweak {
        final String key;
        final int optimizedValue;
        final int defaultValue;

        SystemIntTweak(String id, String key, int optimizedValue, int defaultValue, String title, String description, boolean recommended) {
            super(id, title, description, NEEDS_WRITE_SETTINGS, recommended);
            this.key = key;
            this.optimizedValue = optimizedValue;
            this.defaultValue = defaultValue;
        }

        @Override
        boolean isOptimized(Context context) {
            return Settings.System.getInt(context.getContentResolver(), key, defaultValue) == optimizedValue;
        }

        @Override
        void optimize(Context context) {
            ContentResolver resolver = context.getContentResolver();
            Prefs.saveOriginal(context, id, String.valueOf(Settings.System.getInt(resolver, key, defaultValue)));
            Settings.System.putInt(resolver, key, optimizedValue);
        }

        @Override
        void restore(Context context) {
            int value = parse(Prefs.original(context, id), defaultValue);
            if (value == optimizedValue) {
                value = defaultValue;
            }
            Settings.System.putInt(context.getContentResolver(), key, value);
            Prefs.clearOriginal(context, id);
        }
    }

    private static final class SyncTweak extends Tweak {
        SyncTweak() {
            super("sync", "Pausar a sincronização das contas",
                    "As contas param de atualizar sozinhas. E-mails e backups só chegam quando você abrir o app.",
                    NEEDS_NOTHING, false);
        }

        @Override
        boolean isOptimized(Context context) {
            return !ContentResolver.getMasterSyncAutomatically();
        }

        @Override
        void optimize(Context context) {
            Prefs.saveOriginal(context, id, "1");
            ContentResolver.setMasterSyncAutomatically(false);
        }

        @Override
        void restore(Context context) {
            ContentResolver.setMasterSyncAutomatically(true);
            Prefs.clearOriginal(context, id);
        }
    }

    private static int parse(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
