> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Real examples of what a "Good" interview sounds like. It shows the back-and-forth conversation between an Interviewer and a Candidate.
>
> **Key concepts:**
> - **The Setup:** See how a strong candidate asks questions *before* drawing anything.
> - **Handling Pushback:** Notice how the candidate reacts when the interviewer says, "I don't like that idea." (Spoiler: They don't get defensive; they explain the trade-offs).
> - **The Secret Weapon:** Read the `[ANNOTATION]` blocks. They explain *why* the candidate said what they said.
>
> **Key takeaway:** System Design is a conversation, not a test. Read these transcripts to understand the rhythm and flow of a passing interview.

---
module: 07-interview-templates
topic: Worked Examples
status: unread
tags: [07-interview-templates, interview, worked-examples, walkthrough]
---
# Worked Examples

> **Read these to understand what a passing interview actually sounds like.**

---

## 🗣️ Example 1: Design a Notification System

**Interviewer:** "Design a system that sends push notifications and emails to users."

**Candidate:** "Sure! Before we start drawing, I want to make sure I understand the scale. Are we sending 1,000 notifications a day, or 10 Million? And do users have the ability to turn off emails if they want to?"
> `[ANNOTATION: The candidate does not just start guessing. They ask clarifying questions immediately. This shows they care about the Customer's requirements.]`

**Interviewer:** "Let's say 10 Million a day. Yes, users can opt-out of emails."

**Candidate:** "Great. 10 Million a day means about 115 notifications a second. That's actually not very high for a modern system. *However*, if a celebrity posts a viral video, we might get 10,000 notifications in a single second. I will design the system to handle that sudden 'spike'."
> `[ANNOTATION: The candidate does quick math out loud. They identify the real problem (the sudden spike) instead of just the average traffic.]`

**Candidate:** "Here is the flow: When an event happens, we drop a message into a Kafka Queue. A background worker picks it up, checks a Redis Database to see if the user opted-out, and if not, it sends the email."

**Interviewer:** "What happens if the background worker crashes right after it sends the email, but before it tells Kafka it finished?"

**Candidate:** "That's a great question. If it crashes, Kafka will think the job failed, and it will give the same message to a different worker. That user will get two emails. To fix this, I would use an 'Idempotency Key'. Before sending the email, the worker writes the Event ID in Redis. The next worker will check Redis, see the ID is already there, and skip it."
> `[ANNOTATION: The candidate doesn't panic. They acknowledge the failure mode (sending duplicate emails) and offer a specific fix (Idempotency Key).]`

---

## 🗣️ Example 2: Design a Rate Limiter

**Interviewer:** "Design a system that stops a user from clicking a button more than 100 times a minute."

**Candidate:** "Before I design this, I assume this needs to work across many servers, right? If the user clicks the button on Server A, Server B needs to know about it."
> `[ANNOTATION: The candidate establishes the hardest constraint upfront: Distributed State.]`

**Interviewer:** "Yes, exactly. We have 50 servers."

**Candidate:** "Okay, since we have 50 servers, they cannot store the count in their own local memory. They all need to talk to a central database. I will use a Redis Database because it is incredibly fast (in-memory) and can easily handle 50 servers asking it for counts at the same time."

**Interviewer:** "What happens if that central Redis database crashes?"

**Candidate:** "If Redis crashes, the 50 servers can't check the count. We have two choices: 'Fail Closed' (nobody can click the button) or 'Fail Open' (everyone can click the button as much as they want). I would choose 'Fail Open'. It's better to let users spam the button for a few minutes than to completely break the website for everyone."
> `[ANNOTATION: The candidate doesn't just say 'Redis will never crash'. They embrace the failure, give two options, and choose the one that provides the best User Experience.]`

---

## 🔍 Patterns of a Passing Candidate

If you read the examples above, you will notice 4 patterns that you should copy:

1. **Ask before you draw:** Never assume the scale. Always ask "How many users?" before you start designing.
2. **Think out loud:** The interviewer cannot read your mind. If you are doing math in your head, say it out loud: *"10 million a day divided by 24 hours..."*
3. **Embrace failure:** When the interviewer asks, *"What if X crashes?"*, do not get defensive. Smile and explain how the system recovers.
4. **Prioritize the User:** When forced to choose between two bad options (like in the Redis crash example), always pick the option that annoys the user the least.
