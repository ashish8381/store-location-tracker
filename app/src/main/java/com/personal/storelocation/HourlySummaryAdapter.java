package com.personal.storelocation;

import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class HourlySummaryAdapter extends RecyclerView.Adapter<HourlySummaryAdapter.ViewHolder> {

    private final List<HourlyInfo> list;

    public HourlySummaryAdapter(List<HourlyInfo> list) {
        this.list = list;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvHour, tvDist, tvPoints;
        ImageView icCheckpoint;

        ViewHolder(View view) {
            super(view);
            tvHour = view.findViewById(R.id.tvHour);
            tvDist = view.findViewById(R.id.tvDistance);
            tvPoints = view.findViewById(R.id.tvPoints);
            icCheckpoint = view.findViewById(R.id.icCheckpoint);
        }
    }

    @NonNull
    @Override
    public HourlySummaryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hourly_info, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HourlySummaryAdapter.ViewHolder holder, int position) {
        HourlyInfo info = list.get(position);
        holder.tvHour.setText(String.format(Locale.getDefault(), "%02d:00 - %02d:00", info.hour, info.hour + 1));
        holder.tvDist.setText(String.format(Locale.getDefault(), "%.2f km", info.distance / 1000));
        holder.tvPoints.setText(String.valueOf(info.points));
        if (info.hasCheckpoint) {
            holder.icCheckpoint.setVisibility(View.VISIBLE);

            // Option 1: Android tooltip (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.icCheckpoint.setTooltipText(info.checkpointNote);
            }

            // Option 2: Fallback dialog for older Android versions
            holder.icCheckpoint.setOnClickListener(v -> {
                new AlertDialog.Builder(v.getContext())
                        .setTitle("Checkpoint Note")
                        .setMessage(info.checkpointNote)
                        .setPositiveButton("OK", null)
                        .show();
            });
        } else {
            holder.icCheckpoint.setVisibility(View.GONE);
        }

    }

    @Override
    public int getItemCount() {
        return list.size();
    }
}
