package com.auralis.app.together

import javax.inject.Qualifier

/**
 * The single-threaded scope a session runs on.
 *
 * Confined to one thread on purpose: the message stream and the sync loop touch the same handful
 * of fields, and a race between them would surface as drift nobody could reproduce. Injected so a
 * test can supply its own scope and run an hour of a session in virtual time.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TogetherScope
