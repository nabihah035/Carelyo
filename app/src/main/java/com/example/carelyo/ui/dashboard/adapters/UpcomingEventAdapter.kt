package com.example.carelyo.ui.dashboard.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carelyo.R
import com.example.carelyo.databinding.ItemUpcomingEventBinding
import com.example.carelyo.ui.dashboard.models.UpcomingEvent

class UpcomingEventAdapter(
    private var items: List<UpcomingEvent>,
    private val onItemClick: (UpcomingEvent) -> Unit
) : RecyclerView.Adapter<UpcomingEventAdapter.UpcomingEventViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpcomingEventViewHolder {
        val binding = ItemUpcomingEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UpcomingEventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UpcomingEventViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<UpcomingEvent>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class UpcomingEventViewHolder(
        private val binding: ItemUpcomingEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UpcomingEvent) {
            binding.tvEventTitle.text = item.title
            binding.tvEventDescription.text = item.description
            binding.tvEventDate.text = item.date

            // Set icon based on event type
            val iconRes = when (item.type) {
                UpcomingEvent.Type.VACCINATION -> R.drawable.ic_vaccine
                UpcomingEvent.Type.MEDICATION -> R.drawable.ic_medication
                UpcomingEvent.Type.APPOINTMENT -> R.drawable.ic_appointment
            }
            binding.ivEventIcon.setImageResource(iconRes)

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}