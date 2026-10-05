# Luma Boost não usa reflexão nem serialização. Activities, serviços e o JobService
# são mantidos automaticamente pelo R8 a partir do AndroidManifest.xml.

# Remove logs de depuração na versão de produção (mantém Log.e para falhas reais).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
