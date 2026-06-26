package com.example.fuel_split;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import org.web3j.crypto.Credentials;

import java.util.ArrayList;
import java.util.List;

public class GroupDetailActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ADDRESS = "group_address";

    private String       groupAddress;
    private TextView     tvGroupName, tvMemberCount;
    private LinearLayout membersContainer;

    private WalletManager     wm;
    private BlockchainManager bm;
    private ContractManager   cm;
    private Credentials       creds;

    private List<String> currentMembers   = new ArrayList<>();
    private String       currentGroupName = "";
    private String       creatorAddress   = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        groupAddress     = getIntent().getStringExtra(EXTRA_GROUP_ADDRESS);
        tvGroupName      = findViewById(R.id.tvDetailGroupName);
        tvMemberCount    = findViewById(R.id.tvDetailMemberCount);
        membersContainer = findViewById(R.id.membersContainer);

        // Stage 6: Add Expense → fuel-calc flow preloaded with this group
        findViewById(R.id.btnAddExpense).setOnClickListener(v -> {
            if (currentMembers.isEmpty()) { toast("Loading group, please wait…"); return; }
            Intent intent = new Intent(this, AddTripActivity.class);
            intent.putExtra(AddTripActivity.EXTRA_PRELOAD_GROUP, groupAddress);
            startActivity(intent);
        });

        // Add member by friend code
        findViewById(R.id.btnAddMember).setOnClickListener(v -> {
            if (cm == null) { toast("Connecting, please wait…"); return; }
            showAddMemberDialog();
        });

        // Delete (creator) or Leave (everyone else) — wired after loadGroupData resolves creator
        findViewById(R.id.btnDeleteGroup).setOnClickListener(v -> {
            if (cm == null) { toast("Connecting, please wait…"); return; }
            boolean isCreator = creds != null
                    && creds.getAddress().equalsIgnoreCase(creatorAddress);
            if (isCreator) {
                new AlertDialog.Builder(this)
                        .setTitle("Delete Group")
                        .setMessage("Permanently delete \"" + currentGroupName + "\"? This cannot be undone.")
                        .setPositiveButton("Delete", (d, w) -> deleteGroup())
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Leave Group")
                        .setMessage("Remove yourself from \"" + currentGroupName + "\"?")
                        .setPositiveButton("Leave", (d, w) -> leaveGroup())
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        ContactStore.seedNameResolver(this);
        initBlockchain();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cm != null) loadGroupData();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private void initBlockchain() {
        new Thread(() -> {
            try {
                wm    = new WalletManager(this);
                creds = wm.getOrCreateWallet();
                bm    = new BlockchainManager();
                cm    = new ContractManager(bm.getWeb3(), creds);
                runOnUiThread(this::loadGroupData);
            } catch (Exception e) {
                runOnUiThread(() -> toast("Init error: " + e.getMessage()));
            }
        }).start();
    }

    private void loadGroupData() {
        new Thread(() -> {
            try {
                String       name    = cm.getGroupName(groupAddress);
                List<String> members = cm.getGroupMembers(groupAddress);
                String       creator = cm.getGroupCreator(groupAddress);
                currentGroupName = name;
                currentMembers   = members;
                creatorAddress   = creator;
                boolean iAmCreator = creds != null
                        && creds.getAddress().equalsIgnoreCase(creator);
                runOnUiThread(() -> {
                    tvGroupName.setText(name);
                    tvMemberCount.setText(members.size() + " member" + (members.size() == 1 ? "" : "s"));
                    com.google.android.material.button.MaterialButton btnDel =
                            findViewById(R.id.btnDeleteGroup);
                    if (iAmCreator) {
                        btnDel.setText("Delete");
                    } else {
                        btnDel.setText("Leave");
                    }
                    renderMembers(members);
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Load error: " + e.getMessage()));
            }
        }).start();
    }

    // ── Members list (name + Remove + Settle per row) ─────────────────────────

    private void renderMembers(List<String> members) {
        membersContainer.removeAllViews();
        String myAddr = creds != null ? creds.getAddress().toLowerCase() : "";

        for (String addr : members) {
            boolean isMe = addr.equalsIgnoreCase(myAddr);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_input);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(params);

            // Name label — resolves async
            TextView tvName = new TextView(this);
            tvName.setText((isMe ? "You" : NameResolver.nameFor(addr)));
            tvName.setTextColor(isMe
                    ? ContextCompat.getColor(this, R.color.accent)
                    : ContextCompat.getColor(this, R.color.text_primary));
            tvName.setTextSize(15);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvName);

            // Async name update for non-self members
            if (!isMe) {
                new Thread(() -> {
                    try {
                        String[] info = ProfileClient.lookupByAddress(addr);
                        if (info != null && info.length > 1 && !info[1].isEmpty()) {
                            NameResolver.seed(addr, info[1]);
                            ContactStore.addContact(this, info[0], info[1], addr);
                            runOnUiThread(() -> tvName.setText(info[1]));
                        }
                    } catch (Exception ignored) {}
                }).start();
            }

            if (!isMe) {
                // Settle button — marks this member has paid their share
                MaterialButton btnSettle = new MaterialButton(this,
                        null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btnSettle.setText("Settle");
                btnSettle.setTextSize(11f);
                btnSettle.setAllCaps(false);
                btnSettle.setTextColor(ContextCompat.getColor(this, R.color.money_positive));
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
                btnParams.setMargins(dp(8), 0, 0, 0);
                btnSettle.setLayoutParams(btnParams);
                btnSettle.setOnClickListener(v -> settleForMember(addr, btnSettle));
                row.addView(btnSettle);

                // Remove button
                MaterialButton btnRemove = new MaterialButton(this,
                        null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btnRemove.setText("Remove");
                btnRemove.setTextSize(11f);
                btnRemove.setAllCaps(false);
                btnRemove.setTextColor(ContextCompat.getColor(this, R.color.money_negative));
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
                removeParams.setMargins(dp(6), 0, 0, 0);
                btnRemove.setLayoutParams(removeParams);
                btnRemove.setOnClickListener(v -> {
                    String name = tvName.getText().toString();
                    new AlertDialog.Builder(this)
                            .setTitle("Remove Member")
                            .setMessage("Remove " + name + " from this group?")
                            .setPositiveButton("Remove", (d, w) -> removeMember(addr, btnRemove))
                            .setNegativeButton("Cancel", null)
                            .show();
                });
                row.addView(btnRemove);
            }

            membersContainer.addView(row);
        }
    }

    // ── Per-member settle ─────────────────────────────────────────────────────

    private void settleForMember(String memberAddr, MaterialButton btn) {
        btn.setEnabled(false);
        btn.setText("…");
        new Thread(() -> {
            try {
                String myAddr = creds.getAddress().toLowerCase();
                List<ContractManager.DebtRecord> debts = cm.getDebts(groupAddress);
                List<ContractManager.DebtRecord> mine  = new ArrayList<>();
                for (ContractManager.DebtRecord d : debts) {
                    if (d.debtor.equalsIgnoreCase(myAddr)
                            && d.creditor.equalsIgnoreCase(memberAddr)
                            && !d.settled) {
                        mine.add(d);
                    }
                }
                if (mine.isEmpty()) {
                    runOnUiThread(() -> {
                        btn.setEnabled(true);
                        btn.setText("Settle");
                        toast("No outstanding balance with this member");
                    });
                    return;
                }
                for (ContractManager.DebtRecord d : mine) {
                    cm.waitForReceipt(cm.settleDebt(groupAddress, d.id));
                }
                runOnUiThread(() -> {
                    btn.setText("Done");
                    toast("Settled!");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    btn.setText("Settle");
                    toast("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    // ── Remove member ─────────────────────────────────────────────────────────

    private void removeMember(String addr, MaterialButton btn) {
        btn.setEnabled(false);
        new Thread(() -> {
            try {
                cm.waitForReceipt(cm.removeMemberFromGroup(groupAddress, addr));
                currentMembers.remove(addr);
                runOnUiThread(() -> {
                    toast("Member removed");
                    loadGroupData();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    toast(friendlyGroupError(e.getMessage()));
                });
            }
        }).start();
    }

    private String friendlyGroupError(String raw) {
        if (raw == null) return "Something went wrong";
        if (raw.contains("Member still owes money"))
            return "Can't remove — this member still owes money in the group";
        if (raw.contains("Member is still owed money"))
            return "Can't remove — this member is still owed money by others";
        if (raw.contains("Cannot remove creator"))
            return "The group creator cannot be removed";
        return raw;
    }

    // ── Leave group (remove self) ─────────────────────────────────────────────

    private void leaveGroup() {
        if (creds == null) return;
        new Thread(() -> {
            try {
                cm.removeMemberFromGroup(groupAddress, creds.getAddress());
                runOnUiThread(() -> {
                    toast("You left the group");
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast(friendlyGroupError(e.getMessage())));
            }
        }).start();
    }

    // ── Delete group (creator only) ───────────────────────────────────────────

    private void deleteGroup() {
        new Thread(() -> {
            try {
                cm.waitForReceipt(cm.deleteGroup(groupAddress));
                runOnUiThread(() -> {
                    toast("Group deleted");
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Error: " + e.getMessage()));
            }
        }).start();
    }

    // ── Add member dialog ─────────────────────────────────────────────────────

    private void showAddMemberDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(24), dp(24), dp(24));
        layout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_card));

        TextView title = new TextView(this);
        title.setText("Add Member");
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(16));
        layout.addView(title);

        EditText etCode = new EditText(this);
        etCode.setHint("Friend code");
        etCode.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        etCode.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        etCode.setTextSize(15);
        etCode.setBackgroundResource(R.drawable.bg_input);
        etCode.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        etParams.setMargins(0, 0, 0, dp(12));
        etCode.setLayoutParams(etParams);
        layout.addView(etCode);

        TextView tvStatus = new TextView(this);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvStatus.setTextSize(13);
        tvStatus.setPadding(0, 0, 0, dp(12));
        layout.addView(tvStatus);

        MaterialButton btnAdd = new MaterialButton(this);
        btnAdd.setText("Add to Group");
        btnAdd.setAllCaps(false);
        btnAdd.setTextColor(ContextCompat.getColor(this, R.color.bg_primary));
        btnAdd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent)));
        layout.addView(btnAdd);

        btnAdd.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            if (code.isEmpty()) return;
            tvStatus.setText("Looking up " + code + "…");
            btnAdd.setEnabled(false);
            new Thread(() -> {
                try {
                    String[] result = ProfileClient.lookupByCode(code);
                    String addr  = result[0];
                    String name  = result[1].isEmpty() ? code : result[1];
                    ContactStore.addContact(this, code, name, addr);
                    runOnUiThread(() -> tvStatus.setText("Adding " + name + " on-chain…"));
                    cm.addMemberToGroup(groupAddress, addr);
                    currentMembers.add(addr);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        toast(name + " added!");
                        loadGroupData();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Error: " + e.getMessage());
                        btnAdd.setEnabled(true);
                    });
                }
            }).start();
        });

        dialog.setContentView(layout);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.show();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
