package samples

import net.onedaybeard.transducers.*

typealias Sample = org.junit.Test

class Samples {
    @Sample
    fun branch_a() {
        intoList(xf = copy<Int>() +
                      branch(test = { it % 4 == 0 },
                             xfTrue  = map { i: Int -> i * 100 },
                             xfFalse = map { i: Int -> i / 4 } +
                                       distinct() +
                                       map(Int::toString)),
                 input = (1..9)
        ) assertEquals listOf("0", 400, 400, "1", 800, 800, "2")
    }

    @Sample
    fun collect_a() {
        // using collect for sorting input
        transduce(xf = map { i: Int -> i / 3 } +
                       distinct() +
                       collect(sort = { a, b -> a.compareTo(b) * -1 }),
                  rf = { _, input -> input.toList() },
                  init = listOf<Int>(),
                  input = (0..20)
        ) assertEquals listOf(6, 5, 4, 3, 2, 1, 0)
    }

    @Sample
    fun collect_b() {
        // collecting into a set, releasing sorted iterable
        transduce(xf = map { i: Int -> i / 3 } +
                       collect(sort = { a, b -> a.compareTo(b) * -1 },
                               collector = { mutableSetOf<Int>() }),
                  rf = { _, input -> input.toList() },
                  init = listOf<Int>(),
                  input = (0..20)
        ) assertEquals listOf(6, 5, 4, 3, 2, 1, 0)
    }


    @Sample
    fun mux_a() {
        listOf(xf = mux(isEven@ // labels only for readability
                        { i: Int -> i % 2 == 0 }
                                wires map { i: Int -> i * 100 } +
                                      filter { it != 400 },

                        leetify@
                        { i: Int -> i == 3 }
                                wires map { _: Int -> 1337 } +
                                      collect() + // releases on final result
                                      cat()),
               input = (0..9)
        ) assertEquals listOf(0, 200, 600, 800, 1337)
    }

    @Sample
    fun mux_b() {
        // promiscuous = true: input goes through all applicable transducers
        listOf(xf = mux({ i: Int -> i % 1 == 0 }
                                wires map { i: Int -> i / 2 } +
                                      distinct(),

                        { i: Int -> i % 2 == 0 }
                                wires map { it },

                        { i: Int -> i % 3 == 0 }
                                wires map { i: Int -> i * 100 } +
                                      copy(),
                        promiscuous = true),
               input = (0..6)
        ) assertEquals listOf(0, 0, 0, 0,     // input: 0
                                              // input: 1
                              1, 2,           // input: 2
                              300, 300,       // input: 3
                              2, 4,           // input: 4
                                              // input: 5
                              3, 6, 600, 600) // input: 6
    }

    @Sample
    fun sum_a() {
        // collecting single result into list
        listOf(xf = filter<Int> { 4 > it } + sum<Int>(),
               input = (0..10)
        ) assertEquals listOf(1 + 2 + 3)
    }

    @Sample
    fun sum_b() {
        val xf = filter { i: Int -> i % 5 == 0 } +
                 sum { i: Int -> i / 5 }

        transduce(xf = xf,
                  rf = { result, input -> result + input },
                  init = 0,
                  input = (0..20)
         ) assertEquals (1 + 2 + 3 + 4)
    }
}