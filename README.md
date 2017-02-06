[![Build Status](https://travis-ci.org/junkdog/transducers-kotlin.svg)](https://travis-ci.org/junkdog/transducers-kotlin)

## Transducers for kotlin

The code in this repository is a marginally updated copy of https://gist.github.com/hastebrot/aa7b5366309d42270cc1.
 

[Transducers](http://clojure.org/transducers) are composable algorithmic transformations. They are independent from the context of their input and output sources and specify only the essence of the transformation in terms of an individual element. Because transducers are decoupled from input or output sources, they can be used in many different processes - collections, streams, channels, observables, etc. Transducers compose directly, without awareness of input or creation of intermediate aggregates.

## Original source code (from Oct 12, 2014):

- `Transducers.kt`: https://gist.github.com/Spasi/4052e4e8c8d88a7325fb
- `TransducersTest.kt`: https://gist.github.com/Spasi/2a9d7d420b20f37513d5

## Change notes:

- Converted to Kotlin ~1.0.0-beta-3595~ 1.0.6 and JUnit 4.12.
- Added `T.assertEquals(T)` and auto reformatted the source code.
- ~~Some unit tests are failing with slight differences in the results.~~
- Updated copyright notice since Apache License states "You must cause any modified files to carry prominent notices stating that You changed the files".

## Weblinks:

- http://clojure.org/transducers
- http://blog.cognitect.com/blog/2014/8/6/transducers-are-coming
- https://github.com/cognitect-labs/transducers-java

## Getting started

#### Maven

```xml
<dependency>
	<groupId>net.onedaybeard.transducers</groupId>
	<artifactId>transducers</artifactId>
	<version>0.1.0</version>
</dependency>
```

#### Gradle

```groovy
  dependencies { compile "net.onedaybeard.transducers:transducers:0.1.0" }
```
