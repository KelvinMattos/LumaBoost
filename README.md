# Luma Boost

App Android nativo (Java, sem dependências, ~100 KB) que **otimiza o aparelho de verdade**: apaga o cache de todos os apps, fecha os apps que rodam escondidos, remove arquivos inúteis e desliga recursos que gastam processamento e bateria. Todo resultado é medido antes e depois.

## Primeira abertura: tutorial guiado

Na primeira vez, o app abre um tutorial que leva a pessoa por cada permissão:
1. **Assistente do Luma** (Acessibilidade), com o texto de consentimento. Ao ligar, o app volta sozinho.
2. **Uso dos apps**, **Seus arquivos** e **Ajustes do celular**: o assistente abre a tela certa, rola a lista até o Luma Boost e mostra um **círculo azul pulsando com "Toque aqui para permitir"** sobre a chave. A pessoa toca, e o app volta sozinho para o próximo passo. O assistente só aponta; nunca liga permissões por conta própria.
3. **Botão Otimizar na barra** (monitoramento ativo) e "Tudo pronto!", com a primeira otimização.

Funciona em telas divididas de tablets (Samsung One UI) e pode ser revisto em Ajustes → "Ver o tutorial de novo".

## Funções

| Função | Como funciona | Requer |
|---|---|---|
| **Otimizar agora** (um toque) | Limpa cache interno dos apps com mais de 30 MB, encerra apps em segundo plano, remove temporários e, no fim, abre o diálogo oficial de cache externo | Otimização profunda (acessibilidade) |
| Limpar cache de todos os apps | Toca em "Limpar cache" em Informações do app → Armazenamento, app por app. Nunca em "Limpar armazenamento" | Acessibilidade + Acesso ao uso |
| Encerrar apps em segundo plano | Toca em "Forçar parada" + OK. Pula apps já parados | Acessibilidade |
| Arquivos desnecessários | Miniaturas/caches, temporários e logs, lixeira de mídia, APKs, pastas vazias, downloads antigos, arquivos grandes, com seleção item a item | Acesso a todos os arquivos |
| Monitoramento ativo | Notificação fixa (saúde, RAM, disco) com botão **Otimizar** visível; otimização silenciosa a cada bloqueio de tela | Notificações |
| Apps | Ativos, mais pesados, sem uso há 30 dias; proteger, detalhes, desinstalar | Acesso ao uso |
| Ajustes | Tela 30 s, brilho automático, sons e vibração de toque, rotação, sincronização (todos restauráveis); com root também animações 0,5x e busca Bluetooth | Ajustes do celular / root |
| Modo root | `am force-stop`, `am kill-all`, `pm trim-caches`, compactação de memória (inclusive a cada bloqueio, sem abrir telas) e, manualmente, `sm fstrim` e `cmd package bg-dexopt-job` | Root (Magisk/KernelSU) |
| Extras | Bloco nas configurações rápidas, atalhos no ícone, limpeza diária enquanto carrega | — |

Proteções: launcher, teclado, telefone, SMS e serviços do sistema nunca são encerrados. Mensageiros, relógio, agenda, Play Store, Maps e Fotos ficam protegidos por padrão. Também ficam de fora apps com serviço em primeiro plano (música, navegação), apps que leem notificações (relógios e pulseiras), serviços de acessibilidade, administradores do aparelho, VPN e papel de parede animado.

## Limites do Android (o que não existe sem root)

- Desde o Android 6, nenhum app comum apaga o cache de outro diretamente. Por isso a limpeza usa a acessibilidade, ou o root.
- `killBackgroundProcesses` é ignorado para outros apps no Android 14 e em versões anteriores com patch de segurança recente (confirmado no Moto E22, patch de nov/2024). Por isso o encerramento usa "Forçar parada".
- Com a tela bloqueada a acessibilidade não opera telas. Sem root, a otimização do bloqueio é a silenciosa (cache excedente, temporários, processos). A otimização completa fica no botão da notificação. Com root, o bloqueio faz tudo.
- O Android desliga e religa serviços de acessibilidade em alguns eventos de pacote. O serviço guarda o trabalho em andamento e continua após religar.

## Resultados medidos no Moto E22 (Android 12, 4 GB)

Medições independentes via `adb` (`/proc/meminfo` e `df`):

| Teste | Antes | Depois |
|---|---|---|
| Limpar cache de todos os apps (66 apps) | 6,65 GB livres | **11,61 GB livres (+4,96 GB)** |
| Otimizar agora (9 apps encerrados) | 846 MB RAM | **1.673 MB RAM** |
| Encerrar 10 apps, com religação forçada do serviço no meio | 1,1 GB | 1,6 GB, sem nenhuma falha |
| Botão Otimizar da notificação | 854 MB | 1,5 GB (+635 MB) |

Dados de usuário verificados intactos após a limpeza de cache (Instagram 742 MB, WhatsApp Business 374 MB, Facebook 388 MB). Também testado no Android 16 (emulador, Configurações em Compose e em inglês).

### Galaxy Tab A7 (SM-T505, Android 12, One UI, 3 GB)

| Teste | Resultado |
|---|---|
| Tutorial guiado completo | 4 permissões liberadas com o círculo "Toque aqui", e o app voltou sozinho em cada uma |
| Limpar todos os apps (19 apps, tela dividida) | 21,512 → 21,612 GB livres (+103 MB); LogicLike: cache 0 B, dados 64,59 MB intactos |
| Fechar apps (One UI, botão "Forçar parada" na barra inferior) | 38 apps fechados |

### Fire TV Stick 4K Max (AFTKA, Fire OS 7.7 / Android 9, 1,7 GB)

| Teste | Antes | Depois |
|---|---|---|
| Otimizar agora pelo controle remoto (8 apps fechados em 27 s) | 385 MB RAM | **703 MB RAM (+318 MB)** |
| Otimizar com 7 apps escondidos | 327 MB RAM | 571 MB RAM (+244 MB) |

Particularidades do Fire TV (tratadas em `FireTv.java` e `ForceStopService`):

- As Configurações não listam serviços de acessibilidade de terceiros nem têm as telas de Acesso ao uso e Ajustes do sistema. A liberação é feita uma vez pelo computador; com `WRITE_SECURE_SETTINGS` o próprio Luma liga o assistente (e o religa se o app for forçado a parar):
  ```bash
  adb shell pm grant com.kelvin.lumaboost android.permission.WRITE_SECURE_SETTINGS
  adb shell appops set com.kelvin.lumaboost GET_USAGE_STATS allow
  adb shell appops set com.kelvin.lumaboost WRITE_SETTINGS allow
  ```
- As listas das Configurações da Amazon ignoram `ACTION_CLICK`: o assistente toca por gesto no centro do item, só depois que a posição fica parada (a página insere linhas ao abrir). "Forçar a interrupção" não tem confirmação.
- O cache sai de uma vez por "Limpar o cache de todos os aplicativos" (Configurações → Aplicativos), confirmado no diálogo da Amazon.
- Com o controle dos pais ligado, cada tela de app pede PIN: desligue-o para a otimização profunda.
- Serviços internos da Amazon (perfis, protetor de tela, Alexa) nunca entram na lista; Spotify e Amazon Music ficam protegidos.

## Compilar

```bash
./gradlew :app:assembleDebug        # APK de depuração
./gradlew :app:bundleRelease        # AAB assinado para a Play Store
./gradlew :app:assembleRelease      # APK assinado (R8 + shrink)
```

O release usa `keystore.properties` + `keystore/luma-upload.jks` (fora do git). Veja `PLAY_STORE.md` para publicar e `docs/privacy-policy.html` para a política de privacidade.

## Configuração

`minSdk` 23 · `targetSdk`/`compileSdk` 36 · Java 17 · Views nativas, sem bibliotecas.
