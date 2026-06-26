package com.example.fuel_split;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.security.Security;

public class WalletManager {

    // Fix: Android ships a cut-down BouncyCastle. Web3j needs the full one.
    static {
        Security.removeProvider("BC");
        Security.insertProviderAt(
                new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    private static final String PREF_FILE = "fuel_split_wallet";
    private static final String KEY_PRIVATE = "private_key";

    private final SharedPreferences prefs;

    public WalletManager(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        prefs = EncryptedSharedPreferences.create(
                context,
                PREF_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    public boolean hasWallet() {
        return prefs.contains(KEY_PRIVATE);
    }

    public Credentials createWallet() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String privateKey = Numeric.toHexStringNoPrefixZeroPadded(keyPair.getPrivateKey(), 64);
        prefs.edit().putString(KEY_PRIVATE, privateKey).apply();
        return Credentials.create(keyPair);
    }

    public Credentials loadWallet() {
        String privateKey = prefs.getString(KEY_PRIVATE, null);
        if (privateKey == null) return null;
        return Credentials.create(privateKey);
    }

    public Credentials getOrCreateWallet() throws Exception {
        if (hasWallet()) {
            return loadWallet();
        }
        return createWallet();
    }
}
