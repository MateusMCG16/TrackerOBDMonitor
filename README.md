# TOBD Monitor

Tracker OBD Monitor: aplicativo Android nativo em Kotlin / Jetpack Compose para velocidade, RPM, dados do motor e códigos de falha via ELM327 Bluetooth Classic SPP. Android 8.0+ (API 26). Sem root, internet, BLE ou integração com a central multimídia. Interface em português no tema escuro do Primer (o sistema de design do GitHub), com instrumentos animados e layouts para retrato e paisagem.

O repositório traz também um [painel web](#painel-web) que lê o mesmo adaptador pelo computador.

## Abrir e compilar

1. Clone o repositório e abra a pasta `TrackerOBDMonitor` no Android Studio compatível com AGP 9.2.
2. Selecione **JDK 17** como Gradle JDK. Instale **Android SDK Platform 37.0** e **Build Tools 36.0.0** pelo SDK Manager.
3. Aguarde o Gradle Sync. A primeira compilação precisa de internet para baixar dependências; **o aplicativo instalado funciona totalmente offline**.
4. Execute a configuração `app` em um telefone Android com Bluetooth Classic. Emuladores normalmente não disponibilizam uma conexão RFCOMM a um ELM327 físico.

Windows PowerShell, a partir da pasta do projeto:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

Linux / macOS:

```bash
chmod +x gradlew
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`. Para instalar por USB com depuração habilitada:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

O APK não é versionado (`*.apk` está no `.gitignore`). A cópia local fica em `apk/TrackerOBDMonitor-debug.apk`, e `apk/SHA256SUMS.txt` guarda o SHA-256 dela para conferir o arquivo. Os relatórios de validação estão em `validation/`.

As versões estão fixadas em Gradle Kotlin DSL. O projeto usa AGP com suporte Kotlin integrado; **não adicione `org.jetbrains.kotlin.android` ao módulo app**. O módulo JVM `core` usa o plugin Kotlin JVM e permite testar protocolo e agendamento sem um telefone.

## Usar com o adaptador

1. Faça os primeiros testes com a Tracker estacionada. Conecte o ELM327 à tomada OBD-II, com a ignição ligada. Para observar RPM acima de zero, o motor precisa estar funcionando; assegure ventilação adequada.
2. Pareie o adaptador pelas configurações Bluetooth do Android usando o PIN informado pelo fabricante. Feche outros aplicativos OBD que possam ocupar o adaptador.
3. Abra o aplicativo e toque em **Conectar ELM327**. No Android 12+, autorize **Dispositivos próximos**.
4. Selecione o adaptador na lista de dispositivos pareados. O app não faz descoberta; não precisa de localização nem `BLUETOOTH_SCAN`.
5. Acompanhe configuração, suporte aos PIDs e recepção das leituras. Zero é uma medição válida; `--` significa ausência de medição.
6. **Desconectar** / **Cancelar conexão** interrompe o trabalho e fecha a sessão. A saída do aplicativo para segundo plano também interrompe a conexão. Rotação da tela preserva a sessão e o ViewModel.

O botão de configurações Bluetooth permite parear/ativar o rádio. Ao retornar, toque em **Permitir / atualizar**. O aplicativo mantém a tela acesa somente durante uma sessão ativa. Não existe serviço em segundo plano, retomada automática após sair do aplicativo ou conexão ao abrir o app.

## Polling: alvo de 5 Hz por sensor

- Em CAN, um único worker pede **010C0D**: uma transação devolve RPM e velocidade juntos, no mesmo instante, com espaçamento inicial de **200 ms** — 5 Hz para os dois sensores. O protocolo é identificado por **ATDPN** logo após o `0100`.
- Fora de CAN o worker alterna **010C / 010D**, com espaçamento inicial de **100 ms entre requisições**, ou **200 ms por PID**. Com um único sensor suportado, o espaçamento inicial é 200 ms.
- Três respostas seguidas sem nenhum dado válido ao pedido combinado fazem o aplicativo voltar a um PID por vez **sem derrubar a conexão**, registrando um aviso. Clones que ignoram requisições multi-PID continuam funcionando.
- Cada transação é protegida por `Mutex`: escrever comando + CR, ler até `>`, só então liberar a próxima transação. Não há produtor de consultas, fila de polling, paralelismo de comandos ou recuperação em rajadas.
- O relógio é monotônico (`System.nanoTime`), independente de alterações no horário do telefone.
- Após cada resposta, o intervalo passa a considerar `ceil(latência × 1,15) + 10 ms`. Se necessário ele cresce imediatamente; com respostas rápidas, recupera-se em passos de 5 ms até o mínimo. Respostas sem dados válidos aumentam o intervalo em 50%, com limite de 5 s entre comandos.
- A próxima consulta parte do início **real** da anterior e nunca ocorre antes de 10 ms após seu término. Atrasos são descartados.
- Cada sensor mostra latência da última resposta, idade da última amostra válida e frequência real em janela móvel de 2 s. Respostas inválidas, animação e ticks da interface não contam como amostras. Antes de duas amostras, a estimativa de Hz é zero.
- `ObdRepository.queryTrace` conserva as últimas 120 consultas com sensor, horário monotônico, latência e resultado, em memória. Não grava dados pessoais nem faz uploads.
- A latência inclui envio e espera pela resposta completa. `NO DATA`, `STOPPED`, `?`, mensagens de erro e respostas inválidas não alteram o valor nem a data de uma amostra anterior.

### Dados das telas de detalhe

- Além de velocidade e RPM, o aplicativo lê 33 valores do modo 01 (carga, borboleta, pedal, pressões, trims, temperaturas, sondas, tensões, etanol, histórico desde a limpeza de códigos) e a tensão no adaptador (`ATRV`).
- **Só a tela em exibição é lida.** Em Instrumentos o adaptador fica apenas com `010C0D` e os 5 Hz valem. Numa tela de detalhe entra **uma** transação extra por ciclo, em rodízio entre os pedidos daquela tela; velocidade e RPM caem para cerca de 3,5 Hz enquanto ela estiver aberta.
- Os pedidos de detalhe usam vários PIDs por mensagem, o que só existe em CAN. Em outro protocolo as telas de detalhe ficam vazias e dizem o motivo.
- Os grupos de PIDs são fixos e foram enviados ao veículo em 09/11/2026 com o clone v2.1: pedidos de 2 e 3 PIDs cuja resposta cabe em um quadro CAN funcionam; **um pedido de 4 PIDs reinicia esse clone**. Um teste impede que um grupo novo passe desse limite.
- As máscaras `0120`, `0140`, `0160` e `0180` são lidas na primeira vez que uma tela de detalhe abre; um grupo só é pedido se o veículo anuncia ao menos um dos seus PIDs, e o que não é anunciado aparece como "sem suporte".
- Com os cabeçalhos desligados, dois módulos que respondem ao mesmo pedido são distinguidos pelo conteúdo: a resposta com mais PIDs é a do motor. É assim que a tensão do módulo (PID 42) é separada entre motor e câmbio.
- Um valor de detalhe fica esmaecido 8 s depois da última leitura válida.

**5 Hz é uma meta, não uma garantia.** A capacidade do clone, do Bluetooth e da ECU determina a taxa real. A escala do velocímetro é de 0 a 220 km/h; valores medidos maiores continuam aparecendo numericamente e o arco satura. A escala de RPM vai de 0 a 8.000. A marca de 6.500 a 8.000 é somente ilustrativa e não representa o limite do motor da Tracker.

## Protocolo e falhas

A inicialização aguarda `>` após cada comando:

```text
ATZ → ATE0 → ATL0 → ATS0 → ATH0 → ATSP0 → 0100 → ATDPN
```

`0100` verifica a máscara de suporte antes de qualquer leitura. Consulta-se exclusivamente os PIDs anunciados. Se nenhum dos dois estiver disponível, a configuração termina com erro, sem inventar suporte.

O parser aceita espaços, ausência de espaços, eco, CR/LF, `SEARCHING...` e respostas de mais de uma ECU. Bytes NUL (`00`) que o ELM327 pode inserir no fluxo são descartados, como o datasheet orienta; sem isso uma leitura válida seria recusada. Respostas multilinha ISO 15765 — linha de comprimento seguida de `0:` / `1:` — são remontadas antes da leitura, e uma resposta pode conter vários PIDs. A primeira resposta válida do PID solicitado é usada; a máscara de suporte combina as ECUs que responderem. Cabeçalhos devem estar desligados: uma falha de `ATH0` ou `ATSP0` interrompe a configuração. Clones que recusarem `ATE0`, `ATL0` ou `ATS0` geram avisos; o parser continua tolerando eco e formatação. Não há suposição de que um clone azul anuncie corretamente todas as funções de um ELM327 original.

Timeouts: conexão RFCOMM 12 s por tentativa, `ATZ` 5 s, demais comandos AT 2 s, suporte `0100` 15 s (inclui seleção automática de protocolo) e leituras de PID 6 s. As leituras têm prazo maior porque uma resposta *response pending* (`7F xx 78`) faz o próprio ELM327 esperar até 5 s, e outros 5 s a cada nova: um prazo menor encerraria uma sessão saudável enquanto o adaptador ainda aguarda legitimamente. A conexão usa o UUID SPP `00001101-0000-1000-8000-00805F9B34FB`; se a conexão segura falhar por I/O, tenta a API RFCOMM insegura documentada com o mesmo UUID. Não usa reflexão, canais RFCOMM fixos ou comandos CAN arbitrários.

**Um timeout de transação encerra o socket.** A sessão não envia outro PID nem reutiliza bytes atrasados. Falhas de I/O permitem até três reconexões adicionais, com esperas de 1, 2 e 4 s. Cada nova sessão repete configuração e suporte. O usuário pode cancelar a qualquer momento. Erros de configuração e permissão não são repetidos automaticamente.

As leituras ficam desatualizadas após `max(1,5 s, min(5 s, 6 × intervalo entre comandos))`, fixado no momento de cada amostra válida. A redução de frequência após uma falha não torna dados antigos válidos novamente. A idade continua avançando mesmo após erro ou desconexão. Leituras antigas permanecem visíveis com indicação **DESATUALIZADO** e animação interrompida. Sem nenhuma leitura válida, aparece `--`. Ao encerrar o modo demo, os valores simulados são removidos.

## Modo demonstração

**Modo demo** não solicita permissões, não abre sockets e não acessa o adaptador. Gera velocidade e RPM a cada 200 ms por sensor, com ciclo suave 0 → 100 → 0 km/h em 28 s e rotações com trocas de marcha ilustrativas. Toda a tela identifica **MODO DEMONSTRAÇÃO • DADOS SIMULADOS**. As transições visuais de 180 ms usam o relógio de quadros do Compose: podem ser fluidas em telas de 60 Hz sem aumentar a frequência das medições.

## Telas

Quatro destinos numa barra inferior, **nenhum deles rola**: cada tela ocupa exatamente uma altura de tela e o conteúdo se ajusta ao espaço disponível.

```text
Instrumentos   cabeçalho + velocímetro + conta-giros + botões de sessão
Dados          quatro telas atrás de uma linha de chips:
                 Motor         carga, borboleta, pedal, avanço, tempo desde a partida
                 Mistura       pressões, fluxo de ar, lambda, trims, tanque, etanol, purga
                 Temperatura   arrefecimento, catalisador, ar de admissão, ar ambiente
                 Elétrica      sondas lambda, tensão no motor, no câmbio e no adaptador
Diagnóstico    luz de injeção, histórico desde a limpeza, lista de códigos, botão de leitura
Ajuda          sessão, veículo, como ler o código, o que o diagnóstico faz, limites
```

- Nas telas de Dados cada valor é um cartão; a grade divide a altura livre em linhas iguais e o tamanho do texto sai da altura da linha. Em tela baixa o cartão passa a mostrar rótulo e valor lado a lado em vez de empilhados. Os tamanhos são definidos em dp, então a escala de fonte do sistema não estoura um cartão.
- Em telas de 680 dp ou mais a grade usa quatro colunas em vez de duas. Um último valor ímpar ocupa a linha inteira.
- Os dois instrumentos dividem a altura livre (`weight(1f)` cada). O mostrador, os números e as marcações se dimensionam pelo espaço medido — não há proporção fixa. Em telas de 680 dp ou mais eles ficam lado a lado.
- A seleção de adaptador continua sendo um diálogo sobre a tela atual, não um destino.
- A aba Ajuda divide o texto em cinco tópicos atrás de uma linha de chips: só o tópico escolhido é composto, e em telas baixas a escala de tipografia se aperta em vez de a página crescer.
- A aba mostra um ponto âmbar quando há código de falha (Diagnóstico) ou aviso do adaptador (Ajuda).
- A aba escolhida é `rememberSaveable`: sobrevive à rotação, como a sessão no ViewModel. O botão voltar do sistema retorna a Instrumentos.
- **Uma única região rola**: a lista de códigos de falha, cujo tamanho o aplicativo não controla. Com muitos códigos armazenados, a lista rola dentro do seu quadro; a tela em si não.

## Painel web

`painel-web/` é uma página estática (HTML, CSS e JavaScript, sem dependências) que lê o ELM327 pela Web Serial API. Serve para consultar o carro pelo notebook, com o adaptador pareado como porta serial (por exemplo, `COM7` no Windows).

```powershell
python -m http.server 8137 --bind 127.0.0.1 --directory painel-web
```

Abra `http://127.0.0.1:8137` no **Chrome ou Edge**, clique em **Conectar ELM327** e escolha a porta do adaptador. Outros navegadores, e o navegador embutido de alguns aplicativos, não oferecem a Web Serial nem o seletor de porta. O **Modo demo** funciona sem adaptador.

- Mesma política do aplicativo: uma lista de permissão confere cada comando antes de enviá-lo. Só passam `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH1`, `ATSP0`, `ATDPN`, `ATRV`, modo 01 com até três PIDs por pedido, `03`, `07`, `0A`, `0902` e `090A`. Não há modo 04, escrita, atuador nem `ATSH`.
- Diferente do aplicativo, o painel liga os cabeçalhos (`ATH1`) e identifica qual módulo respondeu.
- Velocidade e RPM são lidos a cada ciclo; os demais PIDs, em rodízio mais lento.

## Arquitetura

```text
app/src/main/java/br/com/tracker/obd/
  MainActivity.kt                 permissões, ciclo de vida, tema, abas
  bluetooth/                      RFCOMM, socket, leitor assíncrono limitado
  viewmodel/                      DashboardViewModel / StateFlow
  ui/                             Navigation, quatro destinos, seleção e tema
core/src/main/kotlin/br/com/tracker/obd/
  data/                           modelos, relógio, idade e Hz
  obd/                            lista de comandos, ELM, parser, scheduler, repositório
  demo/                           fonte simulada independente
core/src/test/kotlin/br/com/tracker/obd/
                                  testes determinísticos com tempo virtual
painel-web/                       página estática com Web Serial, sem build
```

`core` não depende de Android. Isso mantém os testes do protocolo e do relógio independentes das APIs Bluetooth. `app` fornece o transporte nativo e observa o estado com `collectAsStateWithLifecycle`. Leitura bloqueante RFCOMM fica em `Dispatchers.IO`, alimenta um canal limitado e é interrompida ao fechar o socket; conexão e escrita também são canceláveis sem bloquear a thread principal.

## Segurança e limites de validação

A lista fechada `ElmCommand` contém somente leituras:

- os seis comandos AT de configuração, `ATDPN` (lê o número do protocolo) e `ATRV` (lê a tensão no próprio adaptador);
- modo 01: as máscaras `0100`, `0120`, `0140`, `0160` e `0180`, os pedidos `010C`, `010D` e `010C0D`, o estado `0101` e os quinze grupos das telas de detalhe;
- modos `03`, `07` e `0A`: códigos confirmados, pendentes e permanentes;
- modo 09: apenas `0902` (VIN). O `0904` ficou de fora de propósito: a resposta longa reinicia o clone v2.1.

Um teste falha se a lista ganhar um comando fora desses modos. Não há modo 04, ECU write, limpeza de falhas, atuadores, troca de sessão, `ATSH`, fuzzing, CAN bruto, terminal ou comando personalizado. O VIN aparece só na tela de Ajuda e não é gravado nem enviado. Não existe permissão `INTERNET` no manifesto.

Não interaja com o aplicativo durante a condução. Com a Tracker (ano-modelo 2023) e o clone ELM327 v2.1, o aplicativo já leu velocidade e RPM, e os pedidos das telas de detalhe e de diagnóstico foram conferidos no veículo por um script no notebook. As telas de Dados e Diagnóstico ainda não foram testadas no carro pelo aplicativo: os testes automatizados usam transportes simulados e não substituem essa verificação. O app mostra somente as medições recebidas, sem afirmar frequência garantida, precisão de instrumentos originais ou limite real do motor.

Os comandos executados, o resultado real da compilação e dos testes (76 aprovados em 11/11/2026), o que já foi conferido no veículo e o que ainda falta validar estão em `VALIDATION.md`.
