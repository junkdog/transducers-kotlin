package net.onedaybeard.transducers

import java.util.concurrent.atomic.AtomicBoolean


fun <A> copy() = object : Transducer<A, A> {
    override fun <R> apply(rf: ReducingFunction<R, A>) = object : RfOn<R, A, A>(rf) {
        override fun apply(result: R, input: A, reduced: AtomicBoolean): R {
            val res = rf.apply(result, input, reduced)
            return rf.apply(res, input, reduced)
        }
    }
}

fun <A> assertCompletionSanity(tag: String = "",
                               resettable: Boolean = false) = object : Xf<A, A> {

    override fun <R> apply(rf: ReducingFunction<R, A>): ReducingFunction<R, A> {
        return object : ReducingFunction<R, A> {
            var invoked = false
            val prefix = if (tag.isEmpty()) "" else "$tag: "

            override fun apply(): R {
                if (resettable) invoked = false

                return rf.apply()
            }

            override fun apply(result: R): R {
                if (invoked)
                    throw AssertionError("${prefix}invoked x2: apply(result=$result)")

                invoked = true
                return rf.apply(result)
            }

            override fun apply(result: R,
                               input: A,
                               reduced: AtomicBoolean): R {
                if (invoked)
                    throw AssertionError("${prefix}result already computed")

                return rf.apply(result, input, reduced)
            }
        }
    }
}


fun <A> healthInspector(tag: String? = null,
                        indent: Int = 0): Transducer<A, A> {

    return if (tag != null) {
        debug<A>(tag, indent) + assertCompletionSanity(tag)
    } else {
        assertCompletionSanity()
    }
}

infix fun <T> T.assertEquals(expected: T) = kotlin.test.assertEquals(expected, this)
