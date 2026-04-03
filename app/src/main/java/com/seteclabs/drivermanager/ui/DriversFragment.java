package com.seteclabs.drivermanager.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.seteclabs.drivermanager.R;
import com.seteclabs.drivermanager.manager.DriverRegistry;
import com.seteclabs.drivermanager.model.Driver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Displays all discovered drivers with category filtering.
 * Users can enable/disable drivers and select active variants.
 */
public class DriversFragment extends Fragment {

    private DriverRegistry registry;
    private RecyclerView recyclerView;
    private DriverAdapter adapter;
    private String currentFilter = "all";

    public static DriversFragment newInstance() {
        return new DriversFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drivers, container, false);

        registry = ((MainActivity) requireActivity()).getDriverRegistry();

        // Category filter chips
        ChipGroup chipGroup = view.findViewById(R.id.category_chips);
        String[] categories = {"all", "gpu", "wifi", "bluetooth", "audio", "camera", "usb", "sdr"};
        for (String cat : categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(cat.toUpperCase(Locale.ROOT));
            chip.setCheckable(true);
            chip.setChecked("all".equals(cat));
            chip.setOnCheckedChangeListener((v, checked) -> {
                if (checked) {
                    currentFilter = cat;
                    refreshList();
                }
            });
            chipGroup.addView(chip);
        }

        // Driver list
        recyclerView = view.findViewById(R.id.driver_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DriverAdapter();
        recyclerView.setAdapter(adapter);

        // Scan button
        FloatingActionButton fab = view.findViewById(R.id.fab_scan);
        fab.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Scanning drivers...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                registry.scanSystem();
                requireActivity().runOnUiThread(this::refreshList);
            }).start();
        });

        refreshList();
        return view;
    }

    private void refreshList() {
        List<Driver> drivers;
        if ("all".equals(currentFilter)) {
            drivers = registry.getDrivers();
        } else {
            drivers = registry.getDriversByCategory(currentFilter);
        }
        adapter.setDrivers(drivers);
    }

    /**
     * RecyclerView adapter for driver items.
     */
    class DriverAdapter extends RecyclerView.Adapter<DriverAdapter.ViewHolder> {
        private List<Driver> drivers = new ArrayList<>();

        void setDrivers(List<Driver> drivers) {
            this.drivers = drivers;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_driver, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Driver driver = drivers.get(position);
            holder.name.setText(driver.getName());
            holder.category.setText(driver.getCategory().toUpperCase(Locale.ROOT));
            holder.path.setText(driver.getStockPath() != null ? driver.getStockPath() : "");

            int variantCount = driver.getVariants() != null ? driver.getVariants().size() : 0;
            holder.variants.setText(variantCount + " variant" + (variantCount != 1 ? "s" : ""));

            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(driver.isEnabled());
            holder.toggle.setOnCheckedChangeListener((v, checked) -> {
                driver.setEnabled(checked);
                registry.save();
            });

            // Click to show variant selector
            holder.itemView.setOnClickListener(v -> {
                showVariantSelector(driver);
            });
        }

        @Override
        public int getItemCount() { return drivers.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, category, path, variants;
            SwitchMaterial toggle;

            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.driver_name);
                category = v.findViewById(R.id.driver_category);
                path = v.findViewById(R.id.driver_path);
                variants = v.findViewById(R.id.driver_variants);
                toggle = v.findViewById(R.id.driver_toggle);
            }
        }
    }

    private void showVariantSelector(Driver driver) {
        // TODO: Show a bottom sheet with variant list and option to add new variant
        StringBuilder info = new StringBuilder();
        info.append(driver.getName()).append("\n");
        info.append("Category: ").append(driver.getCategory()).append("\n");
        info.append("Library: ").append(driver.getLibraryName()).append("\n");
        info.append("Stock: ").append(driver.getStockPath()).append("\n");
        info.append("Hash: ").append(driver.getStockHash()).append("\n\n");
        info.append("Variants:\n");
        for (Driver.Variant v : driver.getVariants()) {
            String active = v.getId().equals(driver.getActiveVariant()) ? " [ACTIVE]" : "";
            info.append("  - ").append(v.getName()).append(active).append("\n");
            info.append("    ").append(v.getPath()).append("\n");
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Driver Details")
                .setMessage(info.toString())
                .setPositiveButton("OK", null)
                .show();
    }
}
