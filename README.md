# Luma Boost

App Android nativo, leve e sem dependências externas de interface.

## O que o app faz

- Monitora RAM disponível, RAM em uso, heap do próprio app e CPU do processo do app.
- Mostra armazenamento livre, tempo ativo do sistema e recomendações rápidas.
- Executa uma otimização segura: limpa caches temporários do Luma Boost e solicita coleta de memória local.
- Abre atalhos do sistema para aplicativos, armazenamento e bateria.
- Não mantém serviço persistente em segundo plano e não usa backup de dados.

## Observação técnica

Versões modernas do Android não permitem que um app comum force o encerramento de processos de outros apps para "liberar RAM". O Luma Boost segue o modelo seguro do sistema: mede a pressão de memória, reduz seus próprios recursos e leva o usuário para telas nativas onde mudanças reais podem ser feitas.

## Abrir no Android Studio

1. Abra o Android Studio.
2. Escolha **Open**.
3. Selecione esta pasta:

```text
/Users/kelvinmattos/Documents/APPS/LumaBoost
```

4. Aguarde o Gradle sincronizar e execute o app em um dispositivo ou emulador.

## Configuração

- `minSdk`: 23
- `targetSdk`: 36
- Linguagem: Java
- UI: Android Views nativas, sem Compose

## Compilar pelo terminal

```text
./gradlew :app:assembleDebug
```
