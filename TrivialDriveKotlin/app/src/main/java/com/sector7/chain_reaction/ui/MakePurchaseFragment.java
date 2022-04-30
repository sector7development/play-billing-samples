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
import androidx.lifecycle.FlowLiveDataConversions;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sector7.chain_reaction.MakePurchaseViewModel;
import com.sector7.chain_reaction.MakePurchaseViewModelFactory;
import com.sector7.chain_reaction.R;
import com.sector7.chain_reaction.TrivialDriveApplication;
import com.sector7.chain_reaction.TrivialDriveRepository;
import com.sector7.chain_reaction.databinding.FragmentMakePurchaseBinding;

import java.util.ArrayList;
import java.util.List;

public class MakePurchaseFragment extends Fragment {
    private MakePurchaseViewModel makePurchaseViewModel;
    private FragmentMakePurchaseBinding binding;
    private final List<MakePurchaseAdapter.Item> inventoryList = new ArrayList<>();

    void makeInventoryList() {
        inventoryList.add(new MakePurchaseAdapter.Item(
                getText(R.string.header_fuel_your_ride), MakePurchaseAdapter.VIEW_TYPE_HEADER
        ));
        inventoryList.add(new MakePurchaseAdapter.Item(
                getText(R.string.header_go_premium), MakePurchaseAdapter.VIEW_TYPE_HEADER
        ));
        inventoryList.add(new MakePurchaseAdapter.Item(
                TrivialDriveRepository.SKU_TABLET_BOARDS, MakePurchaseAdapter.VIEW_TYPE_ITEM
        ));
        inventoryList.add(new MakePurchaseAdapter.Item(
                getText(R.string.header_subscribe), MakePurchaseAdapter.VIEW_TYPE_HEADER
        ));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_make_purchase, container, false);
        // This allows data binding to automatically observe any LiveData we pass in
        binding.setLifecycleOwner(this);
        makeInventoryList();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MakePurchaseViewModelFactory makePurchaseViewModelFactory =
                new MakePurchaseViewModelFactory(
                        ((TrivialDriveApplication) getActivity().getApplication()).getAppContainer()
                                .getTrivialDriveRepository());
        makePurchaseViewModel = new ViewModelProvider(this, makePurchaseViewModelFactory)
                .get(MakePurchaseViewModel.class);

        binding.setMpvm(makePurchaseViewModel);
        binding.inappInventory.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.inappInventory.setAdapter(new MakePurchaseAdapter(inventoryList, makePurchaseViewModel, this));
    }

    public void makePurchase(@NonNull String sku) {
        makePurchaseViewModel.buySku(getActivity(), sku);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public LiveData<Boolean> canBuySku(String sku) {
        return makePurchaseViewModel.canBuySku(sku);
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

    public LiveData<CharSequence> skuTitle(final @NonNull String sku) {
        TrivialDriveRepository tdr = ((TrivialDriveApplication) getActivity().getApplication()).getAppContainer()
                .getTrivialDriveRepository();
        final LiveData<String> skuTitleLiveData = FlowLiveDataConversions.asLiveData(tdr.getSkuTitle(sku));
        final LiveData<Boolean> isPurchasedLiveData = makePurchaseViewModel.isPurchased(sku);
        final MediatorLiveData<CharSequence> result = new MediatorLiveData<>();
        result.addSource(skuTitleLiveData, title ->
                combineTitleSkuAndIsPurchasedData(result, skuTitleLiveData, isPurchasedLiveData, sku));
        result.addSource(isPurchasedLiveData, isPurchased ->
                combineTitleSkuAndIsPurchasedData(result, skuTitleLiveData, isPurchasedLiveData, sku));
        return result;
    }
}
