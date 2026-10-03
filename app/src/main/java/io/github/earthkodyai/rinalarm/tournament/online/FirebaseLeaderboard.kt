package io.github.earthkodyai.rinalarm.tournament.online

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.BuildConfig
import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentScore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * The board on Firebase's free Spark plan (ADR 0008): Firestore `boards/{game}/scores/{uid}`, signed in anonymously,
 * with App Check (Play Integrity; the debug provider on debug builds). firebase/firestore.rules decides what is
 * accepted; this class only asks.
 *
 * Firebase starts here, on the first call, and never at app start: its start-up provider is removed in the manifest, so
 * the ring path neither waits for nor touches it. Nothing is cached on disk (memory cache), and reads always go to the
 * server, so an offline phone gets an error instead of an empty board.
 */
@Singleton
class FirebaseLeaderboard @Inject constructor(@ApplicationContext private val context: Context) : Leaderboard {
  override val configured: Boolean =
    BuildConfig.FIREBASE_PROJECT_ID.isNotEmpty() && BuildConfig.FIREBASE_APP_ID.isNotEmpty() && BuildConfig.FIREBASE_API_KEY.isNotEmpty()

  private val app: FirebaseApp by lazy {
    check(configured) { "no Firebase project in this build" }
    FirebaseApp.getApps(context).firstOrNull()
      ?: FirebaseApp.initializeApp(
          context,
          FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .build(),
        )
        .also { FirebaseAppCheck.getInstance(it).installAppCheckProviderFactory(appCheckProviderFactory()) }
  }

  private val auth: FirebaseAuth by lazy {
    FirebaseAuth.getInstance(app).apply { emulator?.let { useEmulator(it, AUTH_PORT) } }
  }

  private val db: FirebaseFirestore by lazy {
    FirebaseFirestore.getInstance(app).apply {
      emulator?.let { useEmulator(it, FIRESTORE_PORT) }
      firestoreSettings =
        FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
    }
  }

  private val emulator: String? = BuildConfig.FIREBASE_EMULATOR.ifEmpty { null }

  private val signIn = Mutex()

  /** This player's anonymous id, signing in the first time. It lasts until the app's data is cleared. */
  private suspend fun uid(): String =
    signIn.withLock { auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().await().user).uid }

  private fun scores(game: TournamentGame): CollectionReference =
    db.collection("boards").document(game.stored).collection("scores")

  override suspend fun page(game: TournamentGame): BoardPage = withTimeout(TIMEOUT_MS) {
    val me = uid()
    val scores = scores(game)
    val top =
      scores
        .orderBy("levels", Query.Direction.DESCENDING)
        .orderBy("timeMs", Query.Direction.ASCENDING)
        .limit(OnlineLimits.TOP.toLong())
        .get(Source.SERVER)
        .await()
        .documents
        .mapNotNull(::row)
    val index = top.indexOfFirst { it.id == me }
    if (index >= 0) return@withTimeout BoardPage(top, top[index], index + 1)
    val mine = scores.document(me).get(Source.SERVER).await().let(::row) ?: return@withTimeout BoardPage(top, null, null)
    // Below the top rows: one more than everyone ahead (more levels, or as many in less time).
    val more = scores.whereGreaterThan("levels", mine.levels).count().get(AggregateSource.SERVER).await().count
    val faster =
      scores.whereEqualTo("levels", mine.levels).whereLessThan("timeMs", mine.timeMs).count().get(AggregateSource.SERVER).await().count
    BoardPage(top, mine, (more + faster + 1).toInt())
  }

  override suspend fun post(score: TournamentScore, name: String, university: String?) {
    withTimeout(TIMEOUT_MS) {
      scores(score.game)
        .document(uid())
        .set(
          mapOf(
            "name" to name,
            "uni" to university,
            "levels" to score.levels.toLong(),
            "timeMs" to score.timeMs,
            "at" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    }
  }

  override suspend fun withdraw() {
    // Never signed in on this phone: nothing was ever posted from it.
    val me = auth.currentUser?.uid ?: return
    withTimeout(TIMEOUT_MS) { TournamentGame.entries.forEach { scores(it).document(me).delete().await() } }
  }

  override suspend fun report(game: TournamentGame, rowId: String) {
    withTimeout(TIMEOUT_MS) {
      val me = uid()
      try {
        db.collection("reports")
          .document("${me}_${game.stored}_$rowId")
          .set(mapOf("game" to game.stored, "target" to rowId, "at" to FieldValue.serverTimestamp()))
          .await()
      } catch (e: FirebaseFirestoreException) {
        // The rules refuse a second report of the same row: it is reported already, which is what was asked.
        if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
      }
    }
  }

  private fun row(doc: DocumentSnapshot): BoardRow? {
    if (!doc.exists()) return null
    val levels = doc.getLong("levels") ?: return null
    val timeMs = doc.getLong("timeMs") ?: return null
    return BoardRow(doc.id, doc.getString("name").orEmpty(), doc.getString("uni"), levels.toInt(), timeMs)
  }

  private companion object {
    /** A write waits for the server's answer; offline it would wait for ever. */
    const val TIMEOUT_MS = 15_000L
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
  }
}
