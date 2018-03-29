package com.toshi.adapter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView
import com.toshi.view.adapter.BaseCompoundableAdapter

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

class StringCompoundableAdapter(strings: List<String>): BaseCompoundableAdapter<StringViewHolder, String>() {

    init {
        setItemList(strings)
    }

    override fun compoundableBindViewHolder(viewHolder: RecyclerView.ViewHolder, adapterIndex: Int) {
        val typedHolder = viewHolder as? StringViewHolder ?: return
        onBindViewHolder(typedHolder, adapterIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StringViewHolder {
        return StringViewHolder(parent)
    }

    override fun onBindViewHolder(holder: StringViewHolder, position: Int) {
        val string = itemAt(position)
        holder.displayValue(string)
    }
}