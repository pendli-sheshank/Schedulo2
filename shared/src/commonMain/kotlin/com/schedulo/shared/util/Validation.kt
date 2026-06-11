package com.schedulo.shared.util

fun isValidEmail(email: String): Boolean {
    val pattern = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    return pattern.matches(email.trim())
}

fun isStrongPassword(password: String): Boolean {
    return password.length >= 8 &&
        password.any { it.isLetter() } &&
        password.any { it.isDigit() }
}
