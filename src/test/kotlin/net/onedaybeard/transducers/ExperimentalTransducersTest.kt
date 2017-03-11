package net.onedaybeard.transducers

import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class ExperimentalTransducersTest {
    @Test
    fun test_branch() {
        intoList(xf = branch(test = { it % 4 == 0 },
                             xfTrue = map { i: Int -> i * 100 } +
                                      healthInspector("true"),
                             xfFalse = map { i: Int -> i / 4 } +
                                       healthInspector("false")) +
                      distinct() +
                      healthInspector("final", 4),
                 input = (0..9)
        ) assertEquals listOf(0, 400, 1, 800, 2)
    }

    @Test
    fun test_branch_complex() {
        intoList(xf = copy<Int>() +
                      branch(test = { it % 4 == 0 },
                             xfTrue = map { i: Int -> i * 100 } +
                                      copy() +
                                      healthInspector("truA") +
                                      collect() +
                                      cat() +
                                      healthInspector("true"),
                             xfFalse = map { i: Int -> i / 4 } +
                                       distinct()) +
                      healthInspector("final", 4),
                 input = (3..9)
        ) assertEquals listOf(0, 1, 2, 400, 400, 400, 400, 800, 800, 800, 800)
    }

    @Test
    fun test_count() {
        intoList(xf = count(),
                 input = (0..99)) assertEquals listOf(100)
    }

    @Test
    fun test_collect() {
        val div2 = map { i: Int -> i / 2 }

        transduce(xf = div2 +
                       healthInspector() +
                       collect(collector = { mutableSetOf<Int>() }) +
                       healthInspector() +
                       cat<Int>() +
                       healthInspector(),
                  rf = { result, input: Int -> result.apply { add(input) } },
                  init = mutableListOf<Int>(),
                  input = (0..9)
         ) assertEquals listOf(0, 1, 2, 3, 4)

        intoList(xf = div2 +
                      collect(sort = { a, b -> a.compareTo(b) * -1 }) +
                      cat<Int>(),
                 input = (0..9)
        ) assertEquals listOf(4, 4, 3, 3, 2, 2, 1, 1, 0, 0)

        mutableListOf<Int>()
            .into(xf = div2 +
                       collect(collector = { mutableSetOf<Int>() },
                               sort = { a, b -> if (a > b) -1 else 1 }) +
                       cat<Int>(),
                  input = (0..9)
         ) assertEquals listOf(4, 3, 2, 1, 0)
    }

    @Test
    fun test_mux() {
        intoList(xf = mux(isEven@
                          { i: Int -> i % 2 == 0 }
                              wires map { i: Int -> i * 100 } +
                                    filter { it != 400 } +
                                    copy() +
                                    map { i: Int -> i / 10},

                          lessThan5@
                          { i: Int -> 5 > i }
                              wires map { it * 1000 },

                          leetify@
                          { i: Int -> i == 7 }
                              wires map { _: Int -> 1337 } +
                                    collect() + // asserting completion
                                    cat()),
                 input = (0..9)
        ) assertEquals listOf(0, 0, 1000, 20, 20, 3000, 60, 60, 80, 80, 1337)
    }

    @Test
    fun test_mux_promiscuous() {
        intoList(xf = mux({ i: Int -> i % 1 == 0 }
                              wires map { i: Int -> i / 2 } +
                                    distinct(),

                          { i: Int -> i % 2 == 0 }
                              wires map { it },

                          { i: Int -> i % 3 == 0 }
                              wires map { i: Int -> i * 100 } +
                                    copy(),
                          promiscuous = true),
                 input = (0..6)
        ) assertEquals listOf(input0@ 0, 0, 0, 0,
                              input1@
                              input2@ 1, 2,
                              input3@ 300, 300,
                              input4@ 2, 4,
                              input5@
                              input6@ 3, 6, 600, 600)
    }

    @Test
    fun test_mux_with_branch() {
        val branch400 = branch(test = { it == 400 },
                               xfTrue = map { it: Int -> it / 10 },
                               xfFalse = collect<Int>() + // releases on final result
                                         cat())
        intoList(xf = mux(isEven@
                          { i: Int -> i % 2 == 0 }
                              wires map { i: Int -> i * 100 } +
                                    branch400 +
                                    map { i: Int -> i / 10},

                          lessThan5@
                          { i: Int -> 5 > i }
                              wires map { it * 1000 },

                          leetify@
                          { i: Int -> i == 9 }
                              wires map { _: Int -> 1337 } +
                                    collect() + // asserting completion
                                    cat()) +
                      assertCompletionSanity(),
                 input = (0..9)
        ) assertEquals listOf(1000, 3000, 4, 0, 20, 60, 80, 1337)
    }

    @Test
    fun test_subduce() {
        intoList(xf = mapPair(map { i: Int -> i % 2 },
                              map { i: Int -> i * 2 }),
                 input = (0..5)
        ) assertEquals listOf(
            0 to 0,
            1 to 2,
            0 to 4,
            1 to 6,
            0 to 8,
            1 to 10)

        transduce(xf = mapPair(l = map { i: Int -> i % 2 } +
                                   map(Int::toString),
                               r = map { i: Int -> i * 2 }),
                  rf = addToMap(),
                  init = mutableMapOf<String, Int>(),
                  input = (0..9)
         ) assertEquals mapOf("0" to listOf(0, 4, 8, 12, 16),
                              "1" to listOf(2, 6, 10, 14, 18))
    }

    @Test
    fun test_sort() {
        intoList(xf = map { i: Int -> i / 2 } +
                      sort { a, b -> a.compareTo(b) * -1 },
                 input = (0..9)
        ) assertEquals listOf(4, 4, 3, 3, 2, 2, 1, 1, 0, 0)
    }

    @Test
    fun test_subduce_cat() {
        val left = map { i: Int -> i % 2 } +
                   map(Int::toString) +
                   assertCompletionSanity(tag = "left",
                                          resettable = true)

        val right = mapcat { i: Int -> (0..i).toList() } +
                    collect() +
                    cat() +
                    copy() +
                    assertCompletionSanity(tag = "right",
                                           resettable = true)

        transduce(xf = mapPair(l = left,
                               r = right + count()) +
                       assertCompletionSanity(tag = "outer"),
                  rf = addToMap(),
                  init = mutableMapOf<String, List<Int>>(),
                  input = (0..9)
         ) assertEquals mapOf("0" to listOf(2, 6, 10, 14, 18),
                              "1" to listOf(4, 8, 12, 16, 20))

        transduce(xf = mapPair(l = left,
                               r = right + distinct() + sum { i: Int -> i }) +
                       assertCompletionSanity(),
                  rf = addToMap(),
                  init = mutableMapOf<String, List<Int>>(),
                  input = (0..9)
         ) assertEquals mapOf("0" to listOf(0, 3, 10, 21, 36),
                              "1" to listOf(1, 6, 15, 28, 45))
    }

    @Test
    fun test_sum() {
        intoList(xf = filter { i: Int -> 4 > i } +
                      sum<Int>(),
                 input = (0..10)
        ) assertEquals listOf(1 + 2 + 3)

        transduce(xf = filter { i: Int -> i % 5 == 0 } +
                       sum { i: Int -> i / 5 },
                  rf = { result, input -> result + input },
                  init = 0,
                  input = (0..20)
         ) assertEquals (1 + 2 + 3 + 4)
    }
}

private fun <R, K, V> addToMap() = object : ReducingFunction<R, Pair<K, V>> {
    override fun apply(result: R,
                       input: Pair<K, V>,
                       reduced: AtomicBoolean): R {

        val key = input.first

        @Suppress("UNCHECKED_CAST")
        val group = result as MutableMap<K, MutableList<V>>
        val list = group[key] ?: mutableListOf<V>().apply { group[key] = this }
        list += input.second

        return result
    }
}



