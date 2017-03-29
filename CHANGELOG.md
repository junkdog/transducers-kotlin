## Change Log (we do our best to adhere to [semantic versioning](http://semver.org/))

#### Version: 0.4.0-SNAPSHOT
- *deprecated*
  - `into`, intoList`, `intoSet`
  
- transduce into collections with `listOf(xf, input)` and `setOf(xf, input)`.
- transduce into maps with `mapOf`, `mapOfLists`, `mapOfSets`.
- **new higher-order transducers**:
  - `join`: turns `List<Transducer<A, B>>` into `Transducer<List<A>, B>`.


#### Version: 0.3.0 - 2017-03-12
- Int conversion of Long transducer parameters
- **typealiases**
  - **`Xf<A, B>`**: `Transducer<A, B>`
  - **`Rf<R, A>`**: `ReducingFunction<R, A>`
  - **`RfOn<R, A, B>`**: `ReducingFunctionOn<R, A, B>`
- `intoList` and `intoSet`: `transduce` into requested collection.
- `subducing` and `subduce`: for running a full transduction per input.
  - `subducing`: creates reducing function from transducer.
  - `subduce`: takes the RF from above and an input. Resets transducer lifecycle for every input.
- **new regular transducers**:
  - `collect`: input into a mutable collection, releases it upon computing the final result.
  - `debug`: prints debug statements.
  - `distinct`: no two equal elements shall pass.
  - `pairCat`: concatenates `Pair<A, Iterable<B>>` into `Pair<A, B>`.
  - `resultGate`: ensures _completion_ is calculated once, used with branching/muxing transducers.
  - `sort`: collects and sorts all input.
- **new higher-order transducers**:
  - `branch`: routes input through one out of two transducers, based on predicate.
  - `mapPair`: creates pairs based on two transducers. 
  - `mux`: input multiplexer, routing input over several transducer paths based on predicates.
- **new single item transducers**:
  - `count`: number of input.
  - `sum`: calculates sum, can be parameterized by a function for producing a custom value per input.


#### Version: 0.2.2 - 2017-02-08
- compose transducers with `+`.
- transduce accepts sequences as input.
- `cat()` transducer signature more explicit (vs type inference).
- **Fix**: `cat()` invoked completion operation after each input value.  


#### Version: 0.1.0 - 2017-01-19
- initial release

