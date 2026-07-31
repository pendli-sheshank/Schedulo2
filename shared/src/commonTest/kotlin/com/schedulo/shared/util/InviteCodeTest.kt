package com.schedulo.shared.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteCodeTest {

    @Test
    fun generatedCodeHasTheExpectedLength() {
        assertEquals(InviteCode.LENGTH, InviteCode.generate(Random(1)).length)
    }

    @Test
    fun generatedCodesNeverContainConfusableCharacters() {
        // The pairs I/1 and O/0 are what people mistype when copying a code by
        // eye; a generated code must not contain either half of either pair.
        repeat(500) { seed ->
            val code = InviteCode.generate(Random(seed))
            assertTrue(
                code.none { it in "IO01" },
                "generated code $code contains a confusable character"
            )
        }
    }

    @Test
    fun generatedCodesAreDrawnOnlyFromTheAlphabet() {
        repeat(200) { seed ->
            val code = InviteCode.generate(Random(seed))
            assertTrue(code.all { it in InviteCode.ALPHABET }, "unexpected character in $code")
        }
    }

    @Test
    fun generationIsDeterministicForAGivenSeed() {
        assertEquals(InviteCode.generate(Random(42)), InviteCode.generate(Random(42)))
    }

    @Test
    fun normalizeUppercasesLowercaseInput() {
        assertEquals("ABC234", InviteCode.normalize("abc234"))
    }

    @Test
    fun normalizeStripsSurroundingWhitespaceAndNewlines() {
        assertEquals("ABC234", InviteCode.normalize("  ABC234\n"))
    }

    @Test
    fun normalizeStripsSeparatorsPeopleTypeOrPaste() {
        // "ABC 234" typed in two halves, and a hyphenated "ABC-234".
        assertEquals("ABC234", InviteCode.normalize("ABC 234"))
        assertEquals("ABC234", InviteCode.normalize("ABC-234"))
    }

    @Test
    fun normalizeLeavesConfusableCharactersAlone() {
        // Codes issued before the alphabet excluded these are still live, so
        // normalize must not rewrite them onto their look-alikes.
        assertEquals("I0O1AB", InviteCode.normalize("i0o1ab"))
    }

    @Test
    fun normalizeIsIdempotent() {
        val once = InviteCode.normalize(" abc-234 ")
        assertEquals(once, InviteCode.normalize(once))
    }

    @Test
    fun wellFormedAcceptsAGeneratedCode() {
        assertTrue(InviteCode.isWellFormed(InviteCode.generate(Random(7))))
    }

    @Test
    fun wellFormedAcceptsLegacyCodesContainingConfusableCharacters() {
        assertTrue(InviteCode.isWellFormed("I0O1AB"))
    }

    @Test
    fun wellFormedRejectsWrongLengthOrStrayCharacters() {
        assertFalse(InviteCode.isWellFormed("ABC23"))
        assertFalse(InviteCode.isWellFormed("ABC2345"))
        assertFalse(InviteCode.isWellFormed("ABC-23"))
        assertFalse(InviteCode.isWellFormed(""))
    }
}
