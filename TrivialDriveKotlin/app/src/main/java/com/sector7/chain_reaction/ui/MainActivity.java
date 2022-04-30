package com.sector7.chain_reaction.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.snackbar.Snackbar;
import com.sector7.chain_reaction.MainActivityViewModel;
import com.sector7.chain_reaction.R;
import com.sector7.chain_reaction.TrivialDriveApplication;
import com.sector7.chain_reaction.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding activityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        // Setup toolbar with nav controller
        NavController navController = navHostFragment.getNavController();
        AppBarConfiguration appBarConfiguration =
                new AppBarConfiguration.Builder(navController.getGraph()).build();
        Toolbar toolbar = activityMainBinding.toolbar;
        setSupportActionBar(toolbar);
        NavigationUI.setupWithNavController(
                toolbar, navController, appBarConfiguration);

        MainActivityViewModel.MainActivityViewModelFactory mainActivityViewModelFactory = new
                MainActivityViewModel.MainActivityViewModelFactory(
                ((TrivialDriveApplication) getApplication()).getAppContainer().
                        getTrivialDriveRepository());
        MainActivityViewModel mainActivityViewModel = new ViewModelProvider(this, mainActivityViewModelFactory)
                .get(MainActivityViewModel.class);

        // Create our Activity ViewModel, which exists to handle global Snackbar messages
        mainActivityViewModel.getMessages().observe(this, resourceId -> {
            Snackbar snackbar = Snackbar.make(activityMainBinding.mainLayout, getString(resourceId), Snackbar.LENGTH_SHORT);
            snackbar.show();
        });
        // Allows billing to refresh purchases during onResume
        getLifecycle().addObserver(mainActivityViewModel.getBillingLifecycleObserver());
    }
}
