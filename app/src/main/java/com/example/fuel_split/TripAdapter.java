package com.example.fuel_split;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    public interface OnItemLongClickListener { void onLongClick(int position); }
    public interface OnItemClickListener    { void onClick(int position); }

    private final List<Trip>               tripList;
    private final OnItemLongClickListener  longClickListener;
    private final OnItemClickListener      clickListener;

    public TripAdapter(List<Trip> tripList,
                       OnItemLongClickListener longClickListener,
                       OnItemClickListener clickListener) {
        this.tripList          = tripList;
        this.longClickListener = longClickListener;
        this.clickListener     = clickListener;
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = tripList.get(position);

        holder.tripName.setText(trip.getTripName());
        holder.distance.setText(trip.getDistance());
        holder.fuel.setText(trip.getFuel());
        holder.total.setText("₹" + trip.getTotal());
        holder.paidBy.setText("Paid by: " + trip.getPaidBy());

        // Settled / Pending badge
        boolean settled = trip.isSettled();
        holder.badge.setText(settled ? "Settled" : "Pending");
        holder.badge.setTextColor(settled ? 0xFF00C9A7 : 0xFFFFB347);
        holder.badge.setBackgroundResource(R.drawable.bg_paidby_pill);

        // Member initials
        String raw = trip.getMembers().replace("[", "").replace("]", "");
        String[] names = raw.split(",");
        StringBuilder initials = new StringBuilder();
        for (String name : names) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                initials.append(Character.toUpperCase(trimmed.charAt(0)));
                initials.append("  ");
            }
        }
        holder.memberInitials.setText(initials.toString().trim());

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(position);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(position);
            return true;
        });
    }

    @Override
    public int getItemCount() { return tripList.size(); }

    public static class TripViewHolder extends RecyclerView.ViewHolder {
        TextView tripName, distance, fuel, total, paidBy, memberInitials, badge;

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);
            tripName       = itemView.findViewById(R.id.itemTitle);
            distance       = itemView.findViewById(R.id.itemDist);
            fuel           = itemView.findViewById(R.id.itemFuel);
            total          = itemView.findViewById(R.id.itemTotal);
            paidBy         = itemView.findViewById(R.id.tvPaidBy);
            memberInitials = itemView.findViewById(R.id.tvMemberInitials);
            badge          = itemView.findViewById(R.id.tvSettledBadge);
        }
    }
}
