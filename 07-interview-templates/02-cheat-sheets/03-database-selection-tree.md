> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A simple cheat sheet to help you choose the right database in an interview, without sounding like you're just guessing. 
>
> **Key concepts:**
> - **Relational (SQL):** The accountant. Perfect for money, perfect for complex relationships, but bad at infinite scaling.
> - **Document (MongoDB):** The flexible folder. Perfect when you don't know exactly what your data will look like (e.g., product catalogs).
> - **Wide-Column (Cassandra):** The firehose. Perfect for writing millions of things per second (e.g., IoT sensors, chat messages).
> - **Key-Value (Redis / DynamoDB):** The dictionary. Perfect when you just need to look up one specific thing instantly. 
> - **Search (Elasticsearch):** The Google for your app. Perfect for typing "Red Shoes" and getting fuzzy matches.
>
> **Key takeaway:** Saying "I chose MongoDB because it's fast" is a red flag. Saying "I chose MongoDB because our data doesn't have a strict schema, and we don't need complex JOINs" is a green flag.

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates, cheat-sheets]
---
# Database Selection Decision Tree

> **Choose the database that matches your data shape, not the one you used at your last job.**

---

## 🌳 The Decision Tree (Keep it Simple)

Start here when you get a System Design prompt.

1. **Does this deal with Money, Billing, or absolute mathematical perfection?**
   - **Yes** 👉 Use **SQL** (PostgreSQL).
2. **Does the shape of the data change constantly? (e.g., A TV has a 'screen size' field, but a Shirt has a 'fabric' field)?**
   - **Yes** 👉 Use **Document** (MongoDB).
3. **Are you writing a massive, endless stream of data that you rarely update? (e.g., Temperature sensors logging data every 1 second, or Chat messages).**
   - **Yes** 👉 Use **Wide-Column** (Cassandra).
4. **Do you just need to look up one exact thing instantly? (e.g., "Give me User #123's Profile").**
   - **Yes** 👉 Use **Key-Value** (DynamoDB or Redis).
5. **Do you need to search for text, with spelling mistakes and filters? (e.g., Amazon Search bar).**
   - **Yes** 👉 Use **Search** (Elasticsearch).
6. **Is this a Social Network where you need to find "Friends of Friends of Friends"?**
   - **Yes** 👉 Use **Graph** (Neo4j).

---

## 🏛️ SQL (PostgreSQL / MySQL)

> **Analogy:** The Strict Accountant. 

Everything must be in a perfect row and column. If you try to hand the accountant a form with a missing field, they reject it entirely. 

- **Pros:** ACID compliance (mathematical perfection). You can do complex math (JOINs) to combine different tables.
- **Cons:** It is very hard to scale horizontally. You can't just buy 100 cheap SQL servers and split the data easily. 
- **Use for:** E-commerce checkouts, Bank balances, Uber payments.

---

## 📄 Document (MongoDB)

> **Analogy:** The Flexible Filing Cabinet.

You can throw a piece of paper in here with 3 fields, and another piece of paper with 50 fields. It doesn't care.

- **Pros:** You don't have to define your schema upfront. Great for startups where the product changes every week.
- **Cons:** No complex JOINs. If you want to link a User to an Order, you have to do the math yourself in your Python/Java code.
- **Use for:** User Profiles, Product Catalogs (where a Laptop has different specs than a T-Shirt).

---

## 🌊 Wide-Column (Cassandra)

> **Analogy:** The Firehose.

It is designed to absorb massive amounts of water (writes) without ever slowing down. 

- **Pros:** Insane write speed. You can write 1 million things a second. It scales horizontally perfectly. 
- **Cons:** You can only read data exactly how you wrote it. If you save data by `user_id`, you *cannot* search it by `date` without doing massive extra work. 
- **Use for:** Uber driver GPS locations, Discord chat history, Netflix watch history.

---

## 📖 Key-Value (DynamoDB / Redis)

> **Analogy:** A Dictionary.

You have a word (The Key) and a definition (The Value). You can look up the word instantly, but you cannot search the dictionary by looking for "Words that have 4 vowels."

- **Pros:** Sub-millisecond speed. Scales infinitely. 
- **Cons:** You can only look things up by their exact ID. 
- **Use for:** Shopping Carts (Look up Cart by User_ID), Session tokens.

---

## 🔎 Search (Elasticsearch)

> **Analogy:** The Librarian who read every book.

If you ask the librarian for a book with "a wizard named Harry," they instantly hand you Harry Potter, even if you spelled it wrong.

- **Pros:** Blazing fast text search, fuzzy matching, and filtering (e.g., "Show me red shoes under $50").
- **Cons:** It is NOT a database. You should never use Elasticsearch as your *only* database, because it can lose data. 
- **Use for:** The search bar on Airbnb, Amazon, or Twitter. Always pair it with a primary database (like PostgreSQL).

---

## 🕸️ Graph (Neo4j)

> **Analogy:** A Detective's Murder Board with red string connecting pictures.

It focuses entirely on the *relationships* between things, rather than the things themselves.

- **Pros:** You can ask crazy questions like: *"Find me all people who live in New York, who bought a Bike, whose friends ALSO bought a Bike."* In SQL, this would take 10 minutes to calculate. In Neo4j, it takes 10 milliseconds.
- **Cons:** Terrible for everything else. 
- **Use for:** LinkedIn connection recommendations, Credit Card Fraud rings.

---

## 🎤 Phrase to use in an interview:

> "I will use PostgreSQL for the Payments system, because we absolutely need ACID guarantees and cannot afford to lose a single dollar. However, for the Product Catalog, I will use MongoDB. A T-shirt has a 'size' attribute, while a Laptop has a 'RAM' attribute. MongoDB's flexible Document schema allows us to store both easily without creating 50 empty columns in a SQL database."
