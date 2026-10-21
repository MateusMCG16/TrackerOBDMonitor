# Validação da entrega — 21/10/2026

Executado em Linux com JDK 17, Gradle 9.4.1, AGP 9.2.1, Kotlin/Compose Compiler 2.3.10, SDK Platform 37.0 e Build Tools 36.0.0.

## Resultado executado

```text
:core:test :app:assembleDebug :app:lintDebug
BUILD SUCCESSFUL
```

Neste ambiente foi usado `-Pkotlin.compiler.execution.strategy=in-process` para evitar o daemon Kotlin separado. O projeto pode ser compilado pelos comandos padrão do README em um Android Studio com SDK e JDK configurados.

**31 testes unitários aprovados; zero falhas e zero erros.**

| Classe | Testes | Falhas |
| --- | ---: | ---: |
| Elm327ClientTest | 9 | 0 |
| ObdPidParserTest | 7 | 0 |
| ObdPollingSchedulerTest | 6 | 0 |
| TelemetryTest | 9 | 0 |

O teste de frequência usa tempo virtual e transporte com 20 ms de latência: cada PID inicia cinco consultas por segundo, com 200 ms entre consultas do mesmo sensor, alternância e no máximo uma consulta pendente. Os testes de adaptadores lentos verificam aumento do intervalo, ausência de rajadas e recuperação gradual. Também foram testados timeouts de leitura/escrita, desconexão, cancelamento, erros de clones, suporte de PIDs, respostas inválidas, zero válido e dados antigos que não voltam a ser válidos durante o backoff.

O APK foi gerado e a assinatura foi verificada pelo `apksigner` (esquema v2, um assinante). Manifesto final: min SDK 26, target SDK 37; sem permissão de internet, localização ou varredura Bluetooth.

**Lint: zero erros, dois avisos não bloqueantes:** existe Coroutines 1.11.0 mais recente que a versão estável 1.10.2 usada; `allowBackup=false` pode ser complementado por regras de extração no Android 12+. Não há banco, preferências de telemetria nem arquivos de leitura persistidos pelo aplicativo.

Os relatórios reais estão em `validation/unit-tests/index.html`, `validation/junit/`, `validation/lint-results-debug.html`, `validation/apk-signature.txt` e `validation/build-summary.txt`.

## Limites

Não houve telefone, emulador, ELM327 nem veículo disponíveis para execução física. Instalação em aparelho, interface renderizada, estabilidade do clone e desempenho de Bluetooth/ECU permanecem sem validação física. Não foi medido FPS da interface e não se garante 5 Hz no veículo. O modo demo e a comunicação foram verificados por testes do núcleo com transporte simulado; a implementação Android foi compilada e analisada pelo lint.

## Artefato

APK: `apk/TrackerOBDMonitor-debug.apk` (também entregue separadamente).

SHA-256:

```text
f867df374dde58760c2f7e7ba65cee4e25a0ffd5c5ebde1cf66ba960b09e4e86
```

## Atualização — 21/10/2026

### Medição em veículo

Primeira execução física, sobre uma gravação de tela de 7,1 s com o veículo parado e o motor em funcionamento (Tracker 2022, clone ELM327 Bluetooth). Quatorze amostras lidas da própria interface:

| Métrica | Velocidade | RPM |
| --- | ---: | ---: |
| Latência média | 132 ms | 132 ms |
| Latência mínima / máxima | 123 / 153 ms | 121 / 150 ms |
| Frequência média | 2,71 Hz | 2,76 Hz |

A sessão permaneceu em **AO VIVO** do início ao fim, sem leitura desatualizada, sem `--` e sem reconexão. A idade da amostra ficou entre 0,0 s e 0,3 s, e o RPM acompanhou as acelerações. O valor medido confere com o projeto do agendador: com 132 ms de latência o intervalo calculado é de 162 ms e, alternando dois PIDs, cada sensor cai para cerca de 3 Hz. **O gargalo era o número de transações por ciclo, não o agendador.**

Permanecem sem validação física: velocidade acima de zero, comportamento em movimento, sessões longas e o pedido combinado `010C0D` neste adaptador.

### Alterações desta rodada

- `010C0D` em uma única transação quando o protocolo é CAN, com recuo automático para um PID por vez.
- Bytes NUL descartados no parser, conforme a página 9 do datasheet.
- Leituras de PID com prazo de 6 s, acima da espera interna de 5 s do ELM327 para *response pending*.
- `FrequencyMeter` sincronizado; cálculo de `hz` fora dos lambdas de `MutableStateFlow.update`.

**44 testes unitários aprovados; zero falhas e zero erros** (eram 31).

| Classe | Testes | Falhas |
| --- | ---: | ---: |
| Elm327ClientTest | 9 | 0 |
| ObdCombinedSessionTest | 3 | 0 |
| ObdMultiPidTest | 8 | 0 |
| ObdPidParserTest | 7 | 0 |
| ObdPollingSchedulerTest | 8 | 0 |
| TelemetryTest | 9 | 0 |

`:app:assembleDebug` e `:app:lintDebug` executados com sucesso: **zero erros de lint e dois avisos**, ambos de versão mais nova disponível (Gradle 9.8.2 e Coroutines 1.11.0). O aviso de `allowBackup` foi resolvido com `data_extraction_rules.xml` e `backup_rules.xml`.

O APK em `apk/` e os relatórios em `validation/` são da entrega original e não foram regerados.
