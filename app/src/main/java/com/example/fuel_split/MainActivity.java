package com.example.fuel_split;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import org.web3j.crypto.Credentials;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    // ── Trips ──────────────────────────────────────────────────────────────
    private List<Trip>    tripList;
    private TripAdapter   tripAdapter;

    // ── Groups ─────────────────────────────────────────────────────────────
    private List<GroupItem>  groupList = new ArrayList<>();
    private GroupAdapter     groupAdapter;
    private WalletManager    wm;
    private BlockchainManager bm;
    private ContractManager  cm;
    private Credentials      creds;
    private final Map<String, String> pendingMembers = new HashMap<>();

    // ── Views ──────────────────────────────────────────────────────────────
    private LinearLayout tripsLayout, groupsLayout, balancesLayout,
                         balancesContainer, emptyState, emptyGroups;
    private RecyclerView rvGroups;
    private TextView     tvSubtitle, tvIdentity, tvWalletAddress;
    private ExtendedFloatingActionButton fabAddTrip;
    private BottomNavigationView         bottomNav;

    private int currentTab = R.id.nav_trips; // default tab

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
            headerView.setPadding(headerSidePad, bars.top + dp(20), headerSidePad, dp(16));
            ViewGroup.LayoutParams lp = navView.getLayoutParams();
            lp.height = navBaseHeight + bars.bottom;
            navView.setLayoutParams(lp);
            navView.setPadding(navView.getPaddingLeft(), 0, navView.getPaddingRight(), bars.bottom);
            return insets;
        });

        tripsLayout       = findViewById(R.id.tripsLayout);
        groupsLayout      = findViewById(R.id.groupsLayout);
        balancesLayout    = findViewById(R.id.balancesLayout);
        balancesContainer = findViewById(R.id.balancesContainer);
        emptyState        = findViewById(R.id.emptyState);
        emptyGroups       = findViewById(R.id.emptyGroups);
        rvGroups          = findViewById(R.id.rvGroups);
        tvSubtitle        = findViewById(R.id.tvSubtitle);
        fabAddTrip        = findViewById(R.id.fabAddTrip);
        bottomNav         = findViewById(R.id.bottom_nav);

        // Identity row
        LinearLayout header = findViewById(R.id.appHeader);
        LinearLayout identityRow = new LinearLayout(this);
        identityRow.setOrientation(LinearLayout.HORIZONTAL);
        identityRow.setGravity(Gravity.CENTER_VERTICAL);
        identityRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        header.addView(identityRow, 1);

        tvIdentity = new TextView(this);
        tvIdentity.setTextColor(0xFFFFFFFF);
        tvIdentity.setTextSize(15);
        tvIdentity.setPadding(0, dp(6), 0, dp(2));
        identityRow.addView(tvIdentity, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        ImageButton btnCopyFriendCode = new ImageButton(this);
        btnCopyFriendCode.setImageResource(android.R.drawable.ic_menu_copy);
        btnCopyFriendCode.setBackground(null);
        btnCopyFriendCode.setColorFilter(0xFFFFFFFF);
        btnCopyFriendCode.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnCopyFriendCode.setContentDescription("Copy friend code");
        btnCopyFriendCode.setVisibility(View.GONE);
        identityRow.addView(btnCopyFriendCode, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        tvWalletAddress = new TextView(this);
        tvWalletAddress.setTextColor(0xFF7B8AA0);
        tvWalletAddress.setTextSize(11);
        tvWalletAddress.setPadding(0, 0, 0, dp(4));
        header.addView(tvWalletAddress, 2);

        SharedPreferences profilePrefs = getSharedPreferences("FuelSplitProfile", MODE_PRIVATE);
        String displayName = profilePrefs.getString("displayName", "");
        String friendCode  = normalizeFriendCode(profilePrefs.getString("code", ""));
        if (!displayName.isEmpty()) {
            tvIdentity.setText(displayName + "  —  Code: " + friendCode);
            btnCopyFriendCode.setVisibility(View.VISIBLE);
            btnCopyFriendCode.setOnClickListener(v -> copyFriendCode(friendCode));
        }

        // Pre-warm NameResolver from saved contacts (no network needed)
        ContactStore.seedNameResolver(this);

        // ── Trips RecyclerView ─────────────────────────────────────────────
        tripList = TripStore.loadTrips(this);
        RecyclerView rvTrips = findViewById(R.id.rvTrips);
        rvTrips.setLayoutManager(new LinearLayoutManager(this));

        tripAdapter = new TripAdapter(tripList,
                pos -> {
                    Trip t = tripList.get(pos);
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Trip")
                            .setMessage("Remove \"" + t.getTripName() + "\"?")
                            .setPositiveButton("Delete", (d, w) -> {
                                tripList.remove(pos);
                                TripStore.saveTrips(this, tripList);
                                tripAdapter.notifyDataSetChanged();
                                updateEmptyState();
                                updateSubtitle();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                },
                pos -> {
                    Intent intent = new Intent(this, TripDetailActivity.class);
                    intent.putExtra(TripDetailActivity.EXTRA_TRIP_INDEX, pos);
                    startActivity(intent);
                });
        rvTrips.setAdapter(tripAdapter);

        // ── Groups RecyclerView ────────────────────────────────────────────
        groupAdapter = new GroupAdapter(groupList, group -> {
            Intent intent = new Intent(this, GroupDetailActivity.class);
            intent.putExtra(GroupDetailActivity.EXTRA_GROUP_ADDRESS, group.contractAddress);
            startActivity(intent);
        });
        rvGroups.setLayoutManager(new LinearLayoutManager(this));
        rvGroups.setAdapter(groupAdapter);

        updateEmptyState();
        updateSubtitle();

        // ── Bottom nav ─────────────────────────────────────────────────────
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            currentTab = id;
            tripsLayout.setVisibility(View.GONE);
            groupsLayout.setVisibility(View.GONE);
            balancesLayout.setVisibility(View.GONE);

            if (id == R.id.nav_trips) {
                tripsLayout.setVisibility(View.VISIBLE);
                fabAddTrip.setText("New Trip");
                fabAddTrip.show();
            } else if (id == R.id.nav_groups) {
                groupsLayout.setVisibility(View.VISIBLE);
                fabAddTrip.setText("New Group");
                fabAddTrip.show();
                if (cm != null) loadGroups();
            } else if (id == R.id.nav_balances) {
                balancesLayout.setVisibility(View.VISIBLE);
                fabAddTrip.hide();
                showSettlements();
            }
            return true;
        });

        // ── FAB ────────────────────────────────────────────────────────────
        fabAddTrip.setOnClickListener(v -> {
            if (currentTab == R.id.nav_groups) showCreateGroupDialog();
            else startActivity(new Intent(this, AddTripActivity.class));
        });

        // ── Wallet + auto-register ─────────────────────────────────────────
        new Thread(() -> {
            try {
                wm    = new WalletManager(this);
                creds = wm.getOrCreateWallet();
                String myAddress = creds.getAddress();
                bm    = new BlockchainManager();
                cm    = new ContractManager(bm.getWeb3(), creds);

                String shortAddr = myAddress.substring(0, 8) + "…"
                        + myAddress.substring(myAddress.length() - 6);
                runOnUiThread(() -> tvWalletAddress.setText(shortAddr));

                boolean already = cm.isRegistered(myAddress);
                if (!already) {
                    String savedCode = getSharedPreferences("FuelSplitProfile", MODE_PRIVATE)
                            .getString("code", "");
                    if (!savedCode.isEmpty()) cm.register(savedCode, "");
                }
                // Load groups in background once cm is ready
                runOnUiThread(() -> { if (currentTab == R.id.nav_groups) loadGroups(); });
            } catch (Exception e) {
                android.util.Log.e("FUELSPLIT", "Init error", e);
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload trips whenever we return from AddTripActivity or TripDetailActivity
        tripList.clear();
        tripList.addAll(TripStore.loadTrips(this));
        tripAdapter.notifyDataSetChanged();
        updateEmptyState();
        updateSubtitle();
        // Refresh the active tab
        if (currentTab == R.id.nav_groups && cm != null) loadGroups();
        if (currentTab == R.id.nav_balances) showSettlements();
    }

    // ── Groups loading ─────────────────────────────────────────────────────
    private void loadGroups() {
        new Thread(() -> {
            try {
                List<String> addrs = cm.getUserGroups();
                groupList.clear();
                for (String addr : addrs) {
                    try {
                        if (cm.isGroupDeleted(addr)) continue;
                        String       name    = cm.getGroupName(addr);
                        List<String> members = cm.getGroupMembers(addr);
                        groupList.add(new GroupItem(name, addr, members.size()));
                    } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    groupAdapter.notifyDataSetChanged();
                    boolean empty = groupList.isEmpty();
                    rvGroups.setVisibility(empty ? View.GONE : View.VISIBLE);
                    emptyGroups.setVisibility(empty ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Groups load error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ── Create Group dialog (mirrored from GroupsActivity) ─────────────────
    private void showCreateGroupDialog() {
        if (cm == null) {
            Toast.makeText(this, "Still connecting, please wait…", Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_create_group);
        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        android.widget.EditText etGroupName   = dialog.findViewById(R.id.etGroupName);
        android.widget.EditText etMemberCode  = dialog.findViewById(R.id.etMemberUsername);
        MaterialButton btnAdd    = dialog.findViewById(R.id.btnAddMember);
        ChipGroup chipGroup      = dialog.findViewById(R.id.chipGroupMembers);
        MaterialButton btnCreate = dialog.findViewById(R.id.btnCreateGroup);
        TextView tvStatus        = dialog.findViewById(R.id.tvDialogStatus);

        pendingMembers.clear();
        etMemberCode.setHint("Friend code");

        btnAdd.setOnClickListener(v -> {
            String code = etMemberCode.getText().toString().trim();
            if (code.isEmpty()) return;
            tvStatus.setText("Looking up " + code + "...");
            new Thread(() -> {
                try {
                    String[] result = ProfileClient.lookupByCode(code);
                    String addr      = result[0];
                    String chipLabel = result[1].isEmpty() ? code : result[1];
                    ContactStore.addContact(MainActivity.this, code, chipLabel, addr);
                    runOnUiThread(() -> {
                        pendingMembers.put(code, addr);
                        Chip chip = new Chip(this);
                        chip.setText(chipLabel);
                        chip.setCloseIconVisible(true);
                        chip.setOnCloseIconClickListener(c -> {
                            chipGroup.removeView(chip);
                            pendingMembers.remove(code);
                        });
                        chipGroup.addView(chip);
                        etMemberCode.setText("");
                        tvStatus.setText("");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> tvStatus.setText(e.getMessage()));
                }
            }).start();
        });

        btnCreate.setOnClickListener(v -> {
            String groupName = etGroupName.getText().toString().trim();
            if (groupName.isEmpty()) { etGroupName.setError("Enter group name"); return; }
            if (pendingMembers.isEmpty()) { tvStatus.setText("Add at least one member"); return; }

            tvStatus.setText("Creating group on blockchain...");
            btnCreate.setEnabled(false);
            new Thread(() -> {
                try {
                    List<String> addrs = new ArrayList<>(pendingMembers.values());
                    String hash = cm.createGroup(groupName, addrs);
                    cm.waitForReceipt(hash);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        loadGroups();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Error: " + e.getMessage());
                        btnCreate.setEnabled(true);
                    });
                }
            }).start();
        });

        dialog.show();
    }

    // ── Empty state ────────────────────────────────────────────────────────
    private void updateEmptyState() {
        boolean isEmpty = tripList.isEmpty();
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        findViewById(R.id.rvTrips).setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateSubtitle() {
        int count = tripList.size();
        if (count == 0) tvSubtitle.setText("No trips logged yet");
        else tvSubtitle.setText(count + (count == 1 ? " trip" : " trips") + " logged");
    }

    // ── Balances tab: on-chain debts ──────────────────────────────────────
    private void showSettlements() {
        balancesContainer.removeAllViews();

        if (cm == null || creds == null) {
            TextView tv = new TextView(this);
            tv.setText("Connecting to blockchain…");
            tv.setTextColor(0xFF7B8AA0);
            tv.setTextSize(15);
            tv.setPadding(dp(16), dp(24), dp(16), dp(24));
            balancesContainer.addView(tv);
            return;
        }

        TextView tvLoading = new TextView(this);
        tvLoading.setText("Loading debts…");
        tvLoading.setTextColor(0xFF7B8AA0);
        tvLoading.setTextSize(15);
        tvLoading.setPadding(dp(16), dp(24), dp(16), dp(24));
        balancesContainer.addView(tvLoading);

        final String myAddr = creds.getAddress().toLowerCase();

        new Thread(() -> {
            try {
                List<String> groups = cm.getUserGroups();
                List<ContractManager.DebtRecord> unsettled = new ArrayList<>();
                List<ContractManager.DebtRecord> settled   = new ArrayList<>();

                for (String gAddr : groups) {
                    try {
                        if (cm.isGroupDeleted(gAddr)) continue;
                        List<ContractManager.DebtRecord> debts = cm.getDebts(gAddr);
                        for (ContractManager.DebtRecord d : debts) {
                            boolean mine = d.debtor.equalsIgnoreCase(myAddr)
                                    || d.creditor.equalsIgnoreCase(myAddr);
                            if (!mine) continue;
                            if (d.settled) settled.add(d);
                            else unsettled.add(d);
                        }
                    } catch (Exception ignored) {}
                }

                final List<ContractManager.DebtRecord> finalSettled = settled;
                runOnUiThread(() -> renderDebtList(unsettled, finalSettled, myAddr));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    balancesContainer.removeAllViews();
                    TextView tvErr = new TextView(this);
                    tvErr.setText("Error: " + e.getMessage());
                    tvErr.setTextColor(0xFFFF6B6B);
                    tvErr.setTextSize(13);
                    tvErr.setPadding(dp(16), dp(16), dp(16), dp(16));
                    balancesContainer.addView(tvErr);
                });
            }
        }).start();
    }

    private void renderDebtList(List<ContractManager.DebtRecord> unsettled,
                                List<ContractManager.DebtRecord> settled,
                                String myAddr) {
        balancesContainer.removeAllViews();

        if (unsettled.isEmpty() && settled.isEmpty()) {
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
            sub.setText("No pending payments");
            sub.setTextColor(0xFF7B8AA0);
            sub.setTextSize(14);
            sub.setGravity(android.view.Gravity.CENTER);
            sub.setPadding(0, 8, 0, 0);
            card.addView(sub);

            balancesContainer.addView(card);
            return;
        }

        if (!unsettled.isEmpty()) {
            TextView tvHeader = new TextView(this);
            tvHeader.setText("PENDING");
            tvHeader.setTextColor(0xFF7B8AA0);
            tvHeader.setTextSize(11);
            tvHeader.setLetterSpacing(0.08f);
            tvHeader.setPadding(0, dp(4), 0, dp(12));
            balancesContainer.addView(tvHeader);

            for (ContractManager.DebtRecord d : unsettled) {
                addDebtRow(d, myAddr, false);
            }
        } else {
            // All pending debts settled — show celebration header
            TextView tvDone = new TextView(this);
            tvDone.setText("✓  All caught up — no pending debts");
            tvDone.setTextColor(0xFF00C9A7);
            tvDone.setTextSize(14);
            tvDone.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDone.setPadding(0, dp(4), 0, dp(16));
            balancesContainer.addView(tvDone);
        }

        if (!settled.isEmpty()) {
            TextView tvHistHeader = new TextView(this);
            tvHistHeader.setText("HISTORY");
            tvHistHeader.setTextColor(0xFF7B8AA0);
            tvHistHeader.setTextSize(11);
            tvHistHeader.setLetterSpacing(0.08f);
            tvHistHeader.setPadding(0, dp(16), 0, dp(12));
            balancesContainer.addView(tvHistHeader);

            for (ContractManager.DebtRecord d : settled) {
                addDebtRow(d, myAddr, true);
            }
        }
    }

    private void addDebtRow(ContractManager.DebtRecord d, String myAddr, boolean alreadySettled) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_input);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);
        if (alreadySettled) row.setAlpha(0.45f);

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvDebtor = new TextView(this);
        tvDebtor.setText(NameResolver.nameFor(d.debtor));
        tvDebtor.setTextColor(d.debtor.equalsIgnoreCase(myAddr) ? 0xFFFF6B6B : 0xFFF0F0FF);
        tvDebtor.setTextSize(14);
        topLine.addView(tvDebtor);

        TextView tvArrow = new TextView(this);
        tvArrow.setText("  →  ");
        tvArrow.setTextColor(0xFF7B8AA0);
        tvArrow.setTextSize(14);
        topLine.addView(tvArrow);

        TextView tvCreditor = new TextView(this);
        tvCreditor.setText(NameResolver.nameFor(d.creditor));
        tvCreditor.setTextColor(d.creditor.equalsIgnoreCase(myAddr) ? 0xFF00C9A7 : 0xFFF0F0FF);
        tvCreditor.setTextSize(14);
        tvCreditor.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topLine.addView(tvCreditor);

        TextView tvAmount = new TextView(this);
        long rupees   = d.amountPaise / 100;
        long paiseRem = d.amountPaise % 100;
        tvAmount.setText("₹" + rupees + (paiseRem > 0 ? "." + String.format("%02d", paiseRem) : ""));
        tvAmount.setTextColor(alreadySettled ? 0xFF7B8AA0 : 0xFFFF6B6B);
        tvAmount.setTextSize(16);
        tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
        topLine.addView(tvAmount);

        row.addView(topLine);

        String tripName = parseTripName(d.description);
        if (!tripName.isEmpty()) {
            TextView tvTrip = new TextView(this);
            tvTrip.setText(tripName);
            tvTrip.setTextColor(0xFF7B8AA0);
            tvTrip.setTextSize(12);
            tvTrip.setPadding(0, dp(4), 0, 0);
            row.addView(tvTrip);
        }

        if (alreadySettled) {
            TextView tvBadge = new TextView(this);
            tvBadge.setText("Settled");
            tvBadge.setTextColor(0xFF7B8AA0);
            tvBadge.setTextSize(11);
            tvBadge.setPadding(0, dp(4), 0, 0);
            row.addView(tvBadge);
        } else if (d.debtor.equalsIgnoreCase(myAddr)) {
            MaterialButton btnSettle = new MaterialButton(this,
                    null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnSettle.setText("Settle");
            btnSettle.setAllCaps(false);
            btnSettle.setTextColor(0xFF00C9A7);
            btnSettle.setTextSize(13f);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
            btnParams.setMargins(0, dp(8), 0, 0);
            btnSettle.setLayoutParams(btnParams);
            btnSettle.setOnClickListener(v -> doSettle(d, row, btnSettle));
            row.addView(btnSettle);
        }

        resolveNameAsync(d.debtor, tvDebtor);
        resolveNameAsync(d.creditor, tvCreditor);

        balancesContainer.addView(row);
    }

    private void doSettle(ContractManager.DebtRecord d, LinearLayout row, MaterialButton btn) {
        btn.setEnabled(false);
        btn.setText("Settling…");
        new Thread(() -> {
            try {
                cm.waitForReceipt(cm.settleDebt(d.groupAddress, d.id));
                runOnUiThread(() -> {
                    row.setAlpha(0.45f);
                    btn.setText("Settled ✓");
                    btn.setTextColor(0xFF7B8AA0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    btn.setText("Settle");
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void resolveNameAsync(String addr, TextView tv) {
        new Thread(() -> {
            try {
                String[] info = ProfileClient.lookupByAddress(addr);
                if (info != null && info.length > 1 && !info[1].isEmpty()) {
                    NameResolver.seed(addr, info[1]);
                    runOnUiThread(() -> tv.setText(info[1]));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private String parseTripName(String desc) {
        if (desc == null || desc.isEmpty()) return "";
        if (desc.startsWith("name=")) {
            int semi = desc.indexOf(';');
            return semi > 5 ? desc.substring(5, semi) : desc.substring(5);
        }
        return desc;
    }

    static String normalizeFriendCode(@Nullable String code) {
        return code == null ? "" : code.replaceAll("\\s+", "");
    }

    static boolean shouldShowCopyToast(int sdkInt) {
        return sdkInt < Build.VERSION_CODES.TIRAMISU;
    }

    private void copyFriendCode(String friendCode) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) return;
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Friend code", friendCode));
        if (shouldShowCopyToast(Build.VERSION.SDK_INT)) {
            Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
