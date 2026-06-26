package com.example.fuel_split;

public final class Config {
    static final boolean USE_LOCAL = true;

    static final String RPC_URL = USE_LOCAL
            ? "http://127.0.0.1:8545"
            : "https://rpc-amoy.polygon.technology/";

    static final String FAUCET_URL = USE_LOCAL
            ? "http://127.0.0.1:3000"
            : "https://fuelsplit-faucet.vercel.app/api/fund";

    static final String USER_REGISTRY = USE_LOCAL
            ? "0xc3e53F4d16Ae77Db1c982e75a937B9f60FE63690"
            : "0xD81528FFA49c8BA0d725B4bFd3F27C3b63f983Ea";

    static final String GROUP_FACTORY = USE_LOCAL
            ? "0x9E545E3C0baAB3E08CdfD552C960A1050f373042"
            : "0x97CC2151b535fC1E13D51903D3E4c18D93eF825f";

    static final long   CHAIN_ID     = USE_LOCAL ? 31337L : 80002L;

    static final String PROFILE_BASE = "https://fuelsplit-faucet.vercel.app";

    private Config() {}
}
