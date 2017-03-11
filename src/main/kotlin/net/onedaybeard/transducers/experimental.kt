package net.onedaybeard.transducers

import java.lang.reflect.Field
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

typealias Xf<A, B> = Transducer<A, B>
typealias Rf<R, A> = ReducingFunction<R, A>
typealias RfOn<R, A, B> = ReducingFunctionOn<R, A, B>

/**
 * Conditional transducer, primarily useful for branching transducers.
 *
 * @see [wires]
 * @see [mux]
 * @see [resultGate]
 */
data class Signal<out A, B>(val selector: (B) -> Boolean,
                            val xf: Transducer<A, B>)
/**
 * Creates a [Signal].
 *
 * @see [mux]
 */
infix fun <A, B> ((B) -> Boolean).wires(xf: Transducer<A, B>) : Signal<A, B> {
    return Signal(this, xf)
}

private data class Mux<in A, in B, R>(val test: (B) -> Boolean,
                                      val rf: ReducingFunction<R, A>)

/**
 * Returns a branching transducer, routing each input through
 * either [xfTrue] or [xfFalse], depending on the value returned
 * by [test].
 *
 * @sample samples.Samples.branch_a
 * @see [mux]
 */
fun <A, B> branch(test: (B) -> Boolean,
                  xfTrue: Transducer<A, B>,
                  xfFalse: Transducer<A, B>): Transducer<A, B> {

    return mux(test             wires xfTrue,
               { _: B -> true } wires xfFalse)
}

/**
 * Returns a transducer consuming all input, and then releases the
 * input as an [Iterable] collection.
 *
 * The transducer collects into a list by default, can be changed by
 * supplying another [collector]. [collector] is reset after producing the
 * final result.
 *
 * [sort] may change the type of underlying collection. This implies allocation.
 *
 * @sample samples.Samples.collect_a
 * @sample samples.Samples.collect_b
 */
fun <A> collect(
    collector: () -> MutableCollection<A> = { mutableListOf<A>() },
    sort: (A, A) -> Int
               ): Xf<Iterable<A>, A> = collect(collector, Comparator(sort))

/**
 * Returns a transducer consuming all input, before releasing the
 * accumulated input as an [Iterable] collection.
 *
 * The transducer collects into a list by default; this can be changed by
 * supplying another [collector]. [collector] is reset after producing the
 * final result.
 *
 * [sort] may change the type of underlying collection. This implies allocation.
 */
fun <A> collect(
    collector: () -> MutableCollection<A> = { mutableListOf<A>() },
    sort: Comparator<A>? = null
               ) = object : Transducer<Iterable<A>, A> {

    override fun <R> apply(rf: Rf<R, Iterable<A>>) = object : Rf<R, A> {
        var accumulator: MutableCollection<A> = collector()

        override fun apply(): R = rf.apply()

        override fun apply(result: R): R {
            if (accumulator.isEmpty())
                return result

            val input = if (sort != null)
                accumulator.sortedWith(sort) else accumulator

            val ret = rf.apply(result, input, AtomicBoolean())
            val r = rf.apply(ret)

            accumulator = collector()

            return r
        }

        override fun apply(result: R,
                           input: A,
                           reduced: AtomicBoolean): R {

            accumulator.add(input)
            return result
        }
    }
}

/**
 * Returns a transducer counting the number of input.
 */
fun <A> count(): Transducer<Int, A> = sum { _ -> 1 }

/**
 * Returns a debug-assisting transducer. This transducer
 * prints each input, and the initial plus final results.
 */
fun <A> debug(tag: String, indent: Int = 0) = object : Transducer<A, A> {
    override fun <R> apply(rf: ReducingFunction<R, A>): ReducingFunction<R, A> {
        val length = 15 + indent
        val tagColumn = tag.padEnd(length).replace(' ', '.')

        return object : ReducingFunction<R, A> {
            override fun apply(): R {
                val result = rf.apply()
                println("#  $tagColumn $result")
                return result
            }

            override fun apply(result: R): R {
                val ret = rf.apply(result)
                println("!! $tagColumn $ret")
                return ret
            }

            override fun apply(result: R,
                               input: A,
                               reduced: AtomicBoolean): R {
                println("- $tagColumn $input")
                return rf.apply(result, input, reduced)
            }
        }
    }
}

/**
 * Returns a transducer creating [Pair]s, with values provided
 * via transducers [l] and [r]. Each value transducer runs a full
 * transducible process/lifecycle per input, allowing for
 * more sophisticated transducers at the cost of performance.
 *
 * @see [subduce]
 * @sample net.onedaybeard.transducers.ExperimentalTransducersTest.test_subduce
 */
inline fun <B, reified K, reified V> mapPair(l: Xf<K, B>,
                                             r: Xf<V, B>): Xf<Pair<K, V>, B>
        where K : Any, V : Any {

    return object : Transducer<Pair<K, V>, B> {

        override fun <R> apply(rf: Rf<R, Pair<K, V>>): Rf<R, B> {
            return object : ReducingFunction<R, B> {
                val lRf: Rf<K, B> = subducing(l)
                val rRf: Rf<V, B> = subducing(r)

                override fun apply(result: R): R = rf.apply(result)

                override fun apply(result: R,
                                   input: B,
                                   reduced: AtomicBoolean): R {

                    return rf.apply(result,
                                    Pair(subduce(lRf, input),
                                         subduce(rRf, input)),
                                    reduced)
                }
            }
        }
    }
}

/**
 * Returns a multiplexing transducer, routing input through different
 * transducers. Transducer route is selected based on the outcome of
 * [Signal.selector], evaluated in sequential order. Unmatched input
 * are discarded.
 *
 * Per default, each input is associated with at most _one_ transducer
 * route. If [promiscuous] is `true`, input passes through every
 * matching transducer.
 *
 * When all input has been exhausted, each route processes the final
 * result. Subsequent transducers are [guarded][resultGate] against
 * multiple invocations of `ReducingFunction::apply(result)`.
 *
 * ```
 *     <A,B>         <B,C>           (<B,C>)          <C,D>
 *
 *                                  +-------+
 *                              +-->+ Xf 3a +---+
 *                              |   +-------+   |
 *                              |               |
 *   +-------+     +-------+    |   +-------+   |   +-------+
 *   | Xf 1  +---->+  mux  +------->+ Xf 3b +------>+ Xf 4  |
 *   +-------+     +-------+    |   +-------+   |   +-------+
 *                              |               |
 *                              |   +-------+   |
 *                              +-->+ Xf 3c +---+
 *                                  +-------+
 *
 * ```
 *
 * Depicted transducers `3a`, `3b` and `3c` may be composed of one or more
 * transducers, and can produce zero or many outputs per input value.
 * Transducer signature is the same for each route, as given by [mux].
 *
 * @sample samples.Samples.mux_a
 * @sample samples.Samples.mux_b
 */
fun <A, B> mux(vararg xfs: Signal<A, B>,
               promiscuous: Boolean = false) = muxing(xfs, promiscuous) +
                                               resultGate(xfs.size)

private fun <A, B> muxing(xfs: Array<out Signal<A, B>>,
                          promiscuous: Boolean = false) = object : Xf<A, B> {
    override fun <R> apply(rf: Rf<R, A>) = object : Rf<R, B> {

        val rfs = intoList(xf = map { s: Signal<A, B> -> Mux(s.selector,
                                                             s.xf.apply(rf))},
                           input = xfs.asIterable())



        override fun apply(result: R): R {
            var r = result
            for (mux in rfs)
                r = mux.rf.apply(r)

            return r
        }

        override fun apply(result: R,
                           input: B,
                           reduced: AtomicBoolean): R {

            val maxMatchedRfs = if (promiscuous) rfs.size else 1
            return transduce(xf = filter { s: Mux<B, B, R> -> s.test(input) } +
                                  take(maxMatchedRfs),
                             rf = { _: R, mux -> mux.rf.apply(result, input, reduced) },
                             init = result,
                             input = rfs)
        }
    }
}

/**
 * Returns a transducer concatenating pairs of `Pair<A, Iterable<B>>` into
 * `Pair<A, B>`.
 */
fun <F, A> pairCat() = object : Xf<Pair<F, A>, Pair<F, Iterable<A>>> {
    override fun <R> apply(rf: ReducingFunction<R, Pair<F, A>>)
        = object : RfOn<R, Pair<F, A>, Pair<F, Iterable<A>>>(rf) {

        override fun apply(result: R,
                           input: Pair<F, Iterable<A>>,
                           reduced: AtomicBoolean) = reducePair(rf,
                                                                result,
                                                                input,
                                                                reduced)
    }
}

/**
 * Returns a transducer ensuring _completion_ is calculated once, used
 * by [branch]ing/[mux]ing transducers.
 */
fun <A> resultGate(count: Int) = object : Transducer<A, A> {
    override fun <R> apply(rf: ReducingFunction<R, A>) = object : RfOn<R, A, A>(rf) {

        var remaining = count

        override fun apply(result: R): R {
            remaining--

            return when (remaining) {
                0            -> rf.apply(result)
                !in 0..count -> throw UnsupportedOperationException("negative: $remaining")
                else         -> result
            }
        }

        override fun apply(result: R,
                           input: A,
                           reduced: AtomicBoolean): R {

            return rf.apply(result, input, reduced)
        }
    }
}

/**
 * Returns a transducer collecting all input, before releasing
 * each element in sorted order.
 */
fun <A> sort(sort: (o1: A, o2: A) -> Int) = collect(sort = sort) +
                                            cat<A>()

/**
 * Calculates sum of all input.
 */
fun <A : Number> sum() = sum { i: A -> i }

/**
 * Calculates sum based on values produced by [f].
 *
 * @sample samples.sum_a
 *
 * Testing for fun...
 *
 * @sample samples.sum_b
 */
fun <A : Number, B> sum(f: (b: B) -> A) = object : Transducer<A, B> {
    override fun <R> apply(rf: Rf<R, A>) = object : Rf<R, B> {
        var accumulator: A? = null

        override fun apply(): R = rf.apply()

        override fun apply(result: R): R {
            if (accumulator == null)
                return result

            val ret = rf.apply(result, accumulator!!, AtomicBoolean())
            val r = rf.apply(ret)

            accumulator = null

            return r
        }

        override fun apply(result: R,
                           input: B,
                           reduced: AtomicBoolean): R {

            if (accumulator != null) {
                accumulator = accumulator!! + f(input)
            } else {
                accumulator = f(input)
            }

            return result
        }

        @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")
        private operator fun A.plus(other: A): A {
            return when (this) {
                is Int    -> this + other as Int
                is Float  -> this + other as Float
                is Double -> this + other as Double
                is Long   -> this + other as Long
                is Byte   -> this + other as Byte
                is Short  -> this + other as Short
                else      -> 0
            } as A
        }
    }
}

/**
 * Applies given reducing function to current result and each T in input, using
 * the result returned from each reduction step as input to the next step. Returns
 * final result.
 * @param f a reducing function
 * @param result an initial result value
 * @param input the input to process
 * @param reduced a boolean flag that can be set to indicate that the reducing process
 *                should stop, even though there is still input to process
 * @param <R> the type of the result
 * @param <T> the type of each item in input
 * @return the final reduced result
 */
fun <R, F, T> reducePair(f: ReducingFunction<R, Pair<F, T>>,
                         result: R,
                         input: Pair<F, Iterable<T>>,
                         reduced: AtomicBoolean = AtomicBoolean()): R {

    var ret = result
    for (t in input.second) {
        ret = f.apply(ret, Pair(input.first, t), reduced)
        if (reduced.get()) break
    }

    return ret
}

/**
 * Performs a full transduction on a single input. Typically,
 * used in conjunction with [subduce]. Useful for construction
 * of pairs, tuples and similar data structures - using
 * separate transducers per field.
 *
 * @see subducing
 */
inline fun <reified A : Any, B> subduce(rf: ReducingFunction<A, B>,
                                        input: B,
                                        reduced: AtomicBoolean = AtomicBoolean()): A {

    val r = rf.apply(rf.apply(), input, reduced)
    return rf.apply(r)
}

/**
 * Returns a specialized [ReducingFunction] for [xf], treating each
 * input as a full transducible process - invoking each of the
 * three [ReducingFunction.apply] functions once.
 *
 * An initial result is computed based on the type of [A].
 *
 * @see subduce
 */
@Suppress("IMPLICIT_CAST_TO_ANY")
inline fun <reified A : Any, B> subducing(xf: Xf<A, B>): Rf<A, B> {
    return subducing(xf) {
        when (A::class) {
            Boolean::class  -> false
            Byte::class     -> 0
            Char::class     -> ' '
            Short::class    -> 0
            Int::class      -> 0
            Long::class     -> 0
            Float::class    -> 0
            Double::class   -> 0
            KClass::class   -> KClass::class
            Field::class    -> Dummy().field
            Iterable::class -> mutableListOf<Any>()
            else            -> A::class.java.newInstance()
        } as A
    }
}

/**
 * Returns a specialized [ReducingFunction], treating each
 * input as a full transducible process - invoking each of the
 * three [ReducingFunction.apply] functions once.
 *
 * Initial result supplied by [init], invoked once during
 * initialization.
 */
inline fun <reified A : Any, B> subducing(xf: Xf<A, B>,
                                          crossinline init: () -> A): Rf<A, B> {

    return xf.apply(object : ReducingFunction<A, A> {
        var hasDeliveredResult = false
        val initial: A = init()

        override fun apply(): A {
            hasDeliveredResult = false
            return initial
        }

        override fun apply(result: A): A {
            if (hasDeliveredResult)
                throw RuntimeException("final result already computed")

            hasDeliveredResult = true
            return result
        }

        override fun apply(result: A,
                           input: A,
                           reduced: AtomicBoolean): A = input
    })
}

class Dummy {
    val field: Field = Dummy::class.java.getDeclaredField("field")
}
