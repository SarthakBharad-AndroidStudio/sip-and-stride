package com.example.sipandstride

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Feeds walk sessions into the RecyclerView.
 *
 * A RecyclerView shows only as many rows as fit on screen and reuses them while scrolling,
 * which is why it needs an adapter with three parts (Tutorial 5):
 *   onCreateViewHolder  – build one empty row
 *   onBindViewHolder    – fill that row with the data at a position
 *   getItemCount        – how many rows exist in total
 */
class SessionAdapter(
    private var items: List<WalkSession>,
    private val listener: OnSessionClickListener
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    /**
     * RecyclerView has no setOnItemClickListener() like ListView, so we define our own
     * one-method interface. "fun interface" lets MainActivity pass a lambda instead of
     * writing out an anonymous object.
     */
    fun interface OnSessionClickListener {
        fun onSessionClick(session: WalkSession)
    }

    /**
     * Holds the findViewById() results for one row. Without it, every scroll step would
     * search the view tree again, which is slow.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val steps: TextView = view.findViewById(R.id.itemSteps)
        val info: TextView = view.findViewById(R.id.itemInfo)
        val distance: TextView = view.findViewById(R.id.itemDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.steps.text = context.getString(R.string.detail_steps, item.steps)

        val place = item.startAddress.ifEmpty { context.getString(R.string.unknown_place) }
        holder.info.text = "${item.formattedDate()} · $place"

        holder.distance.text = "${item.meters} m"

        holder.itemView.setOnClickListener { listener.onSessionClick(item) }
    }

    override fun getItemCount(): Int = items.size

    /** Replaces the data and repaints the list. */
    fun submit(newItems: List<WalkSession>) {
        items = newItems
        notifyDataSetChanged()
    }
}