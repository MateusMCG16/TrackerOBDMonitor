# Validação

## Estado atual — 11/11/2026

Executado sobre o commit `23dc4d6` (TOBD Monitor), em Windows 11 com JDK 17.0.16 (Temurin), Gradle 9.4.1, AGP 9.2.1, Kotlin/Compose Compiler 2.3.10, SDK Platform 37.0 e o Build Tools padrão do AGP. A assinatura foi conferida com o `apksigner` do Build Tools 37.0.0.

```text
.\gradlew.bat :core:test --rerun :app:assembleDebug :app:lintDebug
BUILD SUCCESSFUL
```

`--rerun` obriga os testes a rodarem de novo em vez de reaproveitar o resultado do Gradle.

**76 testes unitários aprovados; zero falhas e zero erros.**

| Classe | Testes | Falhas |
| --- | ---: | ---: |
| DtcParserTest | 15 | 0 |
| Elm327ClientTest | 10 | 0 |
| ObdCombinedSessionTest | 3 | 0 |
| ObdDetailsTest | 16 | 0 |
| ObdMultiPidTest | 8 | 0 |
| ObdPidParserTest | 7 | 0 |
| ObdPollingSchedulerTest | 8 | 0 |
| TelemetryTest | 9 | 0 |

Os testes das telas de detalhe usam respostas copiadas da leitura de 09/11 no veículo, já no formato sem cabeçalho (`ATH0`) que o aplicativo usa: preenchimento `AA` no fim do quadro, resposta multilinha do `0168` e duas respostas ao `0142` (motor e câmbio). Um teste impede que um pedido de detalhe passe de três PIDs.

**Lint: zero erros e quatro avisos não bloqueantes:**

- Gradle 9.9.1 disponível (o projeto usa 9.4.1);
- Coroutines 1.11.0 disponível (o projeto usa 1.10.2);
- em `PrimerComponents.kt:60`, o parâmetro `modifier` não é o primeiro opcional;
- a pasta `mipmap-anydpi-v26` é desnecessária com min SDK 26.

Manifesto final: min SDK 26, target SDK 37; só `BLUETOOTH_CONNECT` (mais `BLUETOOTH`/`BLUETOOTH_ADMIN` até o Android 11). Sem internet, localização ou varredura Bluetooth.

### Artefato

`apk/TrackerOBDMonitor-debug.apk`. O APK compilado nesta rodada é byte a byte igual ao da pasta `apk/`. A assinatura foi verificada pelo `apksigner` (esquema v2, um assinante).

```text
0f4dc41dfe72aedfdf32f7fdbe325bca67a88beacfa12c82549f2634d810bfe9
```

Os relatórios desta rodada estão em `validation/unit-tests/index.html`, `validation/junit/`, `validation/lint-results-debug.html`, `validation/apk-signature.txt` e `validation/build-summary.txt`.

## O que foi verificado no veículo

Veículo: Chevrolet Tracker, ano-modelo 2023 pelo VIN. Adaptador: clone ELM327 v2.1 Bluetooth.

**21/10/2026, com o aplicativo:** velocidade e RPM pedidos um de cada vez, veículo parado e motor ligado. Latência média de 132 ms e cerca de 2,7 Hz por sensor; a sessão ficou em **AO VIVO** do início ao fim (detalhes no histórico abaixo).

**09/11/2026, pelo notebook, sem o aplicativo:** o adaptador foi lido por um script na porta serial, com cabeçalhos ligados (`ATH1`) para identificar quem respondia. Os registros ficam fora do repositório porque contêm o VIN.

- Protocolo CAN 29 bits a 500 kbps. Só dois módulos respondem a pedidos OBD: motor (`11`) e câmbio (`18`).
- `010C0D` numa transação só funciona, em cerca de 135 ms; só o motor responde.
- Todos os pedidos de detalhe que estão em `ElmCommand` foram enviados e responderam. Pedidos de 2 e 3 PIDs com resposta num quadro só funcionam; **um pedido de 4 PIDs reinicia o clone**.
- O clone também reinicia com respostas longas (`0904`, `0601`) e não remove o preenchimento `AA` do fim dos quadros.
- `0101`, `03`, `07` e `0A` responderam sem códigos armazenados; `0902` (VIN) é respondido só pelo motor.

## O que ainda não foi validado

- **O aplicativo atual no carro.** As abas Dados e Diagnóstico, o `010C0D` dentro do aplicativo e a leitura do VIN só foram exercitados pelo script de 09/11 e por testes com transporte simulado.
- Comportamento em movimento, velocidade acima de zero e sessões longas.
- Interface num aparelho físico: renderização das telas, rotação e telas pequenas. Não foi medido FPS.
- A taxa de 5 Hz é uma meta, não uma garantia: depende do clone, do Bluetooth e da ECU.

---

## Histórico

### Entrega original — 21/10/2026

Executado em Linux com JDK 17, Gradle 9.4.1, AGP 9.2.1, Kotlin/Compose Compiler 2.3.10, SDK Platform 37.0 e Build Tools 36.0.0, com `-Pkotlin.compiler.execution.strategy=in-process` para evitar o daemon Kotlin separado.

`:core:test :app:assembleDebug :app:lintDebug` com sucesso; **31 testes aprovados** (Elm327ClientTest 9, ObdPidParserTest 7, ObdPollingSchedulerTest 6, TelemetryTest 9).

O teste de frequência usa tempo virtual e transporte com 20 ms de latência: cada PID inicia cinco consultas por segundo, com 200 ms entre consultas do mesmo sensor, alternância e no máximo uma consulta pendente. Os testes de adaptadores lentos verificam aumento do intervalo, ausência de rajadas e recuperação gradual. Também foram testados timeouts de leitura/escrita, desconexão, cancelamento, erros de clones, suporte de PIDs, respostas inválidas, zero válido e dados antigos que não voltam a ser válidos durante o backoff.

Lint com zero erros e dois avisos (Coroutines 1.11.0 disponível; `allowBackup=false` sem regras de extração para o Android 12+). Não houve telefone, emulador, ELM327 nem veículo disponíveis. SHA-256 do APK daquela entrega: `f867df374dde58760c2f7e7ba65cee4e25a0ffd5c5ebde1cf66ba960b09e4e86`.

### Atualização — 21/10/2026

#### Medição em veículo

Primeira execução física, sobre uma gravação de tela de 7,1 s com o veículo parado e o motor em funcionamento. Quatorze amostras lidas da própria interface:

| Métrica | Velocidade | RPM |
| --- | ---: | ---: |
| Latência média | 132 ms | 132 ms |
| Latência mínima / máxima | 123 / 153 ms | 121 / 150 ms |
| Frequência média | 2,71 Hz | 2,76 Hz |

A sessão permaneceu em **AO VIVO** do início ao fim, sem leitura desatualizada, sem `--` e sem reconexão. A idade da amostra ficou entre 0,0 s e 0,3 s, e o RPM acompanhou as acelerações. O valor medido confere com o projeto do agendador: com 132 ms de latência o intervalo calculado é de 162 ms e, alternando dois PIDs, cada sensor cai para cerca de 3 Hz. **O gargalo era o número de transações por ciclo, não o agendador.**

#### Alterações daquela rodada

- `010C0D` em uma única transação quando o protocolo é CAN, com recuo automático para um PID por vez.
- Bytes NUL descartados no parser, conforme a página 9 do datasheet.
- Leituras de PID com prazo de 6 s, acima da espera interna de 5 s do ELM327 para *response pending*.
- `FrequencyMeter` sincronizado; cálculo de `hz` fora dos lambdas de `MutableStateFlow.update`.
- O aviso de `allowBackup` foi resolvido com `data_extraction_rules.xml` e `backup_rules.xml`.

**44 testes aprovados** (eram 31); lint com zero erros e dois avisos de versão mais nova disponível.
