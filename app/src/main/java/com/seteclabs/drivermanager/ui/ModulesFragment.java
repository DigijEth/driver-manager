package com.seteclabs.drivermanager.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.seteclabs.drivermanager.R;
import com.seteclabs.drivermanager.manager.KoManager;
import com.seteclabs.drivermanager.model.KernelModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kernel module (.ko) manager.
 * Load/unload modules, set autoload, view module info.
 */
public class ModulesFragment extends Fragment {

    private KoManager koManager;
    private RecyclerView recyclerView;
    private ModuleAdapter adapter;
    private TextView emptyView;

    public static ModulesFragment newInstance() {
        return new ModulesFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_modules, container, false);

        koManager = ((MainActivity) requireActivity()).getKoManager();

        recyclerView = view.findViewById(R.id.module_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ModuleAdapter();
        recyclerView.setAdapter(adapter);

        emptyView = view.findViewById(R.id.empty_view);

        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> refreshList());

        refreshList();
        return view;
    }

    private void refreshList() {
        new Thread(() -> {
            List<KernelModule> modules = koManager.listModules();
            requireActivity().runOnUiThread(() -> {
                adapter.setModules(modules);
                emptyView.setVisibility(modules.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(modules.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    class ModuleAdapter extends RecyclerView.Adapter<ModuleAdapter.ViewHolder> {
        private List<KernelModule> modules = new ArrayList<>();

        void setModules(List<KernelModule> modules) {
            this.modules = modules;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_module, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            KernelModule mod = modules.get(position);

            holder.name.setText(mod.getName());
            holder.desc.setText(mod.getDescription() != null ? mod.getDescription() : "No description");
            holder.meta.setText(String.format(Locale.US, "v%s | %s | %s",
                    mod.getVersion() != null ? mod.getVersion() : "?",
                    formatSize(mod.getSize()),
                    mod.isCompatible() ? "Compatible" : "Unknown compat"));

            // Load/unload toggle
            holder.loadToggle.setOnCheckedChangeListener(null);
            holder.loadToggle.setChecked(mod.isLoaded());
            holder.loadToggle.setOnCheckedChangeListener((v, checked) -> {
                new Thread(() -> {
                    String result;
                    if (checked) {
                        result = koManager.loadModule(mod.getFilename(), null);
                    } else {
                        result = koManager.unloadModule(mod.getName());
                    }
                    String msg = result;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        refreshList();
                    });
                }).start();
            });

            // Autoload checkbox
            holder.autoload.setOnCheckedChangeListener(null);
            holder.autoload.setChecked(mod.isAutoload());
            holder.autoload.setOnCheckedChangeListener((v, checked) -> {
                koManager.setAutoload(mod.getFilename(), checked);
            });

            // Click for details
            holder.itemView.setOnClickListener(v -> {
                new Thread(() -> {
                    String info = koManager.getModuleInfo(mod.getFilename());
                    requireActivity().runOnUiThread(() -> {
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle(mod.getName())
                                .setMessage(info)
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }).start();
            });
        }

        @Override
        public int getItemCount() { return modules.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, desc, meta;
            SwitchMaterial loadToggle;
            CheckBox autoload;

            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.module_name);
                desc = v.findViewById(R.id.module_desc);
                meta = v.findViewById(R.id.module_meta);
                loadToggle = v.findViewById(R.id.module_toggle);
                autoload = v.findViewById(R.id.module_autoload);
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
