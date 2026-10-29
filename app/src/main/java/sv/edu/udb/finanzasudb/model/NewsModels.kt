package sv.edu.udb.finanzasudb.model

import com.google.gson.annotations.SerializedName

/**
 * Mapeo del JSON original devuelto por el servicio web de la Universidad Don Bosco
 */
data class RawUdbNewsItem(
    @SerializedName("Nombre") val nombre: String?,
    @SerializedName("Descripcion") val descripcion: String?,
    @SerializedName("Resultado") val resultado: String?,
    @SerializedName("Noticia") val noticiaThumb: String?,
    @SerializedName("New_full") val newFull: String?,
    @SerializedName("Enlace") val enlace: String?,
    @SerializedName("Receptor") val receptor: String?
)

/**
 * Modelo de dominio para representar la noticia en RecyclerView
 */
data class NewsItem(
    val id: String = "",
    val titulo: String = "",
    val descripcion: String = "",
    val fecha: String = "",
    val imagenThumb: String? = null,
    val imagenFull: String? = null,
    val url: String = ""
) {
    val enlaceWeb: String get() = url
}
