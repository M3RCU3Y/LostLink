package com.comp3606.lostlink.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

// One small adapter reused by every list: give it a row layout and a function that fills a row.
public class ListAdapter<T> extends RecyclerView.Adapter<ListAdapter.Holder> {
    public interface Binder<T> {
        void bind(View row, T item, int position);
    }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(View v) {
            super(v);
        }
    }

    private final int layout;
    private final Binder<T> binder;
    private List<T> items = new ArrayList<>();

    public ListAdapter(int layout, Binder<T> binder) {
        this.layout = layout;
        this.binder = binder;
    }

    public void submit(List<T> list) {
        items = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        binder.bind(holder.itemView, items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
