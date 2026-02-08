# Building ml systems for a trillion trilion floating point operations

All the ml hype is doing trillion trilion floating point operations

ML systems are different&#x20;

* the problems are very simple but as such we have very high expectation
* Model FLOP utilisation: the percentage of the theoretical max floating point operations per second our hardware can do.  Almost no CPU is anywhere this . we are ofthen hitting 50% in ML.
* THe Field has consolidated significantly
  * from "many artitecture" to "One" artitecture
  * "Many folks training SOTA models" -> "a few companies training SOTA models"
* THere are ways to get more impact&#x20;
  * leaving optimisations up to a compiler
  * getting tools so that you can do the optimisation your self
* ML framework programming model history&#x20;
  * Declarative (Caffe)
  * Graph-builder(tensorflow)
    * define a function&#x20;
    * function gets converted into IR
    * Function eventually executes on GPPU
  * imperative/eager (pytorch)
    * call a function&#x20;
    * gpu runs a function&#x20;
    * function finishes
    * un optimized eager execution didn't even sacrifice performance, 90% of time spent in matmul there is nothing else we need to optimize.
  * But then tensor-cores arrived.
    * thus we get ml compilers
      * keep the eager programming modell
      * capture into a graph in some manner

What ml compilers are doing for us

* 3 things one can be spending time one&#x20;
  * compute: time spent on your gpu computing actual floating point operations
  * memory: time spent transferring tensors within a gpu
  * overhead: Everything else
* All runtime is either compute or shuffling data&#x20;
  * a flop is the only real thing a GPU can do.
