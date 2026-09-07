# Enquadra MG

Simulador de enquadramento de licenciamento ambiental pela **DN COPAM 217/2017** (Minas Gerais).
Android nativo, Kotlin, tudo offline. Ferramenta independente — não substitui a análise do órgão.

A documentação do projeto está em [`LEIA-ME.md`](LEIA-ME.md).

## Estrutura

O projeto Gradle fica na **raiz** deste repositório:

```
settings.gradle.kts
build.gradle.kts
app/
.github/workflows/build-apk.yml
ferramentas/
```

A norma é **dado, não código**: as tabelas da DN vivem em `app/src/main/assets/norma/*.json`.
Quando a DN mudar, troca-se o arquivo, não o aplicativo. Os testes de unidade leem esses mesmos
arquivos, então uma célula quebrada para o build em vez de aparecer no celular de um perito.

## Gerar o APK

Aba **Actions** → **Gerar APK** → **Run workflow**. Baixe o artefato `enquadra-mg-apk`.
