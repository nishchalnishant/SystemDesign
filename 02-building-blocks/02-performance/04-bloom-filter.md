> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A magical data structure that can search through a billion items using almost zero RAM.
>
> **Key topics:**
> - **The Problem:** Storing 1 billion usernames in RAM (so you can check if a username is taken) takes hundreds of gigabytes of memory. 
> - **The Bloom Filter:** A clever math trick that compresses those 1 billion usernames into just a few Megabytes!
> - **The Catch:** It is slightly inaccurate. It will never give you a False Negative ("Definitely Not"), but it might give you a False Positive ("Probably Yes").
> - **Use Cases:** Checking if a URL is malicious, preventing users from seeing the same recommendation twice, or skipping expensive database queries.
>
> **Key takeaway:** If you are asked to quickly check if an item exists in a massive dataset (millions/billions of items) during an interview, the answer is almost always a Bloom Filter.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, performance]
---
# Bloom Filters - System Design Guide

> This guide explains the magic of Bloom Filters using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you are building Twitter. When a user tries to sign up with the username `@systemdesignfan`, you need to check if that username is already taken.

You have 1 Billion registered usernames. 
If you query the Database, it takes 5 seconds (too slow).
If you put all 1 Billion usernames in a Redis Cache (in RAM), it takes up **30 Gigabytes** of extremely expensive memory. 

Is there a way to check if a username exists instantly, without using 30 GB of RAM?
Yes. It's called a **Bloom Filter**. 

By using a clever math trick, a Bloom Filter can store the "existence" of 1 Billion usernames in just **1.5 Gigabytes** of RAM. That is a 95% reduction in size!

---

## 🎩 How the Magic Works (The 2 Rules)

A Bloom Filter is a probabilisitic data structure. That means it trades perfect accuracy for extreme space savings. 

When you ask a Bloom Filter: *"Does the username 'Alex' exist?"*, it can only give you two possible answers:

### 1. "Definitely Not." (100% Accurate)
If the Bloom Filter says "No, the username Alex does not exist," it is 100% telling the truth. There is a 0% chance it is lying. You can immediately let the user sign up!

### 2. "Probably Yes." (Slightly Inaccurate)
If the Bloom Filter says "Yes, the username Alex exists," it *might* be lying to you. 
There is a tiny chance (usually around 1%) that the username `Alex` doesn't actually exist, and the math just accidentally collided with a different name. (This is called a **False Positive**).

Because of this 1% lie, you have to write code like this:
1. Ask the Bloom Filter: "Is 'Alex' taken?"
2. If it says **Definitely Not**: Let the user sign up! (0 database queries used).
3. If it says **Probably Yes**: Ah, it might be lying. Let me run 1 slow Database query to double-check if it's actually taken.

Even with the 1% lie, you just saved your database from doing 99% of the work!

---

## ⚙️ How does it actually save so much space?

> **💡 Analogy:** Imagine a wall with 10 light switches, all turned OFF. 
> 
> You want to save the name "Alice." You put "Alice" into a math formula (a Hash Function). The math formula spits out the numbers 2 and 5. You flip switches #2 and #5 to ON.
> 
> You want to save the name "Bob." The math formula spits out 5 and 8. You flip switch #8 to ON (switch #5 is already ON).
> 
> Now, someone asks: "Is the name Charlie saved?" 
> You put "Charlie" into the formula. The math spits out 3 and 7. You look at the wall. Switches 3 and 7 are OFF. You know for a 100% fact that Charlie is **Definitely Not** saved!
>
> Next, someone asks: "Is the name Dave saved?"
> You put "Dave" into the formula. The math accidentally spits out 2 and 8. You look at the wall. Switches 2 and 8 are both ON! So you say, **"Probably Yes."** But wait... Dave was never actually saved! It's just that Alice turned on #2, and Bob turned on #8. This is a False Positive!

Because the Bloom Filter only stores *light switches* (Bits: 0 or 1) instead of full text strings ("Alice"), it uses almost zero memory.

---

## 🌍 Real-World Use Cases

1. **Google Chrome (Malicious URLs):** Google maintains a list of millions of dangerous, virus-infected websites. Instead of forcing your browser to download a massive 5GB list, Chrome downloads a tiny 2MB Bloom Filter. When you click a link, Chrome checks the local filter. If it says "Definitely Not", you load the page safely! If it says "Probably Yes," Chrome pauses and asks the Google servers for confirmation.
2. **Medium / News Apps (Recommendations):** When showing you articles, Medium uses a Bloom Filter of every article you've ever read to ensure they don't accidentally recommend an article you already finished.
3. **Database Speed (Cassandra/Postgres):** Before a database wastes time searching a massive hard drive for a row that doesn't exist, it checks a Bloom Filter in RAM first.

---

## 🎤 Interview Questions to Practice

1. **"What is a Bloom Filter and why would you use one?"**
   *Answer:* It's a space-efficient, probabilistic data structure used to test whether an element is a member of a set. You use it when you need to check existence against massive datasets (millions/billions of items) but don't have enough RAM to store the actual items in a hash map.
2. **"Can a Bloom Filter return a False Negative?"**
   *Answer:* No. A Bloom Filter guarantees 100% accuracy for negatives (if it says the item is not there, it is definitely not there). It can only return False Positives (it might say an item exists when it actually doesn't).
3. **"How do you remove an item from a Bloom Filter?"**
   *Answer:* You cannot remove items from a standard Bloom Filter. Because multiple items might share the same "switches" (bits) due to hash collisions, turning a switch off to delete one item might accidentally delete other items too! If you need deletions, you must use a more complex structure like a Counting Bloom Filter.

---

# 🎯 SDE-3 Deep Dive

The above covers *why* and *the light-switch intuition*. Seniors get asked to **size the filter, reason about the two knobs, and pick the right variant.**

## The sizing math you should be able to sketch

Two parameters control everything: **m** = number of bits, **k** = number of hash functions, for **n** inserted items.

- **Optimal hash count:** `k = (m/n) · ln 2 ≈ 0.693 · (m/n)`.
- **False-positive probability:** `p ≈ (1 − e^(−kn/m))^k`.
- **Bits per element (the number to memorize):** `m/n = −ln(p) / (ln 2)²`. That's **~9.6 bits/element for 1% FP**, **~14.4 for 0.1%**, **~19.2 for 0.01%**. Each extra order of magnitude of accuracy costs ~4.8 bits/element.

So for 1 billion items at 1% FP: `1e9 × 9.6 bits ≈ 1.2 GB` — matching the page's headline number, and now you can *derive* it. The point seniors make: **accuracy is logarithmic in space** — going from 1% to 0.0001% only ~4×s the memory, not 10000×.

## The two failure modes of getting it wrong

- **Undersized (too small m for actual n):** the bit array saturates, FP rate climbs toward 100%, and the filter becomes useless (says "probably yes" to everything → no queries saved). You must size for *peak* n or use a scalable variant.
- **Wrong k:** too few hashes → collisions; too many → array fills faster. Use the `k = 0.693·m/n` formula, don't guess.

## Variants — know when to reach for each

| Variant | Adds | Use when |
|---|---|---|
| **Counting Bloom Filter** | Small counters instead of bits | You need **deletions** (cost: ~4× memory) |
| **Scalable Bloom Filter** | Chain of filters, grows on demand | **n is unknown/unbounded** up front |
| **Cuckoo Filter** | Stores fingerprints in a cuckoo hash | Deletions **and** better space at low FP; also supports lookup of *count* |
| **Quotient Filter** | Cache-friendly, mergeable | Disk-resident / SSD; supports merges and resizes |

**Cuckoo filter is the modern default** when you need deletions — it beats counting Bloom filters on space below ~3% FP and supports removal cleanly.

## Where the FP cost actually lands

Frame the trade in system terms: a false positive doesn't corrupt data — it just triggers the **fallback path** (the slow DB check). So you tune FP against the *cost of that fallback*. Cassandra/RocksDB use per-SSTable Bloom filters to skip disk reads; a 1% FP means 1% of "not-present" keys pay one wasted disk seek — a great trade against the 99% of seeks eliminated. Tune tighter (lower FP) only if the fallback is expensive.

## Interview probes you should survive

- *"How big a Bloom filter for 1B items at 0.1% FP?"* → ~14.4 bits/item → ~1.8 GB. Derive from `m/n = −ln p / (ln2)²`.
- *"FP rate is climbing over time — why?"* → You inserted more than the sized n; the array saturated. Use a scalable Bloom filter or resize/rebuild periodically.
- *"You need deletions — now what?"* → Counting Bloom filter (4× space) or, better, a **Cuckoo filter** (deletions + tighter space at low FP).
- *"Why does Cassandra put a Bloom filter in front of each SSTable?"* → To skip disk reads for keys that definitely aren't in that file; a small FP just costs an occasional wasted seek, hugely cheaper than reading every SSTable.

---

## Applied In

This concept is used by **5 problems** in this repo:

**High-Level Design**

- [Design Autocomplete / Typeahead Search](../../05-hld-problems/01-easy/autocomplete.md)
- [Design a Web Crawler](../../05-hld-problems/01-easy/web-crawler.md)
- [Design Typeahead Search (Google Search Bar)](../../05-hld-problems/02-medium/typeahead-search.md)
- [Design an Ad Click Aggregator](../../05-hld-problems/03-hard/ad-click-aggregator.md)
- [Design a Web Search Engine (Google)](../../05-hld-problems/03-hard/search-system.md)

