package br.com.oanalistaambiental.enquadramento.norma

import org.json.JSONObject
import java.io.InputStream

/**
 * Carrega a norma dos arquivos JSON. A regra é DADO, não código: quando a DN for alterada,
 * troca-se o arquivo, não o aplicativo.
 */
object BaseNormativa {

    fun carregar(abrir: (String) -> InputStream): Regras {
        fun ler(nome: String) = abrir(nome).bufferedReader().use { it.readText() }

        val t1 = JSONObject(ler("tabela1.json"))
        val t2 = JSONObject(ler("tabela2.json"))
        val t3 = JSONObject(ler("tabela3.json"))
        val t4 = JSONObject(ler("tabela4.json"))
        val t5 = JSONObject(ler("tabela5.json"))
        val mods = JSONObject(ler("modalidades.json"))
        val ativ = JSONObject(ler("atividades.json"))
        val proc = JSONObject(ler("procedencia.json"))

        val tabela1 = t1.getJSONObject("combinacoes").let { o ->
            o.keys().asSequence().associateWith { Grau.valueOf(o.getString(it)) }
        }
        val tabela2 = t2.getJSONObject("classes").let { o ->
            o.keys().asSequence().associateWith { o.getInt(it) }
        }
        val tabela3 = t3.getJSONObject("modalidades").let { o ->
            o.keys().asSequence().associateWith { o.getString(it) }
        }

        val criterios = t4.getJSONArray("criterios").let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                CriterioLocacional(
                    id = c.getString("id"),
                    peso = c.getInt("peso"),
                    texto = c.getString("texto"),
                    camada = if (c.isNull("camada")) null else c.getString("camada"),
                    automatico = c.optBoolean("automatico", false),
                    nota = if (c.isNull("nota")) null else c.optString("nota").ifBlank { null }
                )
            }
        }

        val fatores = t5.getJSONArray("fatores").let { arr ->
            (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                FatorRestricao(
                    id = f.getString("id"),
                    nome = f.getString("nome"),
                    texto = f.getString("texto"),
                    camada = if (f.isNull("camada")) null else f.getString("camada"),
                    automatico = f.optBoolean("automatico", false)
                )
            }
        }

        val g = mods.getJSONObject("regras_gerais")
        val gerais = RegrasGerais(
            prazoAnaliseDias = g.getInt("prazo_analise_dias"),
            prazoAnaliseTexto = g.getString("prazo_analise_texto"),
            prazoAnaliseEiaDias = g.getInt("prazo_analise_eia_dias"),
            prazoAnaliseEiaTexto = g.getString("prazo_analise_eia_texto"),
            renovacaoAntecedenciaDias = g.getInt("renovacao_antecedencia_dias"),
            renovacaoTexto = g.getString("renovacao_texto"),
            observacaoPrazo = g.getString("observacao_prazo")
        )

        val modalidades = mods.getJSONArray("modalidades").let { arr ->
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                val porLicenca = m.optJSONObject("validade_por_licenca")?.let { v ->
                    v.keys().asSequence().associateWith { v.getInt(it) }
                } ?: emptyMap()
                Modalidade(
                    sigla = m.getString("sigla"),
                    nome = m.getString("nome"),
                    descricao = m.getString("descricao"),
                    licencas = m.getJSONArray("licencas").let { a -> (0 until a.length()).map { a.getString(it) } },
                    etapas = m.getInt("etapas"),
                    estudos = m.getJSONArray("estudos").let { a -> (0 until a.length()).map { a.getString(it) } },
                    validadeAnos = m.getInt("validade_anos"),
                    validadeTexto = m.getString("validade_texto"),
                    validadePorLicenca = porLicenca,
                    audienciaPublica = m.optBoolean("audiencia_publica", false),
                    audienciaTexto = m.optString("audiencia_texto", "").ifBlank { null }
                )
            }
        }

        val atividades = ativ.getJSONArray("atividades").let { arr ->
            (0 until arr.length()).map { i ->
                val a = arr.getJSONObject(i)
                val pp = a.getJSONObject("pp")
                Atividade(
                    codigo = a.getString("codigo"),
                    descricao = a.getString("descricao"),
                    pp = PotencialPoluidor(
                        Grau.valueOf(pp.getString("ar")),
                        Grau.valueOf(pp.getString("agua")),
                        Grau.valueOf(pp.getString("solo")),
                        Grau.valueOf(pp.getString("geral"))
                    ),
                    tipo = a.getString("tipo"),
                    parametro = a.getString("parametro"),
                    unidade = a.optString("unidade", "").ifBlank { null },
                    limiteP = if (a.has("limiteP")) a.getDouble("limiteP") else null,
                    limiteM = if (a.has("limiteM")) a.getDouble("limiteM") else null,
                    limitePExclusivo = a.optBoolean("limitePExclusivo", false),
                    limiteMExclusivo = a.optBoolean("limiteMExclusivo", false),
                    unidadeAlternativa = a.optString("unidadeAlternativa", "").ifBlank { null },
                    limitePAlt = if (a.has("limitePAlt")) a.getDouble("limitePAlt") else null,
                    limiteMAlt = if (a.has("limiteMAlt")) a.getDouble("limiteMAlt") else null,
                    categorias = a.optJSONArray("categorias")?.let { c ->
                        (0 until c.length()).map { j ->
                            val cat = c.getJSONObject(j)
                            FaixaCategorica(cat.getString("rotulo"), Grau.valueOf(cat.getString("porte")))
                        }
                    } ?: emptyList(),
                    conferencia = Conferencia.valueOf(a.optString("conferencia", "unico").uppercase()),
                    nota = a.optString("nota", "").ifBlank { null }
                )
            }
        }

        val procedencia = mapOf(
            "norma" to proc.getString("norma"),
            "extraido_em" to proc.getString("extraido_em"),
            "aviso" to proc.getString("aviso"),
            "cobertura" to ativ.getString("cobertura")
        )

        return Regras(tabela1, tabela2, tabela3, criterios, fatores, modalidades, gerais, atividades, procedencia)
    }
}
