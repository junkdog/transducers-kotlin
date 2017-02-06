// Copyright 2014 Cognitect. All Rights Reserved.
// Copyright 2015 Benjamin Gudehus (updated code to Kotlin 1.0.0).
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This is a Kotlin port of https://github.com/cognitect-labs/transducers-java

package net.onedaybeard.transducers

import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A reducing step function.
 * @param <R> Type of first argument and return value
 * @param <T> Type of input to reduce
 */
interface StepFunction<R, in T> {
	/**
	 * Applies the reducing function to the current result and
	 * the new input, returning a new result.
	 *
	 * A reducing function can indicate that no more input
	 * should be processed by setting the value of reduced to
	 * true. This causes the reduction process to complete,
	 * returning the most recent result.
	 * @param result The current result value
	 * @param input New input to process
	 * @param reduced A boolean value which can be set to true
	 *                to stop the reduction process
	 * @return A new result value
	 */
	fun apply(result: R,
	          input: T,
	          reduced: AtomicBoolean): R
}

private inline fun <R, T> makeStepFunction(crossinline step: (R, T) -> R): StepFunction<R, T> {
	return object : StepFunction<R, T> {
		override fun apply(result: R, input: T, reduced: AtomicBoolean): R {
			return step.invoke(result, input)
		}
	}
}


/**
 * A complete reducing function. Extends a single reducing step
 * function and adds a zero-arity function for initializing a new
 * result and a single-arity function for processing the final
 * result after the reduction process has completed.
 * @param <R> Type of first argument and return value
 * @param <T> Type of input to reduce
 */
interface ReducingFunction<R, in T> : StepFunction<R, T> {
	/**
	 * Returns a newly initialized result.
	 * @return a new result
	 */
	fun apply(): R = throw UnsupportedOperationException()

	/**
	 * Completes processing of a final result.
	 * @param result the final reduction result
	 * @return the completed result
	 */
	fun apply(result: R): R = result
}

/**
 * Abstract base class for implementing a reducing function that chains to
 * another reducing function. Zero-arity and single-arity overloads of apply
 * delegate to the chained reducing function. Derived classes must implement
 * the three-arity overload of apply, and may implement either of the other
 * two overloads as required.
 * @param <R> Type of first argument and return value of the reducing functions
 * @param <A> Input type of reducing function being chained to
 * @param <B> Input type of this reducing function
 */
abstract class ReducingFunctionOn<R, in A, in B>(
	val rf: ReducingFunction<R, in A>) : ReducingFunction<R, B> {

	override fun apply() = rf.apply()
	override fun apply(result: R) = rf.apply(result)
}

/**
 * A Transducer transforms a reducing function of one type into a
 * reducing function of another (possibly the same) type, applying
 * mapping, filtering, flattening, etc. logic as desired.
 * @param <B> The type of data processed by an input process
 * @param <C> The type of data processed by the transduced process
 */
interface Transducer<out B, C> {
	/**
	 * Transforms a reducing function of B into a reducing function
	 * of C.
	 * @param rf The input reducing function
	 * @param <R> The result type of both the input and the output
	 *           reducing functions
	 * @return The transformed reducing function
	 */
	fun <R> apply(rf: ReducingFunction<R, in B>): ReducingFunction<R, C>

	/**
	 * Composes a transducer with another transducer, yielding
	 * a new transducer.
	 * @param right the transducer to compose with this transducer
	 * @param <A> the type of input processed by the reducing function
	 *           the composed transducer returns when applied
	 * @return A new composite transducer
	 */
	fun <A> comp(right: Transducer<A, in B>): Transducer<A, C> = object : Transducer<A, C> {
		override fun <R> apply(rf: ReducingFunction<R, in A>) = this@Transducer.apply(right.apply(rf))
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
fun <R, T> reduce(f: ReducingFunction<R, in T>,
                  result: R,
                  input: Iterable<T>,
                  reduced: AtomicBoolean = AtomicBoolean()): R {
	var ret = result
	for (t in input) {
		ret = f.apply(ret, t, reduced)
		if (reduced.get()) break
	}
	return f.apply(ret)
}

fun <R, T> completing(sf: StepFunction<R, T>): ReducingFunction<R, T> =
	sf as? ReducingFunction ?: object : ReducingFunction<R, T> {
		override fun apply(result: R,
		                   input: T,
		                   reduced: AtomicBoolean) = sf.apply(result, input, reduced)
	}

/**
 * Reduces input using transformed reducing function. Transforms reducing function by applying
 * transducer. Reducing function must implement zero-arity apply that returns initial result
 * to start reducing process.
 * @param xf a transducer (or composed transducers) that transforms the reducing function
 * @param rf a reducing function
 * @param input the input to reduce
 * @param <R> return type
 * @param <A> type of input expected by reducing function
 * @param <B> type of input and type accepted by reducing function returned by transducer
 * @return result of reducing transformed input
 */
fun <R, A, B> transduce(xf: Transducer<A, B>,
                        rf: ReducingFunction<R, in A>,
                        input: Iterable<B>): R = reduce(xf.apply(rf), rf.apply(), input)

/**
 * Reduces input using transformed reducing function. Transforms reducing function by applying
 * transducer. Step function is converted to reducing function if necessary. Accepts initial value
 * for reducing process as argument.
 * @param xf a transducer (or composed transducers) that transforms the reducing function
 * @param rf a reducing function
 * @param init an initial value to start reducing process
 * @param input the input to reduce
 * @param <R> return type
 * @param <A> type expected by reducing function
 * @param <B> type of input and type accepted by reducing function returned by transducer
 * @return result of reducing transformed input
 */
fun <R, A, B> transduce(xf: Transducer<A, B>,
                        rf: StepFunction<R, in A>,
                        init: R,
                        input: Iterable<B>): R = reduce(xf.apply(completing(rf)),
                                                        init,
                                                        input)

fun <R, A, B> transduce(xf: Transducer<A, B>,
                        rf: StepFunction<R, in A>,
                        init: R,
                        input: Sequence<B>): R = reduce(xf.apply(completing(rf)),
                                                        init,
                                                        input.asIterable())

fun <R, A, B> transduce(xf: Transducer<A, B>,
                        rf: (R, A) -> R,
                        init: R,
                        input: Iterable<B>): R = transduce(xf, makeStepFunction(rf), init, input)

fun <R, A, B> transduce(xf: Transducer<A, B>,
                        rf: (R, A) -> R,
                        init: R,
                        input: Sequence<B>): R = transduce(xf, makeStepFunction(rf), init, input)

fun <R : MutableCollection<A>, A, B> into(xf: Transducer<A, B>,
                                          init: R,
                                          input: Iterable<B>): R =
	transduce(xf, object : ReducingFunction<R, A> {
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			result.add(input)
			return result
		}
	}, init, input)

/**
 * Composes a transducer with another transducer, yielding a new transducer that
 * @param left left hand transducer
 * @param right right hand transducer
 */
fun <A, B, C> compose(left: Transducer<B, C>,
                      right: Transducer<A, B>): Transducer<A, C> = left.comp(right)

/**
 * Creates a transducer that transforms a reducing function by applying a mapping
 * function to each input.
 * @param f a mapping function from one type to another (can be the same type)
 * @param <A> input type of input reducing function
 * @param <B> input type of output reducing function
 * @return a new transducer
 */
fun <A, B> map(f: (B) -> A): Transducer<A, B> = object : Transducer<A, B> {
	override fun <R> apply(rf: ReducingFunction<R, A>) = object : ReducingFunctionOn<R, A, B>(rf) {
		override fun apply(result: R,
		                   input: B,
		                   reduced: AtomicBoolean) = rf.apply(result, f(input), reduced)
	}
}

/**
 * Creates a transducer that transforms a reducing function by applying a
 * predicate to each input and processing only those inputs for which the
 * predicate is true.
 * @param p a predicate function
 * @param <A> input type of input and output reducing functions
 * @return a new transducer
 */
fun <A> filter(p: (A) -> Boolean): Transducer<A, A> = object : Transducer<A, A> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, A>(rf) {
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {

			return if (p(input)) rf.apply(result, input, reduced) else result
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function by accepting
 * an iterable of the expected input type and reducing it
 * @param <A> input type of input reducing function
 * @param <B> input type of output reducing function
 * @return a new transducer
 */
fun <A> cat(): Transducer<A, Iterable<A>> = object : Transducer<A, Iterable<A>> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, Iterable<A>>(rf) {
		override fun apply(result: R,
		                   input: Iterable<A>,
		                   reduced: AtomicBoolean) = reduce(rf, result, input, reduced)
	}
}

/**
 * Creates a transducer that transforms a reducing function using
 * a composition of map and cat.
 * @param f a mapping function from one type to another (can be the same type)
 * @param <A> input type of input reducing function
 * @param <B> output type of output reducing function and iterable of input type
 *           of input reducing function
 * @param <C> input type of output reducing function
 * @return a new transducer
 */
fun <A, B : Iterable<A>, C> mapcat(f: (C) -> B): Transducer<A, C> =
	map(f).comp(cat())

/**
 * Creates a transducer that transforms a reducing function by applying a
 * predicate to each input and not processing those inputs for which the
 * predicate is true.
 * @param p a predicate function
 * @param <A> input type of input and output reducing functions
 * @return a new transducer
 */
fun <A> remove(p: (A) -> Boolean): Transducer<A, A> = filter { !p(it) }

/**
 * Creates a transducer that transforms a reducing function such that
 * it only processes n inputs, then the reducing process stops.
 * @param n the number of inputs to process
 * @param <A> input type of input and output reducing functions
 * @return a new transducer
 */
fun <A> take(n: Long): Transducer<A, A> = object : Transducer<A, A> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, A>(rf) {
		private var taken = 0L
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			var ret = result
			if (taken < n) {
				ret = rf.apply(result, input, reduced)
				taken++
			} else {
				reduced.set(true)
			}
			return ret
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function such that
 * it processes inputs as long as the provided predicate returns true.
 * If the predicate returns false, the reducing process stops.
 * @param p a predicate used to test inputs
 * @param <A> input type of input and output reducing functions
 * @return a new transducer
 */
fun <A> takeWhile(p: (A) -> Boolean): Transducer<A, A> = object : Transducer<A, A> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, A>(rf) {
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			var ret = result
			if (p(input)) {
				ret = rf.apply(ret, input, reduced)
			} else {
				reduced.set(true)
			}
			return ret
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function such that
 * it skips n inputs, then processes the rest of the inputs.
 * @param n the number of inputs to skip
 * @param <A> input type of input and output reducing functions
 * @return a new transducer
 */
fun <A> drop(n: Long): Transducer<A, A> = object : Transducer<A, A> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, A>(rf) {
		private var dropped = 0L
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			var ret = result
			if (dropped < n) {
				dropped++
			} else {
				ret = rf.apply(ret, input, reduced)
			}
			return ret
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function such that
 * it skips inputs as long as the provided predicate returns true.
 * Once the predicate returns false, the rest of the inputs are
 * processed.
 * @param p a predicate used to test inputs
 * @param <A> input type of input and output reducing functions
 * @return a new transducer
 */
fun <A> dropWhile(p: (A) -> Boolean): Transducer<A, A> = object : Transducer<A, A> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, A>(rf) {
		private var drop = true
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			if (drop && p(input)) {
				return result
			}
			drop = false
			return rf.apply(result, input, reduced)
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function such that
 * it processes every nth input.
 * @param n The frequence of inputs to process (e.g., 3 processes every third input).
 * @param <A> The input type of the input and output reducing functions
 * @return a new transducer
 */
fun <A> takeNth(n: Long): Transducer<A, A> = object : Transducer<A, A> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, A>(rf) {
		private var nth = 0L
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			return if ((nth++ % n) == 0L) rf.apply(result, input, reduced) else result
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function such that
 * inputs that are keys in the provided map are replaced by the corresponding
 * value in the map.
 * @param smap a map of replacement values
 * @param <A> the input type of the input and output reducing functions
 * @return a new transducer
 */
fun <A> replace(smap: Map<A, A>): Transducer<A, A> {
	return map { if (smap.containsKey(it)) smap[it]!! else it }
}

/**
 * Creates a transducer that transforms a reducing function by applying a
 * function to each input and processing the resulting value, ignoring values
 * that are null.
 * @param f a function for processing inputs
 * @param <A> the input type of the input and output reducing functions
 * @return a new transducer
 */
fun <A : Any> keep(f: (A) -> A?): Transducer<A, A> = object : Transducer<A, A> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, A>(rf) {
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			val _input = f(input)
			return if (_input != null) rf.apply(result, _input, reduced) else result
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function by applying a
 * function to each input and processing the resulting value, ignoring values
 * that are null.
 * @param f a function for processing inputs
 * @param <A> the input type of the input and output reducing functions
 * @return a new transducer
 */
fun <A : Any> keepIndexed(f: (Long, A) -> A?): Transducer<A, A> = object : Transducer<A, A> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, A>(rf) {
		private var n = 0L
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {

			val _input = f(++n, input)
			return if (_input != null) rf.apply(result, _input, reduced) else result
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function such that
 * consecutive identical input values are removed, only a single value
 * is processed.
 * @param <A> the input type of the input and output reducing functions
 * @return a new transducer
 */
fun <A : Any> dedupe(): Transducer<A, A> = object : Transducer<A, A> {
	override fun <R> apply(rf: ReducingFunction<R, in A>) = object : ReducingFunctionOn<R, A, A>(rf) {
		var prior: A? = null
		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			var ret = result
			if (prior != input) {
				prior = input
				ret = rf.apply(ret, input, reduced)
			}

			return ret
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function such that
 * it has the specified probability of processing each input.
 * @param prob the probability between expressed as a value between 0 and 1.
 * @param <A> the input type of the input and output reducing functions
 * @return a new transducer
 */
fun <A : Any> randomSample(prob: Double): Transducer<A, A> =
	filter { ThreadLocalRandom.current().nextDouble() < prob }

/**
 * Creates a transducer that transforms a reducing function that processes
 * iterables of input into a reducing function that processes individual inputs
 * by gathering series of inputs for which the provided partitioning function returns
 * the same value, only forwarding them to the next reducing function when the value
 * the partitioning function returns for a given input is different from the value
 * returned for the previous input.
 * @param f the partitioning function
 * @param <A> the input type of the input and output reducing functions
 * @param <P> the type returned by the partitioning function
 * @return a new transducer
 */
fun <A, P> partitionBy(f: (A) -> P): Transducer<Iterable<A>, A> = object : Transducer<Iterable<A>, A> {
	override fun <R> apply(rf: ReducingFunction<R, in Iterable<A>>) = object : ReducingFunction<R, A> {
		val part = ArrayList<A>()
		val mark: Any = Unit
		var prior: Any? = mark

		override fun apply(): R = rf.apply()

		override fun apply(result: R): R {
			var ret = result
			if (part.isNotEmpty()) {
				ret = rf.apply(result, ArrayList(part), AtomicBoolean())
				part.clear()
			}
			return rf.apply(ret)
		}

		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			val p = f(input)
			if (prior === mark || prior == p) {
				prior = p
				part.add(input)
				return result
			}

			val copy = ArrayList(part)
			prior = p
			part.clear()
			val ret = rf.apply(result, copy, reduced)
			if (!reduced.get()) {
				part.add(input)
			}
			return ret
		}
	}
}

/**
 * Creates a transducer that transforms a reducing function that processes
 * iterables of input into a reducing function that processes individual inputs
 * by gathering series of inputs into partitions of a given size, only forwarding
 * them to the next reducing function when enough inputs have been accrued. Processes
 * any remaining buffered inputs when the reducing process completes.
 * @param n the size of each partition
 * @param <A> the input type of the input and output reducing functions
 * @return a new transducer
 */
fun <A> partitionAll(n: Int): Transducer<Iterable<A>, A> = object : Transducer<Iterable<A>, A> {
	override fun <R> apply(rf: ReducingFunction<R, in Iterable<A>>) = object : ReducingFunction<R, A> {
		val part = ArrayList<A>()

		override fun apply(): R = rf.apply()

		override fun apply(result: R): R {
			var ret = result
			if (part.isNotEmpty()) {
				ret = rf.apply(result, ArrayList(part), AtomicBoolean())
				part.clear()
			}
			return rf.apply(ret)
		}

		override fun apply(result: R,
		                   input: A,
		                   reduced: AtomicBoolean): R {
			part.add(input)
			return if (n == part.size) {
				try {
					rf.apply(result, ArrayList(part), reduced)
				} finally {
					part.clear()
				}
			} else {
				result
			}
		}
	}
}