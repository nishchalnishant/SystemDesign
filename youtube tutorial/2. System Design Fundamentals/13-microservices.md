# Microservices Architecture

> **Source**: [What Are Microservices Really All About? (And When Not To Use It)](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #20

---

## Monolith vs Microservices

| Feature | Monolith | Microservices |
|---|---|---|
| **Deployment** | Single deployable unit | Independent services |
| **Scaling** | Scale everything together | Scale individual services |
| **Technology** | Single tech stack | Polyglot (different tech per service) |
| **Team Structure** | One large team | Small, autonomous teams |
| **Complexity** | Simpler initially | Complex infrastructure |
| **Data** | Shared database | Database per service |
| **Communication** | In-process function calls | Network calls (HTTP, gRPC, messages) |

---

## Benefits of Microservices

1. **Independent deployability**: Deploy one service without affecting others
2. **Technology diversity**: Use best tool for each service
3. **Scalability**: Scale busy services independently
4. **Fault isolation**: One service failure doesn't crash everything
5. **Team autonomy**: Small teams own their services end-to-end

---

## Challenges of Microservices

1. **Network complexity**: Latency, failures, retries
2. **Data consistency**: No shared transactions across services
3. **Distributed debugging**: Tracing across services is hard
4. **Operational overhead**: More services = more to manage
5. **Service discovery**: Finding service instances
6. **Configuration management**: Consistent config across services

---

## Communication Patterns

### Synchronous
- **REST/HTTP**: Simple, widely understood
- **gRPC**: Fast, type-safe, streaming

### Asynchronous
- **Message Queue** (Kafka, RabbitMQ): Decoupled, resilient
- **Event-driven**: Services publish/subscribe to events

---

## When NOT to Use Microservices

- Small team (< 10 engineers)
- Simple application with clear boundaries
- Startup / MVP phase — optimize for speed
- Team lacks distributed systems experience
- Strong consistency requirements across services

> **Rule of thumb**: Start with a monolith, extract services as you grow
