package com.example.carelyo.ui.dashboard.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.databinding.ItemMedicationBinding
import com.example.carelyo.ui.dashboard.models.MedicationItem

class MedicationAdapter(
    private var items: List<MedicationItem>,
    private val onItemCheckChanged: (MedicationItem, Boolean) -> Unit
) : RecyclerView.Adapter<MedicationAdapter.MedicationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationViewHolder {
        val binding = ItemMedicationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MedicationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MedicationViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<MedicationItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItems(): List<MedicationItem> = items

    inner class MedicationViewHolder(
        private val binding: ItemMedicationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MedicationItem) {
            binding.tvMedicationName.text = item.name
            binding.tvMedicationDosage.text = item.dosage
            binding.tvMedicationTime.text = item.time

            binding.cbMedication.isChecked = item.isCompleted
            binding.cbMedication.setOnCheckedChangeListener { _, isChecked ->
                // Update the item's completion status
                val updatedItem = item.copy(isCompleted = isChecked)
                // Update the item in the list
                val index = items.indexOf(item)
                if (index != -1) {
                    val mutableList = items.toMutableList()
                    mutableList[index] = updatedItem
                    items = mutableList
                }
                onItemCheckChanged(updatedItem, isChecked)
            }

            // Strike through text if completed
            binding.tvMedicationName.paint.isStrikeThruText = item.isCompleted
            binding.tvMedicationDosage.paint.isStrikeThruText = item.isCompleted
        }
    }
}