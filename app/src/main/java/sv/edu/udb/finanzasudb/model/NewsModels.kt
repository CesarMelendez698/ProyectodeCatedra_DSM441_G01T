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
