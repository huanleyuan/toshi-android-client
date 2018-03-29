package com.toshi.adapter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView
import com.toshi.R.id.textView
import com.toshi.view.adapter.CompoundableAdapter


class StringViewHolder(val view: ViewGroup): RecyclerView.ViewHolder(view) {

    val textView: TextView by lazy {
        val textView = TextView(view.context)
        view.addView(textView)
        textView
    }

    fun displayValue(value: String) {
        textView.text = value
    }
}

class StringCompoundableAdapter(
        val strings: List<String>
): RecyclerView.Adapter<StringViewHolder>(), CompoundableAdapter {
    override fun genericBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val typedHolder = viewHolder as? StringViewHolder ?: return
        onBindViewHolder(typedHolder, position)
    }

    override fun genericCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return onCreateViewHolder(parent, viewType)
    }

    override fun genericItemCount(): Int {
        return itemCount
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StringViewHolder {
        return StringViewHolder(parent)
    }

    override fun onBindViewHolder(holder: StringViewHolder, position: Int) {
        val string = strings[position]
        holder.displayValue(string)
    }


    override fun getItemCount(): Int {
        return strings.size
    }
}