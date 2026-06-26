package com.example.fuel_split;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;

import okhttp3.OkHttpClient;
import okhttp3.ConnectionSpec;

import java.util.Collections;
import java.util.Arrays;


public class BlockchainManager {

    public static final String RPC_URL = Config.RPC_URL;

    private final Web3j web3;

    public BlockchainManager() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(
                        ConnectionSpec.MODERN_TLS,
                        ConnectionSpec.CLEARTEXT))
                .build();
        this.web3 = Web3j.build(new HttpService(RPC_URL, okHttpClient));
    }

    public Web3j getWeb3() {
        return web3;
    }

    // ARCHIVED: testConnection() — dev-only RPC ping, never called in production code
    // public String testConnection() throws Exception {
    //     Web3ClientVersion version = web3.web3ClientVersion().send();
    //     return version.getWeb3ClientVersion();
    // }
}