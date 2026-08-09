# Distributed Architecture

How to design, deploy, and reason about systems made of many independent services.

| # | Topic | What it covers |
|---|---|---|
| 01 | [Distributed Systems](01-distributed-systems.md) | Fallacies, failure modes, consistency |
| 02 | [Core Distributed Concepts](02-distributed-concepts.md) | CAP, PACELC, quorum, vector clocks |
| 03 | [Microservices](03-microservices.md) | Monolith vs microservices, service mesh, Saga pattern, service discovery |
| 04 | [Event-Driven Architecture](04-event-driven-architecture.md) | Commands vs events, choreography vs orchestration, event sourcing |
| 05 | [Stream Processing](05-stream-processing.md) | Batch vs stream, time windows, Kafka Streams/Flink use cases |
| 06 | [Kubernetes & Container Orchestration](06-kubernetes-containers.md) | Docker, K8s control plane, Pods/Deployments/Services, HPA |
| 07 | [Transactional Outbox & CDC](07-outbox-cdc-pattern.md) | Dual-write problem, outbox table, Debezium-style log-based CDC |
| 08 | [gRPC vs REST vs GraphQL](08-grpc-rest-graphql.md) | API paradigm trade-offs — protocols, payload shape, coupling |
