package io.github.earthkodyai.rinalarm

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runs every instrumented test in HiltTestApplication, so receiver tests get the graph from testing/TestModules.kt
 * (in-memory database, fake ring outputs). Tests that build their own objects are unaffected.
 */
class HiltTestRunner : AndroidJUnitRunner() {
  override fun newApplication(classLoader: ClassLoader?, className: String?, context: Context?): Application =
    super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
