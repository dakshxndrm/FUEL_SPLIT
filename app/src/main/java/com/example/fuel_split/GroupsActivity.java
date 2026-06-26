package com.example.fuel_split;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.web3j.crypto.Credentials;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.Intent;

public class GroupsActivity extends AppCompatActivity {

    private RecyclerView rvGroups;
    private View emptyGroups;
    private GroupAdapter adapter;
    private List<GroupItem> groupList = new ArrayList<>();

    private WalletManager wm;
    private BlockchainManager bm;
    private ContractManager cm;
    private Credentials creds;

    // member username → address map for dialog
    private Map<String, String> pendingMembers = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);

        rvGroups    = findViewById(R.id.rvGroups);
        emptyGroups = findViewById(R.id.emptyGroups);
        ExtendedFloatingActionButton fab = findViewById(R.id.fabCreateGroup);

        adapter = new GroupAdapter(groupList, this::onGroupClick);
        rvGroups.setLayoutManager(new LinearLayoutManager(this));
        rvGroups.setAdapter(adapter);

        fab.setOnClickListener(v -> showCreateGroupDialog());

        initBlockchain();
    }

    private void initBlockchain() {
        new Thread(() -> {
            try {
                wm    = new WalletManager(this);
                creds = wm.getOrCreateWallet();
                bm    = new BlockchainManager();
                cm    = new ContractManager(bm.getWeb3(), creds);
                runOnUiThread(this::loadGroups);
            } catch (Exception e) {
                runOnUiThread(() -> showError("Init error: " + e.getMessage()));
            }
        }).start();
    }

    private void loadGroups() {
        new Thread(() -> {
            try {
                List<String> addrs = cm.getUserGroups();
                groupList.clear();
                for (String addr : addrs) {
                    String       name    = cm.getGroupName(addr);
                    List<String> members = cm.getGroupMembers(addr);
                    groupList.add(new GroupItem(name, addr, members.size()));
                }
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    boolean empty = groupList.isEmpty();
                    rvGroups.setVisibility(empty ? View.GONE : View.VISIBLE);
                    emptyGroups.setVisibility(empty ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("Load error: " + e.getMessage()));
            }
        }).start();
    }

    private void showCreateGroupDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_create_group);
        dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        EditText etGroupName     = dialog.findViewById(R.id.etGroupName);
        EditText etMemberUsername= dialog.findViewById(R.id.etMemberUsername);
        MaterialButton btnAdd    = dialog.findViewById(R.id.btnAddMember);
        ChipGroup chipGroup      = dialog.findViewById(R.id.chipGroupMembers);
        MaterialButton btnCreate = dialog.findViewById(R.id.btnCreateGroup);
        TextView tvStatus        = dialog.findViewById(R.id.tvDialogStatus);

        pendingMembers.clear();

        etMemberUsername.setHint("Friend code");
        btnAdd.setOnClickListener(v -> {
            String code = etMemberUsername.getText().toString().trim();
            if (code.isEmpty()) return;
            tvStatus.setText("Looking up " + code + "...");

            new Thread(() -> {
                try {
                    String[] result  = ProfileClient.lookupByCode(code);
                    String addr      = result[0];
                    String chipLabel = result[1].isEmpty() ? code : result[1];
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
                        etMemberUsername.setText("");
                        tvStatus.setText("");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> tvStatus.setText(e.getMessage()));
                }
            }).start();
        });

        btnCreate.setOnClickListener(v -> {
            String groupName = etGroupName.getText().toString().trim();
            if (groupName.isEmpty()) {
                etGroupName.setError("Enter group name");
                return;
            }
            if (pendingMembers.isEmpty()) {
                tvStatus.setText("Add at least one member");
                return;
            }

            tvStatus.setText("Creating group on blockchain...");
            btnCreate.setEnabled(false);

            new Thread(() -> {
                try {
                    List<String> memberAddrs = new ArrayList<>(pendingMembers.values());
                    String hash = cm.createGroup(groupName, memberAddrs);
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

    private void onGroupClick(GroupItem group) {
        Intent intent = new Intent(this, GroupDetailActivity.class);
        intent.putExtra(GroupDetailActivity.EXTRA_GROUP_ADDRESS, group.contractAddress);
        startActivity(intent);
    }

    private void showError(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
    }
}