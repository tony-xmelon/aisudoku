package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CandidateSetTest {

    @Test
    fun `all holds every digit and none holds nothing`() {
        assertEquals(9, CandidateSet.ALL.size)
        assertEquals((1..9).toList(), CandidateSet.ALL.digits())
        assertEquals(0, CandidateSet.NONE.size)
        assertTrue(CandidateSet.NONE.isEmpty)
    }

    @Test
    fun `membership follows addition and removal`() {
        val set = CandidateSet.NONE.plus(3).plus(7)
        assertTrue(3 in set)
        assertTrue(7 in set)
        assertFalse(4 in set)
        assertEquals(listOf(3, 7), set.digits())

        val fewer = set.minus(3)
        assertFalse(3 in fewer)
        assertTrue(7 in fewer)
    }

    @Test
    fun `removing a digit that is absent changes nothing`() {
        val set = CandidateSet.NONE.plus(5)
        assertEquals(set, set.minus(2))
    }

    @Test
    fun `single returns the only digit or null`() {
        assertEquals(6, CandidateSet.NONE.plus(6).single)
        assertNull(CandidateSet.NONE.plus(6).plus(2).single)
        assertNull(CandidateSet.NONE.single)
    }

    @Test
    fun `of builds a set from digits`() {
        assertEquals(listOf(2, 4, 9), CandidateSet.of(9, 2, 4).digits())
    }
}
