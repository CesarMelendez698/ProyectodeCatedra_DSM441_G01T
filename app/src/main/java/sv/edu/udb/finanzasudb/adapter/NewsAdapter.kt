package sv.edu.udb.finanzasudb.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.model.NewsItem

class NewsAdapter(
    private var newsList: List<NewsItem>,
    private val onItemClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    fun updateData(newList: List<NewsItem>) {
        newsList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(newsList[position], onItemClick)
    }

    override fun getItemCount(): Int = newsList.size

    class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb: ImageView = itemView.findViewById(R.id.ivNewsThumb)
        private val tvDate: TextView = itemView.findViewById(R.id.tvNewsDate)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvNewsTitle)
        private val btnViewPortal: Button = itemView.findViewById(R.id.btnViewPortal)
        private val btnShareNews: ImageButton = itemView.findViewById(R.id.btnShareNews)

        fun bind(item: NewsItem, onItemClick: (NewsItem) -> Unit) {
            tvTitle.text = item.titulo
            tvDate.text = "📅 ${item.fecha.ifEmpty { "Reciente" }}"

            Glide.with(itemView.context)
                .load(item.imagenThumb ?: item.imagenFull)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_dialog_alert)
                .into(ivThumb)

            val openInBrowser = {
                val url = item.enlaceWeb
                if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        itemView.context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Toast.makeText(itemView.context, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    onItemClick(item)
                }
            }

            btnViewPortal.setOnClickListener { openInBrowser() }
            itemView.setOnClickListener { onItemClick(item) }

            btnShareNews.setOnClickListener {
                shareNewsItem(itemView.context, item)
            }
        }

        private fun shareNewsItem(context: Context, item: NewsItem) {
            try {
                val shareText = "${item.titulo}\n\nLéela en el Portal UDB: ${item.enlaceWeb}"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, item.titulo)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                val chooser = Intent.createChooser(shareIntent, "Compartir Noticia UDB").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo abrir las opciones de compartir", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
