package com.schedulo.shared.util

import kotlin.random.Random

/**
 * Canonical rules for team invite codes, shared by both platforms.
 *
 * Invite codes are typed by hand far more often than they are pasted — read off
 * a colleague's screen, a photo, or over the phone — and the security rules
 * match them exactly, so a single mistyped character fails the join outright
 * with "no team found". Both the generated alphabet and the input handling are
 * therefore defined here once rather than per platform.
 */
object InviteCode {

    /** Number of characters in every generated code. */
    const val LENGTH = 6

    /**
     * Characters a newly generated code may contain.
     *
     * Excludes `I`, `O`, `0` and `1`: the pairs I/1 and O/0 are the ones people
     * actually confuse when copying a code by eye, and because codes are matched
     * exactly, a confused character is an unrecoverable failure for the joiner.
     * Dropping all four means a generated code can never contain either half of
     * a confusable pair.
     */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Generate a fresh code. [random] is injectable so tests can be deterministic. */
    fun generate(random: Random = Random.Default): String =
        buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    /**
     * Canonicalize a code the user typed or pasted, so presentation differences
     * never decide whether a join succeeds. Case is folded up, and anything that
     * isn't a letter or digit is dropped — that covers the separators people
     * actually introduce: surrounding whitespace, "ABC 123" typed in two halves,
     * a "ABC-123" style hyphen, and the newline that rides along with a copied
     * line of text.
     *
     * Confusable characters are deliberately *not* folded onto one another.
     * Codes issued before [ALPHABET] excluded them are still live and can
     * legitimately contain `I`, `O`, `0` or `1`, so rewriting those characters
     * would break the very codes the exclusion was meant to protect.
     */
    fun normalize(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /**
     * Whether [code] could be a real invite code. Accepts any alphanumeric code
     * of the right length, not just [ALPHABET], so codes issued earlier still
     * pass. Expects an already-[normalize]d string.
     */
    fun isWellFormed(code: String): Boolean =
        code.length == LENGTH && code.all { it in 'A'..'Z' || it in '0'..'9' }
}
