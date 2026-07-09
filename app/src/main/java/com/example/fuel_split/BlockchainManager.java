package com.example.fuel_split;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;


public class BlockchainManager {

    public static final String RPC_URL = Config.RPC_URL;

    private final Web3j web3;

    public BlockchainManager() {
        // HttpService's default OkHttpClient already allows both TLS and
        // cleartext, so no custom ConnectionSpec is needed for the local node.
        this.web3 = Web3j.build(new HttpService(RPC_URL));
    }

    public Web3j getWeb3() {
        return web3;
    }
}