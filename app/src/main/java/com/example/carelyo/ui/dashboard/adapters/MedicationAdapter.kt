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
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<MedicationItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItems(): List<MedicationItem> = items

    fun updateItemCompletion(medicationId: Int, childId: Int, scheduledTime: String?, isCompleted: Boolean) {
        val index = items.indexOfFirst {
            it.medicationId == medicationId &&
                    it.childId == childId &&
                    it.scheduledTime == scheduledTime
        }
        if (index != -1) {
            val updatedItem = items[index].copy(isCompleted = isCompleted)
            items = items.toMutableList().apply { set(index, updatedItem) }
            notifyItemChanged(index)
        }
    }

    inner class MedicationViewHolder(
        private val binding: ItemMedicationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MedicationItem) {
            binding.apply {
                tvMedicationName.text = item.name
                tvMedicationDosage.text = item.dosage
                tvMedicationTime.text = item.time
                cbMedication.isChecked = item.isCompleted

                // Important: Set the checked change listener
                cbMedication.setOnCheckedChangeListener(null)
                cbMedication.isChecked = item.isCompleted
                cbMedication.setOnCheckedChangeListener { _, isChecked ->
                    val updatedItem = item.copy(isCompleted = isChecked)
                    onItemCheckChanged(updatedItem, isChecked)
                    // Update the item in the list to maintain consistency
                    val position = items.indexOf(item)
                    if (position != -1) {
                        items = items.toMutableList().apply { set(position, updatedItem) }
                    }
                }
            }
        }
    }
}