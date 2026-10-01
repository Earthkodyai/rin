package io.github.earthkodyai.rinalarm.ui.qrsetup

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.mission.CodeFormat
import io.github.earthkodyai.rinalarm.mission.QrScanPolicy
import io.github.earthkodyai.rinalarm.mission.QrScanner
import io.github.earthkodyai.rinalarm.mission.QrSticker
import io.github.earthkodyai.rinalarm.mission.ScanVerdict
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.SettingsLinks
import io.github.earthkodyai.rinalarm.ui.common.dateText
import java.time.ZoneId
import kotlin.math.roundToInt

/** Sticker setup for the QR mission (task 3.2). Every control is a tap target; nothing is typed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrSetupScreen(onClose: () -> Unit, viewModel: QrSetupViewModel = hiltViewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val activity = LocalActivity.current

  // D15: the camera permission is asked here, when the mission is set up. Re-read on resume: Settings may change it.
  fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  var cameraAllowed by remember { mutableStateOf(granted()) }
  var askedPermission by rememberSaveable { mutableStateOf(false) }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      askedPermission = true
      cameraAllowed = it
    }
  LifecycleResumeEffect(Unit) {
    cameraAllowed = granted()
    onPauseOrDispose {}
  }
  val allowCamera: () -> Unit = {
    if (askedPermission && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false) {
      SettingsLinks.open(activity, CheckId.MISSIONS, xiaomiFamily = false)
    } else {
      launcher.launch(Manifest.permission.CAMERA)
    }
  }
  val caption = stringResource(R.string.qr_sticker_caption)
  val shareTitle = stringResource(R.string.qr_share_title)
  val share = { payload: String -> StickerImage.share(context, payload, caption, shareTitle) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.qr_setup_title)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.editor_back))
          }
        },
      )
    }
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      when (state.step) {
        SetupStep.START -> Start(state, viewModel::makeSticker, viewModel::useExisting, viewModel::recheck, share)
        SetupStep.STICKER -> Sticker(checkNotNull(state.payload), { share(checkNotNull(state.payload)) }, viewModel::stickerPlaced)
        SetupStep.REGISTER -> {
          Body(
            stringResource(
              when {
                state.recheck -> R.string.qr_register_moved
                state.source == QrSticker.Source.GENERATED -> R.string.qr_register_generated
                else -> R.string.qr_register_existing
              }
            )
          )
          Camera(cameraAllowed, allowCamera, viewModel::onRegisterCodes, viewModel::onCameraError)
          Hint(
            when (state.hint) {
              ScanVerdict.TOO_FAR -> R.string.mission_qr_closer
              ScanVerdict.OTHER -> if (state.recheck) R.string.mission_qr_other else R.string.qr_register_not_new
              else -> R.string.mission_qr_aim
            }
          )
        }
        SetupStep.CONFIRM -> {
          Body(stringResource(R.string.qr_confirm, formatName(state.candidate?.format)))
          Button(onClick = viewModel::confirmCandidate, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.qr_confirm_use))
          }
          OutlinedButton(onClick = viewModel::rescan, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.qr_confirm_rescan))
          }
        }
        SetupStep.BED_INTRO -> {
          if (state.bedSpot == 0) {
            Body(pluralStringResource(R.plurals.qr_bed_intro, QrSetupViewModel.BED_SECONDS, QrSetupViewModel.BED_SECONDS))
          }
          Text(
            stringResource(R.string.qr_bed_spot, state.bedSpot + 1, BedSpot.entries.size, bedSpotName(BedSpot.entries[state.bedSpot])),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
          if (state.bedProblem) Hint(R.string.qr_bed_no_frames)
          Button(onClick = viewModel::startBedCheck, modifier = Modifier.fillMaxWidth().height(64.dp).testTag(BED_START_TAG)) {
            Text(stringResource(R.string.qr_bed_start), style = MaterialTheme.typography.titleMedium)
          }
        }
        SetupStep.BED_RUNNING -> {
          Body(stringResource(R.string.qr_bed_running))
          Camera(cameraAllowed, allowCamera, viewModel::onBedCodes, viewModel::onCameraError)
          Text(
            state.bedSecondsLeft.toString(),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          )
        }
        SetupStep.BED_FAILED -> {
          Body(stringResource(R.string.qr_bed_failed, bedSpotName(BedSpot.entries[state.bedSpot])))
          Button(onClick = viewModel::retryBedCheck, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.qr_bed_again))
          }
        }
        SetupStep.SAVING -> Unit
        SetupStep.DONE -> {
          val saved = checkNotNull(state.saved)
          Body(stringResource(R.string.qr_done))
          Text(measured(saved), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
          Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.qr_done_close)) }
        }
      }
    }
  }
}

@Composable
private fun Start(
  state: QrSetupUiState,
  onMake: () -> Unit,
  onExisting: () -> Unit,
  onRecheck: () -> Unit,
  share: (String) -> Unit,
) {
  Body(stringResource(R.string.qr_intro))
  val existing = state.existing
  if (existing != null) {
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          stringResource(
            R.string.qr_existing,
            formatName(existing.format),
            dateText(existing.setAt.atZone(ZoneId.systemDefault()).toLocalDate()),
          ),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(measured(existing), style = MaterialTheme.typography.bodySmall)
        Button(onClick = onRecheck, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.qr_moved)) }
        existing.payload?.let { payload ->
          OutlinedButton(onClick = { share(payload) }) { Text(stringResource(R.string.qr_share_again)) }
        }
      }
    }
    // Nothing is replaced until the new code passes its bed check, so these need no confirmation.
    Body(stringResource(R.string.qr_replace_note))
  }
  Button(onClick = onMake, modifier = Modifier.fillMaxWidth().height(64.dp)) {
    Text(stringResource(if (existing == null) R.string.qr_make else R.string.qr_make_new), style = MaterialTheme.typography.titleMedium)
  }
  OutlinedButton(onClick = onExisting, modifier = Modifier.fillMaxWidth().height(64.dp)) {
    Text(stringResource(if (existing == null) R.string.qr_use_existing else R.string.qr_use_other), style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
private fun Sticker(payload: String, onShare: () -> Unit, onPlaced: () -> Unit) {
  val image = remember(payload) { StickerImage.bitmap(payload).asImageBitmap() }
  Image(image, contentDescription = stringResource(R.string.qr_sticker_image), modifier = Modifier.size(240.dp), filterQuality = FilterQuality.None)
  Body(stringResource(R.string.qr_sticker_where))
  Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.qr_share)) }
  OutlinedButton(onClick = onPlaced, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.qr_placed)) }
}

@Composable
private fun Camera(allowed: Boolean, onAllow: () -> Unit, onCodes: (List<io.github.earthkodyai.rinalarm.mission.SeenCode>) -> Unit, onError: () -> Unit) {
  if (allowed) {
    QrScanner(onCodes, Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp)), onError = { onError() })
  } else {
    Body(stringResource(R.string.qr_camera_needed))
    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.mission_allow)) }
  }
}

@Composable
private fun Body(text: String) {
  Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Hint(text: Int) {
  Text(
    stringResource(text),
    style = MaterialTheme.typography.titleMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
  )
}

@Composable
private fun bedSpotName(spot: BedSpot): String =
  stringResource(
    when (spot) {
      BedSpot.LYING -> R.string.qr_spot_lying
      BedSpot.SITTING_UP -> R.string.qr_spot_sitting
      BedSpot.EDGE -> R.string.qr_spot_edge
    }
  )

@Composable
private fun formatName(format: CodeFormat?): String =
  stringResource(
    when (format) {
      CodeFormat.QR -> R.string.qr_kind_qr
      CodeFormat.BARCODE -> R.string.qr_kind_barcode
      else -> R.string.qr_kind_other
    }
  )

/** "Up close 34% · from bed not seen (bar 20%)": the numbers the size bar is tuned on (docs/spikes/3.2-qr.md). */
@Composable
private fun measured(sticker: QrSticker): String {
  fun pct(value: Float) = (value * 100).roundToInt()
  val bed = sticker.bedFraction?.let { stringResource(R.string.qr_bed_seen, pct(it)) } ?: stringResource(R.string.qr_bed_not_seen)
  return stringResource(R.string.qr_measured, pct(sticker.closeFraction), bed, pct(QrScanPolicy.MIN_FRACTION))
}

internal const val BED_START_TAG = "qr_bed_start"
