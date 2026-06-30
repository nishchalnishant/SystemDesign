> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A playbook for handling the inevitable interviewer question: *"What happens when this breaks?"*
>
> **Key concepts:**
> - **The Circuit Breaker:** If a server is crashing, stop sending it traffic so it has time to recover.
> - **Thundering Herd:** When a cache expires, 10,000 users will try to hit the database at the exact same millisecond. (You must prevent this).
> - **Graceful Degradation:** If the "Recommendation Engine" breaks on Amazon, don't crash the whole website. Just hide the recommendations and let people buy things.
>
> **Key takeaway:** Never say "it just crashes." Senior engineers walk the interviewer through exactly how the system detects the failure, protects itself, and recovers without losing data.

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates, cheat-sheets]
---
# Failure Recovery Playbook

> **"Everything fails, all the time." — Werner Vogels (CTO of Amazon)**

---

## 🛑 1. The Circuit Breaker (Protecting broken servers)

**The Problem:** Your "Email Service" is broken. It is taking 60 seconds to respond. But your main app keeps trying to send it emails, meaning your main app's threads are all getting stuck waiting for 60 seconds. Soon, your whole app crashes.

**The Solution:** A Circuit Breaker.
- **Closed (Normal):** Traffic flows normally.
- **Open (Broken):** After 5 failed attempts, the Circuit Breaker trips. It instantly rejects all new email requests (*"Sorry, email is down!"*) so your main app doesn't freeze waiting. 
- **Half-Open (Testing):** After 30 seconds, it lets *one* request through. If it succeeds, the breaker closes. If it fails, it opens again.

---

## 📉 2. Graceful Degradation (Don't throw the baby out with the bathwater)

**The Problem:** The "Product Reviews" database on an e-commerce site crashes. 

**The Bad Answer:** "The website goes down until we fix the database."
**The Senior Answer:** "We gracefully degrade. We hide the Reviews section entirely, but we keep the 'Buy Now' button working. The company still makes money, and most users won't even notice the reviews are missing."

---

## 🐘 3. The Thundering Herd (Dog-Piling)

**The Problem:** You have a massive viral Tweet. Millions of people are viewing it. You cache it in Redis for 5 minutes. After exactly 5 minutes, the cache expires. In that exact millisecond, 10,000 users ask for the Tweet. Since it's not in the cache, ALL 10,000 users query the SQL database at the exact same time. The database instantly bursts into flames.

**The Solution:** Mutex Locks (Mutex = Mutually Exclusive).
When the cache expires, the very first user who asks for the Tweet gets a "Lock". They are the *only* one allowed to go ask the Database. The other 9,999 users are told to wait 50 milliseconds. Once the first user gets the Tweet from the database, they put it back in the cache, and the other 9,999 users read it safely from the cache.

---

## 💾 4. Database Failures (The Nightmare Scenario)

**The Problem:** The Primary SQL Database unplugs and dies. 

**The Solution:**
1. **Detection:** The system realizes the Primary DB hasn't responded to a health check in 5 seconds.
2. **Mitigation:** The system instantly stops accepting new Writes (users cannot post new data). 
3. **Failover:** A Backup Database (Read Replica) is automatically "promoted" to become the new Primary. 
4. **Recovery:** Within 60 seconds, the new Primary starts accepting Writes again. No data is lost.

---

## 💳 5. Payment Failures (The Worst Case Scenario)

**The Problem:** A user clicks "Pay $50". Your server asks Stripe to charge the card. Stripe charges the card successfully. But right before Stripe can reply "Success!", your server's wifi drops. Your server thinks the payment failed. The user clicks "Pay $50" again. You just charged them $100.

**The Solution:** Idempotency Keys.
When the user clicks "Pay", you generate a random code: `UUID-1234`. You send Stripe: *"Charge $50 with code UUID-1234"*. 
If your wifi drops, and the user clicks Pay again, you send Stripe the exact same code: *"Charge $50 with code UUID-1234"*. 
Stripe looks at the code and says: *"Wait, I already charged UUID-1234! I'm not doing it again. Here is your Success receipt."* 

No double charges, ever.

---

## 🎤 Phrase to use in an interview:

> "To handle database failures, I'd implement an Active-Passive failover strategy. If the Primary database goes down, we will temporarily buffer all write requests in a Kafka queue while we promote a Read Replica to be the new Primary. Once the new Primary is online (usually within 30-60 seconds), we will drain the Kafka queue into the database so no user data is lost during the outage."
