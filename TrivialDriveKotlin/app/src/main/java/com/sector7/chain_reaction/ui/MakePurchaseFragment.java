package com.sector7.chain_reaction.ui;

import android.os.Bundle;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sector7.chain_reaction.MakePurchaseViewModel;
import com.sector7.chain_reaction.MakePurchaseViewModelFactory;
import com.sector7.chain_reaction.R;
import com.sector7.chain_reaction.databinding.FragmentMakePurchaseBinding;

import java.util.ArrayList;
import java.util.List;

public class MakePurchaseFragment extends Fragment {
    private MakePurchaseViewModel makePurchaseViewModel;
    private FragmentMakePurchaseBinding binding;
    private final List<MakePurchaseAdapter.Item> inventoryList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_make_purchase, container, false);
        // This allows data binding to automatically observe any LiveData we pass in
        binding.setLifecycleOwner(this);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        makePurchaseViewModel = new ViewModelProvider(this, new MakePurchaseViewModelFactory()).get(MakePurchaseViewModel.class);

        binding.setMpvm(makePurchaseViewModel);
        binding.inappInventory.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.inappInventory.setAdapter(new MakePurchaseAdapter(inventoryList, makePurchaseViewModel, this));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void combineTitleSkuAndIsPurchasedData(
            MediatorLiveData<CharSequence> result,
            LiveData<String> skuTitleLiveData,
            LiveData<Boolean> isPurchasedLiveData,
            @NonNull String sku
    ) {
        String skuTitle = skuTitleLiveData.getValue();
        Boolean isPurchased = isPurchasedLiveData.getValue();
        // don't emit until we have all of our data
        if (skuTitle == null || isPurchased == null) {
            return;
        }
        SpannableString titleSpannable = new SpannableString(skuTitle);
        result.setValue(titleSpannable);
    }

    public LiveData<CharSequence> skuTitle() {
        return new MediatorLiveData<>();
    }
}
