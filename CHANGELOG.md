# Changelog

Uma entrada por passo de trabalho, com data. Correções pequenas entram como sub-itens do passo.

## Não lançado

### 2026-11-14 — Ícone monocromático próprio
- **O que estava errado:** o ícone adaptativo usava o foreground também como camada `monochrome`, então o verso da aba (branco a 30%) aparecia no ícone temático do Android e nos icon packs como uma mancha lavada. Quebrava a regra 6 do ícone da família tvnclr.
- **O que mudou:** `drawable/ic_launcher_monochrome.xml` novo, igual ao foreground sem o caminho a 30% (contorno, curva da aba e traços de impacto, todos sólidos). `ic_launcher.xml` e `ic_launcher_round.xml` apontam para ele.
- **Arquivos:** `res/drawable/ic_launcher_monochrome.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/mipmap-anydpi-v26/ic_launcher_round.xml`, `CHANGELOG.md`.
- **Conferido:** `assembleDebug` numa cópia temporária; no APK, `aapt2 dump` mostra a camada `monochrome` apontando para `ic_launcher_monochrome` e o drawable sem nenhum preenchimento a 30%. Não foi instalado em aparelho.
- **Pendente:** o `tvnclr-ui` corrigido (texto legível sobre o destaque Violet/Red no escuro) ainda não está aqui: o submódulo segue em `v1.0.0` até sair a `v1.0.3`.

### 2026-11-14 — Remoção de fundo: causa achada e corrigida
- **O que estava errado:**
  - **IA (ML Kit e U²-Net):** o recorte nunca chegava à imagem. O modelo acertava a máscara, mas `GifAi.apply` a aplicava desenhando um bitmap `ALPHA_8` com `DST_IN`, e isso deixava o quadro intacto. Afetava foto e GIF, em qualquer aparelho.
  - **Cor chapada no POCO X7 Pro (arm64, Android 17):** funcionava na primeira vez e parava depois que o Android compilava o código. Animada de GIF e "Em toda a imagem" saíam sem remover nada. No emulador x86 e no build debug estava certo. O defeito ficava no método grande de `BgRemover` que convertia a máscara entre `boolean[]` e `byte[]`.
- **O que mudou:**
  - `MaskOps.applyAlpha`: a máscara é aplicada nos pixels, com escala bilinear, em vez do desenho com `DST_IN`.
  - `BgRemover` reescrito com uma máscara `boolean[]` do começo ao fim, cada etapa num método pequeno e o núcleo em `solidPixels`, sobre arrays, que dá pra testar na JVM.
  - Imagem com borda quase toda transparente (PNG já recortado) não perde mais o que encosta na borda.
  - `BenchActivity`: bancada escondida que roda uma pasta de imagens por todos os métodos e grava resultado, tempo e autoteste (`diag.txt`).
  - Logs `Slap/bg` em `Log.i`, porque ROMs com `persist.log.tag=I` escondem `Log.d`.
  - `BgRemoverTest`: 10 testes novos.
  - Documentação: `AGENTS.md`, `CLAUDE.md` com `@AGENTS.md`, arquivos locais fora do git.
- **Arquivos:**
  - `engine/BgRemover.java`
  - `engine/MaskOps.java`
  - `engine/GifAi.java`
  - `engine/BenchActivity.java`
  - `ui/EditorController.kt`
  - `AndroidManifest.xml`
  - `test/.../BgRemoverTest.java`
  - `.gitignore`, `AGENTS.md`, `CLAUDE.md`, `CHANGELOG.md`
- **Conferido no POCO X7 Pro**, com a bancada e 13 imagens de teste (emoji, meme, desenho, objeto, 4 fotos reais e 3 GIFs):
  - autoteste sem nenhuma falha;
  - Cor chapada igual ao emulador em todas as imagens;
  - GIF animado salvo pelo editor sai com transparência.
- **ML Kit × U²-Net:** recorte praticamente igual nas fotos e nos GIFs. O ML Kit é cerca de 2× mais rápido por foto (0,7 s contra 1,3 s) e cerca de 4× mais rápido nos GIFs (6,5 s contra 25 s no GIF de 24 quadros). No meme, o ML Kit recortou só a pessoa e o U²-Net manteve o bloco de texto junto. O ML Kit continua como padrão em `dev`/`main`, e o U²-Net fica pro F-Droid e pra quem não tem Google Play.
- **Pendente:** a remoção de fundo em figurinha de vídeo continua fora (decisão anterior).

### 2026-11-11 — Motores de recorte por IA
- U²-Net (u2netp em LiteRT 1.4.2) ao lado do ML Kit, com escolha nas Configurações. O ramo `fdroid` fica só com o U²-Net.

### 2026-11-11 — Arquivos e compartilhamento
- Arquivo aberto pelo app Arquivos não dá mais "Permission Denial" (URI em base64url na rota).
- Compartilhamento sem permissão de leitura mostra explicação em vez de fechar o app.

## 1.1.0
- Texto em tela cheia com várias caixas e 16 fontes livres, reedição de figurinhas, exportação pro Telegram (estáticas), backup em .zip, seletor que separa vídeos/GIFs de fotos.

## 1.0.0
- Primeira versão: reescrita em Compose sobre o design system tvnclr-ui.