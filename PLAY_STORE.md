# Publicação na Google Play: Luma Boost

Arquivo para enviar: `app/build/outputs/bundle/release/app-release.aab`
(gerado com `./gradlew :app:bundleRelease`, assinado com a chave de upload em `keystore/`).

## 1. Antes de tudo

1. **Guarde a chave de upload**: copie `keystore/luma-upload.jks` e `keystore.properties` (tem a senha) para um lugar seguro fora do computador (cofre de senhas, drive privado). Esses arquivos estão no `.gitignore` e nunca devem ir para o git. Ative o **Play App Signing** (padrão): se a chave de upload for perdida, o Google permite trocá-la.
2. **Hospede a política de privacidade**: `docs/privacy-policy.html`. Antes, troque `SEU-EMAIL@exemplo.com` pelo e-mail de suporte. Pode usar GitHub Pages (pasta `docs/`) ou qualquer site. Cole a URL no Play Console.
3. Conta de desenvolvedor: contas pessoais novas precisam de **teste fechado com 12 testadores por 14 dias** antes da produção.

## 2. Ficha da loja

**Nome do app** (até 30): `Luma Boost: Limpeza e RAM`

**Descrição curta** (até 80):
`Limpa cache de todos os apps, libera RAM e otimiza o celular com resultado medido.`

**Descrição completa**:

```
O Luma Boost otimiza o seu Android de verdade e mostra o resultado medido antes e depois. Sem números inventados.

OTIMIZAÇÃO EM UM TOQUE
• Apaga o cache interno de todos os apps pela tela oficial do Android. Logins, conversas e fotos ficam intactos.
• Fecha de verdade os apps que rodam escondidos ("Forçar parada"): saem da memória e só voltam quando você abri-los.
• Remove miniaturas, arquivos temporários e logs esquecidos.
• Relatório com RAM e espaço livre antes e depois.

PROTEÇÕES INTELIGENTES
• Mensageiros, alarmes, agenda, teclado, apps tocando música e relógios ou pulseiras conectados nunca são encerrados.
• Você pode proteger qualquer app manualmente.
• Nunca toca em "Limpar armazenamento": seus dados ficam seguros.

LIMPEZA PROFUNDA
• Encontra instaladores APK, pastas vazias, lixeira de mídia, downloads antigos e arquivos grandes.
• Você revisa item por item antes de apagar.

TUTORIAL GUIADO
• Na primeira abertura, o Luma mostra exatamente onde tocar em cada permissão, com um círculo azul na tela, e volta sozinho para o próximo passo.

MONITORAMENTO ATIVO
• Notificação fixa com RAM livre e o botão Otimizar sempre à mão.
• Otimização automática a cada bloqueio de tela.
• Bloco nas configurações rápidas e limpeza diária enquanto carrega.

APPS
• Veja os apps ativos, os mais pesados e os que você não usa há 30 dias, e desinstale com um toque.

AJUSTES DE DESEMPENHO E BATERIA
• Tempo de tela, brilho automático, sons e vibração ao toque, rotação e sincronização (com root, também animações mais rápidas).
• Tudo pode ser restaurado com um toque.

MODO ROOT (OPCIONAL)
• Em aparelhos com Magisk ou KernelSU: encerra apps e limpa todo o cache sem abrir telas, inclusive a cada bloqueio, além de TRIM do armazenamento e compilação dos apps com o ART. Usa só comandos oficiais do Android.

PRIVACIDADE
• Sem anúncios, sem conta, sem internet. Nada sai do seu aparelho.
• O app tem cerca de 100 KB.

O Luma Boost usa a API de Acessibilidade apenas para tocar em "Forçar parada" e "Limpar cache" quando você pede. O Android não permite que um app faça isso de outra forma. O serviço não lê nem coleta o conteúdo da tela.
```

**Categoria**: Ferramentas · **Tags**: limpeza, desempenho, armazenamento.

**Recursos gráficos** (pasta `store/`):
- Ícone: `icon-512.png` (512×512)
- Recurso gráfico: `feature-graphic-1024x500.png`
- Capturas de telefone: `screenshot-1-inicio.png` … `screenshot-6-notificacao.png` (1080×2400)

## 3. Declarações obrigatórias no Play Console (Conteúdo do app)

### Acessibilidade (Política de uso da API AccessibilityService)
- O app **não** é ferramenta de acessibilidade (`isAccessibilityTool=false` no manifesto).
- Divulgação em destaque: no tutorial de primeira abertura, a página "Ligue o assistente do Luma" explica o uso e exige tocar em "Concordo, quero ativar". Fora do tutorial, o diálogo "Ativar o assistente do Luma" exige "Concordo, ativar".
- Guia de permissões: com o assistente ligado, o app desenha um destaque "Toque aqui" (sobreposição de acessibilidade que não bloqueia toques) sobre a chave da permissão e rola a lista até o app. Quem toca é sempre o usuário: o serviço nunca concede permissões sozinho.
- Texto sugerido para o formulário:
  > O Luma Boost usa o AccessibilityService exclusivamente quando o usuário toca em Otimizar, Encerrar ou Limpar cache. Para cada app escolhido, o serviço abre a tela oficial de Informações do app e toca nos botões "Forçar parada" ou "Limpar cache". O Android não oferece API pública que permita a um app encerrar outros apps ou apagar o cache interno deles. O serviço não lê, não coleta, não armazena e não transmite conteúdo da tela, nunca toca em "Limpar armazenamento" e não age sem uma ação explícita do usuário.
- **Vídeo exigido**: grave a tela mostrando: primeira abertura → "Vamos lá" → página do assistente com o texto de consentimento → "Concordo, quero ativar" → ativação nas configurações → volta automática → passos guiados com o círculo "Toque aqui" → "Fazer minha primeira otimização" com a sobreposição "Fechando apps escondidos…" → relatório.

### Acesso a todos os arquivos (MANAGE_EXTERNAL_STORAGE)
- Uso declarado: **gerenciamento e limpeza de arquivos** (encontrar e apagar temporários, APKs, pastas vazias, lixeira e arquivos grandes, com revisão item por item) e acesso ao diálogo do sistema `ACTION_CLEAR_APP_CACHE`, que exige essa permissão.
- **Risco**: o Google aprova essa permissão com critério. Se for recusada, remova a linha `MANAGE_EXTERNAL_STORAGE` do `AndroidManifest.xml` e publique de novo. O app continua funcionando: a limpeza de cache e o encerramento de apps usam a acessibilidade. Só a busca de arquivos desnecessários fica indisponível.

### Serviço em primeiro plano (FOREGROUND_SERVICE_SPECIAL_USE)
- Tipo `specialUse`. Justificativa sugerida:
  > Monitoramento de desempenho ativado pelo usuário: mantém uma notificação com a RAM livre e o botão Otimizar e otimiza o aparelho a cada bloqueio de tela. Precisa de um receptor de "tela desligada" ativo, o que só é possível com o serviço em execução. O usuário desliga na própria notificação ou nas configurações do app.
- Vídeo curto mostrando o interruptor "Monitoramento ativo", a notificação e o botão "Desativar".

### Segurança dos dados (Data safety)
- Coleta dados? **Não**. Compartilha dados? **Não**.
- O app não declara a permissão INTERNET, então nada pode ser enviado.

### Outros
- Classificação de conteúdo: questionário → sem conteúdo sensível (Livre).
- Público-alvo: 18+ (evita requisitos de apps para crianças).
- Anúncios: não contém.
- `WRITE_SECURE_SETTINGS`: é permitido declará-la. Só é concedida em aparelhos com root, quando o usuário ativa o modo root.
- `REQUEST_DELETE_PACKAGES`: usada para o botão Desinstalar (o sistema confirma).

## 4. Atualizações futuras
Aumente `versionCode` (e `versionName`) em `app/build.gradle` a cada envio e rode `./gradlew :app:bundleRelease`.
