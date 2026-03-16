package com.phoenix.invoice.ui.invoice

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.phoenix.invoice.R
import com.phoenix.invoice.data.db.entities.*
import com.phoenix.invoice.databinding.FragmentInvoiceEditBinding
import com.phoenix.invoice.databinding.ItemInvoiceRowEditBinding
import com.phoenix.invoice.ui.InvoiceEditViewModel
import com.phoenix.invoice.ui.RepoVMFactory
import com.phoenix.invoice.utils.*
import kotlinx.coroutines.launch
import java.util.*

class InvoiceEditFragment : Fragment() {

    private var _b: FragmentInvoiceEditBinding? = null
    private val b get() = _b!!
    private val vm: InvoiceEditViewModel by activityViewModels { RepoVMFactory(requireActivity().application) }
    private var editInvoiceId: Long? = null

    // Barcode scanner launcher
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) handleScan(result.contents)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentInvoiceEditBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        // Load edit or default
        val id = arguments?.getLong("invoiceId", 0L) ?: 0L
        if (id > 0L) { editInvoiceId = id; vm.load(id) } else vm.loadDefaults()

        setupObservers()
        setupButtons()
    }

    private fun setupObservers() {
        vm.invoice.observe(viewLifecycleOwner) { inv ->
            inv ?: return@observe
            b.etInvoiceNumber.setText(inv.invoiceNumber)
            b.etCustomerName.setText(inv.customerName)
            b.etCustomerPhone.setText(inv.customerPhone)
            b.etCustomerAddress.setText(inv.customerAddress)
            b.etCustomerGstin.setText(inv.customerGstin)
            b.etDate.setText(inv.invoiceDate.toDateStr())
            inv.dueDate?.let { b.etDueDate.setText(it.toDateStr()) }
            b.etNotes.setText(inv.notes)
            b.etAmountPaid.setText(if (inv.amountPaid > 0) inv.amountPaid.toString() else "")

            // Template selection
            val rbMap = mapOf(0 to b.rbTemplate0, 1 to b.rbTemplate1, 2 to b.rbTemplate2)
            rbMap[inv.template]?.isChecked = true
        }

        vm.items.observe(viewLifecycleOwner) { items ->
            refreshItemRows(items)
            updateTotals(items)
        }

        vm.saved.observe(viewLifecycleOwner) { id ->
            if (id != null) {
                vm.clearSaved()
                val b2 = Bundle().apply { putLong("invoiceId", id) }
                findNavController().navigate(R.id.action_invoiceEdit_to_invoiceDetail, b2)
            }
        }

        vm.error.observe(viewLifecycleOwner) { err ->
            if (err != null) { requireContext().toast(err); vm.clearError() }
        }

        vm.company.observe(viewLifecycleOwner) { /* company loaded */ }
    }

    private fun setupButtons() {
        // Date pickers
        b.etDate.setOnClickListener    { showDatePicker(b.etDate) }
        b.etDueDate.setOnClickListener { showDatePicker(b.etDueDate) }

        // Customer search
        b.btnSearchCustomer.setOnClickListener { showCustomerSearch() }

        // Add item
        b.btnAddItem.setOnClickListener { vm.addItem() }

        // Scan barcode
        b.btnScanBarcode.setOnClickListener {
            val opts = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                setPrompt("Scan product barcode or QR code")
                setCameraId(0)
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(true)
            }
            barcodeLauncher.launch(opts)
        }

        // Save
        b.btnSave.setOnClickListener { collectAndSave() }

        // Back
        b.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        b.toolbar.title = if (editInvoiceId != null) "Edit Invoice" else "New Invoice"
    }

    private fun showDatePicker(field: com.google.android.material.textfield.TextInputEditText) {
        val cal = Calendar.getInstance()
        try {
            val parts = field.text.toString().split("/")
            if (parts.size == 3) cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
        } catch (_: Exception) { }
        DatePickerDialog(requireContext(), { _, y, m, d ->
            field.setText(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showCustomerSearch() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.sheet_customer_search, null)
        dialog.setContentView(sheetView)

        val etSearch = sheetView.findViewById<EditText>(R.id.etSearch)
        val rv = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvCustomers)
        val adapter = com.phoenix.invoice.adapter.CustomerPickAdapter { customer ->
            b.etCustomerName.setText(customer.name)
            b.etCustomerPhone.setText(customer.phone)
            b.etCustomerAddress.setText(customer.address)
            b.etCustomerGstin.setText(customer.gstin)
            dialog.dismiss()
        }
        rv.adapter = adapter

        lifecycleScope.launch { adapter.submitList(vm.recentCustomers()) }
        etSearch.addTextChangedListener { text ->
            lifecycleScope.launch {
                val q = text.toString()
                val results = if (q.length >= 1) vm.searchCustomers(q) else vm.recentCustomers()
                adapter.submitList(results)
            }
        }
        dialog.show()
    }

    private fun handleScan(barcode: String) {
        lifecycleScope.launch {
            val product = vm.findByBarcode(barcode)
            if (product != null) {
                vm.addScannedProduct(product)
                requireContext().toast("Added: ${product.name}")
            } else {
                // Ask to create product
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Product not found")
                    .setMessage("Barcode: $barcode\n\nCreate a new product?")
                    .setPositiveButton("Add Item Manually") { _, _ ->
                        vm.addItem(InvoiceItem(barcode = barcode))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun refreshItemRows(items: MutableList<InvoiceItem>) {
        b.llItems.removeAllViews()
        items.forEachIndexed { idx, item ->
            val row = ItemInvoiceRowEditBinding.inflate(layoutInflater, b.llItems, false)

            row.tvIndex.text = "${idx + 1}"
            row.etName.setText(item.name)
            row.etQty.setText(if (item.quantity == 1.0) "1" else item.quantity.toString())
            row.etPrice.setText(if (item.unitPrice > 0) item.unitPrice.toString() else "")
            row.etDiscount.setText(if (item.discountPct > 0) item.discountPct.toString() else "")
            row.etTax.setText(if (item.taxPct > 0) item.taxPct.toString() else "")
            row.tvTotal.text = item.lineTotal.toRupee()

            // Barcode badge
            if (item.barcode.isNotEmpty()) {
                row.tvBarcode.visibility = View.VISIBLE
                row.tvBarcode.text = item.barcode
            }

            // Watchers
            fun update() {
                val updated = item.copy(
                    name        = row.etName.text.toString().trim(),
                    quantity    = row.etQty.text.toString().toDoubleOrNull() ?: 1.0,
                    unitPrice   = row.etPrice.text.toString().toDoubleOrNull() ?: 0.0,
                    discountPct = row.etDiscount.text.toString().toDoubleOrNull() ?: 0.0,
                    taxPct      = row.etTax.text.toString().toDoubleOrNull() ?: 0.0
                )
                row.tvTotal.text = updated.lineTotal.toRupee()
                vm.updateItem(idx, updated)
            }
            listOf(row.etName, row.etQty, row.etPrice, row.etDiscount, row.etTax).forEach { et ->
                et.addTextChangedListener { update() }
            }

            // Delete
            row.btnDelete.setOnClickListener { vm.removeItem(idx) }

            // Scan for this row
            row.btnScanRow.setOnClickListener {
                val opts = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    setPrompt("Scan product"); setBeepEnabled(true); setOrientationLocked(true)
                }
                barcodeLauncher.launch(opts)
            }

            b.llItems.addView(row.root)
        }
    }

    private fun updateTotals(items: List<InvoiceItem>) {
        val sub   = items.sumOf { it.subtotal }
        val tax   = items.sumOf { it.taxAmt }
        val disc  = items.sumOf { it.discountAmt }
        val grand = items.sumOf { it.lineTotal }
        b.tvSubtotal.text   = sub.toRupee()
        b.tvTax.text        = tax.toRupee()
        b.tvDiscount.text   = "− ${disc.toRupee()}"
        b.tvGrandTotal.text = grand.toRupee()
        val paid = b.etAmountPaid.text.toString().toDoubleOrNull() ?: 0.0
        b.tvBalanceDue.text = (grand - paid).coerceAtLeast(0.0).toRupee()

        b.layoutDiscountRow.visibility = if (disc > 0) View.VISIBLE else View.GONE
        b.layoutTaxRow.visibility      = if (tax > 0) View.VISIBLE else View.GONE
    }

    private fun collectAndSave() {
        val template = when {
            b.rbTemplate1.isChecked -> 1
            b.rbTemplate2.isChecked -> 2
            else                    -> 0
        }
        val dateEpoch = b.etDate.text.toString().toEpoch() ?: System.currentTimeMillis()
        val dueEpoch  = b.etDueDate.text.toString().toEpoch()
        val inv = (vm.invoice.value ?: Invoice()).copy(
            invoiceNumber   = b.etInvoiceNumber.text.toString().trim(),
            customerName    = b.etCustomerName.text.toString().trim(),
            customerPhone   = b.etCustomerPhone.text.toString().trim(),
            customerAddress = b.etCustomerAddress.text.toString().trim(),
            customerGstin   = b.etCustomerGstin.text.toString().trim(),
            invoiceDate     = dateEpoch,
            dueDate         = dueEpoch,
            notes           = b.etNotes.text.toString().trim(),
            amountPaid      = b.etAmountPaid.text.toString().toDoubleOrNull() ?: 0.0,
            template        = template
        )
        vm.save(inv, editInvoiceId)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
