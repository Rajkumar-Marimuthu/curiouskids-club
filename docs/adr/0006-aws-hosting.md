# ADR-0006: Host on AWS with ECS Fargate, RDS, S3, CloudFront and SES, defined in Terraform

**Status:** Proposed
**Date:** 2026-09-19
**Deciders:** Rajkumar (owner)

## Context

The owner wants an enterprise-grade platform with a path to scale, at a launch cost of roughly $25 to $65 a month. Traffic is small; correctness, security and low operational effort matter most. Nobody should patch servers.

## Decision

Run the API as a container on AWS ECS Fargate behind an Application Load Balancer, PostgreSQL on Amazon RDS, the React build on S3 behind CloudFront (with `/api/*` routed to the load balancer, one origin), email through Amazon SES, DNS in Route 53, and all infrastructure as Terraform deployed by GitHub Actions using OIDC. The same container image can move to a leaner host if needed.

## Options considered

| Option | Complexity | Cost at launch (approx.) | Scalability | Team familiarity |
| --- | --- | --- | --- | --- |
| A. AWS ECS Fargate + RDS + S3/CloudFront + SES | Medium | About $55 to $65 a month | Excellent: add tasks, Multi-AZ, replicas | Medium |
| B. AWS Lightsail containers + managed database | Low | About $25 to $35 a month | Good; migrate to A later | Medium |
| C. Google Cloud Run + managed PostgreSQL | Low to medium | About $5 to $25 a month | Excellent, scales to zero | Medium |
| D. Render or Railway | Low | About $26 to $35 a month | Good; less control | High |
| E. AWS App Runner | Low | Similar to A | n/a | Closed to new customers by AWS |

Costs come from the plan document (section 8), using list prices checked on 2026-09-19.

**A pros:** enterprise standard, deepest feature set (WAF, Multi-AZ, IAM, CloudTrail), no servers to patch, infrastructure as code, easy rollback by image tag.
**A cons:** highest fixed cost (the load balancer alone is about $16 to $20 a month), more configuration than the alternatives.
**B pros:** cheapest AWS path, same account and tooling as A. **B cons:** fewer enterprise controls.
**C pros:** very cheap while traffic is tiny. **C cons:** different cloud skills; database cost dominates.
**D pros:** fastest setup. **D cons:** less control over networking and compliance.

## Trade-off analysis

The owner asked for enterprise-level selection, which favours A. Because the app is a single container plus a database, B and C are cheap fallbacks that need no code changes, so choosing A does not lock in the cost.

## Consequences

- Easier: scale-out, Multi-AZ, WAF, auditing, least-privilege access; repeatable environments.
- Harder: Terraform and AWS knowledge required; fixed monthly floor of roughly $55.
- Revisit if the monthly cost becomes a concern before launch (fall back to B for the pilot), or if a region or data-residency rule requires otherwise (OQ-01).

## Action items

1. [ ] Choose region and domain (OQ-01).
2. [ ] Terraform and staging pipeline (T-005); production (T-064).
3. [ ] Budget alert at $80 a month (T-005).
