package net.onedaybeard.transducers

import java.util.concurrent.atomic.AtomicBoolean


fun <A, B> listOf(xf: Xf<A, B>, input: Iterable<B>): List<A> =
        transduce(xf, intoRf(), mutableListOf(), input)

fun <A, B> listOf(xf: Xf<A, B>, vararg input: B) =
        listOf(xf, input.asIterable())

fun <A, B> setOf(xf: Xf<A, B>, input: Iterable<B>): Set<A> =
        transduce(xf, intoRf(), mutableSetOf(), input)

fun <A, B> setOf(xf: Xf<A, B>, vararg input: B) =
        setOf(xf, input.asIterable())

fun <A, B, C> mapOf(xf: Xf<Pair<A, B>, C>,
                    input: Iterable<C>): Map<A, B> {

    return transduce(xf, intoMapRf(), mutableMapOf<A, B>(), input)
}

fun <A, B, C> mapOf(xf: Xf<Pair<A, B>, C>, vararg input: C) =
        mapOf(xf, input.asIterable())

fun <A, B, C> mapOfLists(xf: Xf<Pair<A, B>, C>,
                         input: Iterable<C>): Map<A, List<B>> {

    return mapOf<A, B, C, MutableList<B>>(xf, ::mutableListOf, input)
}

fun <A, B, C> mapOfLists(xf: Xf<Pair<A, B>, C>,
                         vararg input: C): Map<A, List<B>> {

    return mapOfLists(xf, input.asIterable())
}

fun <A, B, C> mapOfSets(xf: Xf<Pair<A, B>, C>,
                        input: Iterable<C>): Map<A, Set<B>> {

    return mapOf<A, B, C, MutableSet<B>>(xf, ::mutableSetOf, input)
}

fun <A, B, C, T> mapOf(xf: Xf<Pair<A, B>, C>,
                       collection: () -> T,
                       input: Iterable<C>): Map<A, T>
        where T : MutableCollection<B> {

    return transduce(xf,
                     intoMapGroupRf(collection),
                     mutableMapOf<A, T>(),
                     input)
}

inline fun <reified A, reified B, C, T> mapOf(k: Xf<A, C>,
                                              v: Xf<B, C>,
                                              noinline collection: () -> T,
                                              input: Iterable<C>): Map<A, T>
        where T : MutableCollection<B>,
              A : Any,
              B : Any {

    return mapOf(mapPair(k, v), collection, input)
}

fun <A, B, C> mapOfSets(xf: Xf<Pair<A, B>, C>,
                        vararg input: C): Map<A, Set<B>> {

    return mapOfSets(xf, input.asIterable())
}

fun <A> A.withIterable() = object : Iterable<A> {
    override fun iterator() = object : Iterator<A> {
        var consumed = false

        override fun next(): A = this@withIterable.apply { consumed = true }
        override fun hasNext() = !consumed
    }
}

internal fun <A, R : MutableCollection<A>> intoRf(): Rf<R, A> {
    return object : Rf<R, A> {
        override fun apply(result: R,
                           input: A,
                           reduced: AtomicBoolean): R {

            result.add(input)
            return result
        }
    }
}

internal fun <A, B, R : MutableMap<A, B>> intoMapRf(): Rf<R, Pair<A, B>> {
    return object : Rf<R, Pair<A, B>> {
        override fun apply(result: R,
                           input: Pair<A, B>,
                           reduced: AtomicBoolean): R {

            result.put(input.first, input.second)
            return result
        }
    }
}

internal fun <A, B, C, R> intoMapGroupRf(collection: () -> C): Rf<R, Pair<A, B>>
        where C : MutableCollection<B>,
              R : MutableMap<A, C> {

    return object : Rf<R, Pair<A, B>> {
        override fun apply(result: R,
                           input: Pair<A, B>,
                           reduced: AtomicBoolean): R {

            result.getOrPut(input.first, collection).add(input.second)
            return result
        }
    }
}
