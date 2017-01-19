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

import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals


class TransducersTest {


	@Test
	fun testMap() {
		transduce(
			map(Int::toString),
			{ result, input -> "$result$input " },
			"",
			(0..9)
		) assertEquals "0 1 2 3 4 5 6 7 8 9 "

		transduce(
			map { it },
			{ result, input: Int -> result.apply { add(input + 1) } },
			mutableListOf<Int>(),
			(0..9)
		) assertEquals listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

		transduce(
			STRINGIFY,
			ADD_STRING,
			mutableListOf(),
			(0..9L)
		) assertEquals listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
	}

	@Test
	fun testFilter() {
		transduce(
			filter { it % 2 != 0 },
			ADD_INT,
			mutableListOf(),
			(0..9)
		) assertEquals listOf(1, 3, 5, 7, 9)
	}

	@Test
	fun testCat() {
		val data = listOf((0..9), (0..19))

		val vals = transduce(xf = cat(),
		                     rf = ADD_INT,
		                     init = mutableListOf(),
		                     input = data)

		vals.size assertEquals 30

		var i = 0
		(0..9).forEach  { it assertEquals vals[i++] }
		(0..19).forEach { it assertEquals vals[i++] }
	}

	@Test
	fun testMapcat() {
		transduce(
			mapcat { i: Int -> i.toString().toList() },
			{ result, input: Char -> result.apply{ add(input) } },
			mutableListOf<Char>(),
			(0..9)
		) assertEquals listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
	}

	@Test
	fun testComp() {
		val addString = { result: MutableList<String>, input: String ->
			result.apply { add(input) }
		}

		transduce(
			xf = filter<Long> { it % 2L != 0L }.comp(map(Long::toString)),
			rf = addString,
			init = mutableListOf<String>(),
			input = (0..9L)
		) assertEquals listOf("1", "3", "5", "7", "9")
	}

	@Test
	fun testTake() {
		val addInt = { result: MutableList<Int>, input: Int ->
			result.apply { add(input) }
		}

		transduce(
			take(5),
			addInt,
			mutableListOf(),
			(0..19)
		) assertEquals listOf(0, 1, 2, 3, 4)
	}

	@Test
	fun testTakeWhile() {
		transduce(
			takeWhile { it < 10 },
			ADD_INT,
			mutableListOf(),
			(0..19)
		) assertEquals listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
	}

	@Test
	fun testDrop() {
		transduce(
			drop(5),
			ADD_INT,
			mutableListOf(),
			(0..9)
		) assertEquals listOf(5, 6, 7, 8, 9)
	}

	@Test
	fun testDropWhile() {
		transduce(
			dropWhile { it < 10 },
			ADD_INT,
			mutableListOf(),
			(0..19)
		) assertEquals listOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
	}

	@Test
	fun testTakeNth() {
		transduce(
			takeNth(2),
			ADD_INT,
			mutableListOf(),
			(0..9)
		) assertEquals listOf(0, 2, 4, 6, 8)
	}

	@Test
	fun testReplace() {
		transduce(
			replace(mapOf(3 to 42)),
			ADD_INT,
			mutableListOf(),
			(0..4)
		) assertEquals listOf(0, 1, 2, 42, 4)
	}

	@Test
	fun testKeep() {
		val addInt = { result: MutableList<Int>, input: Int -> result.apply { add(input) } }

		transduce(
			keep { if ( it % 2 == 0 ) null else it },
			addInt,
			mutableListOf(),
			(0..9)
		) assertEquals listOf(1, 3, 5, 7, 9)
	}

	@Test
	fun testKeepIndexed() {
		val addInt = { result: MutableList<Int>, input: Int -> result.apply { add(input) } }

		transduce(
			keepIndexed { index, value -> if ( index == 1L || index == 4L ) value else null },
			addInt,
			mutableListOf(),
			(0..9)
		) assertEquals listOf(0, 3)
	}

	@Test
	fun testDedupe() {
		transduce(
			dedupe(),
			ADD_INT,
			mutableListOf(),
			listOf(1, 2, 2, 3, 4, 5, 5, 5, 5, 5, 5, 5, 0)
		) assertEquals listOf(1, 2, 3, 4, 5, 0)
	}

	@Test
	fun testPartitionBy() {
		transduce(
			partitionBy { i: Int -> i },
		   { result, input -> result.apply { add(input.toList()) } },
			mutableListOf<Iterable<Int>>(),
			listOf(1, 1, 1, 2, 2, 3, 4, 5, 5)
		) assertEquals listOf(
			listOf(1, 1, 1),
			listOf(2, 2),
			listOf(3),
			listOf(4),
			listOf(5, 5)
		)
	}

	@Test
	fun testPartitionAll() {
		transduce(
			partitionAll<Int>(3),
			{ result: MutableList<Iterable<Int>>, input -> result.apply { add(input.toList()) } },
			mutableListOf(),
			(0..9)
		) assertEquals listOf(
			listOf(0, 1, 2),
			listOf(3, 4, 5),
			listOf(6, 7, 8),
			listOf(9)
		)
	}

	@Test
	fun testSimpleCovariance() {
		val m = map<Int, Int> { it * 2 }
		val input = (0..19)

		val ADD_NUMBER = object : StepFunction<MutableCollection<Number>, Number> {
			override fun apply(result: MutableCollection<Number>,
			                   input: Number,
			                   reduced: AtomicBoolean): MutableCollection<Number> {
				result.add(input)
				return result
			}
		}

		transduce(
			m,
			ADD_NUMBER,
			mutableListOf<Number>(),
			input
		).size assertEquals 20

		transduce(
			m.comp(filter<Number> { it.toDouble() > 10.0 }),
			ADD_NUMBER,
			mutableListOf<Number>(),
			input
		).size assertEquals 14
	}

	private companion object {
		val STRINGIFY = map(Long::toString)

		val ADD_STRING = object : StepFunction<MutableList<String>, String> {
			override fun apply(result: MutableList<String>,
			                   input: String,
			                   reduced: AtomicBoolean): MutableList<String> {
				result.add(input)
				return result
			}
		}

		val ADD_INT = object : StepFunction<MutableList<Int>, Int> {
			override fun apply(result: MutableList<Int>,
			                   input: Int,
			                   reduced: AtomicBoolean): MutableList<Int> {
				result.add(input)
				return result
			}
		}
	}

	private infix fun <T> T.assertEquals(expected: T) = assertEquals(expected, this)

}
