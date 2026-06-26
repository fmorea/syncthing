package com.fmorea.syncthing.fragments

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.fmorea.syncthing.R
import com.fmorea.syncthing.theme.ApplicationTheme

class DeviceIdDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val deviceName = args.getString(ARG_DEVICE_NAME)!!
        val deviceId = args.getString(ARG_DEVICE_ID)!!
        val isCurrentDevice = args.getBoolean(ARG_IS_CURRENT_DEVICE)

        val qrCode = generateQrCode(deviceId)

        return Dialog(requireContext()).apply {
            val onDismissRequest = { dismiss() }
            setContentView(
                ComposeView(context).apply {
                    setContent {
                        DeviceIdDialog(
                            onDismiss = onDismissRequest,
                            deviceName,
                            deviceId,
                            qrCode,
                            onCopy = { copyDeviceId(deviceId) },
                            onShare = { shareDeviceId(deviceId) },
                            isCurrentDevice,
                        )
                    }
                }
            )
        }
    }

    private fun generateQrCode(deviceId: String): Bitmap {
        val qrSize = 232
        val black = 0xFF000000
        val white = 0x00000000

        val bitMatrix = MultiFormatWriter()
            .encode(deviceId, BarcodeFormat.QR_CODE, qrSize, qrSize)
        val bitMap = createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)

        for (x in 0 until qrSize) {
            for (y in 0 until qrSize) {
                val pixel = if (bitMatrix[x, y]) black else white
                bitMap[x, y] = pixel.toInt()
            }
        }

        return bitMap
    }

    private fun copyDeviceId(id: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = ClipData.newPlainText(getString(R.string.device_id), id)
        clipboard.setPrimaryClip(clip)

        // Android 13+ shows a system confirmation automatically
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                requireContext(),
                R.string.device_id_copied_to_clipboard,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareDeviceId(deviceId: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, deviceId)
        }

        startActivity(
            Intent.createChooser(shareIntent, getString(R.string.share_device_id_chooser))
        )
    }


    companion object {
        private const val ARG_DEVICE_NAME = "device_name"
        private const val ARG_DEVICE_ID = "device_id"
        private const val ARG_IS_CURRENT_DEVICE = "is_current_device"

        fun show(
            fm: FragmentManager,
            deviceName: String,
            deviceId: String,
            isCurrentDevice: Boolean = false,
        ) {
            DeviceIdDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DEVICE_NAME, deviceName)
                    putString(ARG_DEVICE_ID, deviceId)
                    putBoolean(ARG_IS_CURRENT_DEVICE, isCurrentDevice)
                }
            }.show(fm, "DeviceIdDialog")
        }
    }
}

