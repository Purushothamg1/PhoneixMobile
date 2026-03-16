package com.phoenix.invoice.data.repository

import com.phoenix.invoice.data.db.AppDatabase
import com.phoenix.invoice.data.db.entities.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class Repository(db: AppDatabase) {
    private val coDao   = db.companyDao()
    private val cuDao   = db.customerDao()
    private val prDao   = db.productDao()
    private val invDao  = db.invoiceDao()
    private val itmDao  = db.invoiceItemDao()

    // Company
    fun observeCompany(): Flow<CompanyProfile?> = coDao.observe()
    suspend fun getCompany(): CompanyProfile    = coDao.get() ?: CompanyProfile()
    suspend fun saveCompany(p: CompanyProfile)  = coDao.upsert(p)

    // Customers
    fun observeCustomers(): Flow<List<Customer>> = cuDao.observeAll()
    suspend fun searchCustomers(q: String)       = cuDao.search(q)
    suspend fun recentCustomers()                = cuDao.recent()
    suspend fun getCustomer(id: Long)            = cuDao.getById(id)
    suspend fun saveCustomer(c: Customer): Long  = cuDao.upsert(c)
    suspend fun deleteCustomer(c: Customer)      = cuDao.delete(c)
    suspend fun customerCount()                  = cuDao.count()

    // Products
    fun observeProducts(): Flow<List<Product>>       = prDao.observeAll()
    suspend fun findByBarcode(code: String)          = prDao.findByBarcode(code)
    suspend fun searchProducts(q: String)            = prDao.search(q)
    suspend fun getProduct(id: Long)                 = prDao.getById(id)
    suspend fun saveProduct(p: Product): Long        = prDao.upsert(p)
    suspend fun deleteProduct(p: Product)            = prDao.delete(p)
    suspend fun suggestProductNames(q: String)       = prDao.suggestNames(q)
    suspend fun productCount()                       = prDao.count()

    // Invoices
    fun observeInvoices(): Flow<List<Invoice>>    = invDao.observeAll()
    suspend fun getInvoice(id: Long)              = invDao.getById(id)
    suspend fun getItemsFor(invId: Long)          = itmDao.getForInvoice(invId)
    suspend fun deleteInvoice(inv: Invoice)       = invDao.delete(inv)
    suspend fun updatePdfPath(id: Long, path: String) = invDao.updatePdf(id, path)
    suspend fun invoiceCount()                    = invDao.count()
    suspend fun totalRevenue()                    = invDao.totalRevenue()
    suspend fun totalPending()                    = invDao.totalPending()

    suspend fun nextInvoiceNumber(prefix: String): String {
        val last = invDao.lastSeq(prefix)
        val seq  = String.format(Locale.US, "%04d", last + 1)
        val ym   = SimpleDateFormat("yyMM", Locale.US).format(Date())
        return "$prefix-$ym-$seq"
    }

    suspend fun saveInvoiceWithItems(invoice: Invoice, items: List<InvoiceItem>): Long {
        val valid = items.filter { it.isValid }
        val grand = valid.sumOf { it.lineTotal }
        val paid  = invoice.amountPaid
        val status = when {
            paid <= 0.0   -> "UNPAID"
            paid >= grand -> "PAID"
            else          -> "PARTIAL"
        }
        val computed = invoice.copy(
            subtotal      = valid.sumOf { it.subtotal },
            totalDiscount = valid.sumOf { it.discountAmt },
            totalTax      = valid.sumOf { it.taxAmt },
            grandTotal    = grand,
            status        = status
        )
        val savedId = if (computed.id == 0L) {
            invDao.insert(computed)
        } else {
            itmDao.deleteForInvoice(computed.id)
            invDao.update(computed)
            computed.id
        }
        itmDao.insertAll(valid.mapIndexed { i, it -> it.copy(invoiceId = savedId, sortOrder = i) })

        // Auto-save customer if new
        if (computed.customerPhone.isNotBlank() && computed.customerId == null) {
            cuDao.upsert(Customer(
                name    = computed.customerName,
                phone   = computed.customerPhone,
                address = computed.customerAddress,
                gstin   = computed.customerGstin
            ))
        }
        return savedId
    }
}
