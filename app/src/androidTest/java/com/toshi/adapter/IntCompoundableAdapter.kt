package com.toshi.adapter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView
import com.toshi.view.adapter.CompoundableAdapter

class IntViewHolder(val view: ViewGroup): RecyclerView.ViewHolder(view) {

    val textView: TextView by lazy {
        val textView = TextView(view.context)
        view.addView(textView)
        textView
    }

    fun displayValue(value: Int) {
        textView.text = "$value"
    }
}

class IntCompoundableAdapter(
        val ints: List<Int>
): RecyclerView.Adapter<IntViewHolder>(), CompoundableAdapter {
    override fun genericBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val typedHolder = viewHolder as? IntViewHolder ?: return
        onBindViewHolder(typedHolder, position)
    }

    override fun genericCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return onCreateViewHolder(parent, viewType)
    }

    override fun genericItemCount(): Int {
        return itemCount
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntViewHolder {
        return IntViewHolder(parent)
    }

    override fun onBindViewHolder(holder: IntViewHolder, position: Int) {
        val int = ints[position]
        holder.displayValue(int)
    }


    override fun getItemCount(): Int {
        return ints.size
    }
}