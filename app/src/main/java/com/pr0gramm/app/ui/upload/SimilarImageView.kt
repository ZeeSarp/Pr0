package com.pr0gramm.app.ui.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.HasThumbnail
import com.pr0gramm.app.R
import com.pr0gramm.app.services.UriHelper
import com.squareup.picasso.Picasso

/**
 */
class SimilarImageView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    }

    fun setThumbnails(thumbnails: Iterable<HasThumbnail>) {
        adapter = ThumbnailAdapter(thumbnails.toList())
    }

    private inner class ThumbnailAdapter(val thumbnails: List<HasThumbnail>) : RecyclerView.Adapter<ThumbnailViewHolder>() {
        val picasso: Picasso = appKodein().instance()

        override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
            val thumb = thumbnails[position]

            val imageUri = UriHelper.of(context).thumbnail(thumb)
            picasso.load(imageUri)
                    .config(Bitmap.Config.RGB_565)
                    .placeholder(ColorDrawable(0xff333333.toInt()))
                    .into(holder.image)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.thumbnail, parent, false)
            return ThumbnailViewHolder(view)
        }

        override fun getItemCount(): Int {
            return thumbnails.size
        }
    }

    private class ThumbnailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView as ImageView
    }
}
