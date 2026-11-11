# CLAUDE.md

## Versionamento obrigatório

Toda alteração do app, seja debug ou release, muda a versão, seguindo o Versionamento Semântico (`MAJOR.MINOR.PATCH`, com o PATCH em dois dígitos):

| Parte | Quando sobe | Exemplo |
| --- | --- | --- |
| **MAJOR** | mudanças grandes que quebram compatibilidade com versões anteriores | `1.0.00` → `2.0.00` |
| **MINOR** | novas funcionalidades compatíveis com a versão anterior | `1.0.00` → `1.1.00` |
| **PATCH** | correção de alinhamento, espaçamento, cores incorretas ou pequenos defeitos visuais | `1.1.00` → `1.1.01` |

Ao subir uma parte, as partes à direita voltam a zero: `1.1.07` → `1.2.00`, `1.4.03` → `2.0.00`.

### Como aplicar

- A versão fica em `app/build.gradle.kts`, no bloco `defaultConfig`:
  - `versionName` recebe a versão nova (por exemplo, `"1.1.01"`);
  - `versionCode` sobe uma unidade a cada versão nova. O Android recusa instalar por cima um APK com `versionCode` igual ou menor.
- A versão muda no mesmo commit da alteração, e a mensagem do commit cita a versão nova.
- Mudanças só no painel web, na documentação ou nos testes não mudam a versão do app.
