package com.phoenix.invoice.data.db.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Company Profile ─────────────────────────────────────────────────────────
@Entity(tableName = "company_profile")
@Parcelize
data class CompanyProfile(
    @PrimaryKey val id: Int = 1,
    val name: String          = "",
    val address: String       = "",
    val phone: String         = "",
    val email: String         = "",
    val gstin: String         = "",
    val logoUri: String       = "",
    val defaultTaxPct: Double = 18.0,
    val invoicePrefix: String = "PHX",
    val bankDetails: String   = "",
    val upiId: String         = "",
    val currency: String      = "₹"
) : Parcelable

// ─── Customer ─────────────────────────────────────────────────────────────────
@Entity(tableName = "customers", indices = [Index("name"), Index("phone")])
@Parcelize
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String     = "",
    val phone: String    = "",
    val email: String    = "",
    val address: String  = "",
    val gstin: String    = "",
    val createdAt: Long  = System.currentTimeMillis()
) : Parcelable

// ─── Product ──────────────────────────────────────────────────────────────────
@Entity(tableName = "products", indices = [Index("barcode"), Index("name")])
@Parcelize
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String        = "",
    val description: String = "",
    val barcode: String     = "",   // EAN-13, QR, etc.
    val unitPrice: Double   = 0.0,
    val taxPct: Double      = 18.0,
    val unit: String        = "pcs", // pcs, kg, m, etc.
    val stock: Int          = 0,
    val createdAt: Long     = System.currentTimeMillis()
) : Parcelable

// ─── Invoice ──────────────────────────────────────────────────────────────────
@Entity(
    tableName = "invoices",
    foreignKeys = [ForeignKey(
        entity        = Customer::class,
        parentColumns = ["id"],
        childColumns  = ["customerId"],
        onDelete      = ForeignKey.SET_NULL
    )],
    indices = [Index("customerId"), Index("invoiceNumber")]
)
@Parcelize
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String    = "",
    val customerId: Long?        = null,
    val customerName: String     = "",
    val customerPhone: String    = "",
    val customerAddress: String  = "",
    val customerGstin: String    = "",
    val invoiceDate: Long        = System.currentTimeMillis(),
    val dueDate: Long?           = null,
    val notes: String            = "",
    val template: Int            = 0,
    val subtotal: Double         = 0.0,
    val totalDiscount: Double    = 0.0,
    val totalTax: Double         = 0.0,
    val grandTotal: Double       = 0.0,
    val amountPaid: Double       = 0.0,
    val status: String           = "UNPAID",
    val pdfPath: String          = "",
    val createdAt: Long          = System.currentTimeMillis()
) : Parcelable {
    val balanceDue: Double get() = (grandTotal - amountPaid).coerceAtLeast(0.0)

    companion object {
        private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        fun dateStr(epoch: Long): String = sdf.format(Date(epoch))
    }
}

// ─── Invoice Item ─────────────────────────────────────────────────────────────
@Entity(
    tableName = "invoice_items",
    foreignKeys = [ForeignKey(
        entity        = Invoice::class,
        parentColumns = ["id"],
        childColumns  = ["invoiceId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("invoiceId")]
)
@Parcelize
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long     = 0,
    val productId: Long?    = null,
    val name: String        = "",
    val barcode: String     = "",
    val quantity: Double    = 1.0,
    val unitPrice: Double   = 0.0,
    val discountPct: Double = 0.0,
    val taxPct: Double      = 18.0,
    val sortOrder: Int      = 0
) : Parcelable {
    val subtotal: Double       get() = quantity * unitPrice
    val discountAmt: Double    get() = subtotal * discountPct / 100.0
    val taxableAmt: Double     get() = subtotal - discountAmt
    val taxAmt: Double         get() = taxableAmt * taxPct / 100.0
    val lineTotal: Double      get() = taxableAmt + taxAmt
    val isValid: Boolean       get() = name.isNotBlank() && unitPrice > 0.0
}
