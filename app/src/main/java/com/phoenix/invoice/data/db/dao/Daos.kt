package com.phoenix.invoice.data.db.dao

import androidx.room.*
import com.phoenix.invoice.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM company_profile WHERE id=1 LIMIT 1")
    fun observe(): Flow<CompanyProfile?>
    @Query("SELECT * FROM company_profile WHERE id=1 LIMIT 1")
    suspend fun get(): CompanyProfile?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: CompanyProfile)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeAll(): Flow<List<Customer>>
    @Query("SELECT * FROM customers WHERE name LIKE '%'||:q||'%' OR phone LIKE '%'||:q||'%' ORDER BY name LIMIT 20")
    suspend fun search(q: String): List<Customer>
    @Query("SELECT * FROM customers ORDER BY createdAt DESC LIMIT 10")
    suspend fun recent(): List<Customer>
    @Query("SELECT * FROM customers WHERE id=:id LIMIT 1")
    suspend fun getById(id: Long): Customer?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: Customer): Long
    @Delete
    suspend fun delete(c: Customer)
    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeAll(): Flow<List<Product>>
    @Query("SELECT * FROM products WHERE barcode=:barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): Product?
    @Query("SELECT * FROM products WHERE name LIKE '%'||:q||'%' OR barcode LIKE '%'||:q||'%' ORDER BY name LIMIT 20")
    suspend fun search(q: String): List<Product>
    @Query("SELECT * FROM products WHERE id=:id LIMIT 1")
    suspend fun getById(id: Long): Product?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: Product): Long
    @Delete
    suspend fun delete(p: Product)
    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int
    @Query("SELECT DISTINCT name FROM products WHERE name LIKE '%'||:q||'%' LIMIT 10")
    suspend fun suggestNames(q: String): List<String>
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Invoice>>
    @Query("SELECT * FROM invoices WHERE id=:id LIMIT 1")
    suspend fun getById(id: Long): Invoice?
    @Query("SELECT * FROM invoices WHERE customerId=:cid ORDER BY createdAt DESC")
    suspend fun getForCustomer(cid: Long): List<Invoice>
    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun count(): Int
    @Query("SELECT COALESCE(SUM(grandTotal),0) FROM invoices")
    suspend fun totalRevenue(): Double
    @Query("SELECT COALESCE(SUM(grandTotal - amountPaid),0) FROM invoices WHERE status!='PAID'")
    suspend fun totalPending(): Double
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inv: Invoice): Long
    @Update
    suspend fun update(inv: Invoice)
    @Delete
    suspend fun delete(inv: Invoice)
    @Query("UPDATE invoices SET pdfPath=:path WHERE id=:id")
    suspend fun updatePdf(id: Long, path: String)
    @Query("UPDATE invoices SET status=:status,amountPaid=:paid WHERE id=:id")
    suspend fun updatePayment(id: Long, status: String, paid: Double)
    @Query("SELECT COALESCE(MAX(CAST(SUBSTR(invoiceNumber,INSTR(invoiceNumber,'-')+7) AS INTEGER)),0) FROM invoices WHERE invoiceNumber LIKE :prefix||'-%'")
    suspend fun lastSeq(prefix: String): Int
}

@Dao
interface InvoiceItemDao {
    @Query("SELECT * FROM invoice_items WHERE invoiceId=:id ORDER BY sortOrder ASC")
    suspend fun getForInvoice(id: Long): List<InvoiceItem>
    @Query("SELECT * FROM invoice_items WHERE invoiceId=:id ORDER BY sortOrder ASC")
    fun observeForInvoice(id: Long): Flow<List<InvoiceItem>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InvoiceItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: InvoiceItem): Long
    @Delete
    suspend fun delete(item: InvoiceItem)
    @Query("DELETE FROM invoice_items WHERE invoiceId=:id")
    suspend fun deleteForInvoice(id: Long)
}
