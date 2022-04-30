package com.sector7.chain_reaction.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.sector7.chain_reaction.GameViewModel;
import com.sector7.chain_reaction.R;
import com.sector7.chain_reaction.TrivialDriveApplication;
import com.sector7.chain_reaction.databinding.FragmentGameBinding;

public class GameFragment extends Fragment {
    private final String TAG = "GameFragment";

    private GameViewModel gameViewModel;
    private FragmentGameBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.v(TAG, "onCreateView");
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_game, container, false);
        binding.setLifecycleOwner(this);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.v(TAG, "onViewCreated");

        GameViewModel.GameViewModelFactory gameViewModelFactory =
                new GameViewModel.GameViewModelFactory(
                        ((TrivialDriveApplication)getActivity().getApplication()).getAppContainer()
                                .getTrivialDriveRepository());

        gameViewModel = new ViewModelProvider(this,gameViewModelFactory)
                .get(GameViewModel.class);

        binding.setGvm(gameViewModel);
        binding.setGameFragment(this);
    }

    public void purchase(View view) {
        Navigation.findNavController(view).navigate(R.id.action_gameFragment_to_makePurchaseFragment);
    }
}
