[![Build Status](https://travis-ci.org/junkdog/transducers-kotlin.svg)](https://travis-ci.org/junkdog/transducers-kotlin)

## Transducers for kotlin

From the official [clojure documentation](https://clojure.org/reference/transducers):

> Transducers are composable algorithmic transformations. They are independent from the context of their input and output sources and specify only the essence of the transformation in terms of an individual element. Because transducers are decoupled from input or output sources, they can be used in many different processes - collections, streams, channels, observables, etc. Transducers compose directly, without awareness of input or creation of intermediate aggregates.

Refer to [CHANGELOG.md](https://github.com/junkdog/transducers-kotlin/blob/master/CHANGELOG.md) for latest updates.


### Basic usage

```
transduce(xf = <transducer applied to every input>,
          rf = <what we do to the transduced input>,
          init = <initial state>,
          input = <iterable or finite sequence>)
```


```kotlin
// '+' composes transducers
val composedTransducer = filter { i: Int -> i % 5 == 0 } +
                         sum { i: Int -> i / 5 }
    
// applying transducer + step function to input
transduce(xf = composedTransducer,
          rf = { result, input -> result + input },
          init = 0,
          input = (0..20)
) assertEquals (1 + 2 + 3 + 4)
```

`intoList` and `intoSet` wraps `transduce`, the step function is encapsulated, it simply
 adds each input to the collection.

```kotlin
intoList(xf = copy<Int>() +
              branch(test = { it % 4 == 0 },
                     xfTrue  = map { i: Int -> i * 100 },
                     xfFalse = map { i: Int -> i / 4 } +
                               distinct() +
                               map(Int::toString)),
         input = (1..9)
) assertEquals listOf("0", 400, 400, "1", 800, 800, "2")
```

## New/Experimental Transducers
_Experimental_ implies transducers not included with [transducers-java](https://github.com/cognitect-labs/transducers-java).

### new regular transducers
  - `collect`: input into a mutable collection, releases it upon computing the final result.
  - `debug`: prints debug statements.
  - `distinct`: no two equal elements shall pass.
  - `pairCat`: concatenates `Pair<A, Iterable<B>>` into `Pair<A, B>`.
  - `resultGate`: ensures _completion_ is calculated once, used with branching/muxing transducers.
  - `sort`: collects and sorts all input.

### new higher-order transducers
  - `branch`: routes input through one out of two transducers, based on predicate.
  - `mapPair`: creates pairs based on two transducers. 
  - `mux`: input multiplexer, routing input over several transducer paths based on predicates.

### new single item transducers
  - `count`: number of input.
  - `sum`: calculates sum, can be parameterized by a function for producing a custom value per input.


Refer to the [API documentation][api-docs] and [tests][test-src] for details.


## Original source code:

The code in this repository is based on https://gist.github.com/hastebrot/aa7b5366309d42270cc1,
which in turn was based on the original port from [transducers-java][transducers-java]:
Spasi's [Transducers.kt][orig-gist1] and [TransducersTest.kt][orig-gist2] from Oct 12, 2014.  


## Resources:

- http://clojure.org/transducers
- http://blog.cognitect.com/blog/2014/8/6/transducers-are-coming
- https://github.com/cognitect-labs/transducers-java

## Getting started

### Maven

```xml
<dependency>
    <groupId>net.onedaybeard.transducers</groupId>
    <artifactId>transducers</artifactId>
    <version>0.3.0</version>
</dependency>
```

### Gradle

```groovy
dependencies { compile "net.onedaybeard.transducers:transducers:0.3.0" }
```


 [transducers-java]: https://github.com/cognitect-labs/transducers-java
 [test-src]: https://github.com/junkdog/transducers-kotlin/tree/master/src/test/kotlin/net/onedaybeard/transducers
 [orig-gist1]: https://gist.github.com/Spasi/4052e4e8c8d88a7325fb
 [orig-gist2]: https://gist.github.com/Spasi/2a9d7d420b20f37513d5
 [api-docs]: http://junkdog.github.io/doc/transducers-kotlin/0.3.0/net.onedaybeard.transducers/index.html
 