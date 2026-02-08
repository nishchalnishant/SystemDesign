# #15 ATM system

Below is a complete, time-boxed, interview-ready (1 hour) answer for designing an ATM system (the network, ATM machines, bank backend, switch, settlement, reconciliation). Follow the minute markers while speaking so you cover discovery, design, trade-offs and ops within 60 minutes.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Quickly restate the problem and align scope.

<br>

Goal: Build an ATM network that allows customers to withdraw cash, deposit (envelope or cash-in), check balance, transfer funds, and print mini-statements. The network must support multiple banks (interbank switching), card networks (Visa/Mastercard), and be secure, available, and auditable.

<br>

Scope for interview:

* ATM device behavior, message flows to bank switch and core banking, authorization, cash dispensing, deposit intake, reconciliation and settlement.
* Offline/online modes, error & reversal handling, fraud & security, settlements between banks.

<br>

Assumptions (example numbers to size)

* 50,000 ATMs nationwide.
* Avg 100 transactions/ATM/day → 5M tx/day. Peak: morning/evening queues.
* ATM network must support real-time authorization for withdrawals. Target: authorization latency P95 < 2 sec.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements (Must / Should / Nice)

<br>

Must

1. Card accept & authenticate: read EMV/chip and/or magstripe, prompt PIN entry, authenticate cardholder.
2. Cash withdrawal: verify funds, dispense requested amount, record cash cassette depletion.
3. Deposit: accept envelope or note-by-note cash (if supported), provide provisional credit or hold.
4. Balance inquiry: show account balances.
5. Transfer / mini-statement: internal transfers and recent transactions list.
6. Receipt & print: print or offer e-receipt.
7. Reversal & exception flows: handle failed dispenses, partial dispenses, timeouts and generate reversals.
8. Interbank support: route messages via switch to card issuer or acquiring bank; support fee handling.
9. Reconciliation & end-of-day: per-ATM and central reconciliation, settlement files between banks.
10. Monitoring & alerts: ATM health, cash levels, cassette faults, fraud alerts.

<br>

Should

* Multi-language UI, contactless/NFC, cash recycling (dispense/accept same notes).
* Offline-mode: allow limited offline transactions when connectivity down (risk-based).
* Cardless withdrawals (OTP / mobile QR).

<br>

Nice-to-have

* Dynamic cash forecasting & automated cash replenishment scheduling.
* Biometric auth (fingerprint/face) optionally.

<br>

#### Non-Functional Requirements

* Availability: 99.9%+ for ATM network (core functions) during business hours.
* Latency: Authorization P95 < 2s; end-to-end withdrawal < 10s (excluding customer interaction).
* Durability & correctness: no lost transactions; guarantee money conservation — wallet ledger = bank balance changes.
* Security & compliance: PCI DSS, EMV standards, encrypted PIN (PIN block), HSM for key management.
* Scalability: handle peak loads and bursts (e.g., salary days).
* Auditability & traceability: immutable audit logs for every event (for disputes/regulators).
* Operational visibility: real-time metrics, cash level telemetry, ATM health, reconciliation metrics.

<br>

Concrete SLO / Acceptance examples

* Authorization success/latency: P95 < 2s.
* Failed dispense reversal rate < 0.01% and mean reconciliation lag < 5 min.
* Settlement files generated & sent by 01:00 daily.

***

## 15 – 25 min — External APIs, messages & data model (contract)

<br>

#### ATM ↔ Switch message types (ISO 8583 style)

* Authorization request: MTI 0100 with PAN, amount, processing code, PIN block, terminal ID, timestamp.
* Authorization response: MTI 0110 with approval/decline code, auth ID, available balance (optional).
* Advice / Reversal: MTI 0420 / 0430 or specific reversal messages for failed dispenses.
* Financial transaction capture: end-of-day capture files or online settlement messages.
* Reconciliation / Settlement: batch files (ISO 20022, or custom) exchanged with other banks/acquirers and card networks.

<br>

#### Simplified data model

<br>

ATM\_Device

* atm\_id, location, status, cash\_levels{cassette1,cassette2}, capabilities (deposit, recycle)

<br>

TxnMessage

* message\_id, atm\_id, card\_pan\_hash, txn\_type, amount, account\_id (masked), response\_code, auth\_id, ts, server\_ts

<br>

CoreLedger

* ledger\_entry\_id, account\_id, debit/credit, amount, balance\_after, ts, source (atm,msgid)

<br>

ReconciliationRecord

* atm\_id, date, transactions\[], discrepancies\[], cash\_totals, reconciled\_flag

***

## 25 – 40 min — High-level architecture & data flow

```
ATM Device (UI + PIN pad + Cash Dispenser + Deposit Module)
      |
Secure Network (TLS / Private MPLS) or Public Internet (VPN)
      |
ATM Switch / Acquirer Gateway  (ISO8583 translator, routing, logging, rules)
      |
+----------------+         +--------------------+         +------------------+
| Fraud & Risk   |<------->| Authorization Core |<------->| Core Banking /   |
| Service (rules)|         | (acquirer/issuer)  |         | Ledger Database  |
+----------------+         +--------------------+         +------------------+
      |
  Clearing & Settlement (batch) -> Card Networks / Interbank Settlement
      |
Monitoring/Operations, Reconciliation, HSM for PIN/keys, Audit Log
```

Key components explained

1. ATM Device: UI, card reader (EMV), PIN entry device (PED), dispenser, deposit module, local logfile & journaling. PIN block encrypted via shared key from HSM. Device logs events locally until acknowledged by switch.
2. Network & Gateway: secure path to ATM switch (could be bank-owned MPLS or VPN over Internet). Gateway handles protocol translation and initial validations.
3. ATM Switch: routing logic — based on card BIN, routes to appropriate issuer or acquiring switch, enforces limit checks, fee logic, logging, TTLs, and supports retry/backoff and failover.
4. Fraud & Risk Service: real-time rules: velocity checks, blacklists, stolen-card watch, device fingerprint anomaly, geolocation checks.
5. Authorization Core / Core Banking: validates PIN, checks available balance, places hold (authorization) on account, returns approval + auth id. For cash withdrawal, this hold must be captured when cash dispensed.
6. HSM: manages keys, PIN translation and verification, EMV cryptography, MACs and secure key storage.
7. Reconciliation & Settlement: captures transactions, generates settlement files for interchange / interbank settlement, handles disputes & chargebacks.
8. Monitoring & Ops: cabling to CCTV, alarms for dispenser jam, cash low telemetry, predictive cash replenishment.

<br>

End-to-end Withdraw flow (normal online)

1. Card inserted → ATM reads chip / PAN → requests PIN entry (PIN encrypted via HSM keys).
2. ATM sends Authorization request to Switch (ISO 8583) with PIN block and amount.
3. Switch routes to issuer (either in-house core or card network) → Authorization core checks balance, fraud checks, returns APPROVED + auth\_id.
4. ATM dispenses cash. ATM sends Completion / Advice message with dispense details; core posts ledger debit and removes authorization hold.
5. ATM prints receipt; transaction logged centrally and locally.

<br>

Failed dispense / timeout

* If ATM dispenses but response lost, ATM must record local audit and send advice when network restored. Issuer and switch must support idempotent capture via auth\_id or advice messages — if authorization exists but no capture, reversal or reconciliation flow kicks in.

<br>

Deposit flow

* For envelope deposit: ATM accepts envelope and provides provisional credit (or not); physical verification later during branch processing; reconciliation adjusts after verification. For note-by-note deposit machines, count and validate cash, credit available balance (may be provisional until verification).

***

## 40 – 50 min — Deep dives — correctness, consistency, reversals, offline mode, and reconciliation

<br>

#### Money correctness & ledger model

* Two-step model (Authorization + Capture / Completion):
  * AUTH: Place a hold (authorization) on account for amount. The hold prevents double spending.
  * CAPTURE/CAP: On successful dispense (ATM sends confirmation), system converts hold into a debit in ledger. If capture fails or never arrives, reversal must remove hold and refund hold amount.
* Idempotency & unique IDs: auth\_id and message\_id used to identify and make operations idempotent. Messages from ATM must include unique local sequence numbers to avoid duplicates.
* Atomic ledger update: Core banking must ensure atomicity: convert hold → debit or rollback hold. Use DB transaction (ACID) to maintain balance invariants.

<br>

#### Partial / failed dispenses & reversals

* Dispensed but no capture response: ATM logs local dispense proof (journal + cassette counters) and notifies switch. Switch attempts replay; core matches via auth\_id and either captures or triggers investigation.
* Not dispensed but capture recorded: Reversal flow: switch sends reversal or dispute; core reverses ledger entry and credits customer. Strong audit trail needed.

<br>

#### Offline ATM mode

* Risk model: only allow limited offline withdrawals per card (e.g., cuft), or only for preauthorized cards. ATM maintains local list of valid cards or tracks per-card offline counters (risk). Offline increases fraud risk and requires reconciliation upon reconnect.
* Queueing: ATM queues advice messages locally; when network restored, replay with unique sequence ids, switch reconciles.

<br>

#### Reconciliation & settlement

* Per-ATM reconciliation: ATM end-of-day (or periodic) totals of cash dispensed vs transaction logs; cash cassette counts reconciled with central transaction records. Discrepancies open cash audits.
* Interbank settlement: Switch aggregates transactions (interchange fees, acquirer/issuer settlements) into batch files and sends to clearing house/card network. Settlement may settle net positions between banks.
* Dispute management: Chargebacks or customer disputes involve evidence: ATM journal, CCTV, transaction log, and timestamps.

<br>

#### Security & Compliance

* PIN & EMV: PIN entry must be via approved PED; PIN block encrypted (ISO 9564) and only decrypted in HSM inside issuer domain. EMV offline/online transaction handling: dynamic data authentication (DDA) or combined DDA with online PIN.
* PCI DSS: minimize card data exposure, tokenize PAN for logs, secure retention policies.
* HSM: for PIN translation, EMV keys, MAC generation/verification. Key management with rotation & strong access controls.
* Tamper detection: ATM local tamper sensors, alerts to operations.

***

## 50 – 55 min — Back-of-the-envelope capacity, sizing & examples

<br>

Assumptions: 50k ATMs, 100 tx/day per ATM → 5M tx/day.

<br>

Message throughput

* Average messages per tx \~ 2–4 (auth, capture, advice) → 10–20M messages/day ≈ 116–232 msgs/sec average; peak maybe 5× → \~1,200 msgs/sec peak. This is modest for modern systems but design for bursts and retries.

<br>

Network & storage

* Logs: each message \~ 1 KB → 10–20 GB/day of message logs; archive to S3. Keep hot audit logs for 30–90 days.

<br>

HSMs

* HSM transactions: PIN translations per auth. HSM concurrency and throughput sizing required; choose multiple HSMs with load balancing and high availability.

<br>

Databases

* Core ledger DB must handle account transactions with strong consistency and high writes; partition by account ranges or use sharded NewSQL system. 5M tx/day -> sustained write throughput: manageable but requires replication and fast disks.

<br>

ATM devices

* Local storage for journaling: ensure SSD or durable flash to log local events for failover and dispute proofs.

***

## 55 – 58 min — Monitoring, operations & playbooks

<br>

Monitoring & alerts

* Monitor: authorization latency, failed dispense rate, network outages, ATM cash levels, reconciliation mismatches, fraud alerts.
* SLO alerts: high auth latency, message backlog, HSM errors, elevated reversals.

<br>

Ops playbooks

* Cash discrepancy investigation: isolate ATM, request physical audit, escalate to ops.
* Network partition: run on partial capacity, restrict offline limits, notify customers.
* HSM failover: route PIN ops to secondary HSM cluster; ensure key synchronization and secure failover procedures.

<br>

Testing & compliance

* Regular PCI assessments, EMV certifications, penetration testing, mock reconciliation failures, DR drills.

***

## 58 – 60 min — Trade-offs, evolution & summary (wrap-up)

<br>

#### Key trade-offs

* Immediate online-only model vs offline capability: Online-only is safer (real-time auth), simpler correctness; offline allows continuity but increases fraud risk and reconciliation complexity.
* Synchronous durability vs latency: synchronous persist of WAL before response ensures no lost transactions but increases latency; can use local ATM journaling + async central persist with idempotency and replay to balance latency/durability.
* Centralized ledger vs distributed sharding: Centralized simplifies balance checks but is a single scaling barrier; sharded core needed for scale and geo-locality—requires careful routing of auths to correct shard.
* Provisional credit for deposits: giving immediate credit improves UX but exposes bank to risk until envelope verification is done. Policy decision per bank.

<br>

#### Evolution path

1. MVP: online-only auth and capture, basic reconciliation, central ledger, HSM, and switch.
2. Phase 2: add deposit automation (note recycler), ATM cash forecasting & replenishment, better fraud ML.
3. Phase 3: advanced capabilities: cardless flows, mobile OTP withdrawals, real-time interbank settlement, global multi-region resilience.

<br>

#### One-line summary

<br>

Design a secure, auditable ATM ecosystem where each ATM is a trusted endpoint that sends encrypted ISO8583 messages to a resilient ATM switch and core banking ledger; use HSMs for PIN/EMV, ensure two-step auth→capture flows for correctness, implement robust reconciliation & settlement mechanisms, and operate with strong monitoring and incident playbooks — balancing online safety and practical offline fallback where needed.

***

If you want, I can now:

* convert this into a one-page cheat-sheet you can carry into interviews, or
* produce a sequence diagram for a withdrawal (Card→ATM→Switch→Issuer→Capture→Reconciliation), or
* run capacity math tuned to your exact ATM counts and transactions/day.

<br>

Which should I produce next?
