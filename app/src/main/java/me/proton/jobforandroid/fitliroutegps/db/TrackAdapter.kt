package me.proton.jobforandroid.fitliroutegps.db

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.proton.jobforandroid.fitliroutegps.R
import me.proton.jobforandroid.fitliroutegps.databinding.TrackItemBinding

class TrackAdapter : ListAdapter<TrackItem, TrackAdapter.Holder>(Comparator()) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = TrackItemBinding.bind(view)

        fun bind(track: TrackItem) = with(binding) {

            val distance = "${track.distance} km"
            val velocity = "${track.velocity} km/h"
            val time = "${track.time} s"
            tvDate.text = track.date
            tvDistance.text = distance
            tvVelocity.text = velocity
            tvTime.text = time
        }
    }

    class Comparator : DiffUtil.ItemCallback<TrackItem>() {
        override fun areItemsTheSame(oldItem: TrackItem, newItem: TrackItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrackItem, newItem: TrackItem): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.track_item, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }
}