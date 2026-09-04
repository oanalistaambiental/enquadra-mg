# Enquadra MG — simulador de enquadramento e licenciamento ambiental

App Android nativo em Kotlin, segundo aplicativo do kit. Pacote
`br.com.oanalistaambiental.enquadramento`. Nome provisório.

Simula o enquadramento de uma atividade pela **DN COPAM 217/2017** e devolve classe,
modalidade de licenciamento, prazo de análise, validade, estudos exigidos e os fatores de
restrição ou vedação incidentes — com a **memória de cálculo** e a base legal de cada passo.

---

## Por que é um aplicativo separado do de campo

Decisão registrada em `arquitetura-de-produto-e-novas-frentes.md`. Em resumo: momentos de uso
opostos (campo x escritório), cadência de atualização incompatível (a norma muda, a câmera
precisa ser estável) e, principalmente, **isolamento de risco** — este é o módulo com maior
sensibilidade de posicionamento, e ele não pode contaminar o aplicativo de prova.

Por isso, inclusive, o tema aqui é claro, e o do app de campo é escuro. Ninguém confunde os dois.

## Como gerar o APK

Igual ao outro app: repositório no GitHub, o workflow `.github/workflows/build-apk.yml` compila
e publica o artefato **`enquadra-mg-apk`**. O workflow localiza o projeto mesmo que o upload
tenha criado uma subpasta.

> Se a aba Actions disser "Get started with GitHub Actions", a pasta `.github` não subiu — o
> navegador ignora pastas que começam com ponto. Crie o arquivo por "set up a workflow yourself"
> e cole o conteúdo de `.github/workflows/build-apk.yml`.

## A regra é dado, não código

Toda a norma vive em `app/src/main/assets/norma/`:

| Arquivo | Conteúdo |
|---|---|
| `tabela1.json` | Potencial poluidor geral a partir de ar, água e solo (art. 3º) |
| `tabela2.json` | Classe por porte e potencial (art. 5º) |
| `tabela3.json` | Modalidade por classe e critério locacional (art. 6º) |
| `tabela4.json` | Os 11 critérios locacionais e seus pesos, com o texto literal |
| `tabela5.json` | Os 9 fatores de restrição ou vedação, com o texto literal |
| `modalidades.json` | Modalidades, prazos, validade e estudos (Decreto 47.383/2018) |
| `atividades.json` | Listagem de atividades do Anexo Único |
| `procedencia.json` | De onde veio cada coisa e o que ainda não foi conferido |

Quando a DN for alterada, troca-se o arquivo — não o aplicativo.

## O que está conferido e o que não está

Isto é o mais importante deste documento.

| Item | Situação |
|---|---|
| Tabela 2 (classe) | **Conferida** — três leituras independentes concordam, com os cabeçalhos das linhas e colunas citados literalmente |
| Tabela 3 (modalidade) | **Conferida** — duas cópias independentes concordam nas 18 células |
| Tabela 4 (critérios locacionais) | **Conferida** — texto literal e pesos |
| Tabela 5 (restrição/vedação) | **Conferida** — texto literal |
| Prazos e validade | **Conferidos** no Decreto 47.383/2018, arts. 15, 22 e 37, pelo texto atualizado da ALMG |
| Tabela 1 (ar+água+solo) | **Pendente** — a leitura obtida tem uma assimetria entre as variáveis que pode ser erro de transcrição. Na prática é secundária: a listagem de atividades já traz o potencial **geral** pronto, e é ele que o app usa |
| Listagem de atividades | **Só a Listagem A** (26 atividades minerárias). 10 códigos conferidos entre duas cópias com coincidência exata, 1 divergente em um campo, 15 lidos de uma fonte só |

**Um ponto que precisa da sua conferência:** em `A-03-01-8` a faixa pequena é estritamente
menor que 10.000 m³/ano. Ficou a dúvida se o limite superior da faixa média (50.000) é
inclusivo ou exclusivo — hoje o app trata 50.000 exatos como porte MÉDIO. Se a redação da DN
for "de 10.000 a menos de 50.000", o certo seria GRANDE. O modelo já aceita
`limiteMExclusivo: true` no JSON da atividade para expressar isso, sem tocar no motor.

A base foi montada a partir de **cópias públicas** da DN 217 (UDOP e Prefeitura de Belo
Horizonte) porque o portal oficial do SIAM não era alcançável do ambiente onde o app foi
escrito. **Não é a publicação oficial.** Cada atividade mostra na tela o seu nível de
conferência, e o PDF exportado repete o aviso.

### Modo manual — o caminho mais confiável

Quando a atividade não está no catálogo, o app aceita que você informe **porte** e **potencial
poluidor geral** direto (os dois valores estão na listagem da DN, e um analista os lê em
segundos). Esse caminho usa **apenas as tabelas conferidas** e é imune à lacuna do catálogo.

### Como completar o catálogo

`ferramentas/importar-anexo.py` converte uma planilha em CSV no formato do app:

```bash
python3 ferramentas/importar-anexo.py atividades.csv > atividades.json
# substitua app/src/main/assets/norma/atividades.json
```

O script recusa linha sem faixa de porte em vez de completar com palpite.

## Integração com o app de campo

O art. 6º, §5º da própria DN 217 manda consultar o IDE-Sisema para verificar a incidência dos
critérios das Tabelas 4 e 5. O app faz isso **no mesmo pacote GeoPackage** do aplicativo de
campo: informa-se a coordenada e ele sugere quais critérios incidem, sem internet.

```
Android/data/br.com.oanalistaambiental.enquadramento/files/pacotes/mg-base.gpkg
```

É sugestão, não decisão — e a tela diz isso com essas palavras.

## O erro que o app impede

Incidindo mais de um critério locacional, os pesos **não se somam**: prevalece o de maior peso
(art. 6º, §3º). Somar é o engano mais comum de quem faz o enquadramento na mão, e leva a
modalidade mais gravosa do que a devida. Há teste automático travando esse comportamento.

## Testes

`app/src/test/` — 28 testes, entre eles:

- a base normativa carrega inteira e cobre todas as combinações das Tabelas 2 e 3;
- toda modalidade citada na Tabela 3 existe descrita na base;
- o limite de faixa pertence à faixa menor, e a atividade que foge a essa regra é tratada à parte;
- os pesos locacionais não se somam;
- conjunto de atividades vale pela de maior classe (art. 5º, parágrafo único);
- fator de restrição não altera a modalidade, mas gera aviso;
- casos reais completos, com a memória de cálculo.

## Aviso de posicionamento

Ferramenta **independente**, não afiliada ao SISEMA/SEMAD/FEAM e sem vínculo com o sistema
oficial de licenciamento. O resultado é uma **simulação** a partir de norma pública: não
substitui o enquadramento realizado pelo órgão ambiental competente nem vincula a Administração.

> **Item em aberto (seção 10 do contexto do projeto):** o texto exato deste aviso ainda precisa
> ser alinhado antes de qualquer publicação. O que está aqui é uma proposta.
