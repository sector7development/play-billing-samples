/*
 * Copyright (C) 2021 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sector7.chain_reaction.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.sector7.chain_reaction.MakePurchaseViewModel;
import com.sector7.chain_reaction.R;
import com.sector7.chain_reaction.databinding.InventoryHeaderBinding;
import com.sector7.chain_reaction.databinding.InventoryItemBinding;

import java.util.List;

/**
 * Basic implementation of RecyclerView adapter with header and content views.
 */
public class MakePurchaseAdapter extends RecyclerView.Adapter<MakePurchaseAdapter.ViewHolder> {
    static public final int VIEW_TYPE_HEADER = 0;
    static public final int VIEW_TYPE_ITEM = 1;
    private final List<Item> inventoryList;
    private final MakePurchaseViewModel makePurchaseViewModel;
    private final MakePurchaseFragment makePurchaseFragment;

    public MakePurchaseAdapter(@NonNull List<Item> inventoryList,
                               @NonNull MakePurchaseViewModel makePurchaseViewModel,
                               @NonNull MakePurchaseFragment makePurchaseFragment) {
        this.inventoryList = inventoryList;
        this.makePurchaseViewModel = makePurchaseViewModel;
        this.makePurchaseFragment = makePurchaseFragment;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        InventoryItemBinding inventoryItemBinding = null;
        InventoryHeaderBinding inventoryHeaderBinding = null;
        // VIEW_TYPE_ITEM
        if (viewType == VIEW_TYPE_HEADER) {
            inventoryHeaderBinding = DataBindingUtil.inflate(layoutInflater, R.layout.inventory_header, parent,
                    false);
            view = inventoryHeaderBinding.getRoot();
        } else {
            inventoryItemBinding = DataBindingUtil.inflate(layoutInflater, R.layout.inventory_item, parent,
                    false);
            view = inventoryItemBinding.getRoot();
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    }

    @Override
    public int getItemCount() {
        return inventoryList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return inventoryList.get(position).viewType;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }
    }

    /**
     * An item to be displayed in our RecyclerView. Each item contains a single string: either
     * the title of a header or a reference to a SKU, depending on what the type of the view is.
     */
    static class Item {
        public Item(int viewType) {
            this.viewType = viewType;
        }

        private final int viewType;
    }
}
