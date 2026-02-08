# API gateways

* thin layer between the micro service and the client, so it routes the request from client based on a constant url to the specific micro service
* handles routing as well as some middleware standard authentication part which was earlier done by all the micro service
* so an api gateway does --
  * validate the request
  * run a middle ware - oauth, rate limiting etc
  * route to the correct service based on the request
  * transform the response
  * transform a request
* ex — kong
* Don't spend too much time on this, this is taken for granted during the microservice part



<figure><img src="../../../.gitbook/assets/image (1).png" alt=""><figcaption></figcaption></figure>
