package com.example.fuel_split;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TripDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_INDEX = "trip_index";

    private Trip trip;
    private int  tripIndex;

    private TextView        tvDetailTitle, tvDetailBadge, tvDetailTotal,
                            tvDetailDist, tvDetailFuel, tvDetailPaidBy,
                            tvGroupAddressLabel, tvGroupAddress, tvSettleStatus;
    private LinearLayout    sharesContainer;
    private MaterialButton  btnSettle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);
        applyEdgeToEdgeInsets();

        tripIndex = getIntent().getIntExtra(EXTRA_TRIP_INDEX, -1);
        List<Trip> trips = TripStore.loadTrips(this);
        if (tripIndex < 0 || tripIndex >= trips.size()) {
            finish();
            return;
        }
        trip = trips.get(tripIndex);

        tvDetailTitle        = findViewById(R.id.tvDetailTitle);
        tvDetailBadge        = findViewById(R.id.tvDetailBadge);
        tvDetailTotal        = findViewById(R.id.tvDetailTotal);
        tvDetailDist         = findViewById(R.id.tvDetailDist);
        tvDetailFuel         = findViewById(R.id.tvDetailFuel);
        tvDetailPaidBy       = findViewById(R.id.tvDetailPaidBy);
        tvGroupAddressLabel  = findViewById(R.id.tvGroupAddressLabel);
        tvGroupAddress       = findViewById(R.id.tvGroupAddress);
        tvSettleStatus       = findViewById(R.id.tvSettleStatus);
        sharesContainer      = findViewById(R.id.sharesContainer);
        btnSettle            = findViewById(R.id.btnSettle);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSettle.setOnClickListener(v -> settleTrip());

        render();
    }

    private void render() {
        tvDetailTitle.setText(trip.getTripName());
        tvDetailTotal.setText("₹" + trip.getTotal());
        tvDetailDist.setText(trip.getDistance());
        tvDetailFuel.setText(trip.getFuel());
        tvDetailPaidBy.setText(trip.getPaidBy());

        boolean settled = trip.isSettled();
        tvDetailBadge.setText(settled ? "Settled" : "Pending");
        tvDetailBadge.setTextColor(settled ? 0xFF00C9A7 : 0xFFFFB347);

        btnSettle.setVisibility(settled ? View.GONE : View.VISIBLE);

        // Show group address if available
        String groupAddr = trip.getGroupAddress();
        if (groupAddr != null && !groupAddr.isEmpty()) {
            tvGroupAddressLabel.setVisibility(View.VISIBLE);
            tvGroupAddress.setVisibility(View.VISIBLE);
            tvGroupAddress.setText(groupAddr.substring(0, 10) + "…" + groupAddr.substring(groupAddr.length() - 6));
        }

        // Shares
        sharesContainer.removeAllViews();
        Map<String, Double> shares = trip.getAllShares();
        if (shares != null) {
            for (Map.Entry<String, Double> entry : shares.entrySet()) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBackgroundResource(R.drawable.bg_input);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, dp(8));
                row.setLayoutParams(params);

                TextView nameTv = new TextView(this);
                nameTv.setText(entry.getKey());
                nameTv.setTextColor(0xFFF0F0FF);
                nameTv.setTextSize(15);
                nameTv.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(nameTv);

                TextView amtTv = new TextView(this);
                amtTv.setText("₹" + String.format("%.2f", entry.getValue()));
                amtTv.setTextColor(0xFF00C9A7);
                amtTv.setTextSize(15);
                amtTv.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(amtTv);

                sharesContainer.addView(row);
            }
        }
    }

    private void settleTrip() {
        String groupAddr = trip.getGroupAddress();
        if (groupAddr == null || groupAddr.isEmpty()) {
            markSettledLocally();
            return;
        }

        btnSettle.setEnabled(false);
        tvSettleStatus.setVisibility(View.VISIBLE);
        tvSettleStatus.setText("Connecting to blockchain…");

        new Thread(() -> {
            try {
                WalletManager wm   = new WalletManager(this);
                org.web3j.crypto.Credentials creds = wm.getOrCreateWallet();
                BlockchainManager bm = new BlockchainManager();
                ContractManager   cm = new ContractManager(bm.getWeb3(), creds);

                String myAddr = creds.getAddress().toLowerCase();
                runOnUiThread(() -> tvSettleStatus.setText("Fetching debts…"));

                List<ContractManager.DebtRecord> debts = cm.getDebts(groupAddr);
                List<ContractManager.DebtRecord> mine = new ArrayList<>();
                for (ContractManager.DebtRecord d : debts) {
                    if (d.debtor.equalsIgnoreCase(myAddr) && !d.settled) mine.add(d);
                }

                if (mine.isEmpty()) {
                    runOnUiThread(() -> {
                        tvSettleStatus.setText("You have no outstanding debt in this group.");
                        btnSettle.setEnabled(true);
                        markSettledLocally();
                    });
                    return;
                }

                runOnUiThread(() -> tvSettleStatus.setText("Settling " + mine.size() + " debt(s)…"));
                for (ContractManager.DebtRecord d : mine) {
                    cm.waitForReceipt(cm.settleDebt(groupAddr, d.id));
                }

                runOnUiThread(() -> {
                    tvSettleStatus.setText("Settled!");
                    markSettledLocally();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvSettleStatus.setText("Error: " + e.getMessage());
                    btnSettle.setEnabled(true);
                    Toast.makeText(this, "Settlement failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void markSettledLocally() {
        trip.setSettled(true);
        TripStore.updateTrip(this, trip);
        render();
        Toast.makeText(this, "Trip marked as settled", Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Pad the header below the status bar and the settle bar above the nav bar. */
    private void applyEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View header    = findViewById(R.id.tripDetailHeader);
        View bottomBar = findViewById(R.id.tripDetailBottomBar);
        final int headerTop    = header.getPaddingTop();
        final int bottomBottom = bottomBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootTripDetail), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                              header.getPaddingRight(), header.getPaddingBottom());
            bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(),
                                 bottomBar.getPaddingRight(), bottomBottom + bars.bottom);
            return insets;
        });
    }
}
