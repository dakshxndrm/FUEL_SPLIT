package com.example.fuel_split;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.web3j.crypto.Credentials;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class GroupDetailActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ADDRESS = "group_address";

    private String  groupAddress;
    private TextView  tvGroupName, tvMemberCount, tvNoExpenses;
    private LinearLayout membersContainer, balancesContainer, expensesContainer;

    private WalletManager     wm;
    private BlockchainManager bm;
    private ContractManager   cm;
    private Credentials       creds;

    private List<String> currentMembers    = new ArrayList<>();
    private String       currentGroupName  = "";
    // Addresses / amounts where current user is the debtor (used by Settle dialog)
    private List<String> debtorAddresses   = new ArrayList<>();
    private List<Long>   debtAmountsPaise  = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        groupAddress      = getIntent().getStringExtra(EXTRA_GROUP_ADDRESS);
        tvGroupName       = findViewById(R.id.tvDetailGroupName);
        tvMemberCount     = findViewById(R.id.tvDetailMemberCount);
        tvNoExpenses      = findViewById(R.id.tvNoExpenses);
        membersContainer  = findViewById(R.id.membersContainer);
        balancesContainer = findViewById(R.id.balancesContainer);
        expensesContainer = findViewById(R.id.expensesContainer);

        findViewById(R.id.btnEditGroup).setOnClickListener(v -> showEditDialog());
        findViewById(R.id.btnAddExpense).setOnClickListener(v -> {
            if (currentMembers.isEmpty()) { toast("Loading group data, please wait…"); return; }
            showAddExpenseDialog();
        });
        findViewById(R.id.btnSettle).setOnClickListener(v -> {
            if (debtorAddresses.isEmpty()) { toast("You don't owe anyone in this group"); return; }
            showSettleDialog();
        });

        initBlockchain();
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
                currentGroupName = name;
                currentMembers   = members;
                runOnUiThread(() -> {
                    tvGroupName.setText(name);
                    tvMemberCount.setText(members.size() + " members");
                    renderMembers(members);
                    renderBalances(members);
                    renderExpenses(loadExpensesFromPrefs());
                    loadPendingSettlements();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Load error: " + e.getMessage()));
            }
        }).start();
    }

    // ── Members ───────────────────────────────────────────────────────────────

    private void renderMembers(List<String> members) {
        membersContainer.removeAllViews();
        for (String addr : members) {
            TextView tv = new TextView(this);
            tv.setText("• " + addr.substring(0, 10) + "…" + addr.substring(addr.length() - 4));
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tv.setTextSize(14);
            tv.setPadding(0, 8, 0, 8);
            membersContainer.addView(tv);
        }
    }

    // ── Balances ──────────────────────────────────────────────────────────────

    private void renderBalances(List<String> members) {
        balancesContainer.removeAllViews();
        debtorAddresses.clear();
        debtAmountsPaise.clear();

        String myAddr = creds.getAddress().toLowerCase();
        new Thread(() -> {
            for (String other : members) {
                if (other.equalsIgnoreCase(myAddr)) continue;
                try {
                    long bal = cm.getBalance(groupAddress, myAddr, other);
                    String label;
                    int    color;
                    if (bal > 0) {
                        label = "You owe " + shortAddr(other) + "  ₹" + (bal / 100);
                        color = R.color.money_negative;
                        debtorAddresses.add(other);
                        debtAmountsPaise.add(bal);
                    } else if (bal < 0) {
                        label = shortAddr(other) + " owes you  ₹" + (Math.abs(bal) / 100);
                        color = R.color.money_positive;
                    } else {
                        label = "Settled with " + shortAddr(other);
                        color = R.color.text_secondary;
                    }
                    String fLabel = label;
                    int    fColor = color;
                    runOnUiThread(() -> {
                        TextView tv = new TextView(this);
                        tv.setText(fLabel);
                        tv.setTextColor(ContextCompat.getColor(this, fColor));
                        tv.setTextSize(15);
                        tv.setPadding(0, 10, 0, 10);
                        balancesContainer.addView(tv);
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // ── Pending settlement confirmations ──────────────────────────────────────

    private void loadPendingSettlements() {
        new Thread(() -> {
            try {
                List<ContractManager.SettlementRecord> all = cm.getSettlements(groupAddress);
                String myAddr = creds.getAddress().toLowerCase();
                List<ContractManager.SettlementRecord> pending = new ArrayList<>();
                for (ContractManager.SettlementRecord r : all) {
                    if (!r.confirmed && r.creditor.equalsIgnoreCase(myAddr)) pending.add(r);
                }
                if (!pending.isEmpty()) runOnUiThread(() -> renderPendingSettlements(pending));
            } catch (Exception ignored) {}
        }).start();
    }

    private void renderPendingSettlements(List<ContractManager.SettlementRecord> pending) {
        TextView header = new TextView(this);
        header.setText("Pending Confirmations");
        header.setTextColor(ContextCompat.getColor(this, R.color.money_gold));
        header.setTextSize(13);
        header.setLetterSpacing(0.08f);
        header.setPadding(0, dp(20), 0, dp(8));
        balancesContainer.addView(header);

        for (ContractManager.SettlementRecord r : pending) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            TextView tv = new TextView(this);
            tv.setText(shortAddr(r.debtor) + " paid you  ₹" + (r.amountPaise / 100));
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tv.setTextSize(14);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);

            MaterialButton btn = new MaterialButton(this);
            btn.setText("Confirm");
            btn.setTextSize(12f);
            btn.setAllCaps(false);
            btn.setTextColor(ContextCompat.getColor(this, R.color.bg_primary));
            btn.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.money_positive)));
            int id = r.index;
            btn.setOnClickListener(v -> {
                btn.setEnabled(false);
                new Thread(() -> {
                    try {
                        cm.confirmSettlement(groupAddress, id);
                        runOnUiThread(() -> {
                            row.setVisibility(View.GONE);
                            toast("Settlement confirmed!");
                            loadGroupData();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            btn.setEnabled(true);
                            toast("Error: " + e.getMessage());
                        });
                    }
                }).start();
            });
            row.addView(btn);
            balancesContainer.addView(row);
        }
    }

    // ── Expense history ───────────────────────────────────────────────────────

    private void renderExpenses(List<ExpenseRecord> expenses) {
        expensesContainer.removeAllViews();
        if (expenses.isEmpty()) {
            tvNoExpenses.setVisibility(View.VISIBLE);
            return;
        }
        tvNoExpenses.setVisibility(View.GONE);
        for (ExpenseRecord r : expenses) {
            View item = getLayoutInflater().inflate(R.layout.item_expense, expensesContainer, false);
            ((TextView) item.findViewById(R.id.tvExpenseItemDesc)).setText(r.description);
            boolean iMine = creds != null && r.paidBy.equalsIgnoreCase(creds.getAddress());
            ((TextView) item.findViewById(R.id.tvExpenseItemPaidBy))
                    .setText(iMine ? "You paid" : "Paid by " + shortAddr(r.paidBy));
            ((TextView) item.findViewById(R.id.tvExpenseItemAmount))
                    .setText("₹" + (r.amountPaise / 100));
            expensesContainer.addView(item);
        }
    }

    private void saveExpenseLocally(ExpenseRecord record) {
        SharedPreferences prefs = getSharedPreferences("expenses", MODE_PRIVATE);
        String key  = "list_" + groupAddress;
        String json = prefs.getString(key, "[]");
        List<ExpenseRecord> list = new Gson().fromJson(json,
                new TypeToken<List<ExpenseRecord>>() {}.getType());
        if (list == null) list = new ArrayList<>();
        list.add(0, record);
        prefs.edit().putString(key, new Gson().toJson(list)).apply();
    }

    private List<ExpenseRecord> loadExpensesFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("expenses", MODE_PRIVATE);
        String json = prefs.getString("list_" + groupAddress, "[]");
        List<ExpenseRecord> list = new Gson().fromJson(json,
                new TypeToken<List<ExpenseRecord>>() {}.getType());
        return list != null ? list : new ArrayList<>();
    }

    // ── Add Expense dialog ────────────────────────────────────────────────────

    private void showAddExpenseDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_expense);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        EditText       etDesc    = dialog.findViewById(R.id.etExpenseDesc);
        EditText       etAmount  = dialog.findViewById(R.id.etExpenseAmount);
        MaterialButton btnEqual  = dialog.findViewById(R.id.btnSplitEqual);
        MaterialButton btnCustom = dialog.findViewById(R.id.btnSplitCustom);
        LinearLayout   custCont  = dialog.findViewById(R.id.customSharesContainer);
        TextView       tvStatus  = dialog.findViewById(R.id.tvExpenseStatus);
        MaterialButton btnSubmit = dialog.findViewById(R.id.btnAddExpenseSubmit);

        boolean[]       useCustom   = {false};
        List<EditText>  shareInputs = new ArrayList<>();

        // Build one row per member for custom % entry
        for (String addr : currentMembers) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView lbl = new TextView(this);
            lbl.setText(shortAddr(addr));
            lbl.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            lbl.setTextSize(14);
            lbl.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(lbl);

            EditText et = new EditText(this);
            et.setHint("%");
            et.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            et.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
            et.setInputType(InputType.TYPE_CLASS_NUMBER);
            et.setTextSize(14);
            et.setLayoutParams(new LinearLayout.LayoutParams(dp(56),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(et);

            TextView pctLbl = new TextView(this);
            pctLbl.setText("%");
            pctLbl.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            pctLbl.setTextSize(14);
            pctLbl.setPadding(dp(4), 0, 0, 0);
            row.addView(pctLbl);

            custCont.addView(row);
            shareInputs.add(et);
        }

        btnEqual.setOnClickListener(v -> {
            useCustom[0] = false;
            custCont.setVisibility(View.GONE);
            setActiveBtn(btnEqual, btnCustom);
        });
        btnCustom.setOnClickListener(v -> {
            useCustom[0] = true;
            custCont.setVisibility(View.VISIBLE);
            setActiveBtn(btnCustom, btnEqual);
        });

        btnSubmit.setOnClickListener(v -> {
            String desc   = etDesc.getText().toString().trim();
            String amtStr = etAmount.getText().toString().trim();
            if (desc.isEmpty())   { etDesc.setError("Enter description"); return; }
            if (amtStr.isEmpty()) { etAmount.setError("Enter amount"); return; }

            long amtPaise;
            try { amtPaise = (long)(Double.parseDouble(amtStr) * 100); }
            catch (NumberFormatException e) { etAmount.setError("Invalid"); return; }

            List<BigInteger> shares = new ArrayList<>();
            if (useCustom[0]) {
                int total = 0;
                for (EditText et : shareInputs) {
                    String s = et.getText().toString().trim();
                    if (s.isEmpty()) { tvStatus.setText("Fill all percentages"); return; }
                    int pct = Integer.parseInt(s);
                    shares.add(BigInteger.valueOf(pct));
                    total += pct;
                }
                if (total != 100) {
                    tvStatus.setText("Percentages must sum to 100 (currently " + total + ")");
                    return;
                }
            } else {
                shares = equalShares(currentMembers.size());
            }

            tvStatus.setText("Sending to blockchain…");
            btnSubmit.setEnabled(false);
            List<BigInteger> finalShares = shares;
            long finalAmt = amtPaise;

            new Thread(() -> {
                try {
                    cm.addExpense(groupAddress, desc, BigInteger.valueOf(finalAmt),
                            currentMembers, finalShares);
                    ExpenseRecord rec = new ExpenseRecord(desc, finalAmt,
                            creds.getAddress(), System.currentTimeMillis() / 1000);
                    saveExpenseLocally(rec);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        toast("Expense recorded on-chain!");
                        loadGroupData();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Error: " + e.getMessage());
                        btnSubmit.setEnabled(true);
                    });
                }
            }).start();
        });

        dialog.show();
    }

    // ── Settle dialog ─────────────────────────────────────────────────────────

    private void showSettleDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_settle);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        Spinner        spinner   = dialog.findViewById(R.id.spinnerSettleWith);
        EditText       etAmount  = dialog.findViewById(R.id.etSettleAmount);
        TextView       tvStatus  = dialog.findViewById(R.id.tvSettleStatus);
        MaterialButton btnOk     = dialog.findViewById(R.id.btnConfirmSettle);

        List<String> labels = new ArrayList<>();
        for (int i = 0; i < debtorAddresses.size(); i++) {
            labels.add(shortAddr(debtorAddresses.get(i))
                    + "  —  ₹" + (debtAmountsPaise.get(i) / 100) + " owed");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int pos, View cv, ViewGroup p) {
                TextView tv = (TextView) super.getView(pos, cv, p);
                tv.setTextColor(ContextCompat.getColor(GroupDetailActivity.this, R.color.text_primary));
                return tv;
            }
            @Override
            public View getDropDownView(int pos, View cv, ViewGroup p) {
                TextView tv = (TextView) super.getDropDownView(pos, cv, p);
                tv.setTextColor(ContextCompat.getColor(GroupDetailActivity.this, R.color.text_primary));
                tv.setBackgroundColor(ContextCompat.getColor(GroupDetailActivity.this, R.color.bg_card));
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                etAmount.setText(String.valueOf(debtAmountsPaise.get(pos) / 100));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        if (!debtAmountsPaise.isEmpty()) {
            etAmount.setText(String.valueOf(debtAmountsPaise.get(0) / 100));
        }

        btnOk.setOnClickListener(v -> {
            String amtStr = etAmount.getText().toString().trim();
            if (amtStr.isEmpty()) { etAmount.setError("Enter amount"); return; }
            int pos = spinner.getSelectedItemPosition();
            String creditor = debtorAddresses.get(pos);
            long amtPaise;
            try { amtPaise = (long)(Double.parseDouble(amtStr) * 100); }
            catch (NumberFormatException e) { etAmount.setError("Invalid"); return; }

            tvStatus.setText("Submitting settlement…");
            btnOk.setEnabled(false);
            long finalPaise = amtPaise;

            new Thread(() -> {
                try {
                    cm.markSettled(groupAddress, creditor, BigInteger.valueOf(finalPaise));
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        toast("Settlement proposed — the other party needs to confirm.");
                        loadGroupData();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Error: " + e.getMessage());
                        btnOk.setEnabled(true);
                    });
                }
            }).start();
        });

        dialog.show();
    }

    // ── Edit Group dialog ─────────────────────────────────────────────────────

    private void showEditDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_edit_group);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        EditText       etName     = dialog.findViewById(R.id.etEditGroupName);
        EditText       etUsername = dialog.findViewById(R.id.etEditMemberUsername);
        MaterialButton btnAdd     = dialog.findViewById(R.id.btnEditAddMember);
        ChipGroup      chipGroup  = dialog.findViewById(R.id.chipEditMembers);
        MaterialButton btnSave    = dialog.findViewById(R.id.btnSaveGroup);
        TextView       tvStatus   = dialog.findViewById(R.id.tvEditStatus);

        etName.setText(currentGroupName);
        for (String addr : currentMembers) {
            addMemberChip(chipGroup, addr, shortAddr(addr), dialog, tvStatus);
        }

        btnAdd.setOnClickListener(v -> {
            String uname = etUsername.getText().toString().trim();
            if (uname.isEmpty()) return;
            tvStatus.setText("Looking up " + uname + "…");
            new Thread(() -> {
                try {
                    String addr = cm.getUsernameAddress(uname);
                    runOnUiThread(() -> {
                        if (addr == null) {
                            tvStatus.setText(uname + " not found");
                        } else {
                            tvStatus.setText("Adding on-chain…");
                            new Thread(() -> {
                                try {
                                    cm.addMemberToGroup(groupAddress, addr);
                                    runOnUiThread(() -> {
                                        addMemberChip(chipGroup, addr, uname, dialog, tvStatus);
                                        etUsername.setText("");
                                        tvStatus.setText(uname + " added!");
                                        currentMembers.add(addr);
                                    });
                                } catch (Exception e) {
                                    runOnUiThread(() -> tvStatus.setText("Error: " + e.getMessage()));
                                }
                            }).start();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> tvStatus.setText("Error: " + e.getMessage()));
                }
            }).start();
        });

        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) { etName.setError("Enter name"); return; }
            if (newName.equals(currentGroupName)) { dialog.dismiss(); return; }
            tvStatus.setText("Renaming…");
            btnSave.setEnabled(false);
            new Thread(() -> {
                try {
                    cm.renameGroup(groupAddress, newName);
                    currentGroupName = newName;
                    runOnUiThread(() -> {
                        tvGroupName.setText(newName);
                        dialog.dismiss();
                        toast("Group renamed!");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Error: " + e.getMessage());
                        btnSave.setEnabled(true);
                    });
                }
            }).start();
        });

        dialog.show();
    }

    private void addMemberChip(ChipGroup cg, String address, String label,
                               Dialog dialog, TextView tvStatus) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            tvStatus.setText("Removing " + label + "…");
            new Thread(() -> {
                try {
                    cm.removeMemberFromGroup(groupAddress, address);
                    currentMembers.remove(address);
                    runOnUiThread(() -> {
                        cg.removeView(chip);
                        tvStatus.setText(label + " removed");
                        tvMemberCount.setText(currentMembers.size() + " members");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> tvStatus.setText("Error: " + e.getMessage()));
                }
            }).start();
        });
        cg.addView(chip);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private List<BigInteger> equalShares(int n) {
        if (n == 0) return new ArrayList<>();
        List<BigInteger> shares   = new ArrayList<>();
        int              base      = 100 / n;
        int              remainder = 100 - (base * n);
        for (int i = 0; i < n; i++) {
            shares.add(BigInteger.valueOf(i == 0 ? base + remainder : base));
        }
        return shares;
    }

    private void setActiveBtn(MaterialButton active, MaterialButton inactive) {
        active.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent)));
        active.setTextColor(ContextCompat.getColor(this, R.color.bg_primary));
        inactive.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_soft)));
        inactive.setTextColor(ContextCompat.getColor(this, R.color.accent));
    }

    private String shortAddr(String addr) {
        if (addr == null || addr.length() < 12) return addr;
        return addr.substring(0, 8) + "…" + addr.substring(addr.length() - 4);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
