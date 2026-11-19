package com.kaelltzyy.kaellhub.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kaelltzyy.kaellhub.databinding.ItemGameBinding
import com.kaelltzyy.kaellhub.model.Game
import java.io.File

class GameAdapter(
    private val onLaunch: (Game) -> Unit,
    private val onSettings: (Game) -> Unit
) : ListAdapter<Game, GameAdapter.VH>(DIFF) {

    inner class VH(val b: ItemGameBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val game = getItem(position)
        holder.b.tvGameName.text = game.name
        holder.b.tvGamePath.text = File(game.exePath).name
        holder.b.root.setOnClickListener { onLaunch(game) }
        holder.b.root.setOnLongClickListener { onSettings(game); true }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Game>() {
            override fun areItemsTheSame(a: Game, b: Game) = a.id == b.id
            override fun areContentsTheSame(a: Game, b: Game) = a == b
        }
    }
}
