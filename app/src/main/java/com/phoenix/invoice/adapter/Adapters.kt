package com.phoenix.invoice.adapter

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.phoenix.invoice.R
import com.phoenix.invoice.data.db.entities.*
import com.phoenix.invoice.databinding.*
import com.phoenix.invoice.utils.*
import java.text.NumberFormat
import java.util.Locale

private val rupee = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

// ─── Invoice Adapter (List / History) ───────────────────────────────────────
class InvoiceAdapter(
    private val onClickItem:  (Invoice) -> Unit,
    private val onDeleteItem: (Invoice) -> Unit,
    private val onShareItem:  (Invoice) -> Unit
) : ListAdapter<Invoice, InvoiceAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Invoice>() {
            override fun areItemsTheSame(a: Invoice, b: Invoice) = a.id == b.id
            override fun areContentsTheSame(a: Invoice, b: Invoice) = a == b
        }
    }

    inner class VH(val b: ItemInvoiceBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemInvoiceBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val inv = getItem(pos)
        h.b.tvInvoiceNumber.text = inv.invoiceNumber
        h.b.tvCustomerName.text  = inv.customerName
        h.b.tvDate.text          = inv.invoiceDate.toDateStr()
        h.b.tvAmount.text        = inv.grandTotal.toRupee()
        h.b.tvStatus.text        = inv.status
        val col = when (inv.status) {
            "PAID"    -> h.b.root.context.getColor(R.color.success)
            "PARTIAL" -> h.b.root.context.getColor(R.color.warning)
            else      -> h.b.root.context.getColor(R.color.error)
        }
        h.b.tvStatus.setTextColor(col)
        h.b.tvStatus.background?.let { /* tint chip */ }
        h.b.root.setOnClickListener { onClickItem(inv) }
        h.b.btnDelete.setOnClickListener { onDeleteItem(inv) }
        h.b.btnShare.setOnClickListener  { onShareItem(inv) }
    }
}

// ─── Invoice Small Adapter (Dashboard recent) ────────────────────────────────
class InvoiceSmallAdapter(
    private val onClick: (Invoice) -> Unit
) : ListAdapter<Invoice, InvoiceSmallAdapter.VH>(InvoiceAdapter.DIFF) {

    inner class VH(val b: ItemInvoiceSmallBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemInvoiceSmallBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val inv = getItem(pos)
        h.b.tvInvoiceNumber.text = inv.invoiceNumber
        h.b.tvCustomerName.text  = inv.customerName
        h.b.tvAmount.text        = inv.grandTotal.toRupee()
        h.b.tvStatus.text        = inv.status
        val col = when (inv.status) {
            "PAID"    -> h.b.root.context.getColor(R.color.success)
            "PARTIAL" -> h.b.root.context.getColor(R.color.warning)
            else      -> h.b.root.context.getColor(R.color.error)
        }
        h.b.tvStatus.setTextColor(col)
        h.b.root.setOnClickListener { onClick(inv) }
    }
}

// ─── Invoice Item Display (Detail screen) ────────────────────────────────────
class InvoiceItemDisplayAdapter(private val items: List<InvoiceItem>) :
    RecyclerView.Adapter<InvoiceItemDisplayAdapter.VH>() {

    inner class VH(val b: ItemInvoiceItemDisplayBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemInvoiceItemDisplayBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.b.tvName.text    = item.name
        h.b.tvQty.text     = "${item.quantity} × ${item.unitPrice.toRupee()}"
        h.b.tvTax.text     = "Tax: ${item.taxPct}%"
        h.b.tvTotal.text   = item.lineTotal.toRupee()
        h.b.tvBarcode.visibility = if (item.barcode.isNotEmpty()) View.VISIBLE else View.GONE
        h.b.tvBarcode.text = item.barcode
    }
}

// ─── Customer Adapter ─────────────────────────────────────────────────────────
class CustomerAdapter(
    private val onEdit:   (Customer) -> Unit,
    private val onDelete: (Customer) -> Unit
) : ListAdapter<Customer, CustomerAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Customer>() {
            override fun areItemsTheSame(a: Customer, b: Customer) = a.id == b.id
            override fun areContentsTheSame(a: Customer, b: Customer) = a == b
        }
    }

    inner class VH(val b: ItemCustomerBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemCustomerBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = getItem(pos)
        h.b.tvName.text    = c.name
        h.b.tvPhone.text   = c.phone.ifEmpty { "—" }
        h.b.tvEmail.text   = c.email.ifEmpty { "—" }
        h.b.tvInitial.text = c.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        h.b.btnEdit.setOnClickListener   { onEdit(c) }
        h.b.btnDelete.setOnClickListener { onDelete(c) }
    }
}

// ─── Customer Pick Adapter (bottom sheet search) ─────────────────────────────
class CustomerPickAdapter(
    private val onPick: (Customer) -> Unit
) : ListAdapter<Customer, CustomerPickAdapter.VH>(CustomerAdapter.DIFF) {

    inner class VH(val b: ItemCustomerPickBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemCustomerPickBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = getItem(pos)
        h.b.tvName.text  = c.name
        h.b.tvPhone.text = c.phone.ifEmpty { "—" }
        h.b.root.setOnClickListener { onPick(c) }
    }
}

// ─── Product Adapter ─────────────────────────────────────────────────────────
class ProductAdapter(
    private val onEdit:   (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(a: Product, b: Product) = a.id == b.id
            override fun areContentsTheSame(a: Product, b: Product) = a == b
        }
    }

    inner class VH(val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemProductBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = getItem(pos)
        h.b.tvName.text    = p.name
        h.b.tvPrice.text   = p.unitPrice.toRupee()
        h.b.tvBarcode.text = p.barcode.ifEmpty { "No barcode" }
        h.b.tvTax.text     = "Tax: ${p.taxPct}%"
        h.b.tvUnit.text    = p.unit
        h.b.tvBarcode.visibility = if (p.barcode.isNotEmpty()) View.VISIBLE else View.GONE
        h.b.btnEdit.setOnClickListener   { onEdit(p) }
        h.b.btnDelete.setOnClickListener { onDelete(p) }
    }
}
