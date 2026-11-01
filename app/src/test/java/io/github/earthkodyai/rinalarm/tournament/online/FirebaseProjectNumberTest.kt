package io.github.earthkodyai.rinalarm.tournament.online

import io.github.earthkodyai.rinalarm.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Play Integrity needs the Cloud project number in FirebaseOptions; 1.0.0 left it out and no attestation got through. */
class FirebaseProjectNumberTest {
  @Test
  fun readsTheNumberOutOfAnAppId() {
    assertEquals("123456789012", FirebaseLeaderboard.projectNumber("1:123456789012:android:0123456789abcdef"))
  }

  @Test
  fun refusesSomethingElse() {
    assertThrows(IllegalArgumentException::class.java) { FirebaseLeaderboard.projectNumber("rinalarm-5cc21") }
  }

  @Test
  fun thisBuildsAppIdHasOne() {
    // Builds without a Firebase project (CI, clones) have no app id and never start Firebase.
    if (BuildConfig.FIREBASE_APP_ID.isEmpty()) return
    assertTrue(FirebaseLeaderboard.projectNumber(BuildConfig.FIREBASE_APP_ID).toLong() > 0)
  }
}
