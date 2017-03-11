package net.onedaybeard.transducers

import java.util.concurrent.atomic.AtomicBoolean


@Suppress("UNCHECKED_CAST")
fun <R : MutableList<A>, A, B> MutableList<A>.into(xf: Transducer<A, B>,
                                                   input: Iterable<B>): R {

    return intoList(xf, this as R, input)
}

@Suppress("UNCHECKED_CAST")
fun <R : MutableList<A>, A, B> intoList(xf: Transducer<A, B>,
                                        init: R = mutableListOf<A>() as R,
                                        input: Iterable<B>): R {
    return transduce(xf, intoRf<A, R>(), init, input)
}

@Suppress("UNCHECKED_CAST")
fun <R : MutableSet<A>, A, B> MutableSet<A>.into(xf: Transducer<A, B>,
                                                 input: Iterable<B>): R {

    return intoSet(xf, this as R, input)

}

@Suppress("UNCHECKED_CAST")
fun <R : MutableSet<A>, A, B> intoSet(xf: Transducer<A, B>,
                                      init: R = mutableSetOf<A>() as R,
                                      input: Iterable<B>): R {
    return transduce(xf, intoRf<A, R>(), init, input)
}

private fun <A, R : MutableCollection<A>> intoRf(): ReducingFunction<R, A> {
    return object : ReducingFunction<R, A> {
        override fun apply(result: R,
                           input: A,
                           reduced: AtomicBoolean): R {

            result.add(input)
            return result
        }
    }
}
