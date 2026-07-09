# FuelSplit 📱⛽

**Split expenses with friends. The blockchain keeps the receipt — invisibly.**

FuelSplit is a native Android expense-splitting app (think Splitwise) where every expense and settlement is recorded on an immutable on-chain ledger. The twist: **users never see the blockchain.** No wallet setup, no seed phrases, no gas fees, no crypto payments. The chain acts as a tamper-proof referee, not a bank.

> Related repositories:
> - [`FuelSplitContracts`](https://github.com/dakshxndrm/fuelsplit_contracts) — Solidity smart contracts + Hardhat deployment
> - [`fuelsplit-faucet`](https://github.com/dakshxndrm/fuelsplit-faucet) — Serverless gas faucet & identity backend
> - [`fuelsplit_site`](https://github.com/dakshxndrm/fuelsplit_site)

> Apk Link - [`FuelSplit Apk`](https://fuelsplit-site.vercel.app/) — Flask website for downloading the apk of this app.
---

## Table of Contents

- [Why blockchain, and why hide it?](#why-blockchain-and-why-hide-it)
- [Features](#features)
- [Architecture](#architecture)
- [How it works (user journey)](#how-it-works-user-journey)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Local development workflow](#local-development-workflow)
- [Building a release APK](#building-a-release-apk)
- [Reliability engineering](#reliability-engineering)
- [Known limitations](#known-limitations)
- [License](#license)

---

## Why blockchain, and why hide it?

Traditional expense-splitting apps store your group's ledger in a private database. Anyone with admin access can edit or delete history, and you have to trust the company.

FuelSplit puts the ledger on **Ethereum Sepolia**:

| Property | What it means for users |
|---|---|
| **Immutable** | Nobody — not even the developer — can edit or delete an expense once recorded |
| **Transparent** | Every group's history is independently verifiable on Etherscan |
| **Trustless** | The app can disappear tomorrow; the ledger survives |

But blockchain UX is famously terrible. FuelSplit's core design principle is that **crypto is an implementation detail**:

- 🔑 **Wallets are auto-generated** in-app on first launch — no MetaMask, no seed phrases shown to users
- ⛽ **Gas is sponsored** by a treasury-funded faucet backend — users never buy ETH
- 💸 **Settlements are recorded on-chain but paid off-chain** (UPI / cash) — no crypto payments
- 🆔 **Identity is a friend code**, not a `0x...` address

## Features

- ✅ Create groups; each group gets its own on-chain `ExpenseLedger` contract
- ✅ Add expenses with equal splits across selected members
- ✅ Pairwise **net-balance ledger** — reverse debts cancel automatically on-chain
- ✅ **Debt simplification** (greedy algorithm, off-chain): up to N(N−1)/2 debts collapse into ≤ N−1 payments
- ✅ Friend-code identity system (8 chars, collision-resistant charset — no `0/O/1/I/L`)
- ✅ Referral system via `ReferralRegistry`
- ✅ Mark settlements after paying via UPI/cash — permanently recorded on-chain
- ✅ Fully sponsored gas — zero crypto knowledge required

## Architecture

```
┌────────────────────────────────────────────────────┐
│  ANDROID APP (Native Java)                         │
│  • Groups / expenses / balances UI                 │
│  • In-app wallet (auto-generated, stored locally)  │
│  • Web3j 4.9.8 → JSON-RPC                          │
│  • Debt-simplify algorithm (runs on device)        │
└──────────────┬─────────────────────┬───────────────┘
               │ HTTPS               │ JSON-RPC (signed txs)
┌──────────────▼──────────────┐   ┌──▼─────────────────────────┐
│  OFF-CHAIN BACKEND          │   │  ETHEREUM SEPOLIA          │
│  (Vercel + Upstash Redis)   │   │  • UserRegistry            │
│  • /api/fund  → gas faucet  │   │  • ReferralRegistry        │
│  • /api/profile, /api/lookup│   │  • GroupFactory            │
│    → friend-code identity   │   │     └─ ExpenseLedger       │
│  • Atomic daily spend caps  │   │        (one per group)     │
└─────────────────────────────┘   └────────────────────────────┘
```

**Design decisions:**

| Decision | Choice | Why |
|---|---|---|
| Chain | Ethereum Sepolia | Best testnet faucet ecosystem (esp. [PoW faucet](https://sepolia-faucet.pk910.de)) |
| Debt simplification | Off-chain (on device) | Graph algorithms on-chain = gas bomb |
| Group isolation | Contract-per-group (factory) | Clean separation, per-group event streams |
| Settlement money flow | Off-chain (UPI/cash) | Users shouldn't need crypto to pay friends |
| Identity | Friend codes via Redis | Human-friendly; addresses stay hidden |

## How it works (user journey)

1. **Install & open** → app silently generates a wallet, requests gas from the faucet, registers the user in `UserRegistry`, and displays an 8-character friend code.
2. **Create a group** → `GroupFactory` deploys a fresh `ExpenseLedger` contract for that group. Add members by friend code.
3. **Add an expense** → "₹3,000 dinner, split 3 ways" becomes a signed transaction. Pairwise balances update on-chain with automatic net-off.
4. **View balances** → the app shows who owes whom, with a one-tap simplified payment plan.
5. **Settle** → pay your friend over UPI or cash, then mark it settled. The settlement is written to the ledger forever.

## Tech stack

| Layer | Technology |
|---|---|
| App | Native Android, **Java** |
| Blockchain client | **Web3j 4.9.8** |
| Smart contracts | **Solidity 0.8.24**, Hardhat v3 |
| Network | Ethereum **Sepolia** testnet |
| Backend | **Vercel** serverless functions |
| Storage (identity + caps) | **Upstash Redis** |
| Build | Gradle, `apksigner` (v2 scheme) |

## Project structure

```
Fuel_split/
├── app/
│   ├── src/main/java/.../
│   │   ├── ContractManager.java   # Web3j wrapper, tx sending, gas polling
│   │   ├── Config.java            # USE_LOCAL toggle, RPC URLs, contract addresses
│   │   └── ...                    # Activities, adapters, wallet management
│   ├── src/main/res/              # Layouts, drawables, strings
│   └── build.gradle
├── keystore.properties            # Signing credentials (gitignored)
└── gradlew / gradlew.bat
```

## Getting started

### Prerequisites

- Android Studio (Hedgehog or newer)
- JDK 17
- A physical Android device (recommended) or emulator, Android 8.0+
- For local chain testing: Node.js 18+, `adb` in PATH

### Clone & open

```powershell
git clone https://github.com/dakshxndrm/Fuel_split.git
```

Open the folder in Android Studio and let Gradle sync.

### Run against Sepolia (default demo mode)

1. In `Config.java`, set `USE_LOCAL = false`
2. Verify the deployed contract addresses (see [Configuration](#configuration))
3. Run on your device — the faucet funds new wallets automatically

## Configuration

`Config.java` is the single switch between environments:

```java
public static final boolean USE_LOCAL = false; // true = local Hardhat node
```

**Deployed contracts (Ethereum Sepolia):**

| Contract | Address |
|---|---|
| `UserRegistry` | `0x445cdcFB111AbFfA5b239098B04178D6b5e94240` |
| `ReferralRegistry` | `0xAEe80837cF44C013cBBc22dDb585d0E7AD84509B` |
| `GroupFactory` | `0xC8304EBe6B179BA9C2B1A9aEBC311c82008431d2` |

`ExpenseLedger` is **not** deployed standalone — `GroupFactory` deploys one instance per group.

## Local development workflow

Sepolia is reserved for demos. Day-to-day development runs against a **local Hardhat node** tunneled to the physical device:

```powershell
# Terminal 1 — in FuelSplitContracts
npx hardhat node

# Terminal 2 — deploy contracts locally, then seed test users
node seed-users.mjs

# Terminal 3 — tunnel the device to your PC
adb reverse tcp:8545 tcp:8545   # Hardhat RPC
adb reverse tcp:3000 tcp:3000   # local faucet/backend
```

Then set `USE_LOCAL = true` in `Config.java` and run the app.

> **Note:** Friend-code data lives in Upstash Redis (persists across chain resets), but on-chain registrations vanish when the local node restarts — always re-run `seed-users.mjs` after a restart.

## Building a release APK

Signing credentials live in `keystore.properties` (gitignored):

```powershell
.\gradlew assembleRelease
```

Verify the signature:

```powershell
apksigner verify --verbose app\build\outputs\apk\release\app-release.apk
```

Output APK: `app/build/outputs/apk/release/app-release.apk` (signed, v2 scheme).

## Reliability engineering

Real-world testnet conditions (public RPC lag, fresh wallets with zero history) caused first-attempt registration failures. Fixes baked into `ContractManager.java`:

- **`waitForGas()` polling** — after a faucet request, polls the wallet balance up to **15 times at 2s intervals** before attempting the first transaction, absorbing RPC node lag.
- **`sendTx()` retry loop** — every transaction gets **3 attempts**; if gas estimation fails (common on fresh wallets), it falls back to a fixed `REGISTER_GAS_LIMIT` floor instead of aborting.

## Known limitations

Two contract bugs are documented and deferred to a single bundled redeployment (to avoid paying gas twice):

1. `addExpense` doesn't verify the payer is in `_splitMembers` — the payer's share silently drops to zero.
2. `_generateCode` in `UserRegistry` lacks a collision guard on its 8-hex-char code.

Also inherent to the design:

- All balances are publicly readable on-chain (testnet; acceptable for a demo).
- Integer division remainders on uneven splits are assigned per the contract's rounding rule.
- The faucet is a centralized trust point for gas sponsorship (by design — it's what makes the UX invisible).

## License

MIT — see [LICENSE](LICENSE).

---

*Built by Daksh ([@dakshxndrm](https://github.com/dakshxndrm)) — B.Tech CSE, as a full-stack blockchain portfolio project.*
