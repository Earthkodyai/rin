package io.github.earthkodyai.rinalarm.tournament.online

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds are not installed from Play, so Play Integrity would refuse them. The debug provider logs a token
 * ("DebugAppCheckProvider" in logcat) that is registered once in the Firebase console, App Check, Manage debug tokens.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
