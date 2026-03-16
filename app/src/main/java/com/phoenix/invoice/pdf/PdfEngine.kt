package com.phoenix.invoice.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.phoenix.invoice.data.db.entities.CompanyProfile
import com.phoenix.invoice.data.db.entities.Invoice
import com.phoenix.invoice.data.db.entities.InvoiceItem
import com.phoenix.invoice.utils.toDateLong
import com.phoenix.invoice.utils.toRupee
import java.io.File
import java.io.FileOutputStream

object PdfEngine {

    private const val PW = 595f   // A4 width  pts
    private const val PH = 842f   // A4 height pts
    private const val ML = 40f    // margin left
    private const val MR = 40f    // margin right
    private const val CW = PW - ML - MR   // content width = 515

    // ─────────────────────────────────────────────────────────────────────
    fun generate(ctx: Context, co: CompanyProfile, inv: Invoice,
                 items: List<InvoiceItem>): String? {
        return runCatching {
            val doc  = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PW.toInt(), PH.toInt(), 1).create())
            val c    = page.canvas
            when (inv.template) {
                1    -> drawGstPro(c, co, inv, items, ctx)
                2    -> drawMinimal(c, co, inv, items, ctx)
                else -> drawModern(c, co, inv, items, ctx)
            }
            doc.finishPage(page)
            val dir  = File(ctx.getExternalFilesDir(null), "PhoenixInvoices").also { it.mkdirs() }
            val file = File(dir, "Invoice-${inv.invoiceNumber}.pdf")
            doc.writeTo(FileOutputStream(file))
            doc.close()
            file.absolutePath
        }.getOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEMPLATE 0 — MODERN DARK  (navy header, red accent)
    // ─────────────────────────────────────────────────────────────────────
    private fun drawModern(c: Canvas, co: CompanyProfile, inv: Invoice,
                           items: List<InvoiceItem>, ctx: Context) {
        // Background
        c.drawRect(0f, 0f, PW, PH, fp(0xFFF8F9FCL))

        // Header band
        c.drawRect(0f, 0f, PW, 125f, fp(0xFF16213EL))
        // Red accent strip
        c.drawRect(0f, 125f, PW, 131f, fp(0xFFE94560L))

        // Company logo
        var logoEndX = ML
        loadLogo(ctx, co.logoUri, 75, 75)?.let {
            c.drawRoundRect(RectF(ML, 18f, ML + 75f, 93f), 8f, 8f, fp(0xFFFFFFFFL))
            c.drawBitmap(it, ML + 2f, 20f, smoothPaint())
            logoEndX = ML + 84f
        }

        // Company name + info
        c.drawText(co.name.ifEmpty { "Your Business" }, logoEndX, 44f,
            tp(0xFFFFFFFFL, 18f, true))
        c.drawText(co.address, logoEndX, 58f, tp(0xFFB0BEC5L, 8f))
        if (co.phone.isNotEmpty())
            c.drawText("✆ ${co.phone}   ✉ ${co.email}", logoEndX, 70f, tp(0xFFB0BEC5L, 8f))
        if (co.gstin.isNotEmpty())
            c.drawText("GSTIN: ${co.gstin}", logoEndX, 82f, tp(0xFFFFB74DL, 8.5f, true))

        // INVOICE label (right)
        val rx = PW - MR
        c.drawText("INVOICE", rx, 46f, tp(0xFFE94560L, 28f, true, Paint.Align.RIGHT))
        c.drawText(inv.invoiceNumber, rx, 62f, tp(0xFFFFFFFFL, 10f, false, Paint.Align.RIGHT))
        c.drawText(inv.invoiceDate.toDateLong(), rx, 74f, tp(0xFF90A4AEL, 8f, false, Paint.Align.RIGHT))
        inv.dueDate?.let {
            c.drawText("Due: ${it.toDateLong()}", rx, 86f, tp(0xFF90A4AEL, 8f, false, Paint.Align.RIGHT))
        }

        // Bill To
        var y = 150f
        c.drawText("BILL TO", ML, y, tp(0xFFE94560L, 8f, true))
        y += 14f
        c.drawText(inv.customerName, ML, y, tp(0xFF1A1A2EL, 14f, true))
        y += 14f
        if (inv.customerAddress.isNotEmpty()) { c.drawText(inv.customerAddress, ML, y, tp(0xFF607D8BL, 8f)); y += 12f }
        if (inv.customerPhone.isNotEmpty())   { c.drawText("Ph: ${inv.customerPhone}", ML, y, tp(0xFF607D8BL, 8f)); y += 12f }
        if (inv.customerGstin.isNotEmpty())   { c.drawText("GSTIN: ${inv.customerGstin}", ML, y, tp(0xFF607D8BL, 8f)); y += 12f }

        // Separator
        y += 6f
        c.drawLine(ML, y, PW - MR, y, sp(0xFFE0E6EDL, 1f))
        y += 14f

        y = drawItemTable(c, items, y, 0xFF16213EL)
        y = drawTotals(c, inv, y, 0xFF16213EL, 0xFFE94560L)
        drawNotes(c, inv, y)
        drawFooter(c, co.name, 0xFF16213EL)
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEMPLATE 1 — GST PROFESSIONAL (orange accent, formal layout)
    // ─────────────────────────────────────────────────────────────────────
    private fun drawGstPro(c: Canvas, co: CompanyProfile, inv: Invoice,
                           items: List<InvoiceItem>, ctx: Context) {
        c.drawRect(0f, 0f, PW, PH, fp(0xFFFAFAFAL))

        // Top orange stripe
        c.drawRect(0f, 0f, PW, 7f, fp(0xFFE65100L))

        // Page border
        c.drawRect(ML / 2, 12f, PW - MR / 2, PH - 20f, sp(0xFFE0E0E0L, 0.8f))

        // Title
        var y = 22f
        c.drawText("TAX INVOICE", PW / 2f, y + 16f, tp(0xFFE65100L, 20f, true, Paint.Align.CENTER))
        y += 36f

        // Logo
        var lx = ML
        loadLogo(ctx, co.logoUri, 60, 60)?.let { c.drawBitmap(it, ML, y, smoothPaint()); lx = ML + 68f }

        c.drawText(co.name.ifEmpty { "Your Company" }, lx, y + 14f, tp(0xFF212121L, 14f, true))
        var ly = y + 28f
        if (co.address.isNotEmpty()) { c.drawText(co.address, lx, ly, tp(0xFF616161L, 8f)); ly += 12f }
        if (co.phone.isNotEmpty())   { c.drawText("Ph: ${co.phone}", lx, ly, tp(0xFF616161L, 8f)); ly += 12f }
        if (co.email.isNotEmpty())   { c.drawText(co.email, lx, ly, tp(0xFF616161L, 8f)); ly += 12f }
        if (co.gstin.isNotEmpty())   { c.drawText("GSTIN: ${co.gstin}", lx, ly, tp(0xFF212121L, 9f, true)) }

        // Meta right
        val rx = PW - MR
        fun metaRow(label: String, value: String, ry: Float) {
            c.drawText(label, rx - 80f, ry, tp(0xFF757575L, 9f))
            c.drawText(value, rx, ry, tp(0xFF212121L, 10f, true, Paint.Align.RIGHT))
        }
        metaRow("Invoice No:", inv.invoiceNumber, y + 14f)
        metaRow("Date:", inv.invoiceDate.toDateLong(), y + 28f)
        inv.dueDate?.let { metaRow("Due Date:", it.toDateLong(), y + 42f) }

        y = 128f
        c.drawLine(ML, y, PW - MR, y, sp(0xFFE65100L, 1.5f))
        y += 12f

        // Customer box
        val bxR = RectF(ML, y, PW / 2f - 8f, y + 68f)
        c.drawRoundRect(bxR, 6f, 6f, fp(0xFFFFF3E0L))
        c.drawRoundRect(bxR, 6f, 6f, sp(0xFFFFCC80L, 1f))
        c.drawText("BILL TO", ML + 10f, y + 14f, tp(0xFFE65100L, 8f, true))
        c.drawText(inv.customerName, ML + 10f, y + 28f, tp(0xFF212121L, 12f, true))
        if (inv.customerAddress.isNotEmpty()) c.drawText(inv.customerAddress, ML + 10f, y + 42f, tp(0xFF616161L, 8f))
        if (inv.customerPhone.isNotEmpty())   c.drawText("Ph: ${inv.customerPhone}", ML + 10f, y + 54f, tp(0xFF616161L, 8f))
        if (inv.customerGstin.isNotEmpty())
            c.drawText("GSTIN: ${inv.customerGstin}", PW / 2f + 8f, y + 28f, tp(0xFF616161L, 8f))

        y += 78f
        c.drawLine(ML, y, PW - MR, y, sp(0xFFE65100L, 1.5f))
        y += 14f

        y = drawItemTable(c, items, y, 0xFFBF360CL)
        y = drawTotals(c, inv, y, 0xFFE65100L, 0xFFE65100L)
        drawNotes(c, inv, y)
        drawFooter(c, co.name, 0xFFE65100L)
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEMPLATE 2 — MINIMAL CLEAN (teal, ultra-clean)
    // ─────────────────────────────────────────────────────────────────────
    private fun drawMinimal(c: Canvas, co: CompanyProfile, inv: Invoice,
                            items: List<InvoiceItem>, ctx: Context) {
        c.drawRect(0f, 0f, PW, PH, fp(0xFFFFFFFFL))

        // Teal left sidebar
        c.drawRect(0f, 0f, 8f, PH, fp(0xFF00695CL))

        var y = ML
        // Logo (centered top)
        loadLogo(ctx, co.logoUri, 56, 56)?.let {
            c.drawBitmap(it, ML, y, smoothPaint()); y += 64f
        }

        c.drawText(co.name.ifEmpty { "Your Company" }, ML, y + 18f, tp(0xFF00695CL, 20f, true))
        y += 30f
        c.drawText(co.address, ML, y, tp(0xFF607D8BL, 8f))
        y += 12f
        if (co.phone.isNotEmpty()) { c.drawText("${co.phone}  |  ${co.email}", ML, y, tp(0xFF607D8BL, 8f)); y += 12f }
        if (co.gstin.isNotEmpty()) { c.drawText("GSTIN: ${co.gstin}", ML, y, tp(0xFF607D8BL, 8f)); y += 12f }

        y += 8f
        c.drawLine(ML, y, PW - MR, y, sp(0xFF00695CL, 1.5f))
        y += 16f

        // Invoice info
        c.drawText("INVOICE", ML, y, tp(0xFF00695CL, 10f, true))
        c.drawText(inv.invoiceNumber, ML + 60f, y, tp(0xFF212121L, 10f, true))
        c.drawText(inv.invoiceDate.toDateLong(), PW - MR, y, tp(0xFF607D8BL, 9f, false, Paint.Align.RIGHT))
        y += 16f
        c.drawText("Customer:", ML, y, tp(0xFF607D8BL, 9f))
        c.drawText(inv.customerName, ML + 60f, y, tp(0xFF212121L, 11f, true))
        if (inv.customerPhone.isNotEmpty())
            c.drawText(inv.customerPhone, PW - MR, y, tp(0xFF607D8BL, 9f, false, Paint.Align.RIGHT))
        y += 14f
        if (inv.customerAddress.isNotEmpty()) {
            c.drawText(inv.customerAddress, ML + 60f, y, tp(0xFF607D8BL, 8f))
            y += 12f
        }
        if (inv.customerGstin.isNotEmpty()) {
            c.drawText("GSTIN: ${inv.customerGstin}", ML + 60f, y, tp(0xFF607D8BL, 8f))
            y += 12f
        }

        y += 8f
        c.drawLine(ML, y, PW - MR, y, sp(0xFFE0E0E0L, 0.8f))
        y += 12f

        y = drawItemTable(c, items, y, 0xFF004D40L)
        y = drawTotals(c, inv, y, 0xFF004D40L, 0xFF00695CL)
        drawNotes(c, inv, y)
        drawFooter(c, co.name, 0xFF00695CL)
    }

    // ─────────────────────────────────────────────────────────────────────
    // SHARED: Item Table
    // ─────────────────────────────────────────────────────────────────────
    private fun drawItemTable(c: Canvas, items: List<InvoiceItem>,
                              startY: Float, headerCol: Long): Float {
        var y = startY
        val validItems = items.filter { it.isValid }

        // Table header
        c.drawRect(ML, y, PW - MR, y + 24f, fp(headerCol))
        val hW  = tp(0xFFFFFFFFL, 8f, true)
        val hWR = tp(0xFFFFFFFFL, 8f, true, Paint.Align.RIGHT)
        val cx  = colX()

        c.drawText("#",      cx[0], y + 16f, hW)
        c.drawText("ITEM",   cx[1], y + 16f, hW)
        c.drawText("QTY",    cx[2], y + 16f, hWR)
        c.drawText("RATE",   cx[3], y + 16f, hWR)
        c.drawText("DISC%",  cx[4], y + 16f, hWR)
        c.drawText("TAX%",   cx[5], y + 16f, hWR)
        c.drawText("AMOUNT", PW - MR, y + 16f, hWR)
        y += 24f

        validItems.forEachIndexed { i, item ->
            val rh = 20f
            val bg = if (i % 2 == 0) 0xFFFFFFFFL else 0xFFF7F8FAL
            c.drawRect(ML, y, PW - MR, y + rh, fp(bg))
            c.drawLine(ML, y + rh, PW - MR, y + rh, sp(0xFFEEEEEEL, 0.5f))
            val rT  = tp(0xFF212121L, 9f)
            val rTR = tp(0xFF212121L, 9f, false, Paint.Align.RIGHT)
            val nm  = if (item.name.length > 30) item.name.take(27) + "…" else item.name
            c.drawText("${i + 1}",                   cx[0], y + rh - 6f, rT)
            c.drawText(nm,                            cx[1], y + rh - 6f, rT)
            c.drawText(fmtN(item.quantity),           cx[2], y + rh - 6f, rTR)
            c.drawText(item.unitPrice.toRupee(),      cx[3], y + rh - 6f, rTR)
            c.drawText("${fmtN(item.discountPct)}%",  cx[4], y + rh - 6f, rTR)
            c.drawText("${fmtN(item.taxPct)}%",       cx[5], y + rh - 6f, rTR)
            c.drawText(item.lineTotal.toRupee(),      PW - MR, y + rh - 6f, rTR)
            y += rh
        }
        c.drawLine(ML, y, PW - MR, y, sp(0xFF333333L, 1f))
        return y + 12f
    }

    // ─────────────────────────────────────────────────────────────────────
    // SHARED: Totals Block
    // ─────────────────────────────────────────────────────────────────────
    private fun drawTotals(c: Canvas, inv: Invoice, startY: Float,
                           boxColor: Long, labelColor: Long): Float {
        var y  = startY
        val lx = PW - MR - 175f
        val vx = PW - MR

        fun row(label: String, value: String, col: Long = 0xFF607D8BL, sz: Float = 9f, bold: Boolean = false) {
            c.drawText(label, lx, y, tp(col, sz, bold))
            c.drawText(value, vx, y, tp(col, sz, bold, Paint.Align.RIGHT))
            y += sz + 7f
        }

        row("Subtotal:", inv.subtotal.toRupee())
        if (inv.totalDiscount > 0.0) row("Discount:", "− ${inv.totalDiscount.toRupee()}", 0xFF2E7D32L)
        if (inv.totalTax > 0.0)      row("GST / Tax:", inv.totalTax.toRupee())
        y += 3f
        c.drawLine(lx - 4f, y, vx + 4f, y, sp(0xFFDDDDDDL, 0.8f))
        y += 7f

        // Grand total box
        c.drawRect(lx - 10f, y - 4f, vx + 6f, y + 22f, fp(boxColor))
        c.drawText("GRAND TOTAL:", lx, y + 14f, tp(0xFFFFFFFFL, 11f, true))
        c.drawText(inv.grandTotal.toRupee(), vx, y + 14f,
            tp(0xFFFFE082L, 14f, true, Paint.Align.RIGHT))
        y += 30f

        if (inv.amountPaid > 0.0) {
            row("Amount Paid:", inv.amountPaid.toRupee())
            val balColor = if (inv.balanceDue > 0.0) 0xFFC62828L else 0xFF2E7D32L
            val balLabel = if (inv.balanceDue > 0.0) "Balance Due:" else "✓ Fully Paid"
            row(balLabel, inv.balanceDue.toRupee(), balColor, 11f, true)
        }
        return y + 8f
    }

    private fun drawNotes(c: Canvas, inv: Invoice, startY: Float) {
        if (inv.notes.isBlank()) return
        var y = startY + 10f
        c.drawLine(ML, y, PW - MR, y, sp(0xFFEEEEEEL, 0.8f))
        y += 12f
        c.drawText("NOTES:", ML, y, tp(0xFF607D8BL, 8f, true))
        y += 12f
        val notes = if (inv.notes.length > 200) inv.notes.take(197) + "…" else inv.notes
        // Wrap notes text
        val words = notes.split(" ")
        val linePaint = tp(0xFF757575L, 8f)
        var line = ""
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (linePaint.measureText(test) > CW) {
                c.drawText(line, ML, y, linePaint); y += 12f; line = word
            } else { line = test }
        }
        if (line.isNotEmpty()) c.drawText(line, ML, y, linePaint)
    }

    private fun drawFooter(c: Canvas, companyName: String, col: Long) {
        c.drawLine(ML, PH - 38f, PW - MR, PH - 38f, sp(0xFFDDDDDDL, 0.6f))
        c.drawText(
            "Thank you for your business! — $companyName  |  Phoenix Invoice",
            PW / 2f, PH - 22f,
            tp(0xFF9E9E9EL, 7f, false, Paint.Align.CENTER)
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────
    private fun colX() = arrayOf(ML, ML + 20f, ML + 240f, ML + 292f, ML + 350f, ML + 402f)

    private fun tp(color: Long, size: Float, bold: Boolean = false,
                   align: Paint.Align = Paint.Align.LEFT): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toInt()
            textSize   = size
            typeface   = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign  = align
        }

    private fun fp(color: Long): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt(); style = Paint.Style.FILL }

    private fun sp(color: Long, width: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color  = color.toInt(); strokeWidth = width; style = Paint.Style.STROKE
        }

    private fun smoothPaint(): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun fmtN(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)

    private fun loadLogo(ctx: Context, uri: String, w: Int, h: Int): Bitmap? {
        if (uri.isEmpty()) {
            // Use app logo from assets
            return try {
                ctx.assets.open("phoenix_logo.png").use { stream ->
                    val raw   = android.graphics.BitmapFactory.decodeStream(stream) ?: return null
                    val scale = minOf(w.toFloat() / raw.width, h.toFloat() / raw.height, 1f)
                    if (scale < 1f) Bitmap.createScaledBitmap(raw,
                        (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
                    else raw
                }
            } catch (_: Exception) { null }
        }
        return try {
            ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { stream ->
                val raw   = android.graphics.BitmapFactory.decodeStream(stream) ?: return null
                val scale = minOf(w.toFloat() / raw.width, h.toFloat() / raw.height, 1f)
                if (scale < 1f) Bitmap.createScaledBitmap(raw,
                    (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
                else raw
            }
        } catch (_: Exception) { null }
    }
}
