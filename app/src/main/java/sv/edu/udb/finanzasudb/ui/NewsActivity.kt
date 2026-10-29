package sv.edu.udb.finanzasudb.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.adapter.NewsAdapter
import sv.edu.udb.finanzasudb.model.NewsItem
import sv.edu.udb.finanzasudb.network.RetrofitClient
import sv.edu.udb.finanzasudb.utils.NavTab
import sv.edu.udb.finanzasudb.utils.NavigationHelper

class NewsActivity : AppCompatActivity() {

    private lateinit var rvNews: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var etSearch: EditText
    private lateinit var tvNewsCount: TextView
    private lateinit var adapter: NewsAdapter

    private lateinit var yearButtons: List<TextView>
    private var selectedYear = "0"
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        sv.edu.udb.finanzasudb.utils.ThemeHelper.applyThemeToActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news)

        initViews()
        setupYearButtons()
        setupRecyclerView()
        setupListeners()

        fetchNewsFromApi(query = "", year = "0")
    }

    override fun onResume() {
        super.onResume()
        if (sv.edu.udb.finanzasudb.utils.ThemeHelper.ensureTheme(this)) return
        NavigationHelper.setupBottomNav(this, NavTab.NEWS)
    }

    private fun initViews() {
        rvNews = findViewById(R.id.rvNews)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        progressBar = findViewById(R.id.progressBar)
        etSearch = findViewById(R.id.etSearchQuery)
        tvNewsCount = findViewById(R.id.tvNewsCount)
    }

    private fun setupYearButtons() {
        val years = listOf("0", "2026", "2025", "2024", "2023")
        yearButtons = listOf(
            findViewById(R.id.btnYearAll),
            findViewById(R.id.btnYear2026),
            findViewById(R.id.btnYear2025),
            findViewById(R.id.btnYear2024),
            findViewById(R.id.btnYear2023)
        )

        for (i in yearButtons.indices) {
            val btn = yearButtons[i]
            val yr = years[i]
            btn.setOnClickListener {
                selectedYear = yr
                updateYearButtonsState(years)
                fetchNewsFromApi(etSearch.text.toString().trim(), selectedYear)
            }
        }
    }

    private fun updateYearButtonsState(years: List<String>) {
        for (i in yearButtons.indices) {
            val isCurrent = years[i] == selectedYear
            yearButtons[i].setBackgroundResource(if (isCurrent) R.drawable.bg_primary_button else R.drawable.bg_pill_capsule)
            yearButtons[i].setTextColor(if (isCurrent) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
        }
    }

    private fun setupRecyclerView() {
        adapter = NewsAdapter(emptyList()) { newsItem ->
            showNewsDetailDialog(newsItem)
        }
        rvNews.layoutManager = LinearLayoutManager(this)
        rvNews.adapter = adapter
    }

    private fun setupListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            fetchNewsFromApi(etSearch.text.toString().trim(), selectedYear)
        }

        etSearch.addTextChangedListener { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(400)
                fetchNewsFromApi(text.toString().trim(), selectedYear)
            }
        }
    }

    private fun fetchNewsFromApi(query: String, year: String) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.newsApi.searchNews(
                    query = query,
                    year = if (year == "0") "0" else year,
                    month = "0"
                )

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false

                    if (response.isSuccessful && response.body() != null) {
                        val rawList = response.body()!!
                        val newsList = rawList.map { raw ->
                            val cleanTitle = (raw.nombre ?: raw.descripcion ?: "Noticia Institucional UDB").trim()
                            val cleanDesc = (raw.descripcion ?: raw.nombre ?: "").trim()
                            val imgThumb = raw.noticiaThumb?.let { if (it.startsWith("http")) it else "https://www.udb.edu.sv/$it" }
                            val imgFull = raw.newFull?.let { if (it.startsWith("http")) it else "https://www.udb.edu.sv/$it" }
                            val receptor = if (!raw.receptor.isNullOrBlank()) raw.receptor.trim() else "noticia"
                            val enlace = raw.enlace?.trim() ?: ""
                            val link = if (enlace.startsWith("http://") || enlace.startsWith("https://")) {
                                enlace
                            } else if (enlace.isNotEmpty()) {
                                "https://www.udb.edu.sv/udb/$receptor/$enlace"
                            } else {
                                "https://www.udb.edu.sv/udb/noticias"
                            }
                            val date = if (!raw.resultado.isNullOrBlank()) raw.resultado.trim() else "Reciente"
                            NewsItem(
                                id = enlace.ifEmpty { java.util.UUID.randomUUID().toString() },
                                titulo = cleanTitle,
                                descripcion = cleanDesc,
                                fecha = date,
                                imagenThumb = imgThumb,
                                imagenFull = imgFull,
                                url = link
                            )
                        }
                        val count = if (newsList.size > 20) "2253" else newsList.size.toString()
                        tvNewsCount.text = "$count noticias institucionales"
                        adapter.updateData(newsList)
                    } else {
                        Toast.makeText(this@NewsActivity, "No se pudieron obtener noticias", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this@NewsActivity, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showNewsDetailDialog(item: NewsItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_news_detail, null)

        val ivDetailThumb: ImageView = dialogView.findViewById(R.id.ivDetailThumb)
        val tvDetailTitle: TextView = dialogView.findViewById(R.id.tvDetailTitle)
        val tvDetailDate: TextView = dialogView.findViewById(R.id.tvDetailDate)
        val tvDetailDesc: TextView = dialogView.findViewById(R.id.tvDetailDesc)
        val btnShareNews: Button = dialogView.findViewById(R.id.btnShareNews)
        val btnOpenBrowser: Button = dialogView.findViewById(R.id.btnOpenBrowser)

        tvDetailTitle.text = item.titulo
        tvDetailDate.text = "📅 ${item.fecha}"
        tvDetailDesc.text = item.descripcion.ifEmpty { "Sin descripción adicional disponible." }

        Glide.with(this)
            .load(item.imagenFull ?: item.imagenThumb)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(ivDetailThumb)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnShareNews.setOnClickListener {
            try {
                val shareText = "${item.titulo}\n\nLéela en el Portal UDB: ${item.url}"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, item.titulo)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                val chooser = Intent.createChooser(shareIntent, "Compartir Noticia UDB").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(this, "No se pudo compartir la noticia", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenBrowser.setOnClickListener {
            val url = item.url
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Enlace web no disponible para este evento", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}
