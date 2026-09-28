package io.github.earthkodyai.rinalarm.mission

import android.graphics.Rect
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.earthkodyai.rinalarm.R
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.awaitCancellation

/**
 * The back camera at 1x with ML Kit reading every code in view (task 3.2), used by the ring screen and the sticker
 * setup. Frames stay in memory and are dropped after reading: nothing is saved or sent (CLAUDE.md, photos). Zoom stays
 * at 1x on purpose, so the size bar ([QrScanPolicy.MIN_FRACTION]) means distance. [onCodes] runs on the camera's
 * analysis thread for every frame, with an empty list when there is no code. The flashlight follows [TorchControl].
 */
@Composable
fun QrScanner(
  onCodes: (List<SeenCode>) -> Unit,
  modifier: Modifier = Modifier,
  onTorch: (on: Boolean, auto: Boolean) -> Unit = { _, _ -> },
  onError: (Throwable) -> Unit = {},
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
  var camera by remember { mutableStateOf<Camera?>(null) }
  var torchOn by remember { mutableStateOf(false) }
  val torch = remember { TorchControl() }
  val latestOnCodes by rememberUpdatedState(onCodes)
  val latestOnTorch by rememberUpdatedState(onTorch)
  val latestOnError by rememberUpdatedState(onError)

  LaunchedEffect(lifecycleOwner) {
    val main = ContextCompat.getMainExecutor(context)
    val executor = Executors.newSingleThreadExecutor()
    val scanner = BarcodeScanning.getClient()
    val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
    val analysis =
      ImageAnalysis.Builder()
        .setResolutionSelector(
          ResolutionSelector.Builder()
            .setResolutionStrategy(
              ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
    var provider: ProcessCameraProvider? = null
    var analyzer: CodeAnalyzer? = null
    // ML Kit reports each frame on this executor. A frame still being read when the camera closes reports after the
    // executor has shut down: that rejection crashed the app on the device when setup's bed check ended (3.2 device
    // run), and would on the ring screen after a pass. Such a late frame now runs on ML Kit's own thread instead,
    // where it only closes its image.
    val callbacks = Executor { task ->
      try {
        executor.execute(task)
      } catch (_: RejectedExecutionException) {
        task.run()
      }
    }
    try {
      val cameras = ProcessCameraProvider.awaitInstance(context).also { provider = it }
      val bound = cameras.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
      bound.cameraControl.setZoomRatio(1f)
      val hasFlash = bound.cameraInfo.hasFlashUnit()
      analysis.setAnalyzer(
        executor,
        CodeAnalyzer(scanner, callbacks) { codes, luma ->
          latestOnCodes(codes)
          if (hasFlash && synchronized(torch) { torch.onFrame(luma) }) {
            bound.cameraControl.enableTorch(true)
            main.execute {
              torchOn = true
              latestOnTorch(true, true)
            }
          }
        }.also { analyzer = it },
      )
      camera = bound
      awaitCancellation()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "camera failed: ${e.javaClass.simpleName}")
      latestOnError(e)
    } finally {
      // First, so no frame reaches the screen that closed the camera (a pass must not be followed by more codes).
      analyzer?.active = false
      analysis.clearAnalyzer()
      provider?.unbind(preview, analysis)
      executor.execute { scanner.close() }
      executor.shutdown()
      camera = null
    }
  }

  Box(modifier) {
    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
    val flash = camera?.takeIf { it.cameraInfo.hasFlashUnit() }
    if (flash != null) {
      FilledTonalButton(
        onClick = {
          val on = synchronized(torch) { torch.toggle() }
          flash.cameraControl.enableTorch(on)
          torchOn = on
          latestOnTorch(on, false)
        },
        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
      ) {
        Icon(painterResource(if (torchOn) R.drawable.ic_flash_off else R.drawable.ic_flash_on), contentDescription = null)
        Text(stringResource(if (torchOn) R.string.qr_light_off else R.string.qr_light_on), Modifier.padding(start = 6.dp))
      }
    }
  }
}

private const val TAG = "QrLab"

/** Reads one frame at a time (the analysis keeps only the latest) and closes it once ML Kit is done with it. */
private class CodeAnalyzer(
  private val scanner: BarcodeScanner,
  private val executor: Executor,
  private val onFrame: (List<SeenCode>, Int) -> Unit,
) : ImageAnalysis.Analyzer {
  /** False once the camera is closing: frames still in flight only close their image. */
  @Volatile var active = true

  @OptIn(ExperimentalGetImage::class)
  override fun analyze(proxy: ImageProxy) {
    val media = proxy.image
    if (media == null) {
      proxy.close()
      return
    }
    val luma = meanLuma(proxy.planes[0].buffer, proxy.planes[0].rowStride, proxy.width, proxy.height)
    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(input).addOnCompleteListener(executor) { task ->
      val codes =
        try {
          if (active && task.isSuccessful) task.result.mapNotNull { it.toSeen(proxy.width, proxy.height) } else emptyList()
        } finally {
          proxy.close()
        }
      if (!active) return@addOnCompleteListener
      if (codes.isNotEmpty()) {
        // Sizes only, never the content: this log is for tuning the size bar (docs/spikes/3.2-qr.md).
        Log.d(TAG, "codes=${codes.size} fractions=${codes.joinToString(",") { "%.3f".format(java.util.Locale.ROOT, it.fraction) }} luma=$luma")
      }
      onFrame(codes, luma)
    }
  }

  /** Mean of every 16th pixel of every 16th row of the Y plane: plenty for "is the room dark". */
  private fun meanLuma(y: ByteBuffer, rowStride: Int, width: Int, height: Int): Int {
    var sum = 0L
    var count = 0
    for (row in 0 until height step 16) {
      for (col in 0 until width step 16) {
        val index = row * rowStride + col
        if (index < y.limit()) {
          sum += y.get(index).toInt() and 0xff
          count++
        }
      }
    }
    return if (count == 0) 0 else (sum / count).toInt()
  }
}

private fun Barcode.toSeen(width: Int, height: Int): SeenCode? {
  val content = rawValue ?: rawBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: return null
  val points = cornerPoints?.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }?.toFloatArray() ?: boundingBox?.corners() ?: return null
  val kind =
    when (format) {
      Barcode.FORMAT_QR_CODE -> CodeFormat.QR
      Barcode.FORMAT_CODE_128,
      Barcode.FORMAT_CODE_39,
      Barcode.FORMAT_CODE_93,
      Barcode.FORMAT_CODABAR,
      Barcode.FORMAT_EAN_13,
      Barcode.FORMAT_EAN_8,
      Barcode.FORMAT_ITF,
      Barcode.FORMAT_UPC_A,
      Barcode.FORMAT_UPC_E -> CodeFormat.BARCODE
      else -> CodeFormat.OTHER_2D
    }
  return SeenCode(content, kind, QrScanPolicy.fraction(points, width, height))
}

private fun Rect.corners() =
  floatArrayOf(left.toFloat(), top.toFloat(), right.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), left.toFloat(), bottom.toFloat())
