# Mobile App Release & Miscellaneous Topics

> **Source**: Videos #55, #62, #64, #72 from the playlist
> - Top 6 Tools to Turn Code into Beautiful Diagrams
> - Top 9 Must-Read Blogs for Engineers
> - Do You Know How Mobile Apps Are Released?
> - 25 Computer Papers You Should Read!

---

## Mobile App Release Process

### iOS App Release
```
1. Develop & Test locally
2. Archive build in Xcode
3. Upload to App Store Connect
4. TestFlight (beta testing)
5. App Review by Apple (1-3 days)
6. Release to App Store
```

### Android App Release
```
1. Develop & Test locally
2. Build signed APK/AAB
3. Upload to Google Play Console
4. Internal/Closed/Open testing tracks
5. Review by Google (hours to days)
6. Staged rollout (1% → 5% → 25% → 100%)
7. Full release
```

### Key Concepts
- **Feature flags**: Control feature visibility without new release
- **Staged rollout**: Gradually increase user percentage
- **A/B testing**: Test variants with different user groups
- **OTA updates**: Update JS bundle without app store (React Native, Flutter)
- **Force update**: Require users to update for critical fixes

---

## Tools for Diagrams from Code

| Tool | Description |
|---|---|
| **Mermaid** | Markdown-based diagrams (flowcharts, sequence, ER) |
| **PlantUML** | Text-to-UML diagrams |
| **D2** | Modern diagram scripting language |
| **Diagrams (Python)** | Cloud architecture diagrams as code |
| **Excalidraw** | Hand-drawn style diagrams |
| **draw.io** | Free diagramming tool |

---

## Must-Read Engineering Blogs

| Company | Blog URL | Known For |
|---|---|---|
| **Netflix** | netflixtechblog.com | Streaming, microservices, chaos engineering |
| **Uber** | eng.uber.com | Real-time systems, maps, ML |
| **Meta** | engineering.fb.com | Scale, social, ML infrastructure |
| **Google** | cloud.google.com/blog | Distributed systems, infrastructure |
| **Airbnb** | medium.com/airbnb-engineering | Search, payments, data |
| **Stripe** | stripe.com/blog/engineering | Payments, API design |
| **Cloudflare** | blog.cloudflare.com | Networking, edge computing |
| **Discord** | discord.com/blog | Real-time communication |
| **LinkedIn** | engineering.linkedin.com | Kafka, data infrastructure |

---

## Key Computer Science Papers

### Distributed Systems
- **MapReduce** (Google, 2004) — Large-scale data processing
- **GFS** (Google, 2003) — Distributed file system
- **Bigtable** (Google, 2006) — Wide-column store
- **Dynamo** (Amazon, 2007) — Key-value store, consistent hashing
- **Raft** (2014) — Understandable consensus algorithm

### Databases
- **Spanner** (Google, 2012) — Globally distributed database
- **F1** (Google, 2013) — Distributed SQL database

### Networking & Web
- **TCP Congestion Control** (Jacobson, 1988)
- **The Google File System** (2003)

### Machine Learning
- **Attention Is All You Need** (2017) — Transformer architecture
- **BERT** (2018) — Pre-trained language model
- **ImageNet** (2012) — Deep learning breakthrough

### Systems
- **The Unix Time-Sharing System** (1974)
- **End-to-End Arguments in System Design** (1984)
