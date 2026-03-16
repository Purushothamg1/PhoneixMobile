package com.phoenix.invoice.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val rupee = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

fun Double.toRupee(): String = rupee.format(this)
fun Double.toRupeeStr(): String = rupee.format(this)

private val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
private val sdfLong    = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

fun Long.toDateStr(): String     = sdfDisplay.format(Date(this))
fun Long.toDateLong(): String    = sdfLong.format(Date(this))
fun String.toEpoch(): Long?      = runCatching { sdfDisplay.parse(this)?.time }.getOrNull()

fun Context.toast(msg: String)   = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
fun Context.toastLong(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

fun shareViaWhatsApp(
    ctx: Context,
    phone: String,
    customerName: String,
    invoiceNumber: String,
    total: Double,
    pdfPath: String?,
    companyName: String
) {
    val msg = buildString {
        append("Hello $customerName,\n\n")
        append("Please find your invoice attached.\n\n")
        append("Invoice: $invoiceNumber\n")
        append("Amount: ${total.toRupee()}\n\n")
        append("Thank you for your business!\n— $companyName")
    }

    val cleanPhone = phone.replace(Regex("[^0-9+]"), "")

    // Try direct WhatsApp intent with PDF
    if (!pdfPath.isNullOrEmpty()) {
        val file = File(pdfPath)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type    = "application/pdf"
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, msg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try { ctx.startActivity(intent); return } catch (_: Exception) { }
            }
        }
    }

    // Fallback: wa.me link
    val url = if (cleanPhone.isNotEmpty()) {
        "https://wa.me/$cleanPhone?text=${URLEncoder.encode(msg, "UTF-8")}"
    } else {
        "https://wa.me/?text=${URLEncoder.encode(msg, "UTF-8")}"
    }
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        ctx.toast("WhatsApp not installed. PDF saved locally.")
    }
}

fun openPdfFile(ctx: Context, pdfPath: String) {
    val file = File(pdfPath)
    if (!file.exists()) { ctx.toast("PDF file not found"); return }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    try {
        ctx.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Open PDF"
        ))
    } catch (_: Exception) { ctx.toast("No PDF viewer installed") }
}
