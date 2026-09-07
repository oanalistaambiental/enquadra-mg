#!/usr/bin/env python3
"""
Converte uma planilha do Anexo Único da DN COPAM 217/2017 no catálogo do aplicativo.

O aplicativo já traz a Listagem A (minerárias). Este script existe para carregar o resto sem
que ninguém precise digitar JSON à mão — e sem que eu precise adivinhar faixa numérica de norma.

Como usar:
  1. Monte um CSV com o cabeçalho abaixo (separador ponto e vírgula), a partir do Anexo oficial.
  2. python3 importar-anexo.py atividades.csv > atividades.json
  3. Substitua app/src/main/assets/norma/atividades.json pelo arquivo gerado.

Cabeçalho esperado:
  codigo;descricao;ar;agua;solo;geral;parametro;unidade;limiteP;limiteM

Para atividade categórica (ex.: barragem por classe), deixe limiteP e limiteM vazios e use:
  ...;parametro;categorias;Classe I=P|Classe II=M|Classe III=G;
"""
import csv, json, sys, re

def numero(txt):
    if txt is None: return None
    t = txt.strip().replace('.', '').replace(',', '.')
    t = re.sub(r'[^0-9.\-]', '', t)
    return float(t) if t else None

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
                lp, lm = numero(linha.get('limiteP')), numero(linha.get('limiteM'))
                if lp is None or lm is None:
                    sys.exit(f'linha {i}: {codigo} sem limiteP/limiteM — o app não adivinha faixa de porte')
                if lp >= lm:
                    sys.exit(f'linha {i}: {codigo} tem limiteP >= limiteM ({lp} >= {lm})')
                a['limiteP'], a['limiteM'] = lp, lm

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
