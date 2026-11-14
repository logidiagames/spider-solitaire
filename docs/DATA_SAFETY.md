# Data safety (Play Console) — resumo para preencher

Resumo do que a app FreeCell (`com.nexusplaylabss.freecell`, v0.4.4) recolhe, com base no código e no manifesto final da build de release. Não é aconselhamento jurídico: confirma cada ponto com as páginas oficiais da Google antes de submeter:

- Google Mobile Ads (AdMob): https://developers.google.com/admob/android/privacy/play-data-disclosure
- Firebase Analytics e Crashlytics: https://firebase.google.com/docs/android/play-data-disclosure

## O que a app NÃO recolhe

- Nome, email, telefone, morada ou qualquer conta: a app não tem login nem formulários.
- Localização precisa, contactos, fotos, ficheiros, áudio, calendário, mensagens.
- Dados de pagamento: a compra opcional "Remover anúncios" (v1.1) é processada pelo Google Play, que trata os dados de pagamento; a app nunca os vê.
- O progresso do jogo, estatísticas e definições ficam **só no telemóvel** (SharedPreferences) e não são enviados para nenhum servidor.

## O que é recolhido (pelos SDKs da Google)

| Tipo de dados (Play) | Quem recolhe | Para quê | Partilhado com terceiros? |
| --- | --- | --- | --- |
| **IDs do dispositivo ou outros IDs** — ID de publicidade Android (permissão `AD_ID`), ID de instância da app do Firebase | AdMob, Firebase Analytics | Publicidade, análise, prevenção de fraude | Sim, com a Google para anúncios (AdMob) |
| **Atividade na app — interações** — eventos do jogo (lista abaixo) | Firebase Analytics | Análise | Não (só o próprio Firebase do projeto) |
| **Localização aproximada** — derivada do endereço IP | AdMob, Firebase Analytics | Publicidade, análise | Sim (AdMob) |
| **Informações e desempenho da app — diagnósticos** | AdMob | Publicidade, prevenção de fraude | Confirmar na página do AdMob |
| **Informações e desempenho da app — registos de falhas e diagnósticos** (v1.3) | Firebase Crashlytics | Análise (estabilidade da app) | Não (só o próprio Firebase do projeto) |

Notas para as perguntas gerais:

- **Encriptados em trânsito:** sim (os SDKs da Google usam HTTPS).
- **O utilizador pode pedir a eliminação:** a app não tem contas; os dados são pseudónimos (IDs). O contacto de privacidade é `nexusplaylabss@gmail.com`, indicado na política.
- **Recolha opcional:** a publicidade personalizada depende do consentimento no formulário da Google (UMP) nas regiões onde é obrigatório; o jogador pode mudar a escolha em Definições → Política de Privacidade → Opções de privacidade.
- **Público-alvo:** a política de privacidade diz que a app não se destina a menores de 13 anos; mantém a mesma resposta na secção Público-alvo e conteúdo.
- **Política de privacidade:** https://sites.google.com/view/freecellsolitaireprivacypolicy

## Permissões no manifesto final (release)

`INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE` (pedidas pela app) e, acrescentadas pelos SDKs: `com.google.android.gms.permission.AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `BIND_GET_INSTALL_REFERRER_SERVICE`. Como a app usa o ID de publicidade, responde **Sim** à pergunta "A app usa o ID de publicidade?" na Play Console (finalidade: publicidade e análise).

## Eventos de Analytics enviados pela app

Nenhum evento leva identidade das cartas, texto livre, emails ou outros dados pessoais. Detalhe completo em `docs/ANALYTICS_EVENTS.md`.

- Arranque e tutorial: `app_ready`, `tutorial_open`, `tutorial_start`, `tutorial_step`, `tutorial_skip`, `tutorial_complete`
- Jogo e progressão: `game_start`, `game_win`, `game_abandon`, `hint_used`, `undo_used`, `level_up`
- Menus e personalização: `menu_open`, `setting_changed`, `language_changed`, `theme_selected`, `daily_challenge_open`, `daily_challenge_start`, `daily_challenge_complete`
- Publicidade: `ad_request`, `ad_loaded`, `ad_shown`, `ad_dismissed`, `ad_failed`

Parâmetros: modo de jogo, nível, movimentos, duração, pontuação, passo do tutorial, nome da definição/tema/idioma, formato e local do anúncio, número da sessão, jogos jogados, código de erro do anúncio.

O Firebase e o AdMob recolhem também eventos automáticos (`first_open`, `session_start`, `user_engagement`, `screen_view`, `ad_impression`, `ad_click`).

## O que muda com a v1.1 (moedas, bónus, missões, Remover anúncios, avaliação)

- **Compras na app:** a app passa a vender um produto único, `remove_ads` (não consumível), pelo **Google Play Billing**. O pagamento é todo feito pelo Google Play; a app não vê nem guarda dados de cartão, nome ou morada. A app só recebe do Google Play se o produto está comprado (e o recibo, que confirma a compra e nunca é enviado para fora do telemóvel). No formulário Data safety, em **Informações financeiras → Histórico de compras**, confirma na página da Google se o Play Billing exige declaração; a app em si não envia histórico de compras para nenhum servidor.
- **Avaliação:** o botão "Avalia-nos" abre a página do FreeCell na Play Store; a avaliação é feita na Play Store e a app não a recebe.
- **Formulário de queixas:** o texto escrito pelo jogador é passado para a app de email do próprio jogador, já dirigido a `nexusplaylabss@gmail.com`; só é enviado se o jogador carregar em enviar no email. A app não envia esse texto para nenhum servidor nem para o Analytics.
- **Moedas, bónus diário, missões e histórico do Desafio diário** ficam só no telemóvel (como o resto do progresso).
- **Novos eventos de Analytics** (sem dados pessoais): `coins_earned`, `theme_unlocked`, `no_moves_shown`, `daily_bonus_claimed`, `mission_claimed`, `missions_all_claimed`, `remove_ads_shown`, `remove_ads_purchase_started`, `remove_ads_purchase_completed`, `remove_ads_purchase_failed`, `rating_prompt_shown`, `rating_choice`. Os parâmetros novos são só `value` (moedas, preço em moedas, ou um estado como `restored`) e `source`/`step`.
- **Sem permissões novas:** a biblioteca do Play Billing acrescenta sozinha a permissão `com.android.vending.BILLING` ao manifesto; não há outras.

### Texto a acrescentar à política de privacidade

1. Compras: a app oferece uma compra opcional, "Remover anúncios", processada pelo Google Play. Os dados de pagamento são tratados pela Google segundo a política de privacidade do Google Play; a app só sabe se a compra foi feita, para deixar de mostrar anúncios.
2. Avaliações e opiniões: a app pode convidar-te a avaliar o FreeCell na Play Store (a avaliação é tratada pela Google) ou a escrever-nos uma opinião, que segue pela tua app de email para `nexusplaylabss@gmail.com` e é usada só para responder e melhorar o jogo.
3. Analytics: a lista de eventos passa a incluir moedas ganhas, temas desbloqueados, bónus e missões recolhidos, e as interações com o ecrã Remover anúncios e o pedido de avaliação. Continuam sem dados pessoais.

## O que muda com a v1.2 B1 (vídeo com recompensa)

- **Anúncios com recompensa:** o jogador pode, se quiser, ver um vídeo do AdMob para duplicar as moedas de uma vitória ou do bónus diário. Usa o mesmo SDK do AdMob e o mesmo consentimento (UMP); não há dados novos recolhidos pela app.
- **Quem comprou "Remover anúncios"** continua a poder ver estes vídeos, só quando os escolhe. Por isso, nesses telemóveis o formulário de consentimento e o SDK de anúncios continuam a correr.
- **Novos eventos de Analytics:** `rewarded_offered`, `rewarded_not_available`, `rewarded_started`, `rewarded_completed` (parâmetros `placement` e `value`, sem dados pessoais).
- **Texto a acrescentar à política de privacidade:** "A app pode oferecer vídeos publicitários opcionais em troca de moedas do jogo. Estes vídeos são servidos pelo Google AdMob, com o mesmo consentimento dos outros anúncios, e continuam disponíveis depois de comprar 'Remover anúncios', mas só quando o jogador os escolhe."

## O que muda com a v1.2 B2 (partilha)

- **Partilha:** o jogador pode partilhar, pela folha de partilha do Android, o número de um jogo ("Desafiar") ou o resultado do Desafio diário (uma imagem PNG feita no telemóvel, ou só texto). A app abre a folha de partilha e é o jogador que escolhe a app de destino. Não há servidor: nada é enviado pela app.
- **Sem dados pessoais:** o texto e a imagem têm só o número do jogo, a data do Diário, o tempo, os movimentos, a sequência e o link da Play Store.
- **Sem permissões novas:** a imagem fica na cache da app e é partilhada por um FileProvider só de leitura (sem permissão de armazenamento).
- **Novos eventos de Analytics:** `share_challenge` (`game_mode`) e `share_daily` (`source`: `image` ou `text`).

## O que muda com a v1.2 B3 (lembretes)

- **Notificações locais:** um lembrete por dia do Desafio diário, feito no telemóvel pelo WorkManager. Não há servidor nem notificações push; nada sai do telemóvel.
- **Nova permissão:** `POST_NOTIFICATIONS` (Android 13+). O jogo só a pede depois de uma vitória no Diário e de o jogador dizer "Sim" no cartão do jogo. Na Play Console não é preciso declaração especial; a lista de permissões da ficha passa a incluir "mostrar notificações".
- **Novos eventos de Analytics:** `reminder_prompt_shown`, `reminder_choice`, `reminder_opened` (parâmetro `source`, sem dados pessoais).
- **Texto a acrescentar à política de privacidade:** "Se o ativares, o jogo mostra um lembrete local por dia sobre o Desafio diário. É criado no teu telemóvel, não usa nenhum servidor e podes desligá-lo nas Definições do jogo ou do Android."

### Declaração de compras na Play Console

- Produto: `remove_ads`, tipo **produto na app** (não consumível), ativo, com o preço de **5,99 USD** (a Play Console converte para as outras moedas).
- Na ficha da loja e no questionário de classificação de conteúdo, indica que a app **contém compras na app**.

## O que muda com a v1.3 Parte 0 (Crashlytics)

- **Relatórios de falhas:** quando a app fecha por um erro, o Firebase Crashlytics envia o registo técnico do erro (onde falhou no código), o modelo do telemóvel, a versão do Android, a memória e o espaço livres, a orientação do ecrã e um ID de instalação do Crashlytics (não é o ID de publicidade).
- **Erros do jogo (JavaScript):** também são enviados como erros não fatais, com no máximo 300 caracteres, no máximo 5 por sessão, e sem dados do jogo guardado (o texto é cortado ou retido se parecer conter moedas, cartas ou histórico).
- **Contexto enviado com cada falha:** versão da app, modo de jogo, nível de dificuldade (quando existir) e versão do formato do jogo guardado. Nada pessoal.
- **Consentimento:** segue o mesmo comportamento do Firebase Analytics, que já recolhe sem esperar pelo formulário de anúncios; por isso o Crashlytics também recolhe por omissão nas builds de release. Nas builds de debug está desligado.
- **Na Play Console (Data safety):** marcar **Informações e desempenho da app → Registos de falhas** e **Diagnóstico**: recolhidos, não partilhados, finalidade **Análise**, recolha não opcional, encriptados em trânsito. Confirmar também **IDs do dispositivo ou outros IDs** (já marcado por causa do Analytics).
- **Texto a acrescentar à política de privacidade:** "Usamos o Firebase Crashlytics, da Google, para receber relatórios técnicos quando a app falha (o ponto do código onde falhou, o modelo do telemóvel, a versão do Android e um identificador de instalação). Estes relatórios servem só para corrigir erros e não incluem dados pessoais nem o teu progresso no jogo."

## O que muda com a v1.3 Parte 1 (backup)

- **Backup do Android:** o progresso do jogo (moedas, itens, definições, histórico, conquistas) entra na cópia de segurança automática do Android, guardada pela Google na conta Google do jogador. A app não envia nada para nenhum servidor nosso e não vê esse backup. Detalhes técnicos em `docs/BACKUP.md`.
- **Fica de fora:** o consentimento dos anúncios, a compra "Remover anúncios" (volta a ser verificada no Google Play), os IDs do Firebase e do Analytics, a cadência de anúncios e o agendamento dos lembretes.
- **Na Play Console (Data safety):** nada muda; a cópia de segurança do Android não conta como recolha de dados pela app.
- **Texto a acrescentar à política de privacidade:** "O jogo usa a cópia de segurança do próprio Android para guardar o teu progresso (moedas, itens, definições e histórico de jogos) se mudares ou repuseres o telemóvel. Essa cópia é feita e guardada pela Google na tua conta Google; nós não temos acesso a ela. Não tem dados pessoais e nunca inclui as escolhas sobre anúncios nem compras, que voltam a ser verificadas no telemóvel novo. Podes desligá-la nas definições de cópia de segurança do telemóvel."

## O que muda com a v1.3 Partes 2 e 3

- **Níveis de dificuldade e Rivais do dia:** tudo é calculado no telemóvel (lista de jogos e rivais inventados dentro da app). Nada é enviado; não há dados novos a declarar.
- **Analytics:** novo parâmetro `difficulty_tier` (fácil, médio ou difícil) nos eventos de jogo e o evento `rivals_opened`; sem dados pessoais.

## A confirmar pelo dono

- O Firebase Analytics recolhe dados independentemente da resposta ao formulário de anúncios. A UMP grava os sinais de Consent Mode no telemóvel (visto no registo: `UMP_consentModeValues`); confirma no Firebase que o Consent Mode está a ser respeitado nas regiões da UE.
