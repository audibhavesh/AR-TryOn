package com.theoctacoder.cameraxtryon.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.theoctacoder.cameraxtryon.databinding.ItemShirtBinding

// ShirtAdapter.kt
class ShirtAdapter(
    private val shirtImages: List<Int>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ShirtAdapter.ShirtViewHolder>() {

    inner class ShirtViewHolder(val binding: ItemShirtBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                onClick(shirtImages[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShirtViewHolder {
        val binding = ItemShirtBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShirtViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShirtViewHolder, position: Int) {
        val shirtImage = shirtImages[position]
        holder.binding.imageViewShirt.setImageResource(shirtImage)
    }

    override fun getItemCount(): Int = shirtImages.size
}
