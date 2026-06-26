package com.example.fuel_split;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ContractManager {

    public static final String GROUP_FACTORY_ADDRESS = Config.GROUP_FACTORY;
    public static final String USER_REGISTRY_ADDRESS = Config.USER_REGISTRY;
    private static final long  CHAIN_ID              = Config.CHAIN_ID;
    private static final long  DEFAULT_GAS_LIMIT     = 3_000_000L;
    private static final long  REGISTER_GAS_LIMIT    = 500_000L;

    private final Web3j       web3;
    private final Credentials credentials;
    private final PollingTransactionReceiptProcessor receiptProcessor;

    // ── Debt struct mirrors ExpenseLedger.Debt ────────────────────────────────

    public static class DebtStruct extends DynamicStruct {
        public final Uint256    id;
        public final Address    debtor;
        public final Address    creditor;
        public final Uint256    amountPaise;
        public final Utf8String description;
        public final Bool       settled;
        public final Uint256    createdAt;
        public final Uint256    settledAt;

        public DebtStruct(Uint256 id, Address debtor, Address creditor,
                          Uint256 amountPaise, Utf8String description,
                          Bool settled, Uint256 createdAt, Uint256 settledAt) {
            super(id, debtor, creditor, amountPaise, description, settled, createdAt, settledAt);
            this.id          = id;
            this.debtor      = debtor;
            this.creditor    = creditor;
            this.amountPaise = amountPaise;
            this.description = description;
            this.settled     = settled;
            this.createdAt   = createdAt;
            this.settledAt   = settledAt;
        }
    }

    public static class DebtRecord {
        public int     id;
        public String  debtor;
        public String  creditor;
        public long    amountPaise;
        public String  description;
        public boolean settled;
        public long    createdAt;
        public long    settledAt;
        public String  groupAddress; // set by getDebts() for cross-group lookups
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public ContractManager(Web3j web3, Credentials credentials) {
        this.web3             = web3;
        this.credentials      = credentials;
        this.receiptProcessor = new PollingTransactionReceiptProcessor(web3, 5000, 40);
    }

    public TransactionReceipt waitForReceipt(String txHash) throws Exception {
        return receiptProcessor.waitForTransactionReceipt(txHash);
    }

    public boolean hasGas() throws Exception {
        BigInteger balance = web3.ethGetBalance(
                credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .send().getBalance();
        return balance.compareTo(BigInteger.ZERO) > 0;
    }

    private void requireGas() throws Exception {
        if (!hasGas()) {
            throw new Exception(
                    "This wallet has no test POL for gas. " +
                    "Fund it from the Polygon Amoy faucet first. " +
                    "Address: " + credentials.getAddress());
        }
    }

    // ── UserRegistry reads ────────────────────────────────────────────────────

    public boolean isRegistered(String address) throws Exception {
        Function fn = new Function("isRegistered",
                Collections.singletonList(new Address(address)),
                Collections.singletonList(new TypeReference<Bool>() {}));
        List<Type> result = ethCall(USER_REGISTRY_ADDRESS, fn);
        return !result.isEmpty() && (Boolean) result.get(0).getValue();
    }

    public String getUsernameAddress(String username) throws Exception {
        Function fn = new Function("getAddressByUsername",
                Collections.singletonList(new Utf8String(username)),
                Collections.singletonList(new TypeReference<Address>() {}));
        List<Type> result = ethCall(USER_REGISTRY_ADDRESS, fn);
        if (result.isEmpty()) return null;
        String addr = result.get(0).getValue().toString();
        return addr.equals("0x0000000000000000000000000000000000000000") ? null : addr;
    }

    // ── GroupFactory reads ────────────────────────────────────────────────────

    public List<String> getUserGroups() throws Exception {
        Function fn = new Function("getUserGroups",
                Collections.singletonList(new Address(credentials.getAddress())),
                Collections.singletonList(new TypeReference<DynamicArray<Address>>() {}));
        return decodeAddressList(ethCall(GROUP_FACTORY_ADDRESS, fn));
    }

    // ── ExpenseLedger reads ───────────────────────────────────────────────────

    public String getGroupName(String groupAddress) throws Exception {
        Function fn = new Function("groupName", Collections.emptyList(),
                Collections.singletonList(new TypeReference<Utf8String>() {}));
        List<Type> result = ethCall(groupAddress, fn);
        return result.isEmpty() ? "Group" : result.get(0).getValue().toString();
    }

    public boolean isGroupDeleted(String groupAddress) throws Exception {
        Function fn = new Function("deleted", Collections.emptyList(),
                Collections.singletonList(new TypeReference<Bool>() {}));
        List<Type> result = ethCall(groupAddress, fn);
        return !result.isEmpty() && (Boolean) result.get(0).getValue();
    }

    public String getGroupCreator(String groupAddress) throws Exception {
        Function fn = new Function("creator", Collections.emptyList(),
                Collections.singletonList(new TypeReference<Address>() {}));
        List<Type> result = ethCall(groupAddress, fn);
        return result.isEmpty() ? "" : result.get(0).getValue().toString();
    }

    public List<String> getGroupMembers(String groupAddress) throws Exception {
        Function fn = new Function("getMembers", Collections.emptyList(),
                Collections.singletonList(new TypeReference<DynamicArray<Address>>() {}));
        return decodeAddressList(ethCall(groupAddress, fn));
    }

    public long getOwed(String groupAddress, String debtor, String creditor) throws Exception {
        Function fn = new Function("getOwed",
                Arrays.asList(new Address(debtor), new Address(creditor)),
                Collections.singletonList(new TypeReference<Uint256>() {}));
        List<Type> result = ethCall(groupAddress, fn);
        return result.isEmpty() ? 0L : ((BigInteger) result.get(0).getValue()).longValue();
    }

    public List<DebtRecord> getDebts(String groupAddress) throws Exception {
        Function fn = new Function("getDebts", Collections.emptyList(),
                Collections.singletonList(new TypeReference<DynamicArray<DebtStruct>>() {}));
        List<Type> result = ethCall(groupAddress, fn);
        List<DebtRecord> records = new ArrayList<>();
        if (result.isEmpty()) return records;
        @SuppressWarnings("unchecked")
        List<DebtStruct> structs = (List<DebtStruct>) result.get(0).getValue();
        for (DebtStruct d : structs) {
            DebtRecord r = new DebtRecord();
            r.id           = (int) d.id.getValue().longValue();
            r.debtor       = d.debtor.getValue();
            r.creditor     = d.creditor.getValue();
            r.amountPaise  = d.amountPaise.getValue().longValue();
            r.description  = d.description.getValue();
            r.settled      = d.settled.getValue();
            r.createdAt    = d.createdAt.getValue().longValue();
            r.settledAt    = d.settledAt.getValue().longValue();
            r.groupAddress = groupAddress;
            records.add(r);
        }
        return records;
    }

    // ── UserRegistry writes ───────────────────────────────────────────────────

    public String register(String username, String referralCode) throws Exception {
        requireGas();
        Function fn = new Function("register",
                Arrays.asList(new Utf8String(username), new Utf8String(referralCode)),
                Collections.emptyList());
        return sendTx(USER_REGISTRY_ADDRESS, FunctionEncoder.encode(fn), REGISTER_GAS_LIMIT);
    }

    // ── GroupFactory writes ───────────────────────────────────────────────────

    public String createGroup(String groupName, List<String> memberAddresses) throws Exception {
        requireGas();
        Function fn = new Function("createGroup",
                Arrays.asList(new Utf8String(groupName), toAddressArray(memberAddresses)),
                Collections.singletonList(new TypeReference<Address>() {}));
        return sendTx(GROUP_FACTORY_ADDRESS, FunctionEncoder.encode(fn), DEFAULT_GAS_LIMIT);
    }

    // ── ExpenseLedger writes ──────────────────────────────────────────────────

    public String addExpense(String groupAddress, String description,
                             BigInteger amountPaise, List<String> memberAddresses,
                             List<BigInteger> shares) throws Exception {
        requireGas();
        DynamicArray<Uint256> sharesArr = new DynamicArray<>(Uint256.class,
                shares.stream().map(Uint256::new)
                        .collect(java.util.stream.Collectors.toList()));
        Function fn = new Function("addExpense",
                Arrays.asList(new Utf8String(description), new Uint256(amountPaise),
                        toAddressArray(memberAddresses), sharesArr),
                Collections.emptyList());
        return sendTx(groupAddress, FunctionEncoder.encode(fn), DEFAULT_GAS_LIMIT);
    }

    public String settleDebt(String groupAddress, long debtId) throws Exception {
        requireGas();
        Function fn = new Function("settleDebt",
                Collections.singletonList(new Uint256(debtId)),
                Collections.emptyList());
        return sendTx(groupAddress, FunctionEncoder.encode(fn), DEFAULT_GAS_LIMIT);
    }

    public String deleteGroup(String groupAddress) throws Exception {
        requireGas();
        Function fn = new Function("deleteGroup",
                Collections.emptyList(),
                Collections.emptyList());
        return sendTx(groupAddress, FunctionEncoder.encode(fn), DEFAULT_GAS_LIMIT);
    }

    public String renameGroup(String groupAddress, String newName) throws Exception {
        Function fn = new Function("renameGroup",
                Collections.singletonList(new Utf8String(newName)),
                Collections.emptyList());
        return sendTx(groupAddress, FunctionEncoder.encode(fn), DEFAULT_GAS_LIMIT);
    }

    public String addMemberToGroup(String groupAddress, String memberAddress) throws Exception {
        Function fn = new Function("addMember",
                Collections.singletonList(new Address(memberAddress)),
                Collections.emptyList());
        return sendTx(groupAddress, FunctionEncoder.encode(fn), DEFAULT_GAS_LIMIT);
    }

    public String removeMemberFromGroup(String groupAddress, String memberAddress) throws Exception {
        Function fn = new Function("removeMember",
                Collections.singletonList(new Address(memberAddress)),
                Collections.emptyList());
        return sendTx(groupAddress, FunctionEncoder.encode(fn), DEFAULT_GAS_LIMIT);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Type> ethCall(String contractAddress, Function fn) throws Exception {
        String encoded = FunctionEncoder.encode(fn);
        EthCall response = web3.ethCall(
                Transaction.createEthCallTransaction(credentials.getAddress(), contractAddress, encoded),
                DefaultBlockParameterName.LATEST).send();
        if (response.hasError()) throw new Exception(response.getError().getMessage());
        return FunctionReturnDecoder.decode(response.getValue(), fn.getOutputParameters());
    }

    private String sendTx(String to, String encodedFn, long gasLimit) throws Exception {
        BigInteger nonce    = web3.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();
        BigInteger gasPrice = web3.ethGasPrice().send().getGasPrice();
        BigInteger gas      = BigInteger.valueOf(gasLimit);
        try {
            Transaction estimateTx = Transaction.createFunctionCallTransaction(
                    credentials.getAddress(), nonce, gasPrice, null, to, encodedFn);
            org.web3j.protocol.core.methods.response.EthEstimateGas est =
                    web3.ethEstimateGas(estimateTx).send();
            if (!est.hasError()) {
                gas = est.getAmountUsed().multiply(BigInteger.valueOf(5)).divide(BigInteger.valueOf(4));
            }
        } catch (Exception ignored) {}
        RawTransaction raw  = RawTransaction.createTransaction(nonce, gasPrice, gas, to, encodedFn);
        byte[] signed = TransactionEncoder.signMessage(raw, CHAIN_ID, credentials);
        EthSendTransaction tx = web3.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        if (tx.hasError()) throw new Exception(tx.getError().getMessage());
        return tx.getTransactionHash();
    }

    private DynamicArray<Address> toAddressArray(List<String> addresses) {
        return new DynamicArray<>(Address.class,
                addresses.stream().map(Address::new)
                        .collect(java.util.stream.Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private List<String> decodeAddressList(List<Type> result) {
        List<String> out = new ArrayList<>();
        if (result.isEmpty()) return out;
        for (Address a : (List<Address>) result.get(0).getValue()) out.add(a.getValue());
        return out;
    }
}
