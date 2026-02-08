# interview ready

System design solution is judged on&#x20;

* **simplicity** — how simple is the solution
* **fedility** — how can it be altered in future
* **cost requirement** — what is the cost requirement for this

For any solution the best solution is to use an existing solution

the code can be deployed at&#x20;

* local machine vs cloud service providers
* server or serverless
  * server will have a dedicated server for them while the serverless will not have a dedicated server for this.
  * for server it is cheaper and faster but is costlier as serverless we don't have to manage the resource but it takes time to scale up.

Where are the pages --

* perform operations&#x20;
  * login
  * register
  * create a new account
* we can store these pages into our server / serverless or on a CDN
  * using CDN to serve static files is obvious as those are same for all the users as users will have a low latency as this content will be served to the users by the nearest server.
  * CDNs are used for the static pages as we don't have to update the values in our backend, but when we have a lot of updation going on then serving them using the CDN is a bad idea as the updation may take time due to location.
* to serve the content using the CDN we have a folder which is being checked by the CDN every 10 second.and gets updated, this updated content is served to the users.
* DNS —&#x20;
