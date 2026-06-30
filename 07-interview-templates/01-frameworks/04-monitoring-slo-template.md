> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A simple framework for answering interview questions about monitoring, reliability, and what to do when your servers crash.
>
> **Key concepts:**
> - **SLI, SLO, SLA:** SLI is the speedometer. SLO is the speed limit you set for yourself. SLA is the ticket the police gives you if you break it.
> - **Error Budgets:** If your goal is 99.9% uptime, you are allowed to be down for 43 minutes a month. That 43 minutes is your "budget". Spend it wisely.
> - **The Four Golden Signals (RED):** The 4 vital signs of your app (Latency, Traffic, Errors, Saturation).
> - **Alerting:** Don't wake up an engineer at 3 AM because "CPU is high". Only wake them up if "Users can't buy things".
>
> **Key takeaway:** Junior developers build things. Senior developers build things *and know how to fix them when they break at 3 AM*. Mentioning SLOs and Error Budgets proves you have real-world experience.

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates]
---
# Monitoring & SLO Template

> **Use this template when the interviewer asks: "How do you monitor this system?" or "What happens when this service degrades?"**

---

## 🚦 1. SLI, SLO, SLA (The Vocabulary)

You absolutely must know the difference between these three terms.

- **SLI (Service Level Indicator - The Measurement):** This is the raw math. Example: *"99.2% of our web pages loaded in under 200 milliseconds today."*
- **SLO (Service Level Objective - Your Goal):** This is the internal target your engineering team agrees on. Example: *"We want 99.9% of our pages to load in under 200 milliseconds."*
- **SLA (Service Level Agreement - The Contract):** This is a legal promise to the customer. Example: *"If we drop below 99.5% uptime, we will refund you $10,000."*

**Always remember:** Your SLO (99.9%) must be stricter than your SLA (99.5%). This gives you a buffer before you have to pay the customer money!

---

## 💰 2. Error Budgets (Your Allowance for Mistakes)

If your SLO is 99.9% uptime, that means you are *allowing* 0.1% downtime. 
0.1% of a month is **43.8 minutes**. 

That 43.8 minutes is your **Error Budget**. 

- **If you have 40 minutes left this month:** Great! Deploy new features, run experiments, take risks. 
- **If you have 0 minutes left this month:** STOP! Freeze all feature releases. The whole team must spend the rest of the month fixing bugs and making the system stable. 

---

## 🩺 3. The Four Golden Signals (Vital Signs)

When monitoring a server, you can't just stare at a million numbers. Google invented the "Four Golden Signals" to track the health of an app.

1. **Latency (Speed):** How long does a request take? (e.g., "It takes 200ms to load the timeline").
2. **Traffic (Volume):** How many people are using it right now? (e.g., "We are getting 5,000 requests per second").
3. **Errors (Failure):** How many requests are crashing? (e.g., "1% of payments are failing").
4. **Saturation (Fullness):** How "full" is the server? (e.g., "The CPU is at 90%, and the database queue is backing up").

---

## 🚨 4. Alerting (When to wake someone up)

**Rule #1: Alert on Symptoms, Not Causes.**

- ❌ **Bad Alert:** "The CPU is at 80%." (Who cares? If the users aren't noticing anything wrong, do not wake me up at 3 AM for this).
- ✅ **Good Alert:** "The error rate for the Checkout Button is above 1%." (This means the company is losing money right now. Wake me up immediately).

### Burn Rates
A "Burn Rate" is how fast you are eating through your 43-minute Error Budget.
- **Burn Rate 1:** Normal. You will use up exactly 43 minutes by the end of the month.
- **Burn Rate 14:** Disaster. You are failing 14x faster than normal. The whole budget will be gone in 2 hours. Page someone instantly!

---

## 🔍 5. Distributed Tracing (Finding the Needle in the Haystack)

**The Problem:** A user clicks "Buy". That click goes to the Web Server, which talks to the Auth Server, which talks to the Inventory Server, which talks to the Payment Server. 
The user complains: *"It took 10 seconds to load!"* 
How do you know *which* of the 4 servers was slow?

**The Solution:** Distributed Tracing (OpenTelemetry).
1. When the user clicks "Buy", the Web Server creates a unique ticket number: `Trace_ID_123`.
2. The Web Server passes `Trace_ID_123` to the Auth Server. 
3. The Auth Server passes `Trace_ID_123` to the Inventory Server, and so on.
4. Every server logs how long they took, along with `Trace_ID_123`.

Now, you can search for `Trace_ID_123` in a dashboard (like Grafana or Datadog) and see a beautiful waterfall graph showing exactly how many milliseconds each server took. You instantly see that the Inventory Server took 9.5 seconds!

---

## 🎤 Phrase to use in an interview:

> "I would monitor this system using the Four Golden Signals: Latency, Traffic, Errors, and Saturation. We would set an internal SLO of 99.9% uptime, giving us an Error Budget of 43 minutes a month. Crucially, I would only set up PagerDuty alerts for *Symptoms* (like high user error rates) rather than *Causes* (like high CPU), so we don't cause alert fatigue for the engineering team."
