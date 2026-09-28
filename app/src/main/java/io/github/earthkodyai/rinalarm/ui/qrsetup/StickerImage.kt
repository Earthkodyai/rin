package io.github.earthkodyai.rinalarm.ui.qrsetup

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import io.nayuki.qrcodegen.QrCode
import java.io.File

/** The generated sticker as an image to print (task 3.2): the QR with its quiet zone, and a line saying what it is. */
object StickerImage {
  private const val MODULE_PX = 24
  private const val QUIET_MODULES = 4
  private const val CAPTION_PX = 96

  /** Medium error correction: a smudged or slightly curled sticker still reads, and the code stays version 2. */
  fun encode(payload: String): QrCode = QrCode.encodeText(payload, QrCode.Ecc.MEDIUM)

  fun bitmap(payload: String, caption: String? = null): Bitmap {
    val qr = encode(payload)
    val side = (qr.size + 2 * QUIET_MODULES) * MODULE_PX
    val bitmap = createBitmap(side, side + if (caption != null) CAPTION_PX else 0)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val black = Paint().apply { color = Color.BLACK }
    for (y in 0 until qr.size) {
      for (x in 0 until qr.size) {
        if (!qr.getModule(x, y)) continue
        val left = ((x + QUIET_MODULES) * MODULE_PX).toFloat()
        val top = ((y + QUIET_MODULES) * MODULE_PX).toFloat()
        canvas.drawRect(left, top, left + MODULE_PX, top + MODULE_PX, black)
      }
    }
    if (caption != null) {
      val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 44f
        textAlign = Paint.Align.CENTER
      }
      canvas.drawText(caption, side / 2f, side + CAPTION_PX / 2f, text)
    }
    return bitmap
  }

  /** Writes the image to the app's cache and opens the share sheet (print, send to a computer, a print shop...). */
  fun share(context: Context, payload: String, caption: String, chooserTitle: String) {
    val dir = File(context.cacheDir, "sticker").apply { mkdirs() }
    val file = File(dir, "rin-sticker.png")
    file.outputStream().use { bitmap(payload, caption).compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    context.startActivity(Intent.createChooser(send, chooserTitle))
  }
}
