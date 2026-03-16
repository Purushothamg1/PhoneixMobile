package com.phoenix.invoice.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.phoenix.invoice.PhoenixApp
import com.phoenix.invoice.data.db.entities.*
import com.phoenix.invoice.data.repository.Repository
import com.phoenix.invoice.pdf.PdfEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── Generic Factory ─────────────────────────────────────────────────────────
class RepoVMFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(c: Class<T>): T {
        val repo = (app as PhoenixApp).repo
        return when {
            c.isAssignableFrom(DashboardViewModel::class.java)    -> DashboardViewModel(repo) as T
            c.isAssignableFrom(InvoiceListViewModel::class.java)  -> InvoiceListViewModel(repo) as T
            c.isAssignableFrom(InvoiceEditViewModel::class.java)  -> InvoiceEditViewModel(repo) as T
            c.isAssignableFrom(CustomerViewModel::class.java)     -> CustomerViewModel(repo) as T
            c.isAssignableFrom(ProductViewModel::class.java)      -> ProductViewModel(repo) as T
            c.isAssignableFrom(SettingsViewModel::class.java)     -> SettingsViewModel(repo) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: $c")
        }
    }
}

// ─── Dashboard ────────────────────────────────────────────────────────────────
class DashboardViewModel(private val repo: Repository) : ViewModel() {

    // All exposed as LiveData so fragments can use .observe()
    val company      : LiveData<CompanyProfile?> = repo.observeCompany().asLiveData()
    val invoices     : LiveData<List<Invoice>>   = repo.observeInvoices().asLiveData()
    val recent       : LiveData<List<Invoice>>   = repo.observeInvoices()
                           .map { it.take(6) }.asLiveData()
    val revenue      : LiveData<Double>          = repo.observeInvoices()
                           .map { list -> list.sumOf { it.grandTotal } }.asLiveData()
    val pending      : LiveData<Double>          = repo.observeInvoices()
                           .map { list -> list.filter { it.status != "PAID" }.sumOf { it.balanceDue } }.asLiveData()
    val invoiceCount : LiveData<Int>             = repo.observeInvoices()
                           .map { it.size }.asLiveData()

    private val _pdfEvent = MutableLiveData<String?>()
    val pdfEvent: LiveData<String?> = _pdfEvent

    fun generatePdf(ctx: Context, inv: Invoice) = viewModelScope.launch {
        val items = repo.getItemsFor(inv.id)
        val co    = repo.getCompany()
        val path  = PdfEngine.generate(ctx, co, inv, items)
        if (path != null) repo.updatePdfPath(inv.id, path)
        _pdfEvent.value = path
    }
    fun clearPdfEvent() { _pdfEvent.value = null }
    fun delete(inv: Invoice) = viewModelScope.launch { repo.deleteInvoice(inv) }
}

// ─── Invoice List ─────────────────────────────────────────────────────────────
class InvoiceListViewModel(private val repo: Repository) : ViewModel() {

    val invoices: LiveData<List<Invoice>> = repo.observeInvoices().asLiveData()

    private val _pdfEvent = MutableLiveData<String?>()
    val pdfEvent: LiveData<String?> = _pdfEvent

    fun delete(inv: Invoice) = viewModelScope.launch { repo.deleteInvoice(inv) }

    fun generatePdf(ctx: Context, inv: Invoice) = viewModelScope.launch {
        val items = repo.getItemsFor(inv.id)
        val co    = repo.getCompany()
        val path  = PdfEngine.generate(ctx, co, inv, items)
        if (path != null) repo.updatePdfPath(inv.id, path)
        _pdfEvent.value = path
    }
    fun clearPdf() { _pdfEvent.value = null }
}

// ─── Invoice Edit ─────────────────────────────────────────────────────────────
class InvoiceEditViewModel(private val repo: Repository) : ViewModel() {

    private val _items   = MutableLiveData<MutableList<InvoiceItem>>(mutableListOf())
    val items: LiveData<MutableList<InvoiceItem>> = _items

    private val _invoice = MutableLiveData<Invoice?>()
    val invoice: LiveData<Invoice?> = _invoice

    private val _saved   = MutableLiveData<Long?>()
    val saved: LiveData<Long?> = _saved

    private val _error   = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _pdfPath = MutableLiveData<String?>()
    val pdfPath: LiveData<String?> = _pdfPath

    val company: LiveData<CompanyProfile?> = repo.observeCompany().asLiveData()

    fun load(id: Long) = viewModelScope.launch {
        val inv   = repo.getInvoice(id) ?: return@launch
        val items = repo.getItemsFor(id).toMutableList()
        _invoice.value = inv
        _items.value   = if (items.isEmpty()) mutableListOf(InvoiceItem()) else items
    }

    fun loadDefaults() = viewModelScope.launch {
        val co  = repo.getCompany()
        val num = repo.nextInvoiceNumber(co.invoicePrefix.ifEmpty { "PHX" })
        _invoice.value = Invoice(invoiceNumber = num)
        _items.value   = mutableListOf(InvoiceItem(taxPct = co.defaultTaxPct))
    }

    fun setInvoice(inv: Invoice) { _invoice.value = inv }

    fun addItem(item: InvoiceItem = InvoiceItem()) {
        val list = _items.value?.toMutableList() ?: mutableListOf()
        list.add(item)
        _items.value = list
    }

    fun updateItem(index: Int, item: InvoiceItem) {
        val list = _items.value?.toMutableList() ?: return
        if (index < list.size) { list[index] = item; _items.value = list }
    }

    fun removeItem(index: Int) {
        val list = _items.value?.toMutableList() ?: return
        if (list.size > 1 && index < list.size) { list.removeAt(index); _items.value = list }
    }

    fun addScannedProduct(product: Product) {
        addItem(InvoiceItem(
            productId = product.id,
            name      = product.name,
            barcode   = product.barcode,
            unitPrice = product.unitPrice,
            taxPct    = product.taxPct,
            quantity  = 1.0
        ))
    }

    fun save(inv: Invoice, editId: Long?) = viewModelScope.launch {
        val list = _items.value ?: emptyList()
        if (inv.customerName.isBlank()) {
            _error.value = "Customer name is required"; return@launch
        }
        if (list.none { it.isValid }) {
            _error.value = "Add at least one item with name & price"; return@launch
        }
        val toSave  = inv.copy(id = editId ?: 0L)
        val savedId = repo.saveInvoiceWithItems(toSave, list)
        _saved.value = savedId
    }

    fun generateAndDownload(ctx: Context, invoiceId: Long) = viewModelScope.launch {
        val inv   = repo.getInvoice(invoiceId) ?: return@launch
        val items = repo.getItemsFor(invoiceId)
        val co    = repo.getCompany()
        val path  = PdfEngine.generate(ctx, co, inv, items)
        if (path != null) repo.updatePdfPath(invoiceId, path)
        _pdfPath.value = path
    }

    suspend fun searchCustomers(q: String) = repo.searchCustomers(q)
    suspend fun recentCustomers()          = repo.recentCustomers()
    suspend fun findByBarcode(code: String) = repo.findByBarcode(code)

    fun clearSaved()  { _saved.value  = null }
    fun clearError()  { _error.value  = null }
    fun clearPdf()    { _pdfPath.value = null }
}

// ─── Customer ─────────────────────────────────────────────────────────────────
class CustomerViewModel(private val repo: Repository) : ViewModel() {

    val customers: LiveData<List<Customer>> = repo.observeCustomers().asLiveData()

    private val _saved = MutableLiveData<Boolean>()
    val saved: LiveData<Boolean> = _saved

    fun save(c: Customer)    = viewModelScope.launch { repo.saveCustomer(c); _saved.value = true }
    fun delete(c: Customer)  = viewModelScope.launch { repo.deleteCustomer(c) }
    fun clearSaved()         { _saved.value = false }

    suspend fun search(q: String) = repo.searchCustomers(q)
}

// ─── Product ──────────────────────────────────────────────────────────────────
class ProductViewModel(private val repo: Repository) : ViewModel() {

    val products: LiveData<List<Product>> = repo.observeProducts().asLiveData()

    private val _saved = MutableLiveData<Boolean>()
    val saved: LiveData<Boolean> = _saved

    fun save(p: Product)    = viewModelScope.launch { repo.saveProduct(p); _saved.value = true }
    fun delete(p: Product)  = viewModelScope.launch { repo.deleteProduct(p) }
    fun clearSaved()        { _saved.value = false }

    suspend fun findByBarcode(code: String) = repo.findByBarcode(code)
    suspend fun search(q: String)           = repo.searchProducts(q)
}

// ─── Settings ─────────────────────────────────────────────────────────────────
class SettingsViewModel(private val repo: Repository) : ViewModel() {

    val company: LiveData<CompanyProfile?> = repo.observeCompany().asLiveData()

    private val _saved = MutableLiveData<Boolean>()
    val saved: LiveData<Boolean> = _saved

    fun save(p: CompanyProfile) = viewModelScope.launch { repo.saveCompany(p); _saved.value = true }
    fun clearSaved()            { _saved.value = false }
}
