package com.toshi.extensions

import android.support.v7.widget.RecyclerView


fun <VH: RecyclerView.ViewHolder>RecyclerView.Adapter<VH>.isEmpty(): Boolean {
    return (this.itemCount == 0)
}