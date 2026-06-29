---
module: 07-interview-templates
topic: Behavioral
status: unread
tags: [07-interview-templates, amazon, behavioral, leadership-principles]
---
# Amazon Leadership Principles — Behavioral Bank

---

## 1. Customer Obsession

**Signal**: Do you start from the customer's problem, or from the technical solution? Amazon wants to see you work backwards.

**STAR 1 — Proactive customer impact**
- S/T: Users were dropping off at checkout; no tickets filed, no escalation.
- A: Pulled session replay data and identified a confusing error state that wasn't in our error logs.
  Proposed a one-line fix to the error message and a fallback retry path.
  Got buy-in from PM and pushed it in the same sprint.
- R: Cart abandonment at that step dropped 18% in 2 weeks. No customer complained — we caught it first.

**STAR 2 — Pushing back on a feature that hurt UX**
- S/T: Team was asked to add a confirmation modal to every delete action to reduce support tickets.
- A: Analyzed support data — 90% of tickets came from one specific delete flow, not all deletes.
  Presented data to PM and proposed scoping the modal to that one flow only.
- R: Shipped a targeted fix, avoided adding friction site-wide, support tickets for that flow fell 60%.

**Watch out**: Describing what customers want without citing data or direct signal. Avoid "I assumed users would prefer..." — say what you observed or measured.

---

## 2. Ownership

**Signal**: Do you treat problems as yours even when they're outside your ticket? Amazon expects engineers to own outcomes, not tasks.

**STAR 1 — Picking up dropped work**
- S/T: Mid-sprint, a teammate went on leave with an unfinished migration that blocked the release.
- A: Picked up the task without being asked, read their design doc, finished the remaining 40% of the migration.
  Flagged two edge cases I found and fixed them before merging.
- R: Release shipped on time. The edge cases I found would have caused data inconsistency in prod.

**STAR 2 — Cross-team production issue**
- S/T: On-call at 2 AM, pager fired for a service I didn't own. Primary owner unreachable.
- A: Read the runbook, identified the root cause (a misconfigured rate limit), applied the documented mitigation.
  Filed a post-mortem draft and notified the owning team at 9 AM with full context.
- R: Downtime limited to 12 minutes. Owning team used my post-mortem draft and added a better alert.

**Watch out**: Saying "that was outside my scope" or framing ownership as helping a teammate rather than owning the outcome. Avoid "I helped with..." — say "I drove..." or "I took responsibility for..."

---

## 3. Invent and Simplify

**Signal**: Can you reduce complexity without sacrificing correctness? Amazon wants engineers who eliminate toil and rethink assumptions.

**STAR 1 — Removing a manual process**
- S/T: Deploying to staging required 6 manual steps across 3 dashboards; took 20 min per deploy.
- A: Wrote a deploy script that automated steps 1-5 and surfaced step 6 with a single prompt.
  Added a dry-run flag so devs could validate config before firing.
- R: Deploy time dropped to 3 minutes. Team ran 4x more deploys per week; faster iteration on staging bugs.

**STAR 2 — Simplifying an overengineered design**
- S/T: Inherited a service that used a message queue, worker pool, and cache for what was effectively a batch job.
- A: Mapped the actual data flow, showed that 95% of jobs completed in under 50ms, queue was unnecessary overhead.
  Proposed replacing with a direct synchronous call and removing the cache layer.
  Got sign-off from tech lead after a load test validated the new path held at peak traffic.
- R: Reduced infrastructure cost by ~30%, eliminated two failure modes, on-call burden dropped noticeably.

**Watch out**: Presenting complexity as innovation. If your "invention" adds components, explain why simpler options were ruled out first.

---

## 4. Dive Deep

**Signal**: When something breaks, can you get to root cause rather than treating symptoms? Amazon values engineers who don't stop at the first plausible explanation.

**STAR 1 — Debugging an intermittent failure**
- S/T: A service had a 0.3% error rate that appeared random; no pattern in logs.
- A: Added structured logging around the failure path, reproduced it in staging with load testing.
  Traced the failure to a race condition in connection pool reuse under high concurrency.
  Wrote a unit test that reliably reproduced the race before fixing it.
- R: Error rate dropped to 0.0%. Test now runs in CI to prevent regression.

**STAR 2 — Investigating a performance regression**
- S/T: P99 latency on an API increased 40% after a seemingly unrelated schema change.
- A: Ran EXPLAIN ANALYZE on the affected queries, found a missing index after the schema update invalidated the query planner's cached plan.
  Added the index and added a latency check to the release checklist for schema changes.
- R: Latency returned to baseline. The checklist item caught a similar issue 6 weeks later.

**Watch out**: Describing the fix without describing the investigation. The interviewer wants to hear how you narrowed down the cause, not just what you changed.

---

## 5. Bias for Action

**Signal**: Can you make a decision with incomplete information and course-correct? Amazon explicitly values speed over waiting for certainty.

**STAR 1 — Shipping under ambiguity**
- S/T: PM was on leave; a critical bug hit prod, no documented owner for the impacted flow.
- A: Assessed the blast radius (affected ~5% of users), made the call to roll back the last deployment.
  Documented the reasoning and sent an async update to PM and tech lead.
  Followed up the next day with a proper fix and a post-mortem.
- R: Issue contained within 15 minutes. PM confirmed the rollback was the right call.

**STAR 2 — Unblocking a blocked decision**
- S/T: Team spent two weeks debating two database schema options; both had trade-offs, no clear winner.
- A: Proposed time-boxing the debate to one more day, then defaulting to Option A with a documented rollback plan if it proved wrong.
  Built a prototype of Option A over the weekend to generate real data.
- R: Prototype data showed Option A was ~2x faster at our query patterns. Decision made in one meeting. Shipped 3 weeks ahead of revised estimate.

**Watch out**: Confusing recklessness with bias for action. Show that you assessed risk before acting, not that you ignored it.

---

## 6. Deliver Results

**Signal**: Do you hit commitments under pressure, and do you take personal accountability when things slip?

**STAR 1 — Delivering despite scope creep**
- S/T: Feature was scoped for 3 weeks; midway, stakeholder added a new integration requirement.
- A: Re-estimated with the new scope, cut two lower-priority edge cases with PM agreement, negotiated a 1-week extension.
  Built the core integration, flagged the deferred edge cases in a follow-up ticket.
- R: Shipped on the revised date. Deferred items were completed the following sprint with no customer impact.

**STAR 2 — Recovering a slipping project**
- S/T: Joined a project 2 weeks before deadline; previous engineer left mid-implementation, 40% of work remained.
- A: Spent day 1 mapping what was done vs. what was needed, triaged with PM to identify the minimum shippable scope.
  Worked focused sprints, cut non-critical reporting features for v1.
- R: Shipped core feature on deadline. Reported clearly on what was deferred and why.

**Watch out**: Saying you "worked hard" or "put in extra hours" without showing what specifically you did to get back on track. Results need to be concrete.

---

## 7. Earn Trust

**Signal**: Do you communicate transparently, especially when the news is bad? Amazon wants engineers who surface problems early, not cover them up.

**STAR 1 — Raising a risk early**
- S/T: Realized 3 days before a launch that a third-party API we depended on had undocumented rate limits that would break us at scale.
- A: Immediately flagged to PM and tech lead with a written risk summary and two mitigation options.
  Implemented a client-side rate limiter and a graceful degradation path in 2 days.
- R: Launch went live, the rate limit was hit once in week 1 and the degradation path handled it cleanly.

**STAR 2 — Acknowledging a mistake**
- S/T: Deployed a change that caused a 20-minute partial outage due to a config error I missed in review.
- A: Took ownership immediately in the incident channel, ran the remediation, wrote the post-mortem myself.
  In the post-mortem, called out the specific review step I skipped and proposed a config validation check in CI.
- R: Validation check was merged, same class of error has not recurred. Team trust held because I was transparent.

**Watch out**: Being vague about your role in a failure ("the team made a mistake"). Own your part specifically.

---

## 8. Have Backbone; Disagree and Commit

**Signal**: Can you push back with data when you think a decision is wrong, and then fully commit once a decision is made — even if you disagreed?

**STAR 1 — Pushing back on a technical decision**
- S/T: Team decided to use a NoSQL database for a feature that had strong relational requirements.
- A: Wrote a one-page trade-off doc comparing the two options at our expected query patterns with benchmarks.
  Presented in the design review, asked that we reconsider.
  Decision stayed with NoSQL after discussion — I committed and helped design a query pattern that worked within the constraint.
- R: Feature shipped. My trade-off doc became part of the team's design review template.

**STAR 2 — Disagreeing with a PM's prioritization**
- S/T: PM deprioritized a known reliability issue to ship a new feature; I believed the issue would cause a prod incident.
- A: Raised the concern in writing with an estimated blast radius and a time estimate to fix (2 days).
  PM reviewed, agreed to give me 2 days before the feature sprint started.
- R: Fixed the issue; it did cause a brief outage in a canary environment during the fix — would have been worse in prod.

**Watch out**: Describing a disagreement without showing you actually tried to change the decision with data — not just "I voiced my concern." Also avoid stories where you disagreed and the other person was simply wrong; show nuance.

---

## Amazon-Specific Interview Patterns

### Common "Tell me about a time..." openers
- "...you had to make a decision without enough information."
- "...you disagreed with your manager or a stakeholder."
- "...you had to influence without authority."
- "...you failed at something and what did you learn."
- "...you improved a process or system without being asked."
- "...you delivered results under a tight deadline."
- "...you had to work backwards from the customer."

### Handling "most significant failure"
- Pick a real failure, not a disguised success.
- Own it: say what you specifically did wrong, not what "the team" did.
- Show learning: the fix you shipped or the process you changed afterward.
- Keep the result honest — "we recovered" is fine, "there was no real impact" undercuts the story.

### Handling "tell me about a time you disagreed with your manager"
- Must show you disagreed with data, not just opinion.
- Must show you committed fully after the decision was made.
- Avoid stories where you were obviously right and they were obviously wrong — that signals low self-awareness.
- The best stories show a genuine trade-off and that you respected the final decision.

### Ownership framing Amazon expects
- Amazon runs on "two-pizza teams" — small, autonomous, end-to-end accountable.
- Frame ownership as: you owned a system or outcome, not just a ticket.
- If you collaborated, say what your specific contribution was and what you drove.
- "I flagged it to someone else" is a red flag in ownership stories — show that you acted.

---

## Quick Revision

- **Customer Obsession**: You work backwards from real customer signals, not assumptions.
- **Ownership**: You treat problems as yours end-to-end, not just within your ticket boundary.
- **Invent and Simplify**: You eliminate complexity first; you add it only when unavoidable.
- **Dive Deep**: You find root cause through structured investigation, not guesswork.
- **Bias for Action**: You act with incomplete information and course-correct — speed over certainty.
- **Deliver Results**: You hit commitments, and when you can't, you communicate early and adapt.
- **Earn Trust**: You surface bad news early and own your mistakes specifically.
- **Have Backbone; Disagree and Commit**: You challenge with data, then commit fully once decided.
