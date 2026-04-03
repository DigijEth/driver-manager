package com.seteclabs.drivermanager.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.seteclabs.drivermanager.R;
import com.seteclabs.drivermanager.manager.ProtectionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Driver integrity protection view.
 * Shows baseline status, check results, and allows creating new baselines.
 */
public class ProtectionFragment extends Fragment {

    private ProtectionManager protectionManager;
    private TextView statusText, statsText;
    private RecyclerView resultList;
    private ResultAdapter adapter;

    public static ProtectionFragment newInstance() {
        return new ProtectionFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_protection, container, false);

        protectionManager = ((MainActivity) requireActivity()).getProtectionManager();

        statusText = view.findViewById(R.id.protection_status);
        statsText = view.findViewById(R.id.protection_stats);

        resultList = view.findViewById(R.id.result_list);
        resultList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ResultAdapter();
        resultList.setAdapter(adapter);

        Button btnBaseline = view.findViewById(R.id.btn_baseline);
        btnBaseline.setOnClickListener(v -> {
            new Thread(() -> {
                int count = protectionManager.createBaseline();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Baseline created: " + count + " drivers", Toast.LENGTH_SHORT).show();
                    refreshStatus();
                });
            }).start();
        });

        Button btnCheck = view.findViewById(R.id.btn_check);
        btnCheck.setOnClickListener(v -> {
            new Thread(() -> {
                List<ProtectionManager.CheckResult> results = protectionManager.checkIntegrity();
                requireActivity().runOnUiThread(() -> {
                    adapter.setResults(results);
                    refreshStatus();
                    int changes = 0;
                    for (ProtectionManager.CheckResult r : results) {
                        if (!"ok".equals(r.status)) changes++;
                    }
                    if (changes > 0) {
                        Toast.makeText(requireContext(), "WARNING: " + changes + " driver(s) changed!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), "All drivers OK", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        refreshStatus();
        return view;
    }

    private void refreshStatus() {
        new Thread(() -> {
            ProtectionManager.Stats stats = protectionManager.getStats();
            requireActivity().runOnUiThread(() -> {
                if (stats.totalProtected == 0) {
                    statusText.setText("No baseline created");
                    statsText.setText("Tap 'Create Baseline' to start protecting drivers");
                } else {
                    statusText.setText(stats.totalProtected + " drivers protected");
                    statsText.setText("OK: " + stats.ok + " | Changed: " + stats.changed + " | Missing: " + stats.missing);
                }
            });
        }).start();
    }

    class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ViewHolder> {
        private List<ProtectionManager.CheckResult> results = new ArrayList<>();

        void setResults(List<ProtectionManager.CheckResult> results) {
            this.results = results;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProtectionManager.CheckResult result = results.get(position);
            holder.path.setText(result.path);
            holder.status.setText(result.status.toUpperCase());
            holder.status.setTextColor(
                    "ok".equals(result.status) ? 0xFF2ECC71 :
                    "changed".equals(result.status) ? 0xFFE94560 : 0xFFF39C12);
        }

        @Override
        public int getItemCount() { return results.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView path, status;
            ViewHolder(View v) {
                super(v);
                path = v.findViewById(android.R.id.text1);
                status = v.findViewById(android.R.id.text2);
            }
        }
    }
}
