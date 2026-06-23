package com.example.fuel_split;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import android.view.ViewGroup;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.*;
import org.web3j.crypto.Credentials;
import android.content.SharedPreferences;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private List<Trip> tripList;
    private TripAdapter adapter;
    private LinearLayout tripsLayout, balancesLayout, balancesContainer, emptyState;
    private TextView tvSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("fuelsplit", MODE_PRIVATE);
        if (!prefs.contains("username")) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        View rootMain   = findViewById(R.id.rootMain);
        View headerView = findViewById(R.id.appHeader);
        View navView    = findViewById(R.id.bottom_nav);
        final int headerSidePad = headerView.getPaddingLeft();
        final int navBaseHeight  = dp(72);

        ViewCompat.setOnApplyWindowInsetsListener(rootMain, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            headerView.setPadding(headerSidePad, bars.top + dp(20),
                    headerSidePad, dp(16));

            ViewGroup.LayoutParams lp = navView.getLayoutParams();
            lp.height = navBaseHeight + bars.bottom;
            navView.setLayoutParams(lp);
            navView.setPadding(navView.getPaddingLeft(), 0,
                    navView.getPaddingRight(), bars.bottom);

            return insets;
        });

        tripsLayout       = findViewById(R.id.tripsLayout);
        balancesLayout    = findViewById(R.id.balancesLayout);
        balancesContainer = findViewById(R.id.balancesContainer);
        emptyState        = findViewById(R.id.emptyState);
        tvSubtitle        = findViewById(R.id.tvSubtitle);
        ExtendedFloatingActionButton fabAddTrip = findViewById(R.id.fabAddTrip);

        loadData();

        RecyclerView rv = findViewById(R.id.rvTrips);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TripAdapter(tripList, pos -> {
            Trip t = tripList.get(pos);
            new AlertDialog.Builder(this)
                    .setTitle("Delete Trip")
                    .setMessage("Remove \"" + t.getTripName() + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        tripList.remove(pos);
                        saveData();
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                        updateSubtitle();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        rv.setAdapter(adapter);
        updateEmptyState();
        updateSubtitle();

        ((BottomNavigationView) findViewById(R.id.bottom_nav))
                .setOnItemSelectedListener(item -> {
                    if (item.getItemId() == R.id.trips) {
                        tripsLayout.setVisibility(View.VISIBLE);
                        balancesLayout.setVisibility(View.GONE);
                    } else {
                        tripsLayout.setVisibility(View.GONE);
                        balancesLayout.setVisibility(View.VISIBLE);
                        showSettlements();
                    }
                    return true;
                });

        // FAB launches Add Trip screen
        fabAddTrip.setOnClickListener(v -> {
            startActivity(new Intent(this, GroupsActivity.class));
        });
        new Thread(() -> {
            try {
                WalletManager wm = new WalletManager(this);
                Credentials creds = wm.getOrCreateWallet();
                String myAddress = creds.getAddress();
                android.util.Log.d("FUELSPLIT", "Wallet: " + myAddress);

                BlockchainManager bm = new BlockchainManager();
                ContractManager cm = new ContractManager(bm.getWeb3(), creds);

                boolean already = cm.isRegistered(myAddress);
                if (already) {
                    android.util.Log.d("FUELSPLIT", "Already registered on-chain");
                } else {
                    String savedUsername = getSharedPreferences("fuelsplit", MODE_PRIVATE)
                            .getString("username", "");
                    if (!savedUsername.isEmpty()) {
                        String txHash = cm.register(savedUsername, "");
                        android.util.Log.d("FUELSPLIT", "Register tx: " + txHash);
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("FUELSPLIT", "Error", e);
                runOnUiThread(() -> android.widget.Toast.makeText(
                        this, "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ── Update empty state visibility ──────────────────────────────────────
    private void updateEmptyState() {
        boolean isEmpty = tripList.isEmpty();
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        findViewById(R.id.rvTrips).setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // ── Update header subtitle ─────────────────────────────────────────────
    private void updateSubtitle() {
        int count = tripList.size();
        if (count == 0) {
            tvSubtitle.setText("No trips logged yet");
        } else {
            tvSubtitle.setText(count + (count == 1 ? " trip" : " trips") + " logged");
        }
    }

    // ── Build settlement cards ─────────────────────────────────────────────
    private void showSettlements() {
        balancesContainer.removeAllViews();
        List<String> settlements = calculateSettlements();

        if (settlements.isEmpty()) {
            // All settled up card
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(android.view.Gravity.CENTER);
            card.setBackgroundResource(R.drawable.bg_all_settled);
            card.setPadding(48, 56, 48, 56);

            TextView icon = new TextView(this);
            icon.setText("✓");
            icon.setTextColor(0xFF00C9A7);
            icon.setTextSize(40);
            icon.setGravity(android.view.Gravity.CENTER);
            card.addView(icon);

            TextView msg = new TextView(this);
            msg.setText("All settled up!");
            msg.setTextColor(0xFF00C9A7);
            msg.setTextSize(20);
            msg.setTypeface(null, android.graphics.Typeface.BOLD);
            msg.setGravity(android.view.Gravity.CENTER);
            msg.setPadding(0, 12, 0, 0);
            card.addView(msg);

            TextView sub = new TextView(this);
            sub.setText("No pending payments between anyone");
            sub.setTextColor(0xFF7B8AA0);
            sub.setTextSize(14);
            sub.setGravity(android.view.Gravity.CENTER);
            sub.setPadding(0, 8, 0, 0);
            card.addView(sub);

            balancesContainer.addView(card);
            return;
        }

        // Inflate one settlement card per item
        for (String s : settlements) {
            View item = getLayoutInflater()
                    .inflate(R.layout.item_settlement, balancesContainer, false);

            // Parse "Amit pays ₹44.00 to Rahul"
            String[] byPays   = s.split(" pays ");
            String debtorName = byPays[0].trim();

            String[] byTo     = byPays[1].split(" to ");
            String amount     = byTo[0].trim();   // "₹44.00"
            String creditor   = byTo[1].trim();   // "Rahul"

            ((TextView) item.findViewById(R.id.tvDebtor)).setText(debtorName);
            ((TextView) item.findViewById(R.id.tvAmount)).setText(amount);
            ((TextView) item.findViewById(R.id.tvCreditor)).setText(creditor);

            balancesContainer.addView(item);
        }
    }

    // ── Calculate net balances ─────────────────────────────────────────────
    private List<String> calculateSettlements() {
        HashMap<String, Double> netBalance = new HashMap<>();

        for (Trip trip : tripList) {
            double total  = Double.parseDouble(trip.getTotal());
            String paidBy = trip.getPaidBy();
            Map<String, Double> shares = trip.getAllShares();

            netBalance.put(paidBy,
                    netBalance.getOrDefault(paidBy, 0.0) + total);

            for (Map.Entry<String, Double> entry : shares.entrySet()) {
                String person = entry.getKey();
                double amount = entry.getValue();
                netBalance.put(person,
                        netBalance.getOrDefault(person, 0.0) - amount);
            }
        }

        return simplifyDebts(netBalance);
    }

    private List<String> simplifyDebts(HashMap<String, Double> netBalance) {
        List<String> result = new ArrayList<>();

        List<Map.Entry<String, Double>> creditors = new ArrayList<>();
        List<Map.Entry<String, Double>> debtors   = new ArrayList<>();

        for (Map.Entry<String, Double> entry : netBalance.entrySet()) {
            if (entry.getValue() >  0.5) creditors.add(entry);
            if (entry.getValue() < -0.5) debtors.add(entry);
        }

        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            Map.Entry<String, Double> debtor   = debtors.get(i);
            Map.Entry<String, Double> creditor = creditors.get(j);

            double amount = Math.min(-debtor.getValue(), creditor.getValue());

            result.add(debtor.getKey() + " pays ₹"
                    + String.format("%.2f", amount)
                    + " to " + creditor.getKey());

            debtor.setValue(debtor.getValue() + amount);
            creditor.setValue(creditor.getValue() - amount);

            if (Math.abs(debtor.getValue())   < 0.5) i++;
            if (Math.abs(creditor.getValue()) < 0.5) j++;
        }

        return result;
    }

    // ── Receive result from AddTripActivity ───────────────────────────────
    @Override
    @SuppressWarnings({"deprecation", "unchecked"})
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);

        if (req == 100 && res == RESULT_OK && data != null) {
            HashMap<String, Double> shares =
                    (HashMap<String, Double>) data.getSerializableExtra("SHARES_MAP");

            tripList.add(0, new Trip(
                    data.getStringExtra("TRIP_NAME"),
                    data.getStringExtra("DISTANCE") + " km",
                    data.getStringExtra("FUEL_USED") + " L",
                    data.getStringExtra("TOTAL_COST"),
                    data.getStringExtra("MEMBERS"),
                    shares,
                    data.getStringExtra("PAID_BY")
            ));

            saveData();
            adapter.notifyDataSetChanged();
            updateEmptyState();
            updateSubtitle();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────
    private void saveData() {
        getSharedPreferences("FuelSplitPrefs", MODE_PRIVATE)
                .edit()
                .putString("list", new Gson().toJson(tripList))
                .apply();
    }

    private void loadData() {
        String json = getSharedPreferences("FuelSplitPrefs", MODE_PRIVATE)
                .getString("list", null);

        tripList = new Gson().fromJson(json,
                new TypeToken<ArrayList<Trip>>() {}.getType());

        if (tripList == null) tripList = new ArrayList<>();
    }

    // ── Utility method for converting dp to pixels ──────────────────────
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}