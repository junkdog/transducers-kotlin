package net.onedaybeard.transducers

import java.util.concurrent.atomic.AtomicBoolean

fun <A> debug(tag: String, indent: Int = 0) = object : Transducer<A, A> {
    override fun <R> apply(rf: ReducingFunction<R, A>): ReducingFunction<R, A> {
        val length = 15 + indent
        val tagColumn = tag.padEnd(length)

        return object : ReducingFunction<R, A> {
            override fun apply(): R {
                val result = rf.apply()
                println("INIT     $tagColumn $result")
                return result
            }

            override fun apply(result: R): R {
                println("RESULT:I $tagColumn $result")
                val ret = rf.apply(result)
                println("RESULT:O $tagColumn $ret")
                return ret
            }

            override fun apply(result: R,
                               input: A,
                               reduced: AtomicBoolean): R {
                println("    STEP $tagColumn $input")
                return rf.apply(result, input, reduced)
            }
        }
    }
}

fun <A> assertCompletionSanity(tag: String = "") = object : Transducer<A, A> {
    override fun <R> apply(rf: ReducingFunction<R, A>): ReducingFunction<R, A> {
        return object : ReducingFunction<R, A> {
            var invoked = false
            val prefix = if (tag.isEmpty()) "" else "$tag: "

            override fun apply(): R  = rf.apply()

            override fun apply(result: R): R {
                if (invoked)
                    throw AssertionError("${prefix}invoked x2: apply(result=$result)")

                invoked = true
                return rf.apply(result)
            }

            override fun apply(result: R,
                               input: A,
                               reduced: AtomicBoolean): R = rf.apply(result, input, reduced)
        }
    }
}

fun <A> healtInspector(tag: String?, indent: Int = 0) : Transducer<A, A> {
    return if (tag != null)
        debug<A>(tag, indent) + assertCompletionSanity<A>(tag)
    else
        assertCompletionSanity<A>()
}
