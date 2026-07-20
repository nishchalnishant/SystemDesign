# CI/CD Pipeline

> **Source**: [CI/CD In 5 Minutes | Is It Worth The Hassle: Crash Course System Design #2](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #11
> Also see: [How Big Tech Ships Code to Production](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #46

---

## What is CI/CD?

- **CI (Continuous Integration)**: Automatically build and test code on every commit
- **CD (Continuous Delivery)**: Automatically prepare releases for deployment
- **CD (Continuous Deployment)**: Automatically deploy to production

```
Code Commit → Build → Unit Tests → Integration Tests → Deploy to Staging → Deploy to Prod
     CI ─────────────────────────────┘                         CD ────────────────────┘
```

---

## CI Pipeline Steps

1. **Code commit** triggers pipeline (webhook)
2. **Build**: Compile code, resolve dependencies
3. **Unit tests**: Fast, isolated tests
4. **Static analysis**: Linting, code quality checks
5. **Integration tests**: Test component interactions
6. **Artifact creation**: Docker image, binary, package

## CD Pipeline Steps

1. **Deploy to staging**: Mirror of production
2. **Smoke tests**: Basic health checks
3. **Performance tests**: Load testing
4. **Approval gate** (optional): Manual approval
5. **Deploy to production**: Gradual rollout
6. **Monitoring**: Watch for errors/regressions

---

## Deployment Strategies

| Strategy | Description | Risk | Rollback |
|---|---|---|---|
| **Big Bang** | Replace all at once | High | Redeploy old version |
| **Rolling** | Replace instances one by one | Medium | Stop and reverse |
| **Blue-Green** | Run two environments, switch traffic | Low | Switch back to blue |
| **Canary** | Route small % to new version | Low | Remove canary |
| **Feature Flags** | Toggle features in code | Lowest | Turn off flag |

---

## How Big Tech Ships Code

1. **Monorepo** (Google, Meta): All code in one repository
2. **Trunk-based development**: Short-lived branches, merge to main quickly
3. **Feature flags**: Deploy code without enabling features
4. **Canary releases**: Gradual rollout to 1% → 5% → 25% → 100%
5. **Automated testing**: Thousands of tests run per commit
6. **Monitoring + Auto-rollback**: Detect issues, rollback automatically

---

## Key Tools

| Tool | Type |
|---|---|
| GitHub Actions | CI/CD |
| Jenkins | CI/CD (self-hosted) |
| GitLab CI | CI/CD |
| CircleCI | CI/CD |
| ArgoCD | GitOps CD for Kubernetes |
| Spinnaker | CD (Netflix) |
