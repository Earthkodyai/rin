package io.github.earthkodyai.rinalarm.ui.qrsetup

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.RinPage
import io.github.earthkodyai.rinalarm.ui.common.dateText
import io.github.earthkodyai.rinalarm.ui.common.rinCard
import io.github.earthkodyai.rinalarm.ui.common.sticker
import java.time.ZoneId
import kotlin.math.roundToInt

/** Sticker setup for the QR mission (task 3.2). Every control is a tap target; nothing is typed. */
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

  RinPage(stringResource(R.string.qr_setup_title), onClose) {
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
          PillButton(stringResource(R.string.qr_confirm_use), viewModel::confirmCandidate, Modifier.fillMaxWidth())
          QuietPillButton(stringResource(R.string.qr_confirm_rescan), viewModel::rescan, Modifier.fillMaxWidth())
        }
        SetupStep.BED_INTRO -> {
          if (state.bedSpot == 0) {
            Body(pluralStringResource(R.plurals.qr_bed_intro, QrSetupViewModel.BED_SECONDS, QrSetupViewModel.BED_SECONDS))
          }
          Text(
            stringResource(R.string.qr_bed_spot, state.bedSpot + 1, BedSpot.entries.size, bedSpotName(BedSpot.entries[state.bedSpot])),
            style = MaterialTheme.typography.titleLarge,
            color = RinTheme.palette.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
          if (state.bedProblem) Hint(R.string.qr_bed_no_frames)
          PillButton(stringResource(R.string.qr_bed_start), viewModel::startBedCheck, Modifier.fillMaxWidth().testTag(BED_START_TAG))
        }
        SetupStep.BED_RUNNING -> {
          Body(stringResource(R.string.qr_bed_running))
          Camera(cameraAllowed, allowCamera, viewModel::onBedCodes, viewModel::onCameraError)
          Text(
            state.bedSecondsLeft.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = RinTheme.palette.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally).semantics { liveRegion = LiveRegionMode.Polite },
          )
        }
        SetupStep.BED_FAILED -> {
          Body(stringResource(R.string.qr_bed_failed, bedSpotName(BedSpot.entries[state.bedSpot])))
          PillButton(stringResource(R.string.qr_bed_again), viewModel::retryBedCheck, Modifier.fillMaxWidth())
        }
        SetupStep.SAVING -> Unit
        SetupStep.DONE -> {
          val saved = checkNotNull(state.saved)
          Body(stringResource(R.string.qr_done))
          Text(measured(saved), style = MaterialTheme.typography.bodyMedium, color = RinTheme.palette.muted, modifier = Modifier.fillMaxWidth())
          PillButton(stringResource(R.string.qr_done_close), onClose, Modifier.fillMaxWidth())
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
    Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          stringResource(
            R.string.qr_existing,
            formatName(existing.format),
            dateText(existing.setAt.atZone(ZoneId.systemDefault()).toLocalDate()),
          ),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = RinTheme.palette.ink,
        )
        Text(measured(existing), style = MaterialTheme.typography.bodySmall, color = RinTheme.palette.muted)
        PillButton(stringResource(R.string.qr_moved), onRecheck, Modifier.fillMaxWidth())
        existing.payload?.let { payload -> QuietPillButton(stringResource(R.string.qr_share_again), { share(payload) }, height = 46.dp) }
    }
    // Nothing is replaced until the new code passes its bed check, so these need no confirmation.
    Body(stringResource(R.string.qr_replace_note))
  }
  PillButton(stringResource(if (existing == null) R.string.qr_make else R.string.qr_make_new), onMake, Modifier.fillMaxWidth())
  QuietPillButton(stringResource(if (existing == null) R.string.qr_use_existing else R.string.qr_use_other), onExisting, Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.Sticker(payload: String, onShare: () -> Unit, onPlaced: () -> Unit) {
  val image = remember(payload) { StickerImage.bitmap(payload).asImageBitmap() }
  // White in both looks: the code is printed on white, and a scanner wants the quiet margin around it.
  Box(Modifier.align(Alignment.CenterHorizontally).sticker(fill = Color.White, radius = 22.dp).padding(16.dp)) {
    Image(image, contentDescription = stringResource(R.string.qr_sticker_image), modifier = Modifier.size(240.dp), filterQuality = FilterQuality.None)
  }
  Body(stringResource(R.string.qr_sticker_where))
  PillButton(stringResource(R.string.qr_share), onShare, Modifier.fillMaxWidth())
  QuietPillButton(stringResource(R.string.qr_placed), onPlaced, Modifier.fillMaxWidth())
}

@Composable
private fun Camera(allowed: Boolean, onAllow: () -> Unit, onCodes: (List<io.github.earthkodyai.rinalarm.mission.SeenCode>) -> Unit, onError: () -> Unit) {
  if (allowed) {
    QrScanner(onCodes, Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(22.dp)), onError = { onError() })
  } else {
    Body(stringResource(R.string.qr_camera_needed))
    PillButton(stringResource(R.string.mission_allow), onAllow, Modifier.fillMaxWidth())
  }
}

@Composable
private fun Body(text: String) {
  Text(text, style = MaterialTheme.typography.bodyLarge, color = RinTheme.palette.ink, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Hint(text: Int) {
  Text(
    stringResource(text),
    style = MaterialTheme.typography.titleMedium,
    color = RinTheme.palette.ink,
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
