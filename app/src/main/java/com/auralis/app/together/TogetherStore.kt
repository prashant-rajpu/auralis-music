package com.auralis.app.together

import kotlinx.coroutines.flow.StateFlow

/**
 * What a session needs to remember between launches.
 *
 * An interface because the implementation is `SharedPreferences` and therefore needs a `Context`,
 * and the session is the part worth testing — feeding it a real Android preferences file to check
 * a drift correction would be absurd.
 */
interface TogetherStore {
    val lastRoom: StateFlow<LastRoom?>

    /** This phone's credential. The relay treats a returning token as the same person. */
    fun memberToken(): String

    fun rememberRoom(code: String, token: String, partnerName: String)

    fun forgetRoom()
}
