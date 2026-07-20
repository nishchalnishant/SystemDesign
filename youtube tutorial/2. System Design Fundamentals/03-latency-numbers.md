# Latency Numbers Every Programmer Should Know

> **Source**: [Latency Numbers Programmer Should Know: Crash Course System Design #1](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #3

---

## The Numbers

| Operation | Latency | Notes |
|---|---|---|
| L1 cache reference | ~1 ns | |
| L2 cache reference | ~4 ns | |
| L3 cache reference | ~10 ns | |
| Main memory (RAM) reference | ~100 ns | |
| SSD random read | ~100 μs | 1,000x slower than RAM |
| HDD seek | ~10 ms | 100x slower than SSD |
| Send 1 KB over 1 Gbps network | ~10 μs | |
| Read 1 MB sequentially from RAM | ~250 μs | |
| Read 1 MB sequentially from SSD | ~1 ms | |
| Read 1 MB sequentially from HDD | ~20 ms | |
| Round trip within same datacenter | ~500 μs | |
| Round trip CA → Netherlands | ~150 ms | |
| Disk seek | ~10 ms | |
| Send packet CA → Netherlands → CA | ~150 ms | |

---

## Key Insights

### Memory vs Disk
```
RAM:  ~100 ns
SSD:  ~100 μs (1,000x slower)
HDD:  ~10 ms  (100,000x slower)
```

### Network Latency
```
Same datacenter:    ~0.5 ms
Same region:        ~5-10 ms
Cross-continent:    ~100-150 ms
```

### Why This Matters for System Design
1. **Cache aggressively** — RAM is 1000x faster than SSD
2. **Minimize network round trips** — batch requests when possible
3. **Keep data close to compute** — same datacenter/region
4. **Sequential reads >> Random reads** — design for sequential I/O
5. **Compress before sending** — network is often the bottleneck

---

## Back-of-Envelope Calculations

### Quick References
```
1 day     = 86,400 seconds ≈ 10^5 seconds
1 year    = ~31.5 million seconds ≈ 3 × 10^7 seconds
1 million = 10^6
1 billion = 10^9

1 KB = 1,000 bytes
1 MB = 10^6 bytes
1 GB = 10^9 bytes
1 TB = 10^12 bytes
```

### QPS Estimation
```
If 1 million DAU, each makes 10 requests/day:
  Total requests/day = 10^7
  QPS = 10^7 / 10^5 = 100 QPS
  Peak QPS = ~2-3x average = 200-300 QPS
```

### Storage Estimation
```
If each user generates 1 KB of data/day with 1M DAU:
  Daily storage = 1 GB/day
  Yearly storage = 365 GB/year ≈ 0.4 TB/year
```
