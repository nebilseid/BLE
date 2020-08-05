package com.example.bluetoothtest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_log_entry.view.*

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogItemViewHolder>() {

    private val items = mutableListOf<String>()

    fun addItem(item: String) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LogItemViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: LogItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class LogItemViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        fun bind(text: String) {
            containerView.tv_entry.text = text
        }
    }
}