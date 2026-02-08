# Database indexing

How to find a row in a  database without an index

* pull a page into ram/memory and search for the row
* pull another page into memory and search for the row
* repeat untill the row is found
* there are some processing which we can do so that it is faster but overall this is a very slow process

How do indexes speed up queries

* First load the index into memory
* use it to find out exactly which page has our row
* then load just that one page
* incoming query >> ram >> DB

Types of index&#x20;

* B-tree
  * we build a b-tree on the index, lets say the index is age then we can divide the page which is to brought into memory and then read through
  * support range queries and sorting
  * widely used
* Hash index&#x20;
  * we have a hash table which has the value of the page in the db
  * not widely used as it does not support range queries and sorting
  * use mostly in the redis where i/o is not that costly
* B trees are best but in cases of geospatial cases we use different types of indexing , here we use specialised indexing like
  * geo-hashing
  * quad trees \[ not used in prod]
  * r-trees \[used in practical prod situations]
* B-tree are not great in cases where we have inverted index like where we want to have search for text in a text&#x20;
  * here we convert the words in the docs and get the hash of those and then search for that hash
  * used in elastic search

We won't be asked to go deep down into these index applications but we can follow these&#x20;

<figure><img src="../../../.gitbook/assets/image (2).png" alt=""><figcaption></figcaption></figure>

