# SQL for smarties

## Chapter 1&#x20;



focuses on database design, with an emphasis on the importance of creating well-structured schemas to avoid convoluted queries and ensure reliable data extraction. Here are the key points covered in the chapter:

#### 1. Importance of Schema Design

* **Schema Creation**: This chapter introduces the Data Definition Language (DDL), which is used to create database schemas. The main premise is that poor schema design leads to complex queries and inconsistent results, making schema design a critical part of database management.
* **Shared Data**: A well-designed database schema allows data to be shared among different applications, ensuring one trusted source of information for the enterprise. By separating data from programs, the system can maintain, backup, and validate data efficiently.

#### 2. Case Tools and Their Limitations

* **Data Modeling Tools**: SQL has inspired the development of data modeling tools that help in designing schemas. These tools often use graphical or textual descriptions of rules and constraints to create schema declaration statements that can be used directly in SQL products. However, Celko emphasizes that these tools alone cannot prevent bad designs.

#### 3. Schema and Table Creation

* **Thinking Beyond Files**: One of the major challenges for SQL programmers is moving from a file-based mindset to thinking in terms of sets and schemas. In file systems, records are navigated one by one, whereas SQL operates on sets of data.
* **SQL’s Set Model**: SQL databases operate on the set theory concept, which contrasts with the more physical, sequential access model used in traditional file-based systems. The unit of work in SQL is a schema rather than individual tables.

#### 4. CREATE SCHEMA Statement

* **Schema Elements**: A schema is the skeleton of an SQL database and defines its structure and the rules governing its operation. The `CREATE SCHEMA` statement brings an entire schema into existence at once. It includes definitions for tables, views, assertions, and other schema objects.

#### 5. Table Constraints

* **Column Definitions and Constraints**: Celko discusses how SQL columns have more than just data types; they also have constraints such as `NOT NULL`, `UNIQUE`, and `CHECK` to enforce rules about the data they store. This makes tables in SQL behave more like objects with associated rules rather than passive files.

#### 6. Avoiding Common Schema Design Pitfalls

* **Bad Schema Design**: Celko highlights some common design flaws such as wrong data types, denormalization, and missing or incorrect constraints. These errors often lead to convoluted queries that try to work around the structural flaws in the schema.

#### 7. Attribute Splitting

* **Example of Attribute Splitting**: A key pitfall in schema design is "attribute splitting," where a single attribute is incorrectly divided across multiple tables or rows. Celko provides an example where subscription data is split between individual and organizational subscribers into two separate tables, which is incorrect. The correct design would involve a unified table with a `subscription_type` column.

#### 8. Data Integrity and Keys

* **Primary Keys and Unique Constraints**: Properly defining primary keys and using `UNIQUE` constraints help maintain the integrity of data. Celko emphasizes the importance of correct key constraints in ensuring data consistency, such as in scheduling or managing class hierarchies in object-oriented designs.

Chapter 1 sets the foundation for understanding how to design a robust and efficient database, emphasizing the importance of thinking in terms of sets rather than files, enforcing constraints for data integrity, and avoiding common schema design errors.



## Chapter 2

dedicated to normalization, providing an in-depth discussion of various normal forms and how they apply to database design. Here are the detailed notes:

#### 1. Introduction to Normalization

<figure><img src="../../../.gitbook/assets/image (74).png" alt=""><figcaption></figcaption></figure>

* Normalization originates from the relational model of data, first defined by Dr. E. F. Codd in 1970. The aim is to avoid anomalies in data (like deletion, insertion, or update anomalies) by organizing data into relational tables with specific constraints.
* **Functional and Multivalued Dependencies**: These dependencies determine the relationship between attributes in a table. Functional dependencies (FDs) are represented as A → B, where knowing A means you can determine B. Multivalued dependencies (MVDs) represent that knowing one attribute can lead to a set of attributes.

#### 2. First Normal Form (1NF)

* **Definition**: A table is in 1NF when it has no repeating groups, meaning all columns contain atomic values. There should be no arrays or records within columns.
* **Example**: A class schedule table with repeating groups of students and their majors violates 1NF. Flattening the table structure can solve this.



<figure><img src="../../../.gitbook/assets/image (75).png" alt="" width="563"><figcaption></figcaption></figure>

**Problem:** The `ItemsOrdered` and `Quantities` columns are not atomic. They contain multiple values, violating the 1NF rule.

**Normalized Table (in 1NF):**

To normalize this table into 1NF, we'll break it into two tables:



<figure><img src="../../../.gitbook/assets/image (77).png" alt="" width="563"><figcaption></figcaption></figure>

**Explanation:**

* **Atomic Values:** In the normalized tables, each cell contains only one atomic value.  &#x20;
* **No Repeating Groups:** There are no repeating groups of data, such as the multiple items and quantities in the original table.  &#x20;
* **Primary Key:** The `OrderID` column is the primary key in both tables, uniquely identifying each row.

By normalizing the data into 1NF, we've ensured that each attribute contains atomic values, improving data integrity and reducing redundancy. This normalized structure is more efficient for data storage, retrieval, and modification.  &#x20;

Sources and related content

#### 3. Second Normal Form (2NF)

* **Definition**: A table is in 2NF if it is in 1NF and has no partial key dependencies. This means that every non-key attribute must be fully dependent on the primary key, not just part of it.
* **Example**: If a table has a composite primary key, like (student\_name, course\_name), each non-key attribute (e.g., grade) should depend on both attributes in the key.



<figure><img src="../../../.gitbook/assets/image (78).png" alt="" width="563"><figcaption></figcaption></figure>



*   **Problem:**

    In this table, the `Publisher` and `ISBN` attributes depend on the `Author`. This violates the 2NF rule, which states that a non-prime attribute should be fully dependent on the primary key.

    **Normalization into 2NF:**

    To normalize this into 2NF, we can split the `Books` table into two:



<figure><img src="../../../.gitbook/assets/image (79).png" alt="" width="563"><figcaption></figcaption></figure>

**Explanation:**

* **Primary Key:** `BookID` is the primary key in the `Books` table.
* **Partial Dependency:** In the original table, `Publisher` and `ISBN` were partially dependent on the primary key `BookID` and fully dependent on the non-prime attribute `Author`.
* **Normalization:** By separating the `AuthorDetails` table, we've ensured that all non-prime attributes are fully dependent on the primary key `Author`.

This normalization process eliminates redundancy and improves data integrity.

#### 4. Third Normal Form (3NF)

* **Definition**: A table is in 3NF if it is in 2NF and has no transitive dependencies. A transitive dependency occurs when a non-key attribute depends on another non-key attribute.
* **Example**: If room\_size depends on room\_nbr, and room\_nbr depends on course\_name and section\_id, then room\_size has a transitive dependency and should be moved to its own table.



<figure><img src="../../../.gitbook/assets/image (80).png" alt="" width="563"><figcaption></figcaption></figure>

**Problem:**

In this table, the `DepartmentHead` and `DepartmentPhone` attributes are dependent on the `Department` attribute, not the primary key `CourseID`. This violates the 3NF rule, which states that a non-prime attribute should not transitively depend on a primary key.

**Normalization into 3NF:**

To normalize this into 3NF, we can split the `Courses` table into two:



<figure><img src="../../../.gitbook/assets/image (81).png" alt="" width="563"><figcaption></figcaption></figure>

**Explanation:**

* **Primary Key:** `CourseID` is the primary key in the `Courses` table, and `Department` is the primary key in the `Departments` table.
* **Transitive Dependency:** In the original table, `DepartmentHead` and `DepartmentPhone` were transitively dependent on `CourseID` through `Department`.
* **Normalization:** By separating the `Departments` table, we've removed the transitive dependency, ensuring that all non-prime attributes are directly dependent on the primary key.

This normalization process eliminates redundancy and improves data integrity.

#### 5. Boyce-Codd Normal Form (BCNF)

* **Definition**: A stronger version of 3NF. A table is in BCNF if every determinant (a column or set of columns that functionally determines another column) is a candidate key.
* **Purpose**: BCNF addresses issues where 3NF doesn’t solve certain anomalies involving overlapping keys.



<figure><img src="../../../.gitbook/assets/image (82).png" alt="" width="563"><figcaption></figcaption></figure>

**Functional Dependencies:**

* `StudentID -> CourseID, Professor, Department`
* `Professor -> Department`

**Problem:**

In this relation, the functional dependency `Professor -> Department` violates the BCNF rule. This is because `Professor` is not a candidate key, but it determines `Department`.

**Normalization into BCNF:**

To normalize this into BCNF, we can decompose the `Enrollment` relation into two relations:

<figure><img src="../../../.gitbook/assets/image (83).png" alt="" width="563"><figcaption></figcaption></figure>

**Explanation:**

* **Decomposition:** By decomposing the original relation, we've ensured that for every non-trivial functional dependency X -> Y, X is a superkey.
* **BCNF Satisfaction:** In both new relations, every determinant (left-hand side of a functional dependency) is a superkey.

This normalization process eliminates the transitive dependency and ensures that the database is in BCNF, improving data integrity and reducing redundancy.

#### 6. Fourth Normal Form (4NF)

* **Definition**: A table is in 4NF if it is in BCNF and has no multivalued dependencies. If an attribute in a table can have multiple independent values that aren't related, the table violates 4NF.
* **Example**: A table with departments, jobs, and parts could lead to anomalies if multiple parts and jobs are listed for the same department. The solution is to split the table into two: one for (department, job) and another for (department, part).

<figure><img src="../../../.gitbook/assets/image (84).png" alt="" width="563"><figcaption></figcaption></figure>

**Functional Dependencies:**

* `BookID -> Author, Genre`
* `Author -> Genre`

**Problem:**

In this relation, there is a multivalued dependency: `Author -> Genre`. This means that an author can have multiple genres, and this relationship is independent of the specific book.

**Normalization into 4NF:**

To normalize this into 4NF, we can decompose the `BooksAuthors` relation into two relations:



<figure><img src="../../../.gitbook/assets/image (85).png" alt="" width="563"><figcaption></figcaption></figure>

**Explanation:**

* **Multivalued Dependency:** The original relation had a multivalued dependency between `Author` and `Genre`.
* **Decomposition:** By decomposing the relation, we've separated the multivalued dependency into a separate relation.
* **4NF Satisfaction:** In both new relations, there are no non-trivial multivalued dependencies.

This normalization process eliminates redundancy and improves data integrity by ensuring that each relation represents a single fact.



#### 7. Fifth Normal Form (5NF)

* **Definition**: 5NF, also known as the join-projection normal form (JPNF), ensures that data cannot be decomposed into smaller tables without losing information or creating anomalies. It is used when dealing with complex, multi-way relationships.

#### 8. Domain-Key Normal Form (DKNF)

* **Definition**: A table is in DKNF when all constraints on the data are logical consequences of the keys and domains alone, without relying on extraneous constraints. It represents the ultimate goal of normalization, ensuring that all data dependencies are captured.

#### 9. Practical Hints for Normalization

* **Entity-Relationship Diagrams**: Useful tools for visualizing normalization. A normalized database often has many small tables, each with a few columns.
* **Avoid NULLs**: Too many NULL-able columns could indicate a normalization problem. Use NULLs only for temporarily missing values, and avoid them where possible.

#### 10. Denormalization

* **When to Denormalize**: Denormalization may improve performance in data warehouses where extensive JOINs are costly. However, it is generally discouraged in OLTP (Online Transaction Processing) systems due to the risk of data anomalies.

#### 11. Key Types

* **Natural Keys**: Keys derived from real-world data that are verifiable and visible to users (e.g., Social Security numbers, product UPCs).
* **Artificial Keys**: Keys created purely for the purpose of unique identification within the database (e.g., serial numbers or UUIDs).

These principles of normalization ensure that databases are free from redundancy, minimize anomalies, and maintain data integrity&#x20;



## Chapter 3 "Numeric Data in SQL."&#x20;



The chapter covers essential concepts related to handling numeric data within SQL, exploring numeric data types, conversion methods, and common arithmetic operations. Below are detailed notes from the chapter:

#### 1. Overview

* SQL is not a computational language, so its arithmetic capabilities are weaker compared to other programming languages. However, understanding how numbers work in SQL and how they interact with host programs is essential for effective SQL use.

#### 2. Numeric Data Types

* **Exact and Approximate Numbers**: SQL classifies numbers as either exact (like `INTEGER` or `DECIMAL`) or approximate (like `FLOAT` or `REAL`). Exact numeric values have defined precision and scale. Approximate numbers have more flexibility but are less precise.
* **Precision and Scale**: Precision refers to the number of significant digits, while scale indicates the number of digits after the decimal point. For example, `DECIMAL(5, 2)` means five total digits, two of which are after the decimal.
* **Common Numeric Data Types**: These include `INTEGER`, `SMALLINT`, `BIGINT`, `DECIMAL`, and `NUMERIC`. These are primarily used for exact calculations.

#### 3. Handling Rounding and Truncation

* **Truncation**: When a value doesn't fit in a column, SQL either rounds or truncates it depending on the system's configuration. Truncation is done toward zero, meaning 1.5 becomes 1, and -1.5 becomes -1.
* **Rounding**: SQL implementations vary in their rounding techniques. Some systems use commercial rounding (common in banking), where numbers are rounded symmetrically, while others use scientific rounding methods based on significant figures. An example formula provided for commercial rounding uses the `MOD()` function.

#### 4. Numeric Type Conversion

* **CAST() Function**: SQL-92 defines the `CAST()` function for data type conversion, allowing users to change one data type to another. For example, `CAST(amount AS DECIMAL(10, 2))`. This function helps prevent errors during calculations that require specific data types.

#### 5. Four-Function Arithmetic

* **Basic Operators**: SQL supports basic arithmetic operations—addition (`+`), subtraction (`-`), multiplication (`*`), and division (`/`). Operator precedence in SQL is similar to other programming languages but needs careful handling in cases like large sums or subtractions to avoid overflow errors.

#### 6. Dealing with NULLs in Arithmetic

* **Arithmetic and NULLs**: SQL’s handling of `NULL` in arithmetic expressions can cause confusion. Any arithmetic operation involving `NULL` results in a `NULL` value. Functions like `COALESCE()` or `NULLIF()` can help manage these issues by replacing `NULL` values with alternatives.

#### 7. Common SQL Functions Related to Numeric Data

* **NULLIF()**: This function returns `NULL` if two expressions are equal, which is useful for avoiding division by zero.
* **COALESCE()**: This function returns the first non-`NULL` value in a list, allowing you to substitute `NULL` values with a default.

#### 8. Vendor-Specific Functions

* **Mathematical Functions**: Vendors often provide additional mathematical functions, like `SIN()`, `COS()`, `SQRT()`, and logarithmic or exponential functions. These are used for more advanced calculations, but they can vary across systems.

#### 9. Programming Exercises

* One exercise suggests creating a table of random numbers (positive and negative) with four decimal places, rounding them to two decimal places, and analyzing the differences in summing the original and rounded values. This demonstrates how SQL handles precision and rounding in aggregate functions.

In this chapter, Celko emphasizes understanding the limitations of SQL’s numeric processing capabilities and adapting code to avoid common pitfalls related to rounding, truncation, and `NULL` values.



## Chapter 4 "Temporal Data Types in SQL"&#x20;



covers how to work with date and time data types in SQL, addressing both their complexities and practical uses. Here are the detailed notes:

#### 1. Temporal Data Overview

* **Complexity of Time**: Time-related data is challenging due to the irregularity of the calendar and time systems. SQL-92 introduced temporal data types to standardize handling of dates and times in SQL.
* **Common Temporal Data Types**:
  * `DATE`: For calendar dates.
  * `TIME`: For times within a day.
  * `TIMESTAMP`: For a date and time combination.
  * `INTERVAL`: Represents durations of time.

#### 2. Calendar Standards

* **Leap Years**: Temporal data handling needs to accommodate leap years and other anomalies in calendar systems. For example, the Julian calendar drifted over time, requiring periodic adjustments.
* **Gregorian Calendar**: The modern calendar, corrected by Pope Gregory XIII in 1582, solved the Julian drift by skipping days and altering the leap year rule.

#### 3. Temporal Data Types in SQL

* **Internal Representations**: SQL systems may store temporal data in two formats:
  * **UNIX method**: Stores a single integer representing clock ticks since a base date, good for calculations but requires conversion for display.
  * **COBOL method**: Stores separate fields for year, month, day, hours, minutes, and seconds, better for display but weaker for calculations.
* **Handling Dates and Timestamps**:
  * SQL syntax for temporal data varies by vendor.
  * For example, SQL Server allows you to reduce a `TIMESTAMP` to a date using a `CAST()` and `FLOOR()` operation.

#### 4. Date Format Standards

* **ISO-8601 Standard**: SQL adheres to the `yyyy-mm-dd` format for dates and timestamps. This format reduces ambiguity in data exchange and allows for easier sorting and manipulation.
* **Time Zones**: Handling time zones in distributed systems requires the use of Universal Coordinated Time (UTC), to avoid conflicts caused by differences in local times.

#### 5. Working with Time and Interval Data

* **Types of Intervals**:
  * **Year-Month Intervals**: Useful when dealing with durations measured in years or months.
  * **Day-Time Intervals**: Useful for measuring finer durations such as days, hours, or seconds.
* **Queries with Date Arithmetic**:
  * SQL supports simple date arithmetic, such as adding or subtracting days from a date.
  * Subtracting two dates returns the difference in days.

#### 6. Temporal Data Models

* **Transaction Time and Valid Time**: These are the two main time-related aspects of data modeling:
  * **Valid Time**: When a fact was true in the real world.
  * **Transaction Time**: When the data was stored or updated in the database.
* **Temporal Duplicates**: Handling duplicates in temporal data is complex, especially when historical data is kept. For example, preventing duplicates when data has a valid-time range involves using constraints or triggers.

#### 7. Temporal Joins

* **Joining Temporal Data**: Temporal joins are more complex than typical joins. For example, to find records that were valid during overlapping time periods, you need to use comparisons on the `valid_from` and `valid_to` dates.

#### 8. Temporal Data Challenges

* **Handling Daylight Saving Time (DST)**: Systems need to account for legal time changes due to DST. Storing time in UTC can alleviate some of these challenges, though this requires careful implementation.
* **Timestamps for Uniqueness**: Timestamps can be used to create unique keys in databases, but care must be taken in distributed systems where clocks may not be synchronized, potentially creating duplicate keys.

#### 9. Temporal Data Use Cases

* **Audit Logs**: Temporal data models are often used to maintain audit logs, where changes to data over time are tracked with transaction timestamps.
* **Bitemporal Data**: In advanced systems, both valid and transaction times are used, providing full historical tracking of data and changes.

#### 10. Temporal Support in SQL

* **Standard SQL**: While SQL-92 added basic support for temporal data types, handling time-varying data in SQL requires advanced techniques, including triggers and complex queries. Fully supporting temporal databases remains challenging, with proposals for standard temporal tables not yet widely adopted.

In conclusion, temporal data is a critical and complex aspect of database management, requiring careful handling of time zones, intervals, and time-related anomalies. Celko emphasizes the importance of understanding how SQL systems handle time and the need for adhering to standards like ISO-8601 for reliable database operation.



## Chapter 5: Character Data Types in SQL

**5.1 Problems with SQL Strings** SQL strings present various challenges due to differences in string handling across programming languages. Common problems include string equality, ordering, and grouping.

* **5.1.1 Problems of String Equality**: SQL compares strings by padding the shorter string with spaces to match the length of the longer one. This can lead to unexpected results when comparing strings of unequal lengths. Case sensitivity can also vary, with some SQL implementations allowing case-insensitive matches. Functions like `LOWER()` and `UPPER()` can help handle case issues.
* **5.1.2 Problems of String Ordering**: SQL-89 did not specify a collation sequence, but most implementations use ASCII or EBCDIC. Different languages and locales may have different sorting rules (e.g., Nordic languages or variations in German sorting). Standard SQL allows DBAs to define custom collation sequences.
* **5.1.3 Problems of String Grouping**: When grouping by `VARCHAR` fields, SQL may pad strings of different lengths, causing inconsistencies in how groups are handled. The `CHAR_LENGTH()` function can return varying results depending on the database implementation. A constraint to trim trailing blanks can help avoid these issues.

**5.2 Standard String Functions** SQL-92 introduced standard string functions that most products support. These functions include:

* **`SUBSTRING()`**: Extracts a portion of a string based on starting position and length.
* **`TRIM()`**: Removes unwanted characters from the start, end, or both sides of a string.
* **`CHAR_LENGTH()`**: Determines the number of characters in a string.
* **`POSITION()`**: Finds the starting position of a substring within a string.
* **`UPPER()` and `LOWER()`**: Convert a string to uppercase or lowercase, respectively.

**5.3 Common Vendor Extensions** Vendor-specific extensions go beyond the standard SQL string functions. Some common extensions include:

* **`SPACE(n)`**: Produces a string of `n` spaces.
* **`REPLICATE()` or `REPEAT()`**: Repeats a string `n` times.
* **`REPLACE()`**: Replaces occurrences of a substring within a string.
* **`REVERSE()`**: Reverses the order of characters in a string (useful for searching).

**5.3.1 Phonetic Matching** Phonetic algorithms, like the Soundex function, are used to match similar-sounding names. These functions can help handle inconsistencies in name spellings, such as those caused by phonetic errors.

**5.4 Cutter Tables** Cutter tables were not discussed in detail, but they are mentioned as a feature related to handling string data, likely for specialized or legacy SQL implementations .

This chapter provides an overview of the challenges with character data types in SQL and offers both standard and vendor-specific solutions to handle strings effectively.



## Chapter 6: NULLs - Missing Data in SQL

**6.1 Empty and Missing Tables**

* **Empty Table**: Defined with columns and constraints but contains zero rows. All constraints are true.
* **Missing Table**: Removed or not created, or its view is not possible due to a missing underlying table.

**6.2 Missing Values in Columns**

* **NULL**: Represents currently unknown values. SQL uses a general-purpose `NULL` to indicate missing data. This can represent a variety of situations, from unknown values to "not applicable."

**6.3 Context and Missing Values**

* Different contexts in SQL may cause `NULL` to behave in various ways. For example, in queries involving domains (e.g., hair color and car color), comparing `NULL` values from different domains leads to undefined behavior or an "UNKNOWN" result.

**6.4 Comparing NULLs**

* **Three-Valued Logic (3VL)**:
  * SQL employs three values: `TRUE`, `FALSE`, and `UNKNOWN`.
  * A comparison involving `NULL` does not result in `TRUE` or `FALSE`, but `UNKNOWN`.
  * Predicates like `IS NULL` help handle this behavior directly.

**6.5 NULLs and Logic**

* SQL uses **George Boole's** logic with three possible results (`TRUE`, `FALSE`, and `UNKNOWN`) due to `NULL` values.
* Comparisons involving `NULL` result in `UNKNOWN`, affecting query logic. For instance, a query involving `NOT IN` will behave unexpectedly when `NULL` values are involved.

**6.5.1 NULLs in Subquery Predicates**

* Queries using `IN` or `NOT IN` with subqueries behave differently when `NULL` is present. The presence of `NULL` can make an entire query return no results due to `UNKNOWN` values.

**6.5.2 Standard SQL Solutions**

* **SQL-92** introduces predicates like `IS [NOT] TRUE | FALSE | UNKNOWN` to map three-valued logic to two-valued logic, helping manage complex logical conditions involving `NULL`.

**6.6 Math and NULLs**

* Arithmetic operations with `NULL` propagate `NULL`. For example, `NULL + 5` results in `NULL`. This is consistent with the idea that operations with unknown values remain unknown.

**6.7 Functions and NULLs**

* Most SQL functions propagate `NULL` (e.g., trigonometric or arithmetic functions). However, SQL provides two key functions for handling `NULL`:
  1. **`NULLIF(V1, V2)`**: Returns `NULL` if `V1 = V2`; otherwise, it returns `V1`.
  2. **`COALESCE(V1, V2, ..., Vn)`**: Returns the first non-`NULL` value from the list or `NULL` if all values are `NULL`.

**6.8 NULLs and Host Languages**

* Host languages do not natively support `NULL`, requiring mechanisms like **indicator variables** to handle `NULL` values when interacting with SQL.
* **INDICATOR Variables**: Used in host languages to flag whether a column value is `NULL`.

**6.9 Design Advice for NULLs**

* Avoid using `NULL` in **Primary Keys**: A `NULL` in a primary key would mean the system cannot uniquely identify rows.
* Avoid using `NULL` in **Foreign Keys**: Leads to issues in joins and can cause loss of information.
* **Consistency**: In certain cases, `NULL` must be used, like date fields where the absence of a date is significant (e.g., missing birthdate).

**6.9.1 Avoiding NULLs from Host Programs**

* Strategies for avoiding `NULL` in host programs include using default values, deducing missing data, and employing batch processing to validate data before insertion.

**6.10 A Note on Multiple NULL Values**

* A proposal for handling **multiple missing value tokens** in a database, where different `NULL` values could represent different missing value states (e.g., "Not applicable," "Missing but applicable," etc.). These values can be leveraged in functions like `SUM()` or `AVG()` with custom logic to manage degrees of approximation.

This chapter emphasizes the complexity and nuance involved in dealing with `NULL` values in SQL and provides solutions to handle them effectively within the SQL framework.



## Chapter 7: Multiple Column Data Elements

**7.1 Distance Functions**\
This section discusses calculating distances between points on the globe using longitude and latitude values. The example assumes that latitude and longitude values are in radians. An SQL function is provided to compute the great-circle distance between two points on Earth, taking into account potential rounding errors.

* **SQL Function for Distance Calculation**:
  * The function takes two points' latitude and longitude as inputs.
  * It uses trigonometric formulas to calculate the great-circle distance.
  * Intermediate values help prevent round-off errors.

**7.2 Storing an IP Address in SQL**\
Storing IP addresses can be done in multiple ways:

1. **A Single `VARCHAR(15)` Column**:
   * The most straightforward approach, but it requires validation with string checks.
   * Suitable for human readability, but not optimal for programmatic use or indexing.
2. **One `INTEGER` Column**:
   * Converts the IP address into a single integer.
   * Minimal storage but complex to work with for humans.
   * A function can convert the integer back into a readable IP address.
3. **Four `SMALLINT` Columns**:
   * Stores each octet of the IP address in separate columns.
   * Balances storage efficiency and human readability, allowing for indexing on each octet.

**7.3 Currency and Other Unit Conversions**\
Currency values require careful handling of both the amount and the currency unit:

* The **ISO 4217 standard** specifies three-character codes for currencies.
* Conversions often involve maintaining an exchange rate table, and SQL views can be created for different user groups who need amounts in a specific currency.
* Special rules apply to **euro** conversions, which must be triangulated (i.e., converting first to euros and then to the desired currency). Precision is mandated by European Union regulations.

**7.4 Social Security Numbers (SSNs)**\
SSNs in the United States have a specific format (XXX-XX-XXXX) and are divided into three parts:

* **Area**: Represents where the SSN was applied for.
* **Group** and **Serial**: Other identifying numbers.
* The section also touches on how companies verify SSNs and how death records can be searched using the SSN.

**7.5 Rational Numbers**\
This section discusses handling rational numbers, which consist of both a numerator and a denominator. Rational numbers are stored and processed differently from simple integers or floating-point numbers, allowing for precise representation of fractions.

This chapter focuses on modeling multi-part data like coordinates, IP addresses, and numbers that cannot be stored in a single column.



## Chapter 8: Table Operations

**8.1 DELETE FROM Statement** The `DELETE FROM` statement removes rows from a table, with two common forms:

* **Positioned DELETE**: Performed using cursors, typically in host programs like COBOL or Java (not detailed in this book).
* **Searched DELETE**: Uses a `WHERE` clause to target specific rows. If the `WHERE` clause is omitted, all rows in the table are deleted.

Key points:

* **Deleting Based on Conditions**: The `WHERE` clause defines the conditions for deletion. Deleting all rows without conditions will leave the table empty, but the table itself remains.
* **Deleting from Multiple Tables**: SQL does not allow a direct `DELETE` across multiple tables without the use of `CASCADE` referential integrity constraints or by using joins with a subquery.

**8.1.3 Deleting Based on Data in a Second Table** To delete rows based on data from another table, a subquery is typically used. This ensures the targeted rows meet conditions specified in a second table.

**8.1.4 Deleting Within the Same Table** This scenario is used when deleting rows based on conditions from other rows within the same table, often leveraging subqueries or self-joins to filter specific records.

**8.1.5 Deleting in Multiple Tables Without Referential Integrity** When referential integrity is not enforced by the database, deleting data from multiple tables can be challenging. This typically involves manually managing the deletion order to avoid foreign key violations.

***

**8.2 INSERT INTO Statement** The `INSERT INTO` statement adds new rows to a table. Common methods include:

1. **Insert Specific Values**: Uses the `VALUES` clause to add one or more rows.
2. **Insert From a Query**: A more dynamic approach that inserts rows derived from the result of a query.

Key points:

* **Nature of Inserts**: SQL handles insertions as set operations, allowing multiple rows to be added at once.
* **Using Subqueries in Insertions**: You can insert data from another table by querying it directly in the `INSERT INTO` statement.

**8.2.3 Bulk Load and Unload Utilities** Most SQL systems provide tools for bulk loading large amounts of data, which is more efficient than individual insertions. These utilities often bypass standard constraints or triggers, focusing on raw data transfer for performance optimization.

***

**8.3 UPDATE Statement** `UPDATE` is used to modify existing rows in a table. Similar to `DELETE`, it can be:

* **Positioned UPDATE**: Performed using cursors, typically in host languages.
* **Searched UPDATE**: Uses a `WHERE` clause to specify the rows that should be updated.

Key points:

* **SET Clause**: This clause defines which columns to update and their new values.
* **Conditional Updates**: You can use subqueries or the `CASE` expression in the `SET` clause to apply conditional updates, providing flexibility in managing data.

**8.3.4 Updating with a Second Table** In many cases, you may need to update values in one table based on values in another. This can be done using subqueries or joins in the `UPDATE` statement.

**8.3.5 Using CASE Expression in UPDATEs** The `CASE` expression is useful for conditional updates. It allows you to specify different values depending on the conditions met in the `UPDATE` statement.

***

**8.4 A Note on Flaws in a Common Vendor Extension** Some SQL implementations (e.g., T-SQL) introduce non-standard behavior, such as allowing a `FROM` clause in an `UPDATE` statement. This can lead to multiple updates to the same row, which is not allowed in standard SQL.

***

**8.5 MERGE Statement** Introduced in SQL-99, the `MERGE` statement combines `INSERT` and `UPDATE` operations. It’s often used in data warehousing or OLAP (Online Analytical Processing) environments.

* **Logic**: If a row exists in the target table, the `MERGE` operation updates it. If the row does not exist, a new row is inserted. This effectively simulates a "merge" operation between two datasets.

***

**Summary of Key Concepts**:

* **DELETE FROM**: Removes rows from a table, with the ability to target specific rows using the `WHERE` clause. Deletions across multiple tables require careful planning if referential integrity constraints are not enforced.
* **INSERT INTO**: Adds new rows to a table, supporting both individual insertions and bulk data loading through specialized utilities.
* **UPDATE**: Modifies existing data in a table, allowing conditional updates through subqueries or the `CASE` expression.
* **MERGE**: A powerful SQL-99 feature that merges rows based on their existence in the target table, combining insertions and updates into a single operation.

This chapter provides a comprehensive overview of table operations in SQL, focusing on efficient data manipulation techniques.



## Chapter 9: Comparison or Theta Operators

**9.1 Converting Data Types**\
This section explores the complexity of comparing values across different data types in SQL. SQL has overloaded comparison operators that work for numeric, character, and datetime data types. When comparing two different types, one of the values must be converted to the type of the other before a valid comparison can be made.

* The standard numeric data types follow this hierarchy: `SMALLINT`, `INTEGER`, `BIGINT`, `DECIMAL`, `NUMERIC`, `REAL`, `FLOAT`, and `DOUBLE PRECISION`. Conversions between types can result in either rounding or truncation, depending on the database implementation.
* **Floating-point numbers** can present particular challenges due to hardware variations in handling `REAL`, `FLOAT`, and `DOUBLE PRECISION` numbers.
* **Character types** can be compared if they belong to the same repertoire (e.g., ASCII or EBCDIC). Comparisons are made by padding the shorter string with spaces to match the longer string.

**9.2 Row Comparisons in SQL**\
SQL allows theta operators to work not just on scalars but also on row expressions, which is particularly useful when keys consist of multiple columns. These row comparisons follow strict rules:

* For two rows `RX` and `RY`, comparisons are based on corresponding columns. For example, `(RX = RY)` is only `TRUE` if all individual column comparisons return `TRUE`.
* Row comparisons also support logical operators (`=`, `<>`, `<`, `>`, `<=`, `>=`), which are applied to corresponding columns.
* An example with three rows (`A`, `B`, `C`) is provided to show how the presence of `NULL` values results in `UNKNOWN` comparisons, following SQL's three-valued logic. In such cases, the result of a comparison involving `NULL` can be `TRUE`, `FALSE`, or `UNKNOWN`.

**Key Rules for Row Comparisons**:

1. `(RX = RY)` is `TRUE` if all columns match.
2. `(RX <> RY)` is `TRUE` if at least one column does not match.
3. `(RX < RY)` is `TRUE` if all preceding columns are equal, and one column in `RX` is less than the corresponding column in `RY`.
4. The same logic applies for other operators (`>`, `<=`, `>=`), with appropriate reversals of the conditions.

**Avoiding Non-Standard Symbols**\
In earlier SQL implementations, some systems used `!=` or `~=` to represent "not equal," which comes from other programming languages like C and PL/I. These are not part of the standard SQL and should be avoided to maintain code portability and readability.

This chapter emphasizes the importance of understanding how SQL compares different data types and row structures, ensuring that operations involving mixed data types or `NULL` values are handled appropriately【25:0†source】【25:1†source】.



## Chapter 10: Valued Predicates

**10.1 IS NULL Predicate**

*   The `IS NULL` predicate tests if a value or expression is `NULL`. Its syntax is:

    ```sql
    <expression> IS [NOT] NULL
    ```

    This is the only way to determine if an expression is `NULL` in SQL. The predicate works with both scalar expressions and row constructors.

    * If all columns in a row are `NULL`, the expression `R IS NULL` evaluates to `TRUE`.
    * If none of the values are `NULL`, `R IS NOT NULL` evaluates to `TRUE`.
    * When rows contain a mix of `NULL` and non-`NULL` values, special rules apply based on the number of columns in the row (referred to as "degree").

    **Examples**:

    * `(1, 2, 3) IS NULL` returns `FALSE`
    * `(1, NULL, 3) IS NULL` returns `FALSE`
    * `(NULL, NULL, NULL) IS NULL` returns `TRUE`

**10.1.1 Sources of NULLs**

* `NULL` values can originate from several places:
  * **Aggregate functions**: Return `NULL` for empty sets.
  * **OUTER JOINs**: Produce `NULL`s for missing rows.
  * **Arithmetic operations**: If any operand is `NULL`, the result is also `NULL`.
  * **OLAP operations**: Generate `NULL`s in specific cases.

***

**10.2 IS \[NOT] {TRUE | FALSE | UNKNOWN} Predicate**

*   This predicate tests the truth value (`TRUE`, `FALSE`, or `UNKNOWN`) of a Boolean expression. Its syntax is:

    ```sql
    <Boolean test> ::= <Boolean primary> IS [NOT] <truth value>
    ```

    * If the expression evaluates to the specified truth value, the result is `TRUE`; otherwise, it is `FALSE`.

    **Truth Table**:

    * `IS TRUE`: returns `TRUE` only when the expression is `TRUE`.
    * `IS FALSE`: returns `TRUE` when the expression is `FALSE`.
    * `IS UNKNOWN`: returns `TRUE` when the expression is `UNKNOWN` (i.e., involves a `NULL`).
    *   For example, to identify potential `NULL` values in conditions, SQL allows queries like:

        ```sql
        SELECT * 
        FROM Personnel 
        WHERE (job = 'Programmer' AND salary < 50000) IS UNKNOWN;
        ```

        This query checks if there’s uncertainty in the evaluation due to `NULL` values in the conditions.

***

**10.3 IS \[NOT] NORMALIZED Predicate**

* **Normalization** refers to ensuring that a string adheres to a standard form. For Unicode strings, normalization is vital to ensure consistent representation, especially when accents, ligatures, or special characters are involved.
  *   The predicate checks if a string is in one of the four Unicode normal forms (D, C, KD, or KC). For example:

      ```sql
      <string> IS [NOT] NORMALIZED
      ```
  * Normalization is crucial when working with multi-byte character sets or languages with complex writing systems (e.g., Korean, Greek).

This chapter focuses on the nuances of handling `NULL` values and truth conditions in SQL, including how to properly test for `NULL` and manage logical expressions.



## Chapter 11: CASE Expressions

**11.1 The CASE Expression**

The `CASE` expression, introduced in SQL-92, allows programmers to simplify control flow by avoiding procedural `IF-THEN-ELSE` logic within SQL queries. There are two forms of `CASE` expressions:

1. **Simple CASE**: This compares a single expression against multiple values.
2. **Searched CASE**: This evaluates multiple logical conditions, with the first `WHEN` clause that evaluates to `TRUE` being executed.

Syntax for `CASE`:

```sql
CASE <case operand>
   WHEN <value1> THEN <result1>
   WHEN <value2> THEN <result2>
   ...
   ELSE <default_result>
END
```

* **Implicit ELSE Clause**: If no `ELSE` clause is provided, an implicit `ELSE NULL` is inserted by the SQL engine. Explicit casting of `NULL` is recommended to establish the data type.

Examples:

*   A simple example:

    ```sql
    CASE iso_sex_code
      WHEN 0 THEN 'Unknown'
      WHEN 1 THEN 'Male'
      WHEN 2 THEN 'Female'
      ELSE NULL
    END
    ```
* **Nested CASE Expressions**: SQL allows `CASE` expressions to be nested. This provides flexibility in constructing complex conditional logic.

***

**11.1.1 The COALESCE() and NULLIF() Functions**

These functions are defined in terms of the `CASE` expression and provide concise ways to handle `NULL` values:

* **COALESCE()**: Returns the first non-`NULL` value from a list of expressions. If all values are `NULL`, it returns `NULL`.
  *   Defined as:

      ```sql
      COALESCE(expr1, expr2, ..., exprN)
      ```

      Equivalent to:

      ```sql
      CASE
        WHEN expr1 IS NOT NULL THEN expr1
        WHEN expr2 IS NOT NULL THEN expr2
        ...
        ELSE NULL
      END
      ```
* **NULLIF()**: Compares two expressions and returns `NULL` if they are equal; otherwise, it returns the first expression.
  *   Equivalent to:

      ```sql
      CASE
        WHEN expr1 = expr2 THEN NULL
        ELSE expr1
      END
      ```

***

**11.1.2 CASE Expressions with GROUP BY**

`CASE` expressions are extremely useful in `GROUP BY` queries to perform conditional aggregation. For example, counting the number of male and female employees in each department can be achieved using:

```sql
SELECT dept_nbr,
       SUM(CASE WHEN gender = 'M' THEN 1 ELSE 0 END) AS males,
       SUM(CASE WHEN gender = 'F' THEN 1 ELSE 0 END) AS females
FROM Personnel
GROUP BY dept_nbr;
```

Alternatively, using the `COUNT()` function with `NULL` handling:

```sql
SELECT dept_nbr,
       COUNT(CASE WHEN gender = 'M' THEN 1 ELSE NULL END) AS males,
       COUNT(CASE WHEN gender = 'F' THEN 1 ELSE NULL END) AS females
FROM Personnel
GROUP BY dept_nbr;
```

***

**11.1.3 CASE, CHECK() Clauses, and Logical Implication**

Complex logical predicates can be embedded in `CASE` expressions, especially in `CHECK()` constraints. For example:

```sql
CONSTRAINT salary_check
CHECK (CASE WHEN dept_nbr = 'D1'
            THEN CASE WHEN salary < 44000 THEN 1 ELSE 0 END
            ELSE 1 END = 1)
```

This constraint ensures that for department `D1`, salaries must be less than 44,000.

Logical implication can also be expressed using `CASE`, allowing complex constraints to be enforced at the database level.

***

**11.1.4 Subquery Expressions and Constants**

Subquery expressions, which are `SELECT` statements embedded within other queries, can also be used within `CASE` expressions. SQL supports four types of subquery expressions:

1. **Tabular**: Returns a full table.
2. **Columnar**: Returns a single column.
3. **Row**: Returns a single row.
4. **Scalar**: Returns a single value, useful in `SELECT` or `WHERE` clauses.

For example, subqueries can be embedded in `CASE` expressions to make dynamic decisions based on database results.

***

**11.2 Rozenshtein Characteristic Functions**

Rozenshtein’s characteristic functions convert logical expressions into numeric values (`1` for `TRUE`, `0` for `FALSE`). This is useful for creating rules that rely on numeric results. For example, using algebraic functions to compare numeric values:

* `(a = b)` becomes: `1 - ABS(SIGN(a - b))`
* `(a <> b)` becomes: `ABS(SIGN(a - b))`

These expressions allow for efficient implementation of conditional logic in databases that may not natively support `CASE` expressions.

This chapter highlights the power of `CASE` expressions for building flexible, non-procedural control flow directly in SQL queries. The functions derived from `CASE` (`COALESCE()` and `NULLIF()`) and its applications in `GROUP BY` and constraint validation further extend its utility across a wide range of scenarios.



#### Chapter 12: LIKE Predicate

**12.1 LIKE Predicate Overview** The `LIKE` predicate in SQL is used for pattern matching within strings. The syntax for `LIKE` is:

```sql
<match_value> [NOT] LIKE <pattern> [ESCAPE <escape character>]
```

* `LIKE` allows two wildcards:
  * **`%`**: Matches any sequence of characters (including zero characters).
  * **`_`**: Matches exactly one character.

Example:

```sql
SELECT *
  FROM People
 WHERE name LIKE 'Mac%';
```

* The expression `M NOT LIKE P` is equivalent to `NOT (M LIKE P)`.

**12.1.1 Tricks with Patterns**

* The `_` wildcard is faster than `%` because it requires a single operation to match one character, while `%` requires look-ahead parsing.
*   Example to match names starting with "Mac":

    ```sql
    SELECT *
      FROM People
     WHERE name LIKE 'Mac_'
        OR name LIKE 'Mac__'
        OR name LIKE 'Mac___';
    ```
*   Avoid placing `%` at the beginning of patterns for performance reasons:

    ```sql
    SELECT *
      FROM People
     WHERE name LIKE '%son';
    ```

    Instead, use:

    ```sql
    SELECT *
      FROM People
     WHERE name LIKE '_son'
        OR name LIKE '__son';
    ```

**12.2 NULL Values and Empty Strings**

* If `NULL` is involved in the predicate, the result will be `UNKNOWN`. For example, `M LIKE P` returns `UNKNOWN` if either value is `NULL`.
* If both `M` and `P` are empty strings, `M LIKE P` returns `TRUE`.

**12.3 LIKE vs. Equality**

* Strings can be equal but not match under `LIKE`. For instance, `'Smith' = 'Smith '` is `TRUE` because the shorter string is padded with spaces, but `'Smith' LIKE 'Smith '` returns `FALSE` since `LIKE` does not pad strings.
* Use the `TRIM()` function to remove unwanted blanks.

**12.4 Avoiding LIKE with a Join**

*   Beginners might try writing something like `<string> IN LIKE (<pattern_list>)`. This is illegal, but the same result can be achieved using a join:

    ```sql
    CREATE TABLE Patterns (template VARCHAR(10) NOT NULL PRIMARY KEY);
    INSERT INTO Patterns VALUES ('Celko%'), ('Chelko%');

    SELECT A1.lastname
      FROM Patterns AS P1, Authors AS A1
     WHERE A1.lastname LIKE P1.template;
    ```

**12.5 CASE Expressions and LIKE**

*   You can use `CASE` expressions to count occurrences of substrings within strings:

    ```sql
    SELECT text_col, 
           CASE
             WHEN text_col LIKE '%term%term%' THEN 2
             WHEN text_col LIKE '%term%' THEN 1
             ELSE 0
           END AS term_tally
      FROM Foobar
     WHERE text_col LIKE '%term%';
    ```

**12.6 SIMILAR TO Predicate**

*   SQL-99 added the `SIMILAR TO` predicate for more complex pattern matching based on regular expressions. This offers more powerful pattern matching than `LIKE`, using symbols such as:

    * `|`: Alternation (either of two alternatives).
    * `*`: Repetition of the previous item zero or more times.
    * `+`: Repetition of the previous item one or more times.

    Example:

    ```sql
    serial_nbr SIMILAR TO '#[0-9]+'
    ```

**12.7 Tricks with Strings**

*   **String Character Content**: Checking the character content of a string is possible using constraints and comparison functions:

    ```sql
    CHECK (UPPER(some_alpha) <> LOWER(some_alpha))
    ```
*   **Indexing Strings**: Creating an index on a string can improve search performance. Reversing a string or using specific prefixes can optimize string searches:

    ```sql
    CREATE INDEX acct_searching 
        ON CreditCards 
      WITH REVERSE(card_nbr);
    ```

This chapter provides various techniques and optimizations for using `LIKE` and similar string pattern matching tools effectively in SQL queries.



## Chapter 13: BETWEEN and OVERLAPS Predicates

**13.1 The BETWEEN Predicate**

The `BETWEEN` predicate is used to test if a value lies within a specified range of two other values. Its basic form is:

```sql
<value expression> [NOT] BETWEEN <low value expression> AND <high value expression>
```

This is shorthand for:

```sql
(<low value expression> <= <value expression>) AND (<value expression> <= <high value expression>)
```

* **Inclusion of Endpoints**: The range includes the endpoints, which means the comparison checks both boundaries.
* **Applicable to All Data Types**: While commonly used for numeric ranges, `BETWEEN` can also be used with character strings and temporal (date/time) data types.

**13.1.1 Results with NULL Values**

* If any of the values in the `BETWEEN` predicate (the value being checked or the boundaries) are `NULL`, the result is `UNKNOWN`, due to SQL's handling of `NULL` with three-valued logic.

**13.1.2 Empty Sets**

* If the high value is less than the low value (e.g., `x BETWEEN 15 AND 12`), the result is always `FALSE`. This ensures that a logically empty set is produced, except when dealing with `NULL` values, which produce `UNKNOWN`.

**13.1.3 Programming Tips**

* **Edge Case Considerations**: When working with inclusive ranges, especially for tests or grading systems, special care is needed to avoid "borderline" errors. For instance, a student scoring exactly on the boundary (e.g., 90) should receive the correct grade.

Example:

```sql
score BETWEEN 90 AND 100 -- includes both 90 and 100
```

A more precise version might involve:

```sql
(score BETWEEN low_score AND high_score) AND (score > low_score)
```

***

**13.2 The OVERLAPS Predicate**

The `OVERLAPS` predicate checks whether two time periods overlap. Unlike `BETWEEN`, which compares scalar values, `OVERLAPS` is designed for use with intervals and datetime data types.

**13.2.1 Time Periods and the OVERLAPS Predicate**

*   **Syntax**:

    ```sql
    (start1, end1) OVERLAPS (start2, end2)
    ```

    This expression returns `TRUE` if the two periods share any overlap in time.
* **Rules**:
  1. Time periods include their start points but exclude their end points, following ISO conventions.
  2. Two time periods overlap if they share any portion of time.
  3. Instantaneous events (where the start and end times are the same) overlap if they occur at the same moment.
* **Edge Cases**: When one or both time periods involve `NULL` values, `OVERLAPS` returns `UNKNOWN`.

**Examples of Overlap Logic**:

* `(today, today) OVERLAPS (today, today)` → `TRUE`
* `(today, tomorrow) OVERLAPS (today, today)` → `TRUE`
* `(yesterday, today) OVERLAPS (today, tomorrow)` → `FALSE`

**Practical Application**: For example, in a hotel reservation system, you can check if a guest's stay overlaps with a particular event using `OVERLAPS`. If you're trying to determine which guests were at a hotel during an event, the query would look like this:

```sql
SELECT guest_name, celeb_name
  FROM Guests, Celebrations
 WHERE (arrival_date, depart_date) OVERLAPS (start_date, finish_date);
```

Alternatively, using `BETWEEN`:

```sql
SELECT guest_name
  FROM Guests, Celebrations
 WHERE arrival_date BETWEEN start_date AND finish_date;
```

However, `BETWEEN` alone may miss some overlapping cases. A more complete query would involve several conditions to capture all overlap possibilities, including partial and complete overlaps.

This chapter explains how to use `BETWEEN` for scalar comparisons and `OVERLAPS` for temporal intervals, highlighting how both predicates handle `NULL` values and boundary conditions.



## Chapter 14 &#x54;_&#x68;e \[NOT] IN() Predicate_.&#x20;



This chapter provides an in-depth analysis of the IN predicate and its usage within SQL queries. Below are detailed notes based on the subtopics covered:

#### 1. **Optimizing the IN() Predicate (Section 14.1)**

* The chapter starts with optimization techniques for the `IN()` predicate, which is commonly used to filter data where a column's value is within a specified list of values.
* It highlights how to rewrite queries to improve performance by reducing unnecessary scanning.
* Techniques include transforming the `IN()` predicate into more efficient structures or using indexes to ensure faster lookups, particularly in large datasets.

#### 2. **Replacing ORs with the IN() Predicate (Section 14.2)**

* Discusses how multiple `OR` conditions can be replaced with a single `IN()` predicate to make the query more concise and potentially faster.
* This section explores scenarios where this optimization is beneficial and those where it may not result in significant gains.
* The idea is to reduce redundancy and streamline SQL queries.

#### 3. **NULLs and the IN() Predicate (Section 14.3)**

* Focuses on the handling of `NULL` values within the `IN()` predicate.
* Since `NULL` is an unknown value, it leads to complications when checking for membership in a list.
* Solutions and recommendations are provided for scenarios where `NULL` values may disrupt query results.

#### 4. **IN() Predicate and Referential Constraints (Section 14.4)**

* This section explains how `IN()` predicates relate to referential integrity constraints.
* It provides best practices on enforcing these constraints through `IN()` queries, ensuring that database relationships remain intact.
* Practical examples are given to show how this is applied in database design.

#### 5. **IN() Predicate and Scalar Queries (Section 14.5)**

* Explores the use of the `IN()` predicate in scalar subqueries.
* Scalar subqueries return a single value, and the section delves into how `IN()` can be used to check if this value belongs to a list of values.
* There is a focus on the performance implications of using subqueries within `IN()` and how to mitigate potential slowdowns.

These sections form the core of Chapter 14, emphasizing practical usage and performance optimization of the `IN()` predicate in SQL queries【9:13†source】.



## Chapter 15&#x20;

focuses on the `EXISTS()` predicate, its structure, behavior, and how it interacts with various SQL concepts. Here's a detailed breakdown of the key concepts:

#### 1. **Overview of the `EXISTS()` Predicate**

* The `EXISTS()` predicate checks for the existence of rows returned by a subquery. If the subquery returns any rows, the result is `TRUE`; otherwise, it is `FALSE`.
*   The basic syntax is:

    ```sql
    EXISTS (SELECT * FROM <subquery>)
    ```
* Unlike other predicates, `EXISTS()` does not return an `UNKNOWN` result. This makes it a reliable option for testing the existence of rows without worrying about ambiguous outcomes.

#### 2. **`EXISTS()` and `NULLs` (Section 15.1)**

* `NULLs` behave peculiarly with `EXISTS()` predicates. A query involving `EXISTS()` may still return `TRUE` even if some data in the subquery is `NULL`.
*   For example, finding employees who do not share a birthday with a celebrity is straightforward:

    ```sql
    SELECT emp_name FROM Personnel WHERE NOT EXISTS 
    (SELECT * FROM Celebrities WHERE Personnel.birthday = Celebrities.birthday);
    ```
* However, `NULLs` in subqueries can lead to unexpected matches. If a celebrity's birthday is `NULL`, the query logic may incorrectly match employees' birthdays to the unknown value.

#### 3. **Optimizing `EXISTS()` with Indexes (Section 15.2)**

* Using indexes in conjunction with `EXISTS()` can significantly improve query performance. If a subquery references an indexed column, the database can check the index without scanning the entire table.
*   Example: Finding employees born on the same day as a celebrity:

    ```sql
    SELECT emp_name FROM Personnel WHERE EXISTS
    (SELECT * FROM Celebrities WHERE Personnel.birthday = Celebrities.birthday);
    ```
* If the `birthday` column in `Celebrities` is indexed, the optimizer will only check the index, skipping the full table scan.

#### 4. **`EXISTS()` and `INNER JOINs` (Section 15.2)**

*   In many cases, an `EXISTS()` query can be "flattened" into a `JOIN`, which can improve readability and performance. For instance, finding employees with the same birthday as a celebrity can be written using an `INNER JOIN`:

    ```sql
    SELECT P1.emp_name, C1.emp_name FROM Personnel P1 JOIN Celebrities C1
    ON P1.birthday = C1.birthday;
    ```
* This approach allows additional columns from both tables to be included in the result set.

#### 5. **`NOT EXISTS()` and `OUTER JOINs` (Section 15.3)**

*   The `NOT EXISTS()` predicate is commonly used with correlated subqueries and can often be rewritten using an `OUTER JOIN` for performance reasons. Example:

    ```sql
    SELECT emp_name FROM Personnel WHERE NOT EXISTS
    (SELECT * FROM Celebrities WHERE Celebrities.birthday = Personnel.birthday);
    ```
*   This query can be rewritten as a `LEFT OUTER JOIN`:

    ```sql
    SELECT P1.emp_name FROM Personnel P1 LEFT JOIN Celebrities C1
    ON P1.birthday = C1.birthday WHERE C1.emp_name IS NULL;
    ```

#### 6. **EXISTS() and Referential Constraints (Section 15.5)**

*   SQL allows referential constraints to be enforced using the `EXISTS()` predicate in a `CHECK()` clause. For instance, ensuring valid state codes in an address table:

    ```sql
    CHECK (EXISTS (SELECT * FROM ZipCodeData WHERE ZipCodeData.state_code = Addresses.state_code));
    ```
* This ensures that any state code entered in the `Addresses` table must exist in the `ZipCodeData` table.

#### 7. **EXISTS() and Three-Valued Logic (Section 15.6)**

* In SQL’s three-valued logic, `TRUE`, `FALSE`, and `UNKNOWN` outcomes are considered. However, `EXISTS()` behaves as a two-valued predicate, meaning that `NOT (NOT UNKNOWN) = UNKNOWN` does not apply. This behavior can sometimes lead to counterintuitive results when dealing with `NULL` values.

#### Key Takeaways

* The `EXISTS()` predicate is useful for checking the existence of rows and can often be optimized through rewriting into `JOINs` or leveraging indexes.
* It handles `NULLs` differently from other predicates, and special care must be taken when using it in queries involving `NULL` data.
* `NOT EXISTS()` can be a powerful tool for writing exclusion queries, especially when optimized with `OUTER JOINs`.

This chapter provides valuable insights into how to effectively use the `EXISTS()` predicate and optimize SQL queries for performance【9:0†source】【9:1†source】【9:2†source】.



## Chapter 16&#x20;

focuses on _Quantified Subquery Predicates_, which are used to compare single values with a subquery's result set. Here's a detailed breakdown of the chapter's key points:

#### 1. **Quantifiers in SQL**

* SQL uses quantifiers like `ALL`, `ANY`, and `SOME` to extend comparison operators. These allow subquery results to be compared against other values.
* A quantifier determines how many items in the result set must satisfy a given condition.

#### 2. **Scalar Subquery Comparisons (Section 16.1)**

* Standard SQL supports scalar subqueries, which return single-row, single-column results. These can be compared like scalar values in predicates.
*   Example:

    ```sql
    SELECT T1.teacher_name 
    FROM Teachers AS T1
    WHERE T1.birthday > (SELECT MAX(S1.birthday) - INTERVAL '365' DAY FROM Students AS S1);
    ```
* Here, the subquery returns a single value (the maximum birthday from the `Students` table), which is then compared with the `birthday` column in `Teachers`.
* Scalar subqueries can be correlated or uncorrelated, with correlated subqueries being more complex due to their dependency on the outer query.

#### 3. **Quantifiers and Missing Data (Section 16.2)**

* SQL's `ANY`, `SOME`, and `ALL` quantifiers handle missing data (i.e., `NULL` values) in subqueries in different ways.
*   General form:

    ```sql
    <value expression> <comp op> <quantifier> <subquery>
    ```

    This expands into multiple comparisons, combined by logical `OR` for `ANY` and logical `AND` for `ALL`.
* `ANY` or `SOME` returns `TRUE` if any subquery row satisfies the condition.
* `ALL` requires that all rows must satisfy the condition; otherwise, the result is `FALSE`.
* Empty sets or subqueries with all `NULL` values return `FALSE` or `UNKNOWN`, respectively, for `ALL` and `ANY` quantifiers【17:2†source】【17:3†source】.

#### 4. **The `ALL` Predicate and Extrema Functions (Section 16.3)**

*   The `ALL` predicate can be used to compare a value against all values in a subquery. It is useful for checks like:

    ```sql
    SELECT * 
    FROM Authors AS A1 
    WHERE 19.95 < ALL (SELECT price FROM Books AS B1 WHERE A1.author_name = B1.author_name);
    ```
* `ALL` works similarly to extrema functions like `MAX()` and `MIN()`, but does not discard `NULL` values.
* The chapter emphasizes caution with `NULLs` when using `ALL`, as they affect the results differently than extrema functions【17:10†source】.

#### 5. **The `UNIQUE` Predicate (Section 16.4)**

* The `UNIQUE` predicate tests for duplicate rows in a subquery. If no duplicate rows are found, the result is `TRUE`.
*   Syntax:

    ```sql
    UNIQUE <table subquery>
    ```
*   The predicate returns `FALSE` if two rows in the subquery are identical. This is equivalent to counting the rows and ensuring no duplicates:

    ```sql
    EXISTS (SELECT column_list FROM <subquery> GROUP BY column_list HAVING COUNT(*) > 1);
    ```
* The presence of `NULL` values complicates the `UNIQUE` predicate. It treats `NULL` values as not comparable, potentially leading to results that "give the benefit of the doubt" to `NULLs`, making the query return `TRUE` in some cases【17:10†source】【17:4†source】.

#### Key Takeaways:

* The `ANY` and `SOME` predicates are essentially the same in SQL, while `ALL` has stricter conditions.
* Handling of `NULL` values is crucial when using quantified predicates.
* Extrema functions (`MAX`, `MIN`) and `ALL` can often be interchanged, but careful attention is needed due to differences in how they handle `NULLs`.
* The `UNIQUE` predicate ensures the absence of duplicate rows but requires a nuanced understanding of `NULL` values in subqueries.

This chapter emphasizes the logical behavior and edge cases of SQL quantifiers, helping users understand when and how to use each predicate effectively.



## Chapter 17&#x20;

focuses on the _SELECT Statement_, covering its syntax, the use of joins, and various operations related to the SELECT command. Here's a detailed summary:

#### 1. **Introduction to the SELECT Statement**

* SQL programming centers around the `SELECT` statement, which is capable of performing a wide range of operations, from querying tables to performing calculations.
* While introductory SQL tutorials focus on simple one-table `SELECT` queries, this chapter dives into more advanced topics relevant to experienced programmers【21:0†source】.

#### 2. **SELECT and JOINs (Section 17.1)**

* **One-Level SELECT Statement**:
  * The simplest form is `SELECT * FROM <table>`, which retrieves all rows and columns from a table. However, this rarely happens in practice without a `WHERE` clause to filter rows.
  * SQL executes a `SELECT` statement in a particular order, starting with the `FROM` clause, followed by `WHERE`, `GROUP BY`, `HAVING`, and finally `SELECT`.
  * Understanding this order is crucial for troubleshooting and optimizing queries【21:0†source】.
* **Correlated Subqueries**:
  * Correlated subqueries reference columns from the outer query, effectively allowing SQL to iterate over the results.
  *   Example query: Finding students younger than the oldest student of their gender involves a correlated subquery comparing `age` values between the main and subqueries:

      ```sql
      SELECT *
      FROM Students AS S1
      WHERE age < (SELECT MAX(age)
                    FROM Students AS S2
                    WHERE S1.sex = S2.sex);
      ```
  * This type of query can be complex due to the interdependence of the subquery and outer query【21:1†source】【21:2†source】.
* **SELECT Syntax and Execution Order**:
  * The SQL `SELECT` statement follows a structured order, starting with the `FROM` clause, constructing a working table from the result set. Subsequent clauses like `WHERE` and `GROUP BY` further refine this table.
  * The `SELECT` clause is processed last, ensuring only the required columns and rows are returned【21:5†source】.

#### 3. **The ORDER BY Clause (Section 17.1.4)**

* The `ORDER BY` clause is technically part of a cursor declaration rather than the `SELECT` statement itself.
* Optimizers may use existing indexes to avoid unnecessary sorting. However, when writing SQL code, it's crucial not to depend on implicit orderings provided by database optimizers.
* The proper syntax for `ORDER BY` includes specifying column names or scalar expressions, with sorting directions (ASC or DESC) defined explicitly【21:6†source】.

#### 4. **OUTER JOINs (Section 17.2)**

* OUTER JOINs preserve rows from one or both tables, even when there are no matching rows in the other table.
* This can be useful when querying tables like `Suppliers` and `Orders` where a supplier might not have any orders, but should still appear in the result set.
*   Syntax:

    ```sql
    SELECT Suppliers.sup_id, sup_name, order_nbr, order_amt
    FROM Suppliers LEFT JOIN Orders
    ON Suppliers.sup_id = Orders.sup_id;
    ```

    Here, all suppliers will appear, even if they have no associated orders【21:9†source】【21:7†source】.

#### 5. **New vs. Old JOIN Syntax (Section 17.3)**

* SQL-92 introduced new JOIN syntax (infix operators), which is more readable and flexible than older notations.
* INNER, LEFT, RIGHT, and FULL JOINs allow developers to handle a variety of query situations that involve combining data from multiple tables.
* Care should be taken with complex queries involving multiple JOINs and subqueries, especially when optimizing performance【21:13†source】【21:12†source】.

#### 6. **Advanced Topics in JOINs**

* **JOINs by Function Calls**:
  * SQL allows performing JOIN operations within function calls, enabling complex transformations and data restructuring, such as flattening legacy flat-file data into a relational format for a data warehouse【21:14†source】.
* **Packing Joins**:
  * A detailed discussion on how joins can be used to compress and optimize data for storage and querying【21:15†source】.

#### Key Takeaways:

* The `SELECT` statement is powerful but complex, involving multiple clauses and operations.
* Understanding execution order and how JOINs, subqueries, and predicates work together is crucial for writing efficient SQL queries.
* OUTER JOINs and correlated subqueries offer advanced ways to handle complex relationships and data dependencies between tables.

This chapter provides an essential overview of the `SELECT` statement's capabilities, with a particular focus on its advanced uses in querying and joining data.



## Chapter 18&#x20;

explores different types of tables and views used in SQL, such as _VIEWs_, _Derived Tables_, _Materialized Tables_, and _Temporary Tables_. Below are the key takeaways:

#### 1. **VIEWs, Derived Tables, and Temporary Tables Overview**

* These are different ways of representing queries in SQL.
* A **VIEW** is a virtual table that stores a query rather than the result of the query.
* Derived tables and temporary tables also represent ways to manipulate data within the scope of a session or query.
* While a VIEW doesn't physically store data, a materialized view stores query results【25:1†source】【25:2†source】.

#### 2. **VIEWs in Queries (Section 18.1)**

* SQL VIEWs act like physical tables when invoked, although whether the database materializes the results or uses a different mechanism depends on the database implementation.
*   The syntax for creating a VIEW:

    ```sql
    CREATE VIEW <table name> [(<column list>)] AS <query expression> [WITH CHECK OPTION]
    ```
* This section covers how VIEWs are utilized in SQL queries, and mentions that they do not exist physically until invoked【25:11†source】.

#### 3. **Updatable and Read-Only VIEWs (Section 18.2)**

* VIEWs can either be updatable or read-only.
* Updatable VIEWs are restricted by several conditions: they must be based on one table, cannot use aggregate functions or GROUP BY, and must have a key to map back to the underlying base table.
* Read-only VIEWs, as the name suggests, are for data retrieval only【25:13†source】【25:13†source】.

#### 4. **Types of VIEWs (Section 18.3)**

* Various types of VIEWs are described:
  * **Single-Table Projection and Restriction**: These are used for security, hiding specific columns or rows.
  * **Calculated Columns**: Create computed values like sums or averages within a view.
  * **Translated Columns**: Replace codes or IDs with more meaningful text.
  * **Grouped VIEWs**: Based on queries using GROUP BY; these are typically read-only.
  * **Unioned VIEWs**: Combine results from multiple queries using UNION【25:9†source】【25:9†source】.

#### 5. **How VIEWs Are Handled in Database Systems (Section 18.4)**

* Database systems manage VIEWs through either materialization (storing the results) or in-line text expansion (expanding the query at runtime).
* Materialized views physically store the results and are often used when sorting or aggregating data. This method can use a lot of storage but ensures better performance for certain operations.
* In-line text expansion integrates the VIEW’s SQL query into the main query dynamically at runtime【25:15†source】【25:16†source】.

#### 6. **Temporary Tables (Section 18.7)**

* Temporary tables allow data to be stored temporarily for a specific session or transaction.
* SQL syntax allows the creation of both GLOBAL and LOCAL temporary tables, where the GLOBAL table is shared across sessions, while the LOCAL table is session-specific【25:8†source】【25:10†source】.

#### 7. **Hints on Using VIEWs and Temporary Tables (Section 18.8)**

* **VIEWs**: Avoid nesting too deeply as this can impact performance. VIEWs are also used for security, enabling the DBA to hide sensitive information.
* **Temporary Tables**: Useful for storing intermediate query results, improving performance, and preventing locking issues with base tables【25:8†source】.

#### 8. **Derived Tables (Section 18.9)**

* Derived tables are similar to VIEWs but exist only within the scope of a single query.
* These tables are constructed dynamically using the `WITH` clause in SQL and can significantly improve query organization and performance when managing complex datasets【25:14†source】【25:17†source】.

In summary, Chapter 18 provides a comprehensive guide to using and managing different types of tables and views in SQL, focusing on their functionality, optimization, and practical applications.



## Chapter 19&#x20;

focused on _Partitioning Data in Queries_. Here are the detailed notes:

#### 1. **Coverings and Partitions (Section 19.1)**

* **Coverings**: A collection of subsets that together make up the entire set, but can overlap.
* **Partitions**: A special type of covering where subsets do not intersect. Partitions are critical in reports and other data aggregations where the total is derived from its parts. For example, a company budget broken down into departments and divisions.
* Example: Creating tables for ZIP code ranges using a `CREATE TABLE` statement, and querying them using predicates like `BETWEEN` to find ranges【29:2†source】【29:9†source】.

#### 2. **Partitioning by Ranges (Section 19.1.1)**

* One common issue is classifying data by numeric or alphabetic ranges. This can be handled using tables with high and low values that define ranges.
* Example: A ZIP code range table could map ZIP codes to their corresponding state. This partitioning allows grouping and validating data across defined ranges.
* The chapter also advises caution when using predicates like `BETWEEN`, which can produce incorrect results if there are overlaps or `NULL` values in the range【29:2†source】.

#### 3. **Partitioning by Functions (Section 19.1.2)**

* Functions can also be used to partition data. For instance, a function might divide names by phonetic similarity (e.g., Soundex) or scientific calculations that SQL alone cannot handle efficiently.
* The chapter explains that in some cases, the function needs to be external to SQL, requiring integration with other programming languages or computational engines to return results to the SQL system【29:15†source】【29:16†source】.

#### 4. **Partitioning by Sequences (Section 19.1.3)**

* Partitioning by sequences involves grouping data that follows a sequential order, such as a time series. This is useful when analyzing historical data or looking for patterns (e.g., grouping consecutive on-time and late payments).
* The book provides a query example for assigning grouping numbers to runs of consecutive records based on a sequence of payments that were made on time or late【29:16†source】.

#### 5. **Relational Division (Section 19.2)**

* **Relational division** is a key operation in relational algebra, involving dividing one table by another to produce a result that shows which items (e.g., people, entities) possess all the required attributes.
* Example: A table of pilots and the planes they can fly is divided by a table of planes in a hangar to find which pilots can fly all the planes in the hangar.
* The chapter covers two types of relational division: **division with a remainder** (where extra values are allowed in the dividend) and **exact division** (where there must be an exact match between the dividend and divisor)【29:8†source】【29:18†source】.

#### 6. **Division with a Remainder (Section 19.2.1)**

* This allows for more values in the dividend than in the divisor. In SQL terms, this can be expressed using `NOT EXISTS` predicates to find which items are present in both sets, even if there are extras in the dividend【29:18†source】.

#### 7. **Exact Division (Section 19.2.2)**

* Requires the exact number of values in the dividend to match the divisor, which can be expressed in SQL using `LEFT OUTER JOIN` combined with `HAVING` clauses to compare the counts of matching rows【29:13†source】【29:18†source】.

#### 8. **Performance Considerations (Section 19.2.3)**

* The chapter discusses the performance of different approaches to relational division. Using `EXISTS()` versus `COUNT()` can have performance implications depending on the optimizer. It also suggests that certain queries can be made more efficient by avoiding deeply nested subqueries【29:13†source】.

#### 9. **Todd’s Division (Section 19.2.4)**

* This is a more complex form of relational division that uses `JOINs` and `COUNT()` to handle situations where matching needs to be precise across multiple tables and attributes.
* An example is provided with multiple suppliers and job parts to find exact matches using SQL joins and divisions【29:6†source】【29:4†source】.

#### 10. **FIFO and LIFO Subsets (Section 19.5)**

* Discusses how SQL can be used to handle inventory tracking using **FIFO** (First In, First Out) and **LIFO** (Last In, First Out) principles.
* These approaches are useful in inventory systems to calculate the value of items sold based on the order in which they were received into inventory【29:17†source】.

#### Key Takeaways:

* SQL provides several ways to partition data, ranging from simple range-based partitioning to more complex relational divisions.
* Relational division is a powerful tool in SQL, but performance considerations must be kept in mind.
* Advanced topics like Todd’s division and FIFO/LIFO inventory tracking show how SQL can handle complex business logic efficiently.

This chapter provides an in-depth look at how to segment data and use relational algebra in SQL for querying and reporting【29:18†source】【29:12†source】【29:19†source】.





Chapter 20 of _SQL for Smarties_ covers **Grouping Operations** in SQL, focusing on how data can be aggregated into groups for analysis. Below are the key details:

#### 1. **The GROUP BY Clause (Section 20.1)**

* The `GROUP BY` clause is fundamental for partitioning data into subsets based on column values, enabling aggregation functions like `SUM()`, `COUNT()`, and `AVG()` to work on these subsets.
* The result of a `GROUP BY` operation is a **grouped table**, where rows with the same values for specified columns are grouped into one row【17:0†source】【17:7†source】.
* **Handling NULLs**: In SQL, `NULL` values are grouped together as one group, similar to any other value.
* Best practices include ensuring that the columns listed in `GROUP BY` are aligned with those in the `SELECT` list for readability【17:7†source】.

#### 2. **GROUP BY and HAVING Clause (Section 20.2)**

* The `HAVING` clause functions like a `WHERE` clause but applies to groups rather than individual rows. It's used to filter out groups that do not satisfy certain conditions, typically aggregate expressions.
*   Example:

    ```sql
    SELECT department, SUM(salary)
    FROM Employees
    GROUP BY department
    HAVING SUM(salary) > 100000;
    ```
* The clause focuses on **group characteristics**, e.g., filtering out departments with a total salary below a certain threshold【17:5†source】.

#### 3. **Multiple Aggregation Levels (Section 20.3)**

* This section discusses how data can be grouped at multiple levels in hierarchical reports. For instance, sales data can be grouped by salesperson, then by district, and then by region.
* Techniques like **grouped VIEWs** and **subquery expressions** are introduced to create multi-level aggregations【17:5†source】.
* **CASE expressions** are also used for more complex grouping scenarios. These allow conditional aggregation, enabling dynamic group assignments based on conditions【17:5†source】【17:8†source】.

#### 4. **Grouping on Computed Columns (Section 20.4)**

* SQL-99 introduced the ability to group by computed columns, such as extracting parts of dates or performing arithmetic operations on column values.
*   Example:

    ```sql
    SELECT EXTRACT(MONTH FROM sale_date) AS sale_month, SUM(sales_amount)
    FROM Sales
    GROUP BY sale_month;
    ```
* Before this standard, SQL did not allow grouping on computed values unless done via subqueries【17:5†source】.

#### 5. **Grouping into Pairs (Section 20.5)**

* This concept covers more specialized grouping, such as pairing individuals from two different categories (e.g., men and women) for events like a dinner party.
* Solutions for creating pairs include using **FULL OUTER JOINs** and calculating ranks based on specific conditions like gender【17:6†source】.

#### 6. **Sorting and GROUP BY (Section 20.6)**

* Although `GROUP BY` does not require sorting, many SQL implementations automatically sort results by the grouped columns.
* Sorting within grouped data is also possible by using an `ORDER BY` clause to arrange the final output based on aggregate functions or calculated columns【17:6†source】.
* The distinction between **stable** and **non-stable sorts** is explained, where stable sorts preserve the original order of rows with equal values, while non-stable sorts do not【17:6†source】.

#### Key Takeaways:

* `GROUP BY` and `HAVING` are essential tools for summarizing and analyzing large datasets in SQL.
* SQL-99's support for computed column grouping adds flexibility to data analysis.
* Techniques like multi-level aggregation and grouping into pairs expand the analytical capabilities of SQL beyond simple data partitioning.

Chapter 20 emphasizes advanced grouping techniques, providing SQL users with the tools to efficiently handle complex datasets and hierarchical data reporting【17:0†source】【17:5†source】【17:6†source】.



## Chapter 21&#x20;

dedicated to **Aggregate Functions**, covering various types of aggregation operations used to summarize data in SQL. Below are the detailed notes:

#### 1. **Overview of Aggregate Functions**

* Aggregate functions in SQL provide a way to perform statistical summaries such as `COUNT()`, `SUM()`, `AVG()`, `MIN()`, `MAX()`, and others.
* These functions are called **set functions** in the SQL standard but are more commonly referred to as aggregate functions.
* The basic principle is to first create a working column from the result set and remove `NULLs`. Functions like `DISTINCT` remove redundant duplicates, while `ALL` retains all rows .

#### 2. **COUNT() Functions (Section 21.1)**

* The `COUNT()` function has two forms:
  * **COUNT(\*)**: Counts the total number of rows, including `NULLs`. It is the only function that works with the `*` symbol and counts all rows without considering the content.
  * **COUNT(expression)**: Counts only non-`NULL` values in a column or expression.
*   Example:

    ```sql
    SELECT COUNT(*) FROM Employees;
    SELECT COUNT(salary) FROM Employees WHERE department = 'HR';
    ```

#### 3. **SUM() Functions (Section 21.2)**

* `SUM()` totals the values in a numeric column, ignoring `NULLs`.
* It can also use `DISTINCT` to sum only distinct values, which avoids double-counting.
*   Example:

    ```sql
    SELECT SUM(salary) FROM Employees WHERE department = 'Sales';
    ```

#### 4. **AVG() Functions (Section 21.3)**

* **AVG()** computes the average of numeric values, and `NULLs` are ignored in the calculation.
* There is a distinction between `AVG(DISTINCT x)` and `AVG(x)`, as `DISTINCT` removes duplicate values before averaging.
* **Averages with Empty Groups**: The chapter discusses how to handle cases where averages are computed for groups with no data, such as when trying to find the average population of a sample with missing values .
* **Averages across Columns**: SQL can use `COALESCE()` to handle multiple columns when calculating averages, ensuring `NULL` values are treated properly .

#### 5. **Extrema Functions (Section 21.4)**

* **MIN() and MAX()**: These extrema functions return the smallest and largest values, respectively, in a dataset.
* **Generalized Extrema**: SQL can be used to find more complex extrema values across multiple criteria by using `GROUP BY` with sorting logic .
*   Example:

    ```sql
    SELECT MIN(salary), MAX(salary) FROM Employees;
    ```

#### 6. **LIST() Aggregate Function (Section 21.5)**

* The **LIST()** function is used to concatenate values from multiple rows into a single string. This is often done with a `GROUP_CONCAT()` or similar functions in certain databases.
* It can also be implemented using procedures or crosstabs for specific datasets .

#### 7. **PRD() Aggregate Function (Section 21.6)**

* **PRD()** calculates the product of a set of values, much like `SUM()` totals values. It is particularly useful in financial calculations, such as calculating compounded returns over time.
* It can be implemented using logarithmic functions for more complex datasets .

#### 8. **Bitwise Aggregate Functions (Section 21.7)**

* Bitwise functions such as **bitwise OR** and **bitwise AND** can be used to perform operations on binary data.
* These are not commonly supported by all SQL databases but can be used in specialized cases where bit-level operations are required .

#### Key Takeaways:

* Aggregate functions are essential for summarizing and analyzing data in SQL, with many variations available for specific needs.
* Proper handling of `NULL` values and using `DISTINCT` when necessary can affect the outcomes of aggregation operations.
* Functions like `PRD()` and `LIST()` are more advanced and may require additional logic to implement in SQL.

This chapter provides a comprehensive look at the various types of aggregate functions and their practical uses in SQL queries .



## Chapter 22 _Auxiliary Tables_&#x20;

discusses the use of auxiliary tables in SQL to handle computations and lookup functions that SQL alone cannot efficiently perform. Here's a detailed breakdown:

#### 1. **Introduction to Auxiliary Tables (Section 22.1)**

* Auxiliary tables are static, lookup-style tables constructed from external data sources. They do not require dynamic constraint checks and are often used in conjunction with queries via joins rather than computations.
* These tables serve as a way to manage computations that would be difficult or impossible with SQL's limited computational power.

#### 2. **The Sequence Table (Section 22.1.1)**

* The **Sequence Table** is a simple list of integers used to replace looping constructs in procedural languages. Rather than incrementing a counter, SQL can work with a full set of values.
*   A general declaration example:

    ```sql
    CREATE TABLE Sequence (
      seq INTEGER NOT NULL PRIMARY KEY CHECK (seq > 0),
      CHECK ((SELECT COUNT(*) FROM Sequence) = (SELECT MAX(seq) FROM Sequence))
    );
    ```
* Sequence tables are useful for row numbering, row selection, and performing operations that require iteration【41:5†source】【41:10†source】.

#### 3. **Enumerating a List (Section 22.1.1)**

* This section discusses how to use the sequence table to generate a list of values or map numbers to sequences and cycles. This is handy for operations like creating reports or paginated data where row numbers are needed【41:15†source】.

#### 4. **Replacing Iterative Loops (Section 22.1.3)**

* SQL can use sequence tables to replace loops that would otherwise be implemented in procedural code. This allows operations to be expressed as set operations, aligning with SQL’s set-based nature【41:12†source】.

#### 5. **Lookup Auxiliary Tables (Section 22.2)**

* **Lookup auxiliary tables** are tables used to store static data like country codes, error messages, or language translations.
* Examples include simple lookup tables with two columns (value and translation) and more complex tables that provide translations for multiple parameters.
*   Example:

    ```sql
    CREATE TABLE CountryCodes (
      country_code CHAR(2) NOT NULL PRIMARY KEY,
      country_name VARCHAR(20) NOT NULL
    );
    ```
* Lookup tables are particularly useful when different users need to view data in their preferred language, or where a translation is needed for codes【41:12†source】【41:2†source】【41:11†source】.

#### 6. **Range Auxiliary Tables (Section 22.2.4)**

* These tables map values to ranges, like grade score ranges or reporting periods.
*   Example:

    ```sql
    CREATE TABLE ReportPeriods (
      period_name CHAR(15) NOT NULL, 
      start_date DATE NOT NULL, 
      end_date DATE NOT NULL, 
      CHECK(start_date < end_date),
      PRIMARY KEY (start_date, end_date)
    );
    ```
* Ranges are often searched using the `BETWEEN` predicate. A `NULL` value can serve as a marker for an open-ended range【41:1†source】.

#### 7. **Hierarchical Auxiliary Tables (Section 22.2.5)**

* Hierarchical tables organize data into nested categories. A common example is the Dewey Decimal Classification system used in libraries.
* These tables allow for searches within nested ranges and help organize data into distinct hierarchies.
*   Example:

    ```sql
    CREATE TABLE DeweyDecimalClassification (
      category_name CHAR(35) NOT NULL, 
      low_dewey INTEGER NOT NULL, 
      high_dewey INTEGER NOT NULL, 
      CHECK (low_dewey <= high_dewey), 
      PRIMARY KEY (low_dewey, high_dewey)
    );
    ```
* A search on a specific Dewey number returns all relevant categories within the hierarchy【41:1†source】【41:3†source】.

#### 8. **Auxiliary Function Tables (Section 22.3)**

* These tables are used to store results of functions like financial calculations, including **Net Present Value (NPV)** and **Internal Rate of Return (IRR)**, which can be difficult to calculate using SQL alone.
*   Example for NPV function:

    ```sql
    CREATE TABLE CashFlows (
      project_id CHAR(15) NOT NULL, 
      time_period INTEGER NOT NULL CHECK (time_period >= 0), 
      amount DECIMAL(12,4) NOT NULL, 
      PRIMARY KEY (project_id, time_period)
    );
    ```
* Auxiliary function tables allow consistent calculation across all projects and support large datasets more efficiently than spreadsheets【41:12†source】【41:17†source】.

#### 9. **Global Constants Tables (Section 22.4)**

* These tables store global constants, which can be reused across different functions and tables. They provide a centralized repository for constant values used across various calculations or queries【41:0†source】.

#### Key Takeaways:

* **Auxiliary tables** offer a method to offload complex computations from SQL to precomputed tables, improving performance and efficiency.
* They are especially useful for **lookup operations**, **hierarchical data**, **range queries**, and **financial computations**.
* Proper use of these tables helps SQL developers avoid complicated procedural logic and enables them to work with SQL's set-based nature.

Chapter 22 emphasizes that while SQL is not a computational language, using auxiliary tables allows SQL to handle a wide range of operations effectively【41:0†source】【41:12†source】【41:17†source】.



## Chapter 23 **Statistics in SQL**,&#x20;



exploring various statistical operations that can be performed within SQL. Here are detailed notes from this chapter:

#### 1. **Introduction**

* SQL is not primarily designed for statistical analysis but can handle basic descriptive statistics. Many SQL products also include functions for calculating median, mode, variance, and standard deviation.
* For complex statistical analysis, SQL data is typically extracted and processed using statistical software like SAS or SPSS .

#### 2. **Mode (Section 23.1)**

* The **mode** is the most frequently occurring value in a dataset.
*   SQL does not usually provide a built-in function for mode, but it can be computed using a query with `GROUP BY` and `HAVING`:

    ```sql
    SELECT salary, COUNT(*) AS frequency
    FROM Payroll
    GROUP BY salary
    HAVING COUNT(*) >= ALL (SELECT COUNT(*) FROM Payroll GROUP BY salary);
    ```
* This method handles bimodal and multimodal distributions.

#### 3. **Average (AVG()) Function (Section 23.2)**

* The `AVG()` function calculates the arithmetic mean of a set of values.
*   SQL handles this with simple syntax:

    ```sql
    SELECT AVG(salary) FROM Payroll;
    ```
* `AVG()` is straightforward but does not address skewed distributions or extreme outliers.

#### 4. **Median (Section 23.3)**

* The **median** is a measure of central tendency. Various methods to calculate it in SQL are discussed:
  * **Date’s First Median**: Based on sorting values and selecting the middle value.
  * **Celko’s First Median**: Uses set theory to identify the median.
  * **Murchison’s Median**: A version of the median that handles specific SQL constraints .

#### 5. **Variance and Standard Deviation (Section 23.4)**

* **Variance** measures how far values deviate from the mean, while **Standard Deviation** is its square root.
*   SQL does not natively handle the square root, making it challenging to compute standard deviation:

    ```sql
    SELECT ((COUNT(*) * SUM(x*x)) - (SUM(x) * SUM(x)))
    /(COUNT(*) * (COUNT(*)-1)) AS variance
    FROM Samples;
    ```

#### 6. **Average Deviation (Section 23.5)**

*   **Average Deviation** can be computed using the `ABS()` function to find how far data points deviate from the mean:

    ```sql
    SELECT SUM(ABS(x - :average)) / COUNT(x) AS AverDeviation
    FROM Samples;
    ```

#### 7. **Cumulative Statistics (Section 23.6)**

* **Running Totals**: Keep track of cumulative sums over time, typically for financial reports.
*   Example for a bank account:

    ```sql
    SELECT B0.transaction, B0.trans_date, SUM(B1.amount) AS balance
    FROM BankAccount AS B0, BankAccount AS B1
    WHERE B1.trans_date <= B0.trans_date
    GROUP BY B0.transaction, B0.trans_date;
    ```
* **Running Differences**: Track the difference between successive values, useful for tracking changes like stock prices or quantities over time .
*   **Cumulative Percentages**: Show what percentage of a total a subset represents. Example with sales data:

    ```sql
    SELECT S0.salesman, S0.client_name, S0.sales_amt,
          ((S0.sales_amt * 100)/ ST.salesman_total) AS percent_of_total
    FROM Sales AS S0;
    ```

#### 8. **Rankings and Quintiles (Section 23.6.5)**

* **Rankings**: Compute rankings for a set of values, assigning ordinal numbers to data points.
* Quintiles divide data into five equal groups, which can be expanded to percentiles or other partitionings. SQL queries compute rank and classify data into these partitions.

#### 9. **Crosstabulations (Section 23.7)**

* **Crosstabs** are common statistical reports that summarize the relationships between two variables. While SQL does not have built-in crosstab functions, it can be done using `CASE` expressions or `JOIN` operations .

#### 10. **Harmonic Mean and Geometric Mean (Section 23.8)**

*   **Harmonic Mean** is suitable for rates and can be computed as:

    ```sql
    SELECT COUNT(*)/SUM(1.0/x) AS harmonic_mean FROM Foobar;
    ```
*   **Geometric Mean** is more appropriate for analyzing changes over time, computed by:

    ```sql
    SELECT EXP(AVG(LOG(nbr))) AS geometric_mean FROM NumberTable;
    ```

#### 11. **Multivariable Descriptive Statistics (Section 23.9)**

*   **Covariance** measures how two variables move together. SQL can compute covariance with:

    ```sql
    SELECT ((1.0/n) * SUM((x - xbar)*(y - ybar))) AS covariance
    FROM Samples;
    ```
*   **Pearson’s r**: A measure of linear correlation between two variables, computed with:

    ```sql
    SELECT (SUM(x - AVG(x))*(y - AVG(y))) / SQRT(SUM((x - AVG(x))^2) * SUM((y - AVG(y))^2)) AS pearson_r
    FROM Samples;
    ```

This chapter provides a thorough guide to using SQL for basic statistical operations, with more complex operations needing statistical tools .



## Chapter 24 of _SQL_ "Regions, Runs, Gaps, Sequences, and Series":

Here’s a detailed breakdown of Chapter 24 from _SQL for Smarties_ by Joe Celko, which focuses on "Regions, Runs, Gaps, Sequences, and Series." This chapter provides techniques for dealing with ordered data in SQL—important concepts for your exam.

#### Key Concepts in Chapter 24:

**1. Finding Subregions of Size (n)**

* **Definition**: A subregion is a contiguous part of a dataset, such as a group of consecutive values or identifiers.
* **Purpose**: You might need to locate specific-sized groups (like `n` consecutive rows) in a larger dataset.
* **Example**: If you have a sequence of seat numbers in a theater, you can query for groups of three available seats together.
* **Techniques**: Use window functions or self-joins to extract subregions.

**2. Numbering Regions**

* **Definition**: Assigning a group number to rows that belong to the same "region" or sequence.
* **Purpose**: Helps in labeling or segmenting ordered data, like marking payment records that occurred consecutively.
* **Example**: Grouping records based on whether payments were made on time or delayed.
* **SQL Tip**: You can create unique numbering for regions using `ROW_NUMBER()` or similar window functions and conditional logic.

**3. Finding Regions of Maximum Size**

* **Definition**: Locating the largest set of consecutive records that satisfy a condition.
* **Purpose**: Useful for finding the longest stretch where something holds true, like the longest sequence of available seats.
* **Example**: Identify the largest sequence of consecutive available seats in a theater booking system.
* **SQL Approach**: Use subqueries with `MAX` or recursive queries to find uninterrupted sequences.

**4. Bound Queries**

* **Definition**: Queries that find a sequence or region between two specific values or points.
* **Purpose**: These queries help track data changes over specific intervals, like price changes between two dates.
* **Example**: You can query the change in stock prices between two time periods.
* **Key Operators**: Use BETWEEN or inequality operators to define bounds.

**5. Run and Sequence Queries**

* **Definition**: A run is a sequence that might have gaps, but where a general order or pattern is still maintained.
* **Purpose**: To group and analyze data where values are not perfectly consecutive but still form meaningful sequences.
* **Example**: Track sales trends even if there are missing data points (e.g., gaps in daily sales records).
* **Techniques**: Use `ROW_NUMBER()` with `PARTITION BY` and `ORDER BY` clauses to generate sequence numbers even in imperfect sequences.

**5.1 Filling in Sequence Numbers**

* **Purpose**: Fix sequences by filling in missing numbers.
* **Example**: If you have missing invoice numbers, fill in the gaps in the sequence.
* **SQL Techniques**: Use `MIN()`, `MAX()`, and set operations to detect and generate missing numbers.

**6. Summation of a Series**

* **Definition**: Calculating a running total or sum of values in a series.
* **Purpose**: Important for tasks like calculating cumulative totals.
* **Example**: Finding a running total of sales over a period.
* **SQL Functions**: Use aggregate functions like `SUM()` with window functions to calculate cumulative sums.

**7. Swapping and Sliding Values in a List**

* **Definition**: Techniques to move values within an ordered list, such as swapping positions of two elements or shifting all values by one position.
* **Purpose**: Useful for scenarios where you need to reorder or modify an ordered dataset.
* **Example**: Swap the positions of two values in a ranking list, or slide all values down by one row.
* **SQL Operators**: `UPDATE` with self-joins to adjust positions.

**8. Condensing a List of Numbers**

* **Definition**: Taking a list of numbers and condensing them into ranges.
* **Purpose**: Instead of showing each number individually, display contiguous ranges of numbers.
* **Example**: If a customer ordered items in a sequence of IDs (101, 102, 103, 110, 111), condense it into two ranges: 101-103 and 110-111.
* **SQL Queries**: Use `GROUP BY` and `MIN()`/`MAX()` to identify ranges.

**9. Folding a List of Numbers**

* **Definition**: Folding is taking a one-dimensional list of numbers and turning it into rows and columns.
* **Purpose**: Useful for displaying data in a matrix-like format rather than a single column.
* **Example**: Convert a list of numbers into a table with multiple columns for easier comparison.
* **SQL Techniques**: Use the `MOD()` function to divide the list into different rows and columns.

**10. Coverings**

* **Definition**: A covering is a set of intervals or ranges that "cover" all possible values in a dataset.
* **Purpose**: Find the minimal set of intervals that encompasses all given data points.
* **Example**: Identify the smallest number of date ranges that include all stock trading days in a month.
* **SQL Approach**: Use `UNION` or recursive queries to merge overlapping intervals.

***

#### Key SQL Techniques Covered:

* **Window Functions**: These play a big role in the chapter for numbering sequences, calculating running totals, and creating partitions.
* **Set Operations**: These are used to identify missing sequence numbers and manage ranges.
* **Aggregate Functions**: `SUM()`, `MAX()`, `MIN()` are often used to condense, sum, and manage lists and sequences.
* **Subqueries**: Critical for finding regions of maximum size and handling sequence data efficiently.

This chapter is essential for understanding how to work with sequential data in SQL, especially when dealing with gaps, series, or ordered datasets. For your exam, focus on how to:

1. Identify and work with sequences.
2. Handle gaps in data.
3. Create SQL queries that manipulate and group ordered data, like runs, regions, and ranges.



Here are the detailed notes on Chapter 25 of _SQL for Smarties_ by Joe Celko, titled "Arrays in SQL."

#### Chapter 25: Arrays in SQL

This chapter explores the use of arrays in SQL and highlights that arrays are not directly supported by SQL-92 but were later introduced in SQL-99. Arrays can violate relational database principles, especially the First Normal Form (1NF), which requires no repeating groups or nonscalar data in tables. Despite these challenges, Celko outlines several methods to simulate arrays in SQL.

**25.1 Arrays via Named Columns**

* **Explanation**: Arrays are a common feature in many programming languages, allowing data of the same type to be indexed using subscripts.
*   **SQL Approach**: Since arrays are not directly supported, one way to mimic arrays is by creating individual columns for each array element. For example:

    ```sql
    CREATE TABLE Foobar1 (
        element1 INTEGER NOT NULL,
        element2 INTEGER NOT NULL,
        element3 INTEGER NOT NULL,
        element4 INTEGER NOT NULL,
        element5 INTEGER NOT NULL
    );
    ```

    * **Drawbacks**: This approach is limited because you cannot iterate over elements with a subscript. Each element must be accessed explicitly by name.

**25.2 Arrays via Subscript Columns**

*   **Concept**: Arrays can also be modeled using subscript columns. Each subscript becomes a separate row in a table. For example, a 1-dimensional array of five elements can be represented as:

    ```sql
    CREATE TABLE Foobar (
        i INTEGER NOT NULL PRIMARY KEY CHECK(i BETWEEN 1 AND 5),
        element REAL NOT NULL
    );
    ```

    * **Advantages**: This method is closer to how arrays work in programming, allowing each subscript (or dimension) to be a row in the table. It also supports multidimensional arrays by adding more columns for each dimension.
    * **Example**: A 3-dimensional array can be created as:

    ```sql
    CREATE TABLE ThreeD (
        i INTEGER NOT NULL CHECK(i BETWEEN 1 AND 3),
        j INTEGER NOT NULL CHECK(j BETWEEN 1 AND 4),
        k INTEGER NOT NULL CHECK(k BETWEEN 1 AND 5),
        element REAL NOT NULL,
        PRIMARY KEY (i, j, k)
    );
    ```

**25.3 Matrix Operations in SQL**

*   **Matrix Equality**: Two matrices are equal if their corresponding elements are the same. Celko provides a query for comparing two matrices by counting the total elements and comparing them element-wise:

    ```sql
    SELECT COUNT(*) FROM MatrixA
    UNION 
    SELECT COUNT(*) FROM MatrixB
    UNION 
    SELECT COUNT(*)
      FROM MatrixA AS A, MatrixB AS B
     WHERE A.i = B.i AND A.j = B.j AND A.element = B.element;
    ```

    * **Matrix Addition**: SQL can also handle matrix addition where corresponding elements are summed:

    ```sql
    SELECT A.i, A.j, (A.element + B.element) AS total
      FROM MatrixA AS A, MatrixB AS B
     WHERE A.i = B.i AND A.j = B.j;
    ```

    * **Matrix Multiplication**: Multiplication of matrices can be performed by summing the products of corresponding elements. A view can be created to store the result of matrix multiplication:

    ```sql
    CREATE VIEW MatrixC(i, j, element) AS
    SELECT i, j, SUM(MatrixA.element * MatrixB.element)
      FROM MatrixA, MatrixB
     WHERE MatrixA.k = MatrixB.k
     GROUP BY i, j;
    ```

**25.4 Flattening a Table into an Array**

* **Use Case**: Sometimes data needs to be presented as an array, like in reports or data warehouses. This can be achieved by flattening a table into an array format where columns represent different time periods or attributes.
*   **Example**: To create a report of time worked by a driver over a series of weeks:

    ```sql
    CREATE TEMPORARY TABLE TimeReportWork (
        driver_name CHAR(25) NOT NULL,
        wk1 INTEGER, wk2 INTEGER, wk3 INTEGER, wk4 INTEGER, wk5 INTEGER, wk6 INTEGER
    );

    INSERT INTO TimeReportWork (driver_name, wk1, wk2, wk3, wk4, wk5, wk6)
    SELECT driver_name, 
           SUM(CASE WHEN week_nbr = 1 THEN work_hrs ELSE 0 END) AS wk1,
           SUM(CASE WHEN week_nbr = 2 THEN work_hrs ELSE 0 END) AS wk2,
           ...
      FROM Timecards
     WHERE week_nbr BETWEEN 1 AND 6;
    ```

**25.5 Comparing Arrays in Table Format**

* **Comparing Arrays**: When arrays are represented as rows in a table, you might need to compare them. Celko gives an example with employees and their dependents, comparing arrays (sets of dependents) based on birth order.
*   **Example**: Find employees whose children are named in the same order:

    ```sql
    CREATE VIEW Familysize (emp_id, tally) AS 
    SELECT emp_id, COUNT(*) FROM Dependents GROUP BY emp_id;

    SELECT A.emp_id, B.emp_id
      FROM Dependents AS A, Dependents AS B
     WHERE A.emp_id < B.emp_id AND A.kid = B.kid
       AND A.birthorder = B.birthorder;
    ```

#### Summary

Chapter 25 of _SQL for Smarties_ focuses on simulating arrays in SQL, an important concept in database design. While SQL-92 does not natively support arrays, Celko demonstrates how to work around this limitation using named columns, subscript columns, and matrix operations. He also discusses flattening tables into array-like structures and comparing arrays represented in table format.

These techniques are critical for handling structured, multidimensional data within SQL systems, especially for applications involving reporting, data warehousing, and mathematical operations.



Here are the detailed notes for Chapter 26 of _SQL for Smarties_ by Joe Celko, which focuses on "Set Operations":

## Chapter 26: Set Operations

This chapter covers set operations, which allow manipulation and comparison of data sets (tables in SQL). These operations—union, intersection, and set difference—are crucial for managing multisets (SQL tables with duplicates). Here's a breakdown:

**26.1 UNION and UNION ALL**

* **Definition**:
  * **UNION** combines two tables, returning all distinct rows from both tables.
  * **UNION ALL** combines tables but includes all duplicates.
* **Conditions**:
  * The tables involved must be **union-compatible**, meaning they must have the same number of columns with matching data types.
  * The **UNION** operator removes duplicates, while **UNION ALL** preserves them.
*   **Example**:

    ```sql
    SELECT a1 FROM S1
    UNION
    SELECT a2 FROM S2;
    ```

    This query combines two tables S1 and S2 and removes duplicate rows.

**26.2 INTERSECT and EXCEPT**

* **INTERSECT**:
  * Returns only rows that are present in both tables.
  * Removes duplicates unless **INTERSECT ALL** is used, which includes all matching duplicates.
* **EXCEPT**:
  * Returns rows from the first table that do not exist in the second.
  * Removes duplicates unless **EXCEPT ALL** is used, which includes all rows from the first table that do not exist in the second, even duplicates.
* **Examples**:
  *   **INTERSECT**:

      ```sql
      SELECT * FROM S1
      INTERSECT
      SELECT * FROM S2;
      ```
  *   **EXCEPT**:

      ```sql
      SELECT * FROM S1
      EXCEPT
      SELECT * FROM S2;
      ```
  * These operations simplify complex set comparisons, such as determining shared data across tables or eliminating differences between sets.

**26.2.1 Handling NULLs in INTERSECT and EXCEPT**

* SQL handles NULLs in set operations with special care. NULLs are treated as unknown values, which complicates matching.
*   **Example**:

    ```sql
    SELECT * 
    FROM S1 
    WHERE EXISTS (SELECT * FROM S2 WHERE S1.a1 = S2.a2);
    ```

    This query performs an INTERSECT-like operation but handles NULLs more explicitly.

**26.3 A Note on ALL and SELECT DISTINCT**

* The **ALL** and **DISTINCT** keywords control whether duplicates are preserved or removed:
  * **ALL** keeps duplicates.
  * **DISTINCT** removes duplicates from a result set.
*   **Example**:

    ```sql
    SELECT DISTINCT col1 FROM Table;
    ```

    This removes duplicate values in `col1`.

**26.4 Equality and Proper Subsets**

* Set **equality** checks whether two tables have the same rows, while **subset** operations check whether one table's rows are a subset of another.
*   **Example** for checking subset containment:

    ```sql
    SELECT SUM(DISTINCT match_col)
    FROM (SELECT CASE WHEN S1.col IN (SELECT S2.col FROM S2) THEN 1 ELSE -1 END
            FROM S1) AS X(match_col)
    HAVING SUM(DISTINCT match_col) = :n;
    ```

    This query checks if one table is a subset of another by summing values based on whether they exist in both tables.

#### Key SQL Techniques Covered:

* **UNION, INTERSECT, EXCEPT**: These are the primary set operators used to combine or compare rows from multiple tables.
* **Handling duplicates**: Use of `UNION ALL` and `EXCEPT ALL` ensures that duplicates are included in the results, while `DISTINCT` removes them.
* **NULL handling**: Special attention must be given to NULL values in set operations to ensure accurate results.

#### Summary

Chapter 26 of _SQL for Smarties_ is crucial for understanding set operations in SQL. These operations allow you to manipulate tables as if they were mathematical sets, performing unions, intersections, and set differences. Mastering these techniques is essential for tasks like merging datasets, comparing different tables, and eliminating or preserving duplicate rows.

For your exam, focus on:

1. Understanding how UNION, INTERSECT, and EXCEPT work.
2. Knowing the difference between `UNION` and `UNION ALL`.
3. Handling NULLs and duplicates properly during set operations.



Here are detailed notes for Chapter 27 of _SQL for Smarties_ by Joe Celko, focusing on "Subsets":

## Chapter 27: Subsets

This chapter dives into techniques to extract specific subsets of data from SQL tables, which can be more complex than simple filtering using the `WHERE` clause.

***

**1. Every nth Item in a Table**

* **Concept**: Selecting every nth record from a dataset (e.g., every third employee in a table).
* **Challenges**: SQL operates on unordered sets, so physical row positions in files are irrelevant. Employee numbers may not be consecutive.
*   **SQL Technique**: Use the `MOD` function:

    ```sql
    SELECT * FROM Employees
    WHERE MOD(employee_id, :n) = 0;
    ```
* This method ensures that every nth employee is selected if IDs are relatively ordered.

**2. Picking Random Rows from a Table**

* **Use Case**: Selecting random samples from a dataset.
* **Approach**: A common way is to use a `RANDOM()` function.
  *   Example:

      ```sql
      SELECT * FROM Employees
      ORDER BY RANDOM()
      LIMIT 10;
      ```
* Alternatively, you can use the `CEIL()` function with row counts to select a random row.

**3. The CONTAINS Operator**

* **Concept**: In set theory, the subset operator checks whether all elements of one set are contained in another set.
* **Issue**: SQL lacks a built-in `CONTAINS` operator.
* **Subset Queries**: You can simulate this using `NOT EXISTS` to find if all values from one subset exist in another.
  *   Example:

      ```sql
      SELECT name FROM Employees
      WHERE NOT EXISTS (SELECT *
                        FROM Projects
                        WHERE project_id NOT IN
                              (SELECT project_id
                               FROM EmployeeProjects
                               WHERE Employees.emp_id = EmployeeProjects.emp_id));
      ```
  * This query finds employees who work on all projects in department 5.

**4. Proper Subset Operators**

* **IN Predicate**: This is used to check membership of an element in a set, similar to checking if a value belongs to a subset.
* **Subset Comparison**: You can also compare whether one table is a proper subset of another using relational division, relational algebra, or `NOT EXISTS` in SQL.

**5. Table Equality**

* **Definition**: Two tables are equal if:
  1. Both have the same number of rows.
  2. All rows from one table exist in the other.
*   **SQL Example**:

    ```sql
    SELECT COUNT(*) FROM TableA
    WHERE NOT EXISTS (SELECT * FROM TableB WHERE TableA.col = TableB.col);
    ```
* This checks if all rows from TableA exist in TableB.

**6. Picking a Representative Subset**

* **Problem**: Selecting a minimal set of rows where each value in two specific columns appears at least once.
*   **SQL Example**:

    ```sql
    SELECT member_id, club_name, ifc
    FROM Memberships
    WHERE member_id IN (SELECT MIN(member_id) FROM Memberships GROUP BY club_name)
    UNION
    SELECT MIN(member_id) FROM Memberships GROUP BY ifc;
    ```
* This ensures that at least one of each distinct value from the `club_name` and `ifc` columns is included in the result.

***

#### Key SQL Techniques Covered:

* **MOD function**: Useful for selecting every nth row.
* **RANDOM() function**: Employed to pick random rows from a table.
* **NOT EXISTS**: Emulates the `CONTAINS` operator for set comparisons.
* **Relational Division**: Applied to find proper subsets and table equality.

#### Summary

Chapter 27 provides advanced techniques for extracting subsets from SQL tables, from simple random selection to complex relational division and table equality checks. For your exam, focus on mastering the use of functions like `MOD()`, `RANDOM()`, and relational division via `NOT EXISTS`. Understanding how to simulate set operations in SQL is crucial for effective data handling.

These techniques are essential for solving problems that require precise control over data subsets in large datasets.



## Chapter 28: Trees and Hierarchies in SQL

In this chapter, Celko addresses methods for managing tree structures and hierarchical data in SQL. Trees are special kinds of directed graphs, consisting of nodes (representing entities) and edges (representing relationships between nodes). Hierarchies are a common requirement in fields like organizational charts, parts explosions (bill of materials), and taxonomies.

**1. Tree Structures**

* **Nodes**: Represent entities in a hierarchy (e.g., employees in an org chart or parts in an assembly).
* **Edges**: Represent one-way relationships between nodes (e.g., "reports to" in an org chart or "is made of" in a bill of materials).
* **Root**: The top of the tree, with no parent.
* **Leaf Nodes**: Nodes with no children.
* **Binary Tree**: A tree where each node has at most two children.

**2. Modeling Trees in SQL**

There are three major approaches to modeling trees and hierarchies in SQL, each with its own strengths and weaknesses:

1. **Adjacency List Model**:
   * Each row records a node and its parent, making it similar to pointer chains in programming.
   *   Example table structure:

       ```sql
       CREATE TABLE AdjTree (
           child CHAR(2) NOT NULL,
           parent CHAR(2),  -- NULL is the root
           PRIMARY KEY (child, parent)
       );
       ```
   * **Queries**:
     * To find the root: Look for nodes with a `NULL` parent.
     * To find leaf nodes: Look for nodes with no children.
   * **Limitations**:
     * Integrity constraints can be difficult to enforce (e.g., ensuring there’s only one root or preventing cycles).
     * Recursive queries are needed for traversal, making it complex and slow for deep hierarchies.
2. **Path Enumeration Model**:
   * Stores the path from the root to each node as a string.
   *   Example table structure:

       ```sql
       CREATE TABLE PathTree (
           node CHAR(2) NOT NULL PRIMARY KEY,
           path VARCHAR(900) NOT NULL
       );
       ```
   * **Queries**:
     * To find a subtree rooted at a particular node, use the `LIKE` operator on the path string.
     * To determine the depth of a node, count the slashes in the path string.
   * **Advantages**: Simple for reading and querying.
   * **Drawbacks**: Updating the tree (e.g., changing the root) requires recalculating all paths, which can be slow.
3. **Nested Set Model**:
   * Nodes are represented by two numbers (`lft` and `rgt`), which define the range of their descendants.
   *   Example table structure:

       ```sql
       CREATE TABLE NestTree (
           node CHAR(2) NOT NULL PRIMARY KEY,
           lft INTEGER NOT NULL UNIQUE CHECK (lft > 0),
           rgt INTEGER NOT NULL UNIQUE CHECK (rgt > lft)
       );
       ```
   * **Queries**:
     * To find all descendants of a node, use a self-join on the `lft` and `rgt` columns.
     * To find the level of a node (i.e., its depth in the tree), count how many nodes enclose it.
   * **Advantages**: Efficient for querying subtrees.
   * **Drawbacks**: Updating the tree (e.g., inserting or deleting nodes) requires renumbering many nodes, which can be slow.

**3. Other Models for Trees and Hierarchies**

There are additional models that can be applied in specific situations, such as:

* **Binary Trees**: Efficient for algorithms like search trees.
* **Specialized Hierarchies**: Some structures, like message boards or historical data in data warehouses, may require different approaches depending on the frequency of node or structure changes.

#### Key Concepts

* **Adjacency List Model**: Best for simple trees or graphs where traversal depth is shallow.
* **Path Enumeration Model**: Useful for static trees where updates are infrequent.
* **Nested Set Model**: Ideal for efficient querying of subtrees but costly for updates.

#### Summary

Chapter 28 is crucial for understanding how to handle hierarchical data in SQL. Celko explains the strengths and weaknesses of the three major tree models—Adjacency List, Path Enumeration, and Nested Set. Each model is suitable for different use cases, depending on the frequency of updates and the complexity of queries. For your exam, focus on understanding how each model works and when to use them effectively in SQL-based hierarchical data management.





## Chapter 29: Temporal Queries

This chapter focuses on handling time-related data in SQL, which is one of the more complex and challenging aspects of database management. Temporal queries are essential for tracking changes over time, performing historical analysis, and managing time-dependent data.

***

**29.1 Temporal Math**

* **Date and Time Arithmetic**: SQL implementations often support basic date arithmetic.
  * Example operations include:
    * Adding or subtracting days from a date.
    * Subtracting two dates to find the number of days between them.
  * **Interval Arithmetic**: You can perform operations with temporal intervals (e.g., adding months or minutes to a timestamp).
  * **Valid Combinations**: Celko describes valid combinations of `datetime` and `interval` types, which are supported in SQL. These include expressions like:
    * `datetime - datetime = interval`
    * `datetime + interval = datetime`

**29.2 Personal Calendars**

* **Managing Personal Calendars**: This section discusses how SQL can handle personal or custom calendars for different users, such as managing individual working schedules or holidays.
* **Custom Functions**: SQL implementations typically include functions like `CURRENT_DATE`, `CURRENT_TIME`, and `CURRENT_TIMESTAMP` for handling time-specific queries.

**29.3 Time Series**

This section focuses on working with time series data, which involves tracking values that change over time, like stock prices, weather data, or sales figures.

1. **Gaps in Time Series**:
   * Identifying missing data in a time series (e.g., days where no data was recorded) and filling those gaps is a common problem.
   * Example Query: Using a calendar table to identify missing dates and filling them in.
2. **Continuous Time Periods**:
   * Queries to find continuous periods where an event occurred, such as the continuous employment of an employee or consecutive sales records.
   *   Example:

       ```sql
       SELECT job_code, start_date, end_date
       FROM Timesheets
       WHERE start_date <= end_date;
       ```
3. **Missing Times in Contiguous Events**:
   * Detecting missing events between otherwise contiguous events (e.g., gaps in employment history).
   * This can be done with simple temporal arithmetic by calculating the differences between the end and start of subsequent events.
4. **Locating Dates**:
   * Searching for specific dates or ranges within temporal data.
   * Example: Queries to find the first or last recorded event within a time frame, such as when an employee was first hired.
5. **Temporal Starting and Ending Points**:
   * Identifying the start and end of events or periods within temporal data (e.g., the first and last sale of a product).
6. **Average Wait Times**:
   * Calculating average time intervals between events (e.g., the average time customers wait between making appointments).

**29.4 Julian Dates**

* **Julian Date System**: SQL can work with Julian dates, which are continuous counts of days from a fixed starting point.
* **Usage**: Julian dates are useful for handling astronomical data or performing date arithmetic across long time spans.

**29.5 Date and Time Extraction Functions**

* **Extracting Temporal Parts**: SQL provides functions to extract specific components of date-time values, such as:
  * `YEAR()`
  * `MONTH()`
  * `DAY()`
  * `HOUR()`, etc.

**29.6 Other Temporal Functions**

* **Additional Temporal Functions**: Functions like `DATEDIFF()` and `DATEADD()` help perform arithmetic with dates and times, allowing for precise temporal queries.

**29.7 Weeks**

* **Handling Weekly Data**:
  * Sorting and filtering data by weeks or weekday names.
  * Special SQL functions can convert dates into week numbers or extract specific weekdays from timestamps.

**29.8 Modeling Time in Tables**

* **Storing Temporal Data**: This section explains different ways to represent time-dependent data in SQL tables.
  1. **Using Duration Pairs**: Representing events with a start time and an end time using pairs of columns (e.g., `start_time` and `end_time`).

**29.9 Calendar Auxiliary Table**

* **Using an Auxiliary Table**: An auxiliary calendar table is a handy method to handle time series queries efficiently. The table can store all dates within a certain range, making it easier to identify gaps, missing data, or continuous time spans.

**29.10 Year 2000 Problems**

* **Y2K Issues**: Discusses common problems with handling dates around the year 2000, including legacy data with two-digit year fields, handling leap years, and incorrect assumptions about date formats.

***

#### Key Takeaways:

* **Temporal Data Handling**: SQL offers a variety of functions and methods for managing and querying temporal data, ranging from simple date arithmetic to complex time series analysis.
* **Time Series Analysis**: Gaps, continuous periods, and missing data can be managed using specific SQL queries, often aided by auxiliary tables like a calendar.
* **Temporal Joins**: Combining data from different time periods, ensuring events overlap or match specific time intervals.
* **Personalized Calendars**: Custom calendars and date management for users or systems can be efficiently handled in SQL.

#### Summary

Chapter 29 is crucial for understanding temporal data management and how SQL can be used to query, analyze, and manipulate data that changes over time. The techniques described, such as time series handling, gap analysis, and date arithmetic, are vital for working with historical data and time-bound queries.



Here are detailed notes for Chapter 30 of _SQL for Smarties_ by Joe Celko, titled "Graphs in SQL":

## Chapter 30: Graphs in SQL

This chapter explores how to model, query, and manipulate graphs using SQL. Graphs are vital for representing complex relationships between data points, such as in network systems, organizational structures, and transportation routes.

***

**30.1 Basic Graph Characteristics**

* **Graphs** are composed of nodes connected by edges.
  * **Nodes**: Represent data points.
  * **Edges**: Connections between nodes, which can be directed (one-way) or undirected (two-way).
  * **Indegree**: The number of edges pointing into a node.
  * **Outdegree**: The number of edges leaving a node.
  * **Path**: A set of connected edges between nodes.
  * **Cycle**: A path that starts and ends at the same node without crossing itself.
* **Adjacency List Model**: The most common way to represent graphs in SQL.
  * This method stores each edge of the graph as a pair of nodes (with possible additional edge attributes).
  *   Example:

      ```sql
      CREATE TABLE AdjacencyListGraph (
        begin_node_id INTEGER NOT NULL REFERENCES Nodes(node_id),
        end_node_id INTEGER NOT NULL REFERENCES Nodes(node_id),
        PRIMARY KEY (begin_node_id, end_node_id),
        CHECK (begin_node_id <> end_node_id)
      );
      ```

**30.1.1 All Nodes in the Graph**

*   To view all nodes in the graph:

    ```sql
    CREATE VIEW GraphNodes (node_id)
    AS
    SELECT DISTINCT node_id FROM AdjacencyListGraph;
    ```

**30.1.2 Path Endpoints**

* Path endpoints are the first and last nodes of a path in a graph.
* Paths can be stored using a nested sets model or using recursive queries.

**30.1.3 Reachable Nodes**

* A node is reachable from another node if there is a path connecting them.
*   Example:

    ```sql
    CREATE VIEW ReachableNodes (begin_node_id, end_node_id)
    AS
    SELECT DISTINCT begin_node_id, end_node_id
    FROM AdjacencyListGraph;
    ```

**30.1.4 Indegree and Outdegree**

* **Indegree**: The number of edges pointing to a node.
  *   Example to compute indegree:

      ```sql
      CREATE VIEW Indegree (node_id, node_indegree)
      AS
      SELECT node_id, COUNT(begin_node_id)
      FROM Edges
      WHERE end_node_id = node_id
      GROUP BY node_id;
      ```
* **Outdegree**: The number of edges leaving a node.
  *   Example to compute outdegree:

      ```sql
      CREATE VIEW Outdegree (node_id, node_outdegree)
      AS
      SELECT node_id, COUNT(end_node_id)
      FROM Edges
      WHERE begin_node_id = node_id
      GROUP BY node_id;
      ```

**30.2 Paths in a Graph**

* **Paths**: Represent a sequence of edges connecting nodes in a graph.
* **Path Length**: The number of edges traversed along a path.
* SQL can represent paths using recursive queries or specialized structures like the adjacency matrix model.
* **Shortest Path**: Finding the shortest path between nodes is a common graph problem.
  * This can be done using recursive queries or algorithms like Dijkstra's algorithm.

**30.3 Acyclic Graphs as Nested Sets**

* **Acyclic Graphs**: Graphs that do not contain cycles.
* Nested set models can be used to represent acyclic graphs by assigning each node a `left` and `right` value to define its place in the hierarchy.
  *   Example structure:

      ```sql
      CREATE TABLE NestedSetsGraph (
        node_id INTEGER NOT NULL PRIMARY KEY,
        lft INTEGER NOT NULL,
        rgt INTEGER NOT NULL,
        CHECK (rgt > lft)
      );
      ```
  * A stack algorithm can be used to convert an adjacency list into a nested set structure.

**30.4 Paths with CTE (Common Table Expressions)**

* **CTE**: A powerful feature for expressing recursive queries, often used to traverse paths in a graph.
*   Example CTE query to find all reachable nodes from a starting node:

    ```sql
    WITH RECURSIVE ReachableNodes (begin_node_id, end_node_id)
    AS (
      SELECT begin_node_id, end_node_id
      FROM Edges
      WHERE begin_node_id = :start_node
      UNION ALL
      SELECT r.end_node_id, e.end_node_id
      FROM ReachableNodes r
      JOIN Edges e ON r.end_node_id = e.begin_node_id
    )
    SELECT end_node_id FROM ReachableNodes;
    ```

**30.5 Adjacency Matrix Model**

* **Adjacency Matrix**: A square matrix used to represent a graph, where rows represent starting nodes and columns represent ending nodes.
  *   Example matrix for a graph:

      ```
      A B C D
      A 1 1 0 0
      B 0 1 1 0
      C 0 0 1 1
      D 0 0 0 1
      ```

**30.6 Points Inside Polygons**

* Although not strictly graph theory, SQL can also handle spatial queries, such as determining whether a point is inside a polygon.
* **Algorithm**: Points inside a polygon can be determined using simple SQL without needing trigonometric functions.
  *   Example structure:

      ```sql
      CREATE TABLE Polygon (
        x FLOAT NOT NULL,
        y FLOAT NOT NULL
      );
      ```
  * A query can determine whether a given point is inside or outside of the polygon by using comparisons on the x and y coordinates of the polygon’s vertices.

***

#### Summary:

Chapter 30 of _SQL for Smarties_ provides detailed methods for handling graphs and graph-like structures in SQL. Key techniques include using adjacency lists, nested sets, recursive queries with CTEs, and adjacency matrices. These concepts are essential for solving complex problems involving networks, paths, and hierarchical relationships in databases.

Here are the detailed notes for Chapter 31 of _SQL for Smarties_ by Joe Celko, titled "OLAP in SQL":

#### Chapter 31: OLAP in SQL

This chapter explores **Online Analytical Processing (OLAP)**, which is designed for summarizing, reporting, and querying large datasets in data warehouses. OLAP focuses on multidimensional analysis rather than the transactional operations typical of Online Transaction Processing (OLTP) systems.

***

**31.1 Star Schema**

* **Star Schema**:
  * A denormalized schema used in data warehouses. A central **fact table** contains all event-related data (e.g., sales), and smaller **dimension tables** allow filtering and grouping (e.g., by time, product, region).
  * Example: The **Sales Fact Table** might include dimensions like year, month, and region, allowing flexible aggregation and reporting across different levels of granularity.

***

**31.2 OLAP Functionality**

SQL’s OLAP functionality is built on features like **window functions**, **ranking**, **grouping** (ROLLUP, CUBE), and **partitioning**. These features allow sophisticated querying for data analysis.

1. **RANK and DENSE\_RANK**:
   * **RANK**: Assigns a rank to each row in a window. Rows with the same value receive the same rank, but gaps are left in the numbering for ties.
   * **DENSE\_RANK**: Similar to RANK but without gaps in the ranking sequence.
   * **Example**: Ranking sales performance across regions while ensuring tied ranks get the same rank.
2. **ROW\_NUMBER**:
   * **ROW\_NUMBER**: Assigns a unique sequential number to each row in a result set, ordered by a specific column.
   * **Example**: Numbering rows within a result set by sales figures in descending order.
3. **GROUPING Operators**:
   * **ROLLUP**: Provides hierarchical aggregates for a series of dimensions, generating subtotals and a grand total.
   * **CUBE**: Extends ROLLUP by computing subtotals across all combinations of dimensions.
   * **GROUPING SETS**: Specifies multiple grouping sets in a query to avoid repeated `UNION` operations.
   * **GROUPING()**: A function to differentiate between NULL values and subtotals generated by OLAP operations.
4. **Window Clause**:
   * Defines a subset of rows (a **window**) for which an aggregate function is applied.
   * A typical window clause consists of **partitioning**, **ordering**, and **frame** specifications.
   * **Example**: Calculating a moving average of sales over a 3-month period.
5. **OLAP SQL Examples**:
   *   Using the **ROLLUP** function to calculate total sales by city and region:

       ```sql
       SELECT B.region_type, S.city_id, SUM(S.sales) AS total_sales
       FROM SalesFacts AS S, MarketLookup AS M
       WHERE EXTRACT (YEAR FROM trans_date) = 1999 
       AND S.city_id = B.city_id
       AND B.region_type = 6
       GROUP BY ROLLUP(B.region_type, S.city_id)
       ORDER BY B.region_type, S.city_id;
       ```
   * This query produces subtotals by city and region, as well as a grand total.
6. **Enterprise-Wide Dimensional Layer**:
   * A data warehouse architecture typically contains a normalized **atomic layer** of granular data, which is used as the source for multidimensional data marts, such as those organized by **Star Schemas** or **MOLAP cubes**.
   * **Materialized Views**: Used to improve query performance, especially in OLAP settings.

***

**31.3 A Bit of History**

* **Historical Context**: OLAP features in SQL were proposed by IBM and Oracle in 1999, and quickly became part of the SQL-99 standard. Other vendors, such as Red Brick, contributed to the development of these features.

***

#### Key Concepts:

* **OLAP vs. OLTP**: OLAP focuses on complex analytical queries with large datasets, often using denormalized schemas like Star Schema, whereas OLTP deals with real-time, transactional data in normalized schemas.
* **Window Functions**: SQL's window functions allow for powerful data analysis by specifying partitions and aggregates across rows without collapsing them.
* **ROLLUP and CUBE**: These OLAP extensions enable advanced reporting by computing hierarchical and cross-tabulated aggregates.

***

#### Summary:

Chapter 31 discusses OLAP features in SQL, focusing on tools that support multidimensional data analysis. These include ranking functions like RANK, window functions for partitioning data, and grouping operators like ROLLUP and CUBE. These functions allow SQL queries to handle complex analytics efficiently, making them integral for data warehousing and business intelligence operations.

For your exam, focus on understanding:

1. How to use ROLLUP and CUBE for hierarchical and cross-tabulated aggregates.
2. Window functions and their role in moving averages and rankings.
3. Differences between OLAP and OLTP, especially regarding schema design and query complexity.



#### Key Points from Chapter 32:

1. **ACID Properties:**
   * **Atomicity:** Ensures that all parts of a transaction are completed or none at all. Either the entire transaction is committed, or it is rolled back in case of errors. An example is inserting rows into a table; if any constraint is violated, the transaction will be rolled back completely​(celkos-sql-for-smarties…).
   * **Consistency:** The database must remain in a consistent state before and after a transaction, meaning all integrity constraints (relational, referential) must hold​(celkos-sql-for-smarties…).
   * **Isolation:** Transactions must be executed in isolation from each other, giving the illusion of serial execution. However, in practice, interleaving is used to simulate this​(celkos-sql-for-smarties…).
   * **Durability:** Once a transaction is committed, the changes must persist, even in the event of system crashes. Techniques like logging and backups ensure durability​(celkos-sql-for-smarties…)​(celkos-sql-for-smarties…).
2. **Concurrency Control:**
   * **Pessimistic Concurrency Control:** Assumes conflicts are likely and uses locks to prevent issues. This can range from table-level locks to row-level or page-level locks. Different locking levels impact performance​(celkos-sql-for-smarties…)​(celkos-sql-for-smarties…).
   * **Optimistic Concurrency Control:** Assumes conflicts are rare, resolving issues after they occur. Snapshot isolation is an example of optimistic control, where a transaction works on its private copy of data​(celkos-sql-for-smarties…)​(celkos-sql-for-smarties…).
3. **Isolation Levels:** SQL supports multiple levels of isolation, each affecting how transactions interact with one another:
   * **Serializable:** Transactions are guaranteed to run as if they were executed serially, preventing all phenomena.
   * **Repeatable Read:** Prevents changes to rows that have been read during the transaction but allows phantom rows.
   * **Read Committed:** Only sees committed rows, which means updates made by other transactions are visible during execution.
   * **Read Uncommitted:** No restrictions, meaning dirty reads, non-repeatable reads, and phantoms can occur​(celkos-sql-for-smarties…)​(celkos-sql-for-smarties…).
4. **Snapshot Isolation:** Snapshot isolation lets transactions work with snapshots of the data from the moment they started. A transaction only commits if no conflicts (such as write-skew) are detected. This method is non-serializable but works effectively for many applications​(celkos-sql-for-smarties…)​(celkos-sql-for-smarties…).
5. **Deadlocks and Livelocks:**
   * **Deadlock:** Occurs when two or more users hold locks on resources that the others need, resulting in a standoff.
   * **Livelock:** A situation where transactions never complete because others continually take priority. Solutions include killing transactions or adjusting priorities​(celkos-sql-for-smarties…)​(celkos-sql-for-smarties…).

This chapter provides a comprehensive look at how SQL handles the complexities of concurrent transactions, ensuring integrity, performance, and stability in multi-user environments.

