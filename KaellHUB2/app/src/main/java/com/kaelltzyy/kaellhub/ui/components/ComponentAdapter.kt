package com.kaelltzyy.kaellhub.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kaelltzyy.kaellhub.core.wine.Component
import com.kaelltzyy.kaellhub.databinding.ItemComponentBinding

class ComponentAdapter(
    private val components: List<Component>,
    private val isInstalled: (Component) -> Boolean,
    private val onInstall: (Component) -> Unit
) : RecyclerView.Adapter<ComponentAdapter.VH>() {

    private val progress = mutableMapOf<String, Int>()   // id → pct
    private val done     = mutableMapOf<String, Boolean>()
    private val errors   = mutableMapOf<String, String?>()

    inner class VH(val b: ItemComponentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemComponentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = components.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val comp = components[position]
        val installed = isInstalled(comp)
        val pct  = progress[comp.id] ?: -1
        val isDone = done[comp.id] ?: false
        val err  = errors[comp.id]

        holder.b.tvName.text = comp.name
        holder.b.tvDesc.text = comp.description
        holder.b.tvVersion.text = "v${comp.version}"

        when {
            installed || isDone -> {
                holder.b.btnInstall.text = "Installed ✓"
                holder.b.btnInstall.isEnabled = false
                holder.b.progressBar.visibility = View.GONE
            }
            pct >= 0 -> {
                holder.b.btnInstall.text = "Downloading $pct%"
                holder.b.btnInstall.isEnabled = false
                holder.b.progressBar.visibility = View.VISIBLE
                holder.b.progressBar.progress = pct
            }
            err != null -> {
                holder.b.btnInstall.text = "Retry"
                holder.b.btnInstall.isEnabled = true
                holder.b.progressBar.visibility = View.GONE
                holder.b.tvDesc.text = "Error: $err"
            }
            else -> {
                holder.b.btnInstall.text = if (comp.required) "Install (Required)" else "Install"
                holder.b.btnInstall.isEnabled = true
                holder.b.progressBar.visibility = View.GONE
                holder.b.btnInstall.setOnClickListener { onInstall(comp) }
            }
        }
    }

    fun updateProgress(id: String, pct: Int, isDone: Boolean, error: String?) {
        progress[id] = pct
        done[id] = isDone
        errors[id] = error
        val idx = components.indexOfFirst { it.id == id }
        if (idx >= 0) notifyItemChanged(idx)
    }
}
