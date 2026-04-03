package com.seteclabs.drivermanager.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.seteclabs.drivermanager.R;
import com.seteclabs.drivermanager.manager.DriverRegistry;
import com.seteclabs.drivermanager.manager.KoManager;
import com.seteclabs.drivermanager.manager.ProtectionManager;
import com.seteclabs.drivermanager.manager.RootShell;
import com.seteclabs.drivermanager.manager.ScopeManager;

/**
 * Main activity with bottom navigation.
 * Tabs: Drivers, Apps (scope), Modules (.ko), Protection
 */
public class MainActivity extends AppCompatActivity {

    private DriverRegistry driverRegistry;
    private ScopeManager scopeManager;
    private KoManager koManager;
    private ProtectionManager protectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize root and config directories
        RootShell.ensureConfigDirs();

        // Initialize managers
        driverRegistry = new DriverRegistry(this);
        scopeManager = new ScopeManager(this, driverRegistry);
        koManager = new KoManager();
        protectionManager = new ProtectionManager(this, driverRegistry);

        // Bottom navigation
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_drivers) {
                fragment = DriversFragment.newInstance();
            } else if (id == R.id.nav_apps) {
                fragment = AppsFragment.newInstance();
            } else if (id == R.id.nav_modules) {
                fragment = ModulesFragment.newInstance();
            } else if (id == R.id.nav_protection) {
                fragment = ProtectionFragment.newInstance();
            }

            if (fragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
            }
            return true;
        });

        // Default to drivers tab
        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_drivers);
        }
    }

    public DriverRegistry getDriverRegistry() { return driverRegistry; }
    public ScopeManager getScopeManager() { return scopeManager; }
    public KoManager getKoManager() { return koManager; }
    public ProtectionManager getProtectionManager() { return protectionManager; }
}
