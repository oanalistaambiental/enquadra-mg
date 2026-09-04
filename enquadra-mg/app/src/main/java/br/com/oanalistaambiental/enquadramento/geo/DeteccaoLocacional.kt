package br.com.oanalistaambiental.enquadramento.geo

import br.com.oanalistaambiental.enquadramento.norma.CriterioLocacional
import br.com.oanalistaambiental.enquadramento.norma.FatorRestricao
import br.com.oanalistaambiental.enquadramento.norma.Regras
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateFilter
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import java.io.File
import kotlin.math.cos

/**
 * Sugestão automática dos critérios locacionais a partir de um ponto.
 *
 * O art. 6º, §5º da própria DN 217 manda o empreendedor consultar o IDE-Sisema para verificar
 * a incidência dos critérios das Tabelas 4 e 5. É exatamente o mesmo pacote GeoPackage que o
 * aplicativo de campo usa — um único pacote serve aos dois.
 *
 * A palavra "sugestão" é literal: o app aponta, quem decide é quem assina.
 */
class DeteccaoLocacional(private val pacote: GeoPacote) : AutoCloseable {

    private val gf = GeometryFactory()

    data class Incidencia(
        val criterios: List<CriterioLocacional>,
        val fatores: List<FatorRestricao>,
        val camadasNaoInstaladas: List<String>,
        val versaoPacote: String
    )

    /**
     * [margemM] amplia a busca. Diferente do aplicativo de campo, aqui não há GNSS ao vivo:
     * o ponto costuma vir de coordenada digitada, então a margem representa a incerteza
     * daquela coordenada, e o padrão é conservador.
     */
    fun verificar(regras: Regras, lat: Double, lon: Double, margemM: Double = 30.0): Incidencia {
        val zona = Utm.zonaDe(lon)
        val p = Utm.projetar(lat, lon, zona)
        val ponto = gf.createPoint(Coordinate(p.easting, p.northing))

        val infos = runCatching { pacote.camadas().associateBy { it.tabela } }.getOrDefault(emptyMap())
        val ausentes = mutableListOf<String>()

        fun intercepta(camada: String?): Boolean {
            if (camada == null) return false
            val info = infos[camada] ?: run { ausentes += camada; return false }

            // BUG corrigido: o alcance era fixo em 30 m e ignorava o raio gravado na camada.
            // Cavidade, por exemplo, é guardada como PONTO com raio de influência de 250 m —
            // com o alcance fixo, o critério deixaria de ser detectado. Subdetectar critério
            // locacional reduz a modalidade, que é o lado perigoso do erro.
            val alcance = margemM + info.toleranciaM + (info.raioM ?: 0.0)
            val dLat = alcance / 111_320.0
            val dLon = alcance / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.1))

            val candidatas = runCatching {
                pacote.candidatas(camada, lon - dLon, lat - dLat, lon + dLon, lat + dLat)
            }.getOrDefault(emptyList())
            return candidatas.any { f ->
                val g = projetar(f.geometria, zona)
                g.contains(ponto) || g.distance(ponto) <= alcance
            }
        }

        val criterios = regras.criterios.filter { it.automatico && intercepta(it.camada) }
        val fatores = regras.fatores.filter { it.automatico && intercepta(it.camada) }

        return Incidencia(
            criterios = criterios,
            fatores = fatores,
            camadasNaoInstaladas = ausentes.distinct(),
            versaoPacote = runCatching { pacote.versaoPacote() }.getOrDefault("desconhecida")
        )
    }

    private fun projetar(geom: Geometry, zona: Int): Geometry {
        val copia = geom.copy()
        copia.apply(CoordinateFilter { c ->
            val q = Utm.projetar(c.y, c.x, zona)   // GeoPackage guarda x=lon, y=lat
            c.x = q.easting
            c.y = q.northing
        })
        copia.geometryChanged()
        return copia
    }

    override fun close() = pacote.close()

    companion object {
        fun abrir(arquivo: File): DeteccaoLocacional = DeteccaoLocacional(GeoPacote(arquivo))
    }
}
