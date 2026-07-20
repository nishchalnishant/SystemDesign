# DevOps, SRE & Platform Engineering

> **Source**: Videos #9, #36, #38 from the playlist
> - Debugging Like A Pro
> - DevOps vs SRE vs Platform Engineering | Clear Big Misconceptions
> - Why Google and Meta Put Billion Lines of Code In 1 Repository?

---

## DevOps vs SRE vs Platform Engineering

| Role | Focus | Responsibility |
|---|---|---|
| **DevOps** | Culture & practices | Bridge between dev and ops; CI/CD, automation, collaboration |
| **SRE** | Reliability & uptime | SLAs/SLOs, incident response, error budgets, on-call |
| **Platform Engineering** | Developer experience | Build internal tools and platforms for developers |

### DevOps
- **Philosophy**: Break down silos between development and operations
- **Practices**: CI/CD, Infrastructure as Code, monitoring, automation
- **Tools**: Jenkins, Terraform, Ansible, Docker, Kubernetes

### SRE (Site Reliability Engineering)
- **Origin**: Created by Google ("SRE is what happens when you treat operations as a software problem")
- **Key concepts**:
  - **SLI** (Service Level Indicator): Measurable metric (latency, error rate)
  - **SLO** (Service Level Objective): Target value for an SLI (99.9% availability)
  - **SLA** (Service Level Agreement): Contract with customers
  - **Error Budget**: Allowed downtime before stopping feature releases

### Platform Engineering
- **Focus**: Build a self-service internal developer platform (IDP)
- **Goal**: Developers should be able to deploy and operate their services independently
- **Examples**: Internal CI/CD, Kubernetes abstractions, service templates

---

## Monorepo vs Polyrepo

### Why Google & Meta Use Monorepos

| Feature | Monorepo | Polyrepo |
|---|---|---|
| **All code** | Single repository | Multiple repositories |
| **Code sharing** | Easy (same repo) | Requires packages/versioning |
| **Dependency management** | Single version | Diamond dependency problem |
| **Refactoring** | Atomic cross-project changes | Coordinated PRs across repos |
| **Tooling** | Shared build tools | Independent toolchains |
| **Scale challenge** | Need specialized VCS (Sapling, Piper) | Standard Git works fine |

### Monorepo Benefits
1. **Code visibility**: Everyone can see all code
2. **Code sharing**: Easy to reuse libraries
3. **Atomic changes**: Update API + all consumers in one commit
4. **Consistent tooling**: One build system, one CI pipeline

### Monorepo Challenges
- Standard Git doesn't scale to millions of files
- Google uses **Piper** (internal), Meta uses **Sapling** (open-source)
- Need **virtual file system** to avoid cloning everything

---

## Debugging Like A Pro

### Systematic Debugging Approach
1. **Reproduce**: Consistently reproduce the issue
2. **Isolate**: Narrow down where the problem occurs
3. **Inspect**: Use logs, debuggers, metrics
4. **Hypothesize**: Form a theory about the cause
5. **Test**: Validate the hypothesis
6. **Fix**: Implement the solution
7. **Verify**: Confirm the fix works
8. **Learn**: Document and prevent recurrence

### Key Debugging Tools
- **Logging**: Structured logs with context (request ID, user ID)
- **Distributed tracing**: Jaeger, Zipkin — trace requests across services
- **Metrics**: Prometheus, Grafana — system health dashboards
- **Profiling**: CPU, memory, I/O profiling
- **Core dumps**: Post-mortem analysis of crashes
