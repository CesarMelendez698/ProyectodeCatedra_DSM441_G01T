package sv.edu.udb.finanzasudb.network

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST
import sv.edu.udb.finanzasudb.model.RawUdbNewsItem

interface NewsApiService {

    @FormUrlEncoded
    @Headers(
        "X-Requested-With: XMLHttpRequest",
        "Accept: application/json, text/javascript, */*; q=0.01"
    )
    @POST("udb/index.php/noticias/buscar")
    suspend fun searchNews(
        @Field("frm-search-new") query: String = "",
        @Field("frm-search-year") year: String = "0",
        @Field("frm-search-month") month: String = "0"
    ): Response<List<RawUdbNewsItem>>
}
