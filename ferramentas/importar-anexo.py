#!/usr/bin/env python3
"""
Converte uma planilha do Anexo Único da DN COPAM 217/2017 no catálogo do aplicativo.

O aplicativo já traz a Listagem A (minerárias). Este script existe para carregar o resto sem
que ninguém precise digitar JSON à mão — e sem que eu precise adivinhar faixa numérica de norma.

Como usar:
  1. Monte um CSV com o cabeçalho abaixo (separador ponto e vírgula), a partir do Anexo oficial.
  2. python3 importar-anexo.py atividades.csv > atividades.json
  3. Substitua app/src/main/assets/norma/atividades.json pelo arquivo gerado.

Cabeçalho esperado (as sete últimas colunas são opcionais):
  codigo;descricao;ar;agua;solo;geral;parametro;unidade;limiteP;limiteM;
  limitePExclusivo;limiteMExclusivo;unidadeAlternativa;limitePAlt;limiteMAlt;nota

ATENÇÃO — bug corrigido em 07/09/2026. A versão anterior deste script NÃO emitia
limitePExclusivo, unidadeAlternativa, limitePAlt, limiteMAlt nem nota. Como as instruções
mandam SUBSTITUIR atividades.json pelo arquivo gerado, reimportar o Anexo apagava em silêncio
as regras de fronteira já conferidas. Concretamente: A-03-01-8 perdia limitePExclusivo e um
empreendimento com produção de exatamente 10.000 m³/ano caía de porte M (classe 3, LAS/RAS)
para porte P (classe 2, LAS/Cadastro) — contrariando a nota da própria atividade. E o teste da
base só confere limiteP < limiteM, então o CI passava verde.

Colunas de fronteira, quando o Anexo usar "<" em vez de "≤":
  limitePExclusivo=sim  →  o valor igual ao limiteP já é porte MÉDIO
  limiteMExclusivo=sim  →  o valor igual ao limiteM já é porte GRANDE

Para atividade categórica (ex.: barragem por classe), deixe limiteP e limiteM vazios e use:
  ...;parametro;categorias;Classe I=P|Classe II=M|Classe III=G;
"""
import csv, json, sys, re

def numero(txt, linha=None, campo=None):
    """
    Lê um número da planilha SEM adivinhar separador.

    A versão anterior fazia replace('.', '') incondicional — o mesmo bug que estava na tela do
    app: "1.5" virava 15. Aqui, se houver ponto E vírgula, o último é o decimal; se houver só
    um deles, ele é o decimal, e o separador de milhar precisa ser removido na planilha. Na
    dúvida o script PARA em vez de chutar: é número que decide modalidade de licenciamento.
    """
    if txt is None: return None
    t = txt.strip()
    if not t: return None
    if re.search(r'[^0-9.,\-\s]', t):
        sys.exit(f'linha {linha}: campo "{campo}" tem caractere inesperado — "{txt}"')
    t = t.replace(' ', '')
    ponto, virgula = t.rfind('.'), t.rfind(',')
    if ponto >= 0 and virgula >= 0:
        # o último separador é o decimal; o outro é milhar
        if virgula > ponto: t = t.replace('.', '').replace(',', '.')
        else: t = t.replace(',', '')
    elif virgula >= 0:
        if t.count(',') > 1:
            sys.exit(f'linha {linha}: campo "{campo}" tem mais de uma vírgula — "{txt}"')
        t = t.replace(',', '.')
    elif ponto >= 0:
        if t.count('.') > 1:
            sys.exit(f'linha {linha}: campo "{campo}" tem mais de um ponto — "{txt}"')
        # ponto único: DECIMAL. Se na sua planilha ele é separador de milhar, tire-o antes.
    try:
        return float(t)
    except ValueError:
        sys.exit(f'linha {linha}: campo "{campo}" não é número — "{txt}"')

def booleano(txt):
    return (txt or '').strip().lower() in ('sim', 's', 'true', '1', 'x')

def main(caminho):
    atividades = []
    with open(caminho, encoding='utf-8-sig', newline='') as f:
        for i, linha in enumerate(csv.DictReader(f, delimiter=';'), start=2):
            codigo = (linha.get('codigo') or '').strip()
            if not codigo:
                continue
            for campo in ('ar', 'agua', 'solo', 'geral'):
                v = (linha.get(campo) or '').strip().upper()
                if v not in ('P', 'M', 'G'):
                    sys.exit(f'linha {i}: campo "{campo}" precisa ser P, M ou G — veio "{v}"')

            a = {
                'codigo': codigo,
                'descricao': (linha.get('descricao') or '').strip(),
                'pp': {c: (linha.get(c) or '').strip().upper() for c in ('ar','agua','solo','geral')},
                'parametro': (linha.get('parametro') or '').strip(),
                'conferencia': 'unico',
            }

            cats = (linha.get('categorias') or '').strip()
            if cats:
                a['tipo'] = 'categorico'
                a['categorias'] = []
                for par in cats.split('|'):
                    rotulo, _, porte = par.partition('=')
                    porte = porte.strip().upper()
                    if porte not in ('P','M','G'):
                        sys.exit(f'linha {i}: categoria "{par}" sem porte P, M ou G')
                    a['categorias'].append({'rotulo': rotulo.strip(), 'porte': porte})
            else:
                a['tipo'] = 'numerico'
                a['unidade'] = (linha.get('unidade') or '').strip()
                lp = numero(linha.get('limiteP'), i, 'limiteP')
                lm = numero(linha.get('limiteM'), i, 'limiteM')
                if lp is None or lm is None:
                    sys.exit(f'linha {i}: {codigo} sem limiteP/limiteM — o app não adivinha faixa de porte')
                if lp >= lm:
                    sys.exit(f'linha {i}: {codigo} tem limiteP >= limiteM ({lp} >= {lm})')
                a['limiteP'], a['limiteM'] = lp, lm

                # Campos que a versão anterior perdia silenciosamente. Só entram no JSON quando
                # estão preenchidos, para o arquivo não encher de chaves vazias.
                if booleano(linha.get('limitePExclusivo')):
                    a['limitePExclusivo'] = True
                if booleano(linha.get('limiteMExclusivo')):
                    a['limiteMExclusivo'] = True

                ualt = (linha.get('unidadeAlternativa') or '').strip()
                if ualt:
                    lpa = numero(linha.get('limitePAlt'), i, 'limitePAlt')
                    lma = numero(linha.get('limiteMAlt'), i, 'limiteMAlt')
                    if lpa is None or lma is None:
                        sys.exit(f'linha {i}: {codigo} tem unidadeAlternativa "{ualt}" sem '
                                 f'limitePAlt/limiteMAlt — a tela quebra ao trocar de unidade')
                    if lpa >= lma:
                        sys.exit(f'linha {i}: {codigo} tem limitePAlt >= limiteMAlt ({lpa} >= {lma})')
                    a['unidadeAlternativa'], a['limitePAlt'], a['limiteMAlt'] = ualt, lpa, lma

            nota = (linha.get('nota') or '').strip()
            if nota:
                a['nota'] = nota

            atividades.append(a)

    if not atividades:
        sys.exit('nenhuma atividade lida — confira o separador (ponto e vírgula) e o cabeçalho')

    saida = {
        'titulo': 'Listagem de Atividades — Anexo Único da DN COPAM 217/2017',
        'cobertura': f'{len(atividades)} atividades importadas de planilha local',
        'base_legal': 'DN COPAM 217/2017, Art. 4º e Anexo Único',
        'como_completar': 'Gerado por ferramentas/importar-anexo.py',
        'legenda_conferencia': {
            'cruzado': 'coincidiu exatamente entre duas cópias independentes',
            'unico': 'lido de uma cópia só — confira antes de usar em processo',
            'divergente': 'as cópias discordaram em algum campo — veja a nota'
        },
        'atividades': atividades,
    }
    print(json.dumps(saida, ensure_ascii=False, indent=2))
    print(f'{len(atividades)} atividades convertidas', file=sys.stderr)

if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
