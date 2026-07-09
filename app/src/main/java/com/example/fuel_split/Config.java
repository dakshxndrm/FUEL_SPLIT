package com.example.fuel_split;

public final class Config {
    static final boolean USE_LOCAL = false;

    static final String RPC_URL = USE_LOCAL
            ? "http://127.0.0.1:8545"
            : "https://ethereum-sepolia-rpc.publicnode.com";

    static final String FAUCET_URL = USE_LOCAL
            ? "http://127.0.0.1:3000/api/fund"
            : "https://fuelsplit-faucet.vercel.app/api/fund";

    static final String USER_REGISTRY = USE_LOCAL
            ? "0x5FbDB2315678afecb367f032d93F642f64180aa3"
            : "0xf033b30Aa179A56CccD241E6e715236a132Cede1";

    static final String GROUP_FACTORY = USE_LOCAL
            ? "0x9fE46736679d2D9a65F0992F2272dE9f3c7fa6e0"
            : "0xf2691bF8eB76773A1D861a36f4a919B37f2cf5be";

    static final long   CHAIN_ID     = USE_LOCAL ? 31337L : 11155111L;

    static final String PROFILE_BASE = USE_LOCAL
            ? "http://127.0.0.1:3000"
            : "https://fuelsplit-faucet.vercel.app";

    private Config() {}
}