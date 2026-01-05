package com.kunzisoft.keepass.credentialprovider

enum class TypeMode(val forceUserVerification: Boolean = false) {
    DEFAULT,
    MAGIKEYBOARD,
    PASSWORD(forceUserVerification = true),
    PASSKEY(forceUserVerification = true),
    AUTOFILL
}