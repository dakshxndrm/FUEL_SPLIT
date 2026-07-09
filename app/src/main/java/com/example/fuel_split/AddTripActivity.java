package com.example.fuel_split;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.math.BigInteger;
import java.util.*;
import java.util.HashSet;
import java.util.Set;


public class AddTripActivity extends AppCompatActivity {

    private ViewFlipper viewFlipper;
    private EditText etTripName, etTripNamePreload, etNewPersonName, etDistance, etMileage, etFuelPrice;
    private ChipGroup chipGroup;
    private LinearLayout percentageContainer, splitModeContainer, contactsListContainer;
    private Button btnNext, btnSplitEqual, btnSplitPercent, btnSplitKm;
    private MaterialButton btnAddPerson;
    private Spinner spinnerPaidBy;
    private TextView tvStepLabel, tvStepTitle, tvSplitModeHint;
    private LinearProgressIndicator stepProgress;

    public static final String EXTRA_PRELOAD_GROUP = "preload_group";

    // member displayName → wallet address (null if not resolved)
    private final LinkedHashMap<String, String> memberToAddress = new LinkedHashMap<>();
    private final List<String> members = new ArrayList<>();
    // known people (contacts + group members): address → displayName
    private final LinkedHashMap<String, String> allKnownPeople = new LinkedHashMap<>();
    // addresses currently selected in the list
    private final Set<String> selectedAddresses = new HashSet<>();

    private int step = 0;
    private String splitMode = "equal";
    private String preloadGroupAddress = null; // set when launched from a group

    private static final String[] STEP_TITLES = {
            "Who was there?",
            "Trip Details",
            "Split the Cost"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_trip);
        EdgeToEdge.apply(this, R.id.rootAddTrip, R.id.addTripHeader, R.id.addTripBottomBar);

        viewFlipper         = findViewById(R.id.viewFlipper);
        etTripName          = findViewById(R.id.etTripName);
        etNewPersonName     = findViewById(R.id.etNewPersonName);
        etDistance          = findViewById(R.id.etDistance);
        etMileage           = findViewById(R.id.etMileage);
        etFuelPrice         = findViewById(R.id.etFuelPrice);
        chipGroup           = findViewById(R.id.dynamicChipGroup);
        percentageContainer   = findViewById(R.id.percentageContainer);
        contactsListContainer = findViewById(R.id.contactsListContainer);
        etTripNamePreload     = findViewById(R.id.etTripNamePreload);
        spinnerPaidBy         = findViewById(R.id.spinnerPaidBy);
        btnNext             = findViewById(R.id.btnNext);
        btnAddPerson        = findViewById(R.id.btnAddPerson);
        tvStepLabel         = findViewById(R.id.tvStepLabel);
        tvStepTitle         = findViewById(R.id.tvStepTitle);
        stepProgress        = findViewById(R.id.stepProgress);

        ContactStore.seedNameResolver(this);

        btnAddPerson.setOnClickListener(v -> addMember());
        btnNext.setOnClickListener(v -> handleNext());

        // Load contacts + group members for the selectable list (only relevant in normal mode)
        loadKnownPeople();

        // Stage 6: if launched from a group, skip "Who was there?" and preload members
        preloadGroupAddress = getIntent().getStringExtra(EXTRA_PRELOAD_GROUP);
        if (preloadGroupAddress != null) {
            step = 1;
            viewFlipper.setDisplayedChild(1);
            btnNext.setEnabled(false);
            btnNext.setText("Loading members…");
            tvStepTitle.setText("Loading group…");
            // Show trip name field in step 2 (step 0 is skipped)
            etTripNamePreload.setVisibility(View.VISIBLE);
            findViewById(R.id.tvTripNamePreloadLabel).setVisibility(View.VISIBLE);
            loadGroupMembersForPreload(preloadGroupAddress);
        }

        updateHeader();
    }

    private void loadGroupMembersForPreload(String groupAddr) {
        new Thread(() -> {
            try {
                WalletManager wm = new WalletManager(this);
                org.web3j.crypto.Credentials creds = wm.getOrCreateWallet();
                BlockchainManager bm = new BlockchainManager();
                ContractManager cm = new ContractManager(bm.getWeb3(), creds);

                List<String> addrs = cm.getGroupMembers(groupAddr);
                for (String addr : addrs) {
                    // Try live lookup; fall back to NameResolver cache or shortAddr
                    String name;
                    try {
                        String[] info = ProfileClient.lookupByAddress(addr);
                        if (info != null && info.length > 1 && !info[1].isEmpty()) {
                            NameResolver.seed(addr, info[1]);
                            name = info[1];
                        } else {
                            name = NameResolver.nameFor(addr);
                        }
                    } catch (Exception e) {
                        name = NameResolver.nameFor(addr);
                    }
                    String finalName = name;
                    String finalAddr = addr;
                    runOnUiThread(() -> {
                        if (!members.contains(finalName)) {
                            members.add(finalName);
                            memberToAddress.put(finalName, finalAddr);
                        }
                    });
                }
                runOnUiThread(() -> {
                    btnNext.setEnabled(true);
                    updateHeader();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnNext.setEnabled(true);
                    updateHeader();
                    Toast.makeText(this, "Could not load group: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ── Known-people list (contacts + group members) ───────────────────────
    private void loadKnownPeople() {
        // 1. Load contacts from disk immediately (fast)
        List<ContactStore.Contact> contacts = ContactStore.loadContacts(this);
        for (ContactStore.Contact c : contacts) {
            if (c.address == null || c.address.isEmpty()) continue;
            String name = (c.displayName != null && !c.displayName.isEmpty())
                    ? c.displayName : c.code;
            allKnownPeople.put(c.address.toLowerCase(), name);
        }
        renderKnownPeopleList();

        // 2. Pull group members from chain in background and merge
        new Thread(() -> {
            try {
                WalletManager wm = new WalletManager(this);
                org.web3j.crypto.Credentials creds2 = wm.getOrCreateWallet();
                BlockchainManager bm = new BlockchainManager();
                ContractManager cm2 = new ContractManager(bm.getWeb3(), creds2);
                String myAddr = creds2.getAddress().toLowerCase();

                List<String> groups = cm2.getUserGroups();
                boolean changed = false;
                for (String grpAddr : groups) {
                    for (String addr : cm2.getGroupMembers(grpAddr)) {
                        String lowerAddr = addr.toLowerCase();
                        if (lowerAddr.equals(myAddr)) continue;
                        if (!allKnownPeople.containsKey(lowerAddr)) {
                            allKnownPeople.put(lowerAddr, NameResolver.nameFor(addr));
                            changed = true;
                        }
                    }
                }
                if (changed) runOnUiThread(this::renderKnownPeopleList);
            } catch (Exception ignored) {}
        }).start();
    }

    private void renderKnownPeopleList() {
        if (contactsListContainer == null) return;
        contactsListContainer.removeAllViews();

        if (allKnownPeople.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No saved contacts yet");
            empty.setTextColor(0xFF7B8AA0);
            empty.setTextSize(13);
            empty.setPadding(0, 4, 0, 4);
            contactsListContainer.addView(empty);
            return;
        }

        for (Map.Entry<String, String> entry : allKnownPeople.entrySet()) {
            String addr = entry.getKey();
            String name = entry.getValue();
            boolean selected = selectedAddresses.contains(addr);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackgroundResource(selected ? R.drawable.bg_segment_selected : R.drawable.bg_input);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(6));
            row.setLayoutParams(params);

            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextColor(selected ? 0xFF0A0E1A : 0xFFF0F0FF);
            tvName.setTextSize(15);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvName);

            TextView tvMark = new TextView(this);
            tvMark.setText(selected ? "✓" : "+");
            tvMark.setTextColor(selected ? 0xFF0A0E1A : 0xFF7B8AA0);
            tvMark.setTextSize(18);
            row.addView(tvMark);

            row.setOnClickListener(v -> {
                if (selectedAddresses.contains(addr)) {
                    // Deselect
                    selectedAddresses.remove(addr);
                    members.remove(name);
                    memberToAddress.remove(name);
                    // Remove matching chip
                    for (int i = chipGroup.getChildCount() - 1; i >= 0; i--) {
                        View child = chipGroup.getChildAt(i);
                        if (addr.equals(child.getTag())) {
                            chipGroup.removeView(child);
                            break;
                        }
                    }
                } else {
                    // Select
                    selectedAddresses.add(addr);
                    if (!members.contains(name)) {
                        members.add(name);
                        memberToAddress.put(name, addr);
                    }
                    Chip chip = new Chip(this);
                    chip.setText(name + " ✓");
                    chip.setTag(addr);
                    chip.setCloseIconVisible(true);
                    chip.setOnCloseIconClickListener(cv -> {
                        selectedAddresses.remove(addr);
                        members.remove(name);
                        memberToAddress.remove(name);
                        chipGroup.removeView(chip);
                        renderKnownPeopleList();
                    });
                    chipGroup.addView(chip);
                }
                renderKnownPeopleList();
            });

            contactsListContainer.addView(row);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ── Update header ──────────────────────────────────────────────────────
    private void updateHeader() {
        if (preloadGroupAddress != null) {
            // 2-step flow: Trip Details (step=1) → Split the Cost (step=2)
            int displayStep = step - 1 + 1; // step 1→1, step 2→2
            tvStepLabel.setText("Step " + displayStep + " of 2");
            stepProgress.setProgress(displayStep * 50, true);
        } else {
            tvStepLabel.setText("Step " + (step + 1) + " of 3");
            stepProgress.setProgress((step + 1) * 33, true);
        }
        tvStepTitle.setText(STEP_TITLES[step]);
        btnNext.setText(step == 2 ? "Submit Trip  ✓" : "Next  →");
    }

    // ── Add a member via friend code (async lookup) ────────────────────────
    private void addMember() {
        String input = etNewPersonName.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Enter a friend code or name", Toast.LENGTH_SHORT).show();
            return;
        }

        btnAddPerson.setEnabled(false);
        btnAddPerson.setText("…");

        new Thread(() -> {
            String displayName = input;
            String address     = null;
            try {
                String[] result = ProfileClient.lookupByCode(input);
                address     = result[0];
                displayName = result[1].isEmpty() ? input : result[1];
                ContactStore.addContact(AddTripActivity.this, input, displayName, address);
            } catch (Exception ignored) {
                // no profile found — store as plain name, no address
            }

            String finalName    = displayName;
            String finalAddress = address;

            runOnUiThread(() -> {
                btnAddPerson.setEnabled(true);
                btnAddPerson.setText("Add");

                if (members.contains(finalName)) {
                    Toast.makeText(this, finalName + " already added", Toast.LENGTH_SHORT).show();
                    return;
                }

                members.add(finalName);
                memberToAddress.put(finalName, finalAddress);
                if (finalAddress != null) {
                    // Also add to allKnownPeople so row shows ✓
                    allKnownPeople.put(finalAddress.toLowerCase(), finalName);
                    selectedAddresses.add(finalAddress.toLowerCase());
                }

                Chip chip = new Chip(this);
                chip.setText(finalName + (finalAddress != null ? " ✓" : ""));
                chip.setTag(finalAddress != null ? finalAddress.toLowerCase() : null);
                chip.setCloseIconVisible(true);
                chip.setOnCloseIconClickListener(v -> {
                    members.remove(finalName);
                    memberToAddress.remove(finalName);
                    chipGroup.removeView(chip);
                    if (finalAddress != null) selectedAddresses.remove(finalAddress.toLowerCase());
                    renderKnownPeopleList();
                });
                chipGroup.addView(chip);
                etNewPersonName.setText("");
                renderKnownPeopleList(); // update row checkmarks
            });
        }).start();
    }

    // ── Next / Submit ─────────────────────────────────────────────────────
    private void handleNext() {
        if (step == 0) {
            if (etTripName.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Please enter a trip name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (members.size() < 2) {
                Toast.makeText(this, "Add at least 2 members", Toast.LENGTH_SHORT).show();
                return;
            }
            step++;
            viewFlipper.showNext();
            updateHeader();

        } else if (step == 1) {
            String dist  = etDistance.getText().toString().trim();
            String mil   = etMileage.getText().toString().trim();
            String price = etFuelPrice.getText().toString().trim();

            if (dist.isEmpty() || mil.isEmpty() || price.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Double.parseDouble(mil) <= 0) {
                Toast.makeText(this, "Mileage must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            step++;
            buildStep3();
            viewFlipper.showNext();
            updateHeader();

        } else {
            submitTrip();
        }
    }

    // ── Step 3 UI (unchanged) ─────────────────────────────────────────────
    private void buildStep3() {
        percentageContainer.removeAllViews();

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, members);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaidBy.setAdapter(spinnerAdapter);

        double distance  = Double.parseDouble(etDistance.getText().toString());
        double mileage   = Double.parseDouble(etMileage.getText().toString());
        double fuelPrice = Double.parseDouble(etFuelPrice.getText().toString());
        double totalCost = (distance / mileage) * fuelPrice;

        TextView tvTotal = new TextView(this);
        tvTotal.setText("Total fuel cost:  ₹" + String.format("%.2f", totalCost));
        tvTotal.setTextColor(0xFF00C9A7);
        tvTotal.setTextSize(17);
        tvTotal.setPadding(0, 0, 0, dp(8));
        percentageContainer.addView(tvTotal);

        TextView tvSplitLabel = new TextView(this);
        tvSplitLabel.setText("How do you want to split?");
        tvSplitLabel.setTextColor(0xFF7B8AA0);
        tvSplitLabel.setTextSize(13);
        tvSplitLabel.setPadding(0, dp(20), 0, dp(10));
        percentageContainer.addView(tvSplitLabel);

        splitModeContainer = new LinearLayout(this);
        splitModeContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        modeParams.setMargins(0, 0, 0, dp(20));
        splitModeContainer.setLayoutParams(modeParams);

        btnSplitEqual   = createModeButton("Equal",   "equal");
        btnSplitPercent = createModeButton("By %",    "percentage");
        btnSplitKm      = createModeButton("By KM",   "km");

        splitModeContainer.addView(btnSplitEqual);
        splitModeContainer.addView(btnSplitPercent);
        splitModeContainer.addView(btnSplitKm);
        percentageContainer.addView(splitModeContainer);

        tvSplitModeHint = new TextView(this);
        tvSplitModeHint.setTextColor(0xFF7B8AA0);
        tvSplitModeHint.setTextSize(13);
        tvSplitModeHint.setPadding(0, 0, 0, dp(16));
        percentageContainer.addView(tvSplitModeHint);

        updateSplitMode("equal");
    }

    private Button createModeButton(String label, String mode) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(0, 0, dp(8), 0);
        btn.setLayoutParams(params);
        btn.setStateListAnimator(null);
        btn.setOnClickListener(v -> updateSplitMode(mode));
        return btn;
    }

    private void updateSplitMode(String mode) {
        splitMode = mode;
        updateButtonState(btnSplitEqual,   mode.equals("equal"));
        updateButtonState(btnSplitPercent, mode.equals("percentage"));
        updateButtonState(btnSplitKm,      mode.equals("km"));

        int childCount = percentageContainer.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            View child = percentageContainer.getChildAt(i);
            if (child.getTag() != null && child.getTag().equals("inputRow"))
                percentageContainer.removeView(child);
        }

        if (mode.equals("equal")) {
            tvSplitModeHint.setText("Cost will be divided equally among all members");
            buildEqualSplitUI();
        } else if (mode.equals("percentage")) {
            tvSplitModeHint.setText("Enter each person's % — must total 100");
            buildPercentageSplitUI();
        } else {
            tvSplitModeHint.setText("Enter kilometers driven by each person");
            buildKmSplitUI();
        }
    }

    private void updateButtonState(Button btn, boolean selected) {
        if (selected) {
            btn.setBackgroundResource(R.drawable.bg_segment_selected);
            btn.setTextColor(0xFF0A0E1A);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            btn.setBackgroundResource(R.drawable.bg_segment_unselected);
            btn.setTextColor(0xFF8B98AE);
            btn.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void buildEqualSplitUI() {
        double totalCost  = calculateTotalCost();
        double equalShare = totalCost / members.size();
        for (String name : members) {
            TextView tv = new TextView(this);
            tv.setText(name + "  •  ₹" + String.format("%.2f", equalShare));
            tv.setTextColor(0xFFF0F0FF);
            tv.setTextSize(15);
            tv.setPadding(dp(16), dp(12), dp(16), dp(12));
            tv.setBackgroundResource(R.drawable.bg_input);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(8));
            tv.setLayoutParams(params);
            tv.setTag("inputRow");
            percentageContainer.addView(tv);
        }
    }

    private void buildPercentageSplitUI() {
        for (String name : members) percentageContainer.addView(createInputRow(name, "%", "0"));
    }

    private void buildKmSplitUI() {
        for (String name : members) percentageContainer.addView(createInputRow(name, "km", "0"));
    }

    private LinearLayout createInputRow(String name, String unit, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_input);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);
        row.setTag("inputRow");

        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextColor(0xFFF0F0FF);
        tv.setTextSize(15);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(0xFF3A4A60);
        et.setTextColor(0xFF00C9A7);
        et.setTextSize(16);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setGravity(Gravity.CENTER);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(dp(72),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        etParams.setMargins(dp(8), 0, dp(8), 0);
        et.setLayoutParams(etParams);

        TextView unitTv = new TextView(this);
        unitTv.setText(unit);
        unitTv.setTextColor(0xFF7B8AA0);
        unitTv.setTextSize(14);

        row.addView(tv);
        row.addView(et);
        row.addView(unitTv);
        return row;
    }

    private double calculateTotalCost() {
        double distance  = Double.parseDouble(etDistance.getText().toString());
        double mileage   = Double.parseDouble(etMileage.getText().toString());
        double fuelPrice = Double.parseDouble(etFuelPrice.getText().toString());
        return (distance / mileage) * fuelPrice;
    }

    // ── Submit: compute shares, then save to TripStore (+ chain if possible) ──
    private void submitTrip() {
        double totalCost = calculateTotalCost();
        double distance  = Double.parseDouble(etDistance.getText().toString());
        double mileage   = Double.parseDouble(etMileage.getText().toString());
        double fuelUsed  = distance / mileage;

        HashMap<String, Double> shares = new HashMap<>();

        if (splitMode.equals("equal")) {
            double equalShare = totalCost / members.size();
            for (String name : members) shares.put(name, equalShare);

        } else if (splitMode.equals("percentage")) {
            double totalPercent = 0;
            for (int i = 0; i < members.size(); i++) {
                View row = findInputRowByIndex(i);
                if (row instanceof LinearLayout) {
                    EditText et = (EditText) ((LinearLayout) row).getChildAt(1);
                    String percentStr = et.getText().toString().trim();
                    if (percentStr.isEmpty()) {
                        Toast.makeText(this, members.get(i) + "'s % is empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double percent = Double.parseDouble(percentStr);
                    if (percent < 0 || percent > 100) {
                        Toast.makeText(this, members.get(i) + "'s % must be 0–100", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    totalPercent += percent;
                    shares.put(members.get(i), (percent / 100.0) * totalCost);
                }
            }
            if (Math.abs(totalPercent - 100) > 0.01) {
                Toast.makeText(this,
                        "Percentages must total 100%  (currently " +
                        String.format("%.1f", totalPercent) + "%)",
                        Toast.LENGTH_LONG).show();
                return;
            }

        } else {
            double totalKm = 0;
            HashMap<String, Double> kmMap = new HashMap<>();
            for (int i = 0; i < members.size(); i++) {
                View row = findInputRowByIndex(i);
                if (row instanceof LinearLayout) {
                    EditText et = (EditText) ((LinearLayout) row).getChildAt(1);
                    String kmStr = et.getText().toString().trim();
                    if (kmStr.isEmpty()) {
                        Toast.makeText(this, members.get(i) + "'s km is empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double km = Double.parseDouble(kmStr);
                    if (km < 0) {
                        Toast.makeText(this, members.get(i) + "'s km cannot be negative", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    totalKm += km;
                    kmMap.put(members.get(i), km);
                }
            }
            if (totalKm == 0) { Toast.makeText(this, "Total km cannot be 0", Toast.LENGTH_SHORT).show(); return; }
            for (String name : members) shares.put(name, (kmMap.get(name) / totalKm) * totalCost);
        }

        // In preload mode the trip name comes from the step-2 field
        String tripName = (preloadGroupAddress != null)
                ? etTripNamePreload.getText().toString().trim()
                : etTripName.getText().toString().trim();
        if (tripName.isEmpty()) {
            Toast.makeText(this, "Please enter a trip name", Toast.LENGTH_SHORT).show();
            btnNext.setEnabled(true);
            btnNext.setText("Submit Trip  ✓");
            return;
        }
        String paidBy   = spinnerPaidBy.getSelectedItem().toString();
        String distStr  = String.format("%.1f", distance);
        String fuelStr  = String.format("%.2f", fuelUsed);
        String costStr  = String.format("%.2f", totalCost);

        // disable button while saving
        btnNext.setEnabled(false);
        btnNext.setText("Saving…");

        HashMap<String, Double> finalShares = shares;

        new Thread(() -> {
            // If launched from a group, write expense on that group; otherwise keep trip local-only
            String groupAddress = preloadGroupAddress;

            if (groupAddress != null) {
                try {
                    WalletManager   wm    = new WalletManager(this);
                    org.web3j.crypto.Credentials creds = wm.getOrCreateWallet();
                    BlockchainManager bm  = new BlockchainManager();
                    ContractManager   cm  = new ContractManager(bm.getWeb3(), creds);

                    String desc = "name=" + tripName
                            + ";km=" + distStr
                            + ";mileage=" + String.format("%.1f", mileage)
                            + ";fuel=" + String.format("%.2f", totalCost);

                    long amtPaise = Math.round(totalCost * 100);

                    // Use the group's actual member list for the on-chain expense
                    List<String>     groupMembers   = cm.getGroupMembers(groupAddress);
                    List<BigInteger> equalPctShares = equalShares(groupMembers.size());
                    cm.waitForReceipt(cm.addExpense(groupAddress, desc,
                            BigInteger.valueOf(amtPaise), groupMembers, equalPctShares));
                } catch (Exception e) {
                    android.util.Log.e("FUELSPLIT", "Chain error during trip submit", e);
                    // non-fatal: still save locally
                }
            }

            // Build and persist the trip locally
            Trip trip = new Trip(tripName,
                    distStr + " km",
                    fuelStr + " L",
                    costStr,
                    members.toString(),
                    finalShares,
                    paidBy);
            trip.setGroupAddress(groupAddress);
            TripStore.addTrip(this, trip);

            final boolean onChain = (groupAddress != null);
            runOnUiThread(() -> {
                Toast.makeText(this,
                        onChain ? "Trip saved on-chain!" : "Trip saved locally",
                        Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    private List<BigInteger> equalShares(int n) {
        if (n == 0) return new ArrayList<>();
        List<BigInteger> result    = new ArrayList<>();
        int              base      = 100 / n;
        int              remainder = 100 - (base * n);
        for (int i = 0; i < n; i++)
            result.add(BigInteger.valueOf(i == 0 ? base + remainder : base));
        return result;
    }

    private View findInputRowByIndex(int index) {
        int rowCount = 0;
        for (int i = 0; i < percentageContainer.getChildCount(); i++) {
            View child = percentageContainer.getChildAt(i);
            if (child.getTag() != null && child.getTag().equals("inputRow")) {
                if (rowCount == index) return child;
                rowCount++;
            }
        }
        return null;
    }
}