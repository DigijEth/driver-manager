package com.seteclabs.drivermanager.ui;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.seteclabs.drivermanager.R;
import com.seteclabs.drivermanager.manager.DriverRegistry;
import com.seteclabs.drivermanager.manager.ScopeManager;
import com.seteclabs.drivermanager.model.Driver;

import java.util.ArrayList;
import java.util.List;

/**
 * LSPosed-style per-app scope selection.
 *
 * 1. User selects a driver from the dropdown
 * 2. Scrollable list of all installed apps appears
 * 3. User checks which apps should use the custom driver variant
 * 4. The Xposed hook reads this config and redirects library loading
 */
public class AppsFragment extends Fragment {

    private DriverRegistry driverRegistry;
    private ScopeManager scopeManager;
    private RecyclerView recyclerView;
    private AppAdapter adapter;
    private Spinner driverSpinner;
    private EditText searchBar;

    private List<Driver> enabledDrivers = new ArrayList<>();
    private List<ScopeManager.AppInfo> allApps = new ArrayList<>();
    private List<ScopeManager.AppInfo> filteredApps = new ArrayList<>();
    private String selectedDriverId = null;

    public static AppsFragment newInstance() {
        return new AppsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_apps, container, false);

        driverRegistry = ((MainActivity) requireActivity()).getDriverRegistry();
        scopeManager = ((MainActivity) requireActivity()).getScopeManager();

        // Driver selector dropdown
        driverSpinner = view.findViewById(R.id.driver_spinner);
        setupDriverSpinner();

        // Search bar
        searchBar = view.findViewById(R.id.search_bar);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterApps(s.toString());
            }
        });

        // App list
        recyclerView = view.findViewById(R.id.app_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AppAdapter();
        recyclerView.setAdapter(adapter);

        // Load apps in background
        new Thread(() -> {
            allApps = scopeManager.getInstalledApps();
            filteredApps = new ArrayList<>(allApps);
            requireActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();

        return view;
    }

    private void setupDriverSpinner() {
        enabledDrivers = driverRegistry.getEnabledDrivers();

        List<String> driverNames = new ArrayList<>();
        driverNames.add("Select a driver to scope...");
        for (Driver d : enabledDrivers) {
            driverNames.add("[" + d.getCategory().toUpperCase() + "] " + d.getName());
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, driverNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        driverSpinner.setAdapter(spinnerAdapter);

        driverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos == 0) {
                    selectedDriverId = null;
                } else {
                    selectedDriverId = enabledDrivers.get(pos - 1).getId();
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void filterApps(String query) {
        query = query.toLowerCase();
        filteredApps.clear();
        for (ScopeManager.AppInfo app : allApps) {
            if (app.label.toLowerCase().contains(query)
                    || app.packageName.toLowerCase().contains(query)) {
                filteredApps.add(app);
            }
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * RecyclerView adapter for the app list with scope checkboxes.
     */
    class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ScopeManager.AppInfo app = filteredApps.get(position);

            holder.appName.setText(app.label);
            holder.appPackage.setText(app.packageName);

            // Load app icon
            try {
                Drawable icon = requireContext().getPackageManager()
                        .getApplicationIcon(app.packageName);
                holder.appIcon.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException e) {
                holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            // System app indicator
            holder.systemBadge.setVisibility(app.isSystem ? View.VISIBLE : View.GONE);

            // Scope checkbox
            holder.scopeCheck.setOnCheckedChangeListener(null);
            if (selectedDriverId != null) {
                holder.scopeCheck.setEnabled(true);
                holder.scopeCheck.setChecked(scopeManager.isAppScoped(selectedDriverId, app.packageName));
                holder.scopeCheck.setOnCheckedChangeListener((v, checked) -> {
                    if (checked) {
                        scopeManager.addAppToScope(selectedDriverId, app.packageName);
                    } else {
                        scopeManager.removeAppFromScope(selectedDriverId, app.packageName);
                    }
                });
            } else {
                holder.scopeCheck.setEnabled(false);
                holder.scopeCheck.setChecked(false);
            }

            // Click entire row to toggle
            holder.itemView.setOnClickListener(v -> {
                if (selectedDriverId != null) {
                    holder.scopeCheck.toggle();
                }
            });
        }

        @Override
        public int getItemCount() { return filteredApps.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView appIcon;
            TextView appName, appPackage, systemBadge;
            CheckBox scopeCheck;

            ViewHolder(View v) {
                super(v);
                appIcon = v.findViewById(R.id.app_icon);
                appName = v.findViewById(R.id.app_name);
                appPackage = v.findViewById(R.id.app_package);
                systemBadge = v.findViewById(R.id.system_badge);
                scopeCheck = v.findViewById(R.id.scope_check);
            }
        }
    }
}
