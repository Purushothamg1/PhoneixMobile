package com.phoenix.invoice.ui.product

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.phoenix.invoice.data.db.entities.Product
import com.phoenix.invoice.databinding.FragmentProductBinding
import com.phoenix.invoice.databinding.SheetEditProductBinding
import com.phoenix.invoice.ui.ProductViewModel
import com.phoenix.invoice.ui.RepoVMFactory
import com.phoenix.invoice.utils.toast

class ProductFragment : Fragment() {

    private var _b: FragmentProductBinding? = null
    private val b get() = _b!!
    private val vm: ProductViewModel by activityViewModels { RepoVMFactory(requireActivity().application) }

    private var pendingSheet: SheetEditProductBinding? = null

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            pendingSheet?.etBarcode?.setText(result.contents)
            pendingSheet = null
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentProductBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        val adapter = com.phoenix.invoice.adapter.ProductAdapter(
            onEdit   = { showEditSheet(it) },
            onDelete = { confirmDelete(it) }
        )
        b.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        b.rvProducts.adapter = adapter

        vm.products.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        vm.saved.observe(viewLifecycleOwner) { saved ->
            if (saved == true) { requireContext().toast("Product saved"); vm.clearSaved() }
        }

        b.fabAdd.setOnClickListener { showEditSheet(null) }
        b.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        b.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean {
                val query = q.orEmpty().lowercase()
                vm.products.value?.let { all ->
                    adapter.submitList(if (query.isEmpty()) all
                    else all.filter {
                        it.name.lowercase().contains(query) || it.barcode.contains(query)
                    })
                }
                return true
            }
        })
    }

    private fun showEditSheet(product: Product?) {
        val dialog = BottomSheetDialog(requireContext())
        val sb = SheetEditProductBinding.inflate(layoutInflater)
        pendingSheet = sb
        dialog.setContentView(sb.root)
        dialog.setOnDismissListener { if (pendingSheet == sb) pendingSheet = null }

        sb.tvTitle.text = if (product != null) "Edit Product" else "New Product"
        product?.let {
            sb.etName.setText(it.name)
            sb.etDescription.setText(it.description)
            sb.etBarcode.setText(it.barcode)
            sb.etPrice.setText(if (it.unitPrice > 0) it.unitPrice.toString() else "")
            sb.etTax.setText(if (it.taxPct > 0) it.taxPct.toString() else "")
            sb.etUnit.setText(it.unit)
            sb.etStock.setText(if (it.stock > 0) it.stock.toString() else "")
        }

        sb.btnScanBarcode.setOnClickListener {
            pendingSheet = sb
            barcodeLauncher.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                setPrompt("Scan product barcode"); setBeepEnabled(true); setOrientationLocked(true)
            })
        }

        sb.btnSave.setOnClickListener {
            val name = sb.etName.text.toString().trim()
            val price = sb.etPrice.text.toString().toDoubleOrNull() ?: 0.0
            if (name.isEmpty()) { requireContext().toast("Product name required"); return@setOnClickListener }
            vm.save(Product(
                id          = product?.id ?: 0L,
                name        = name,
                description = sb.etDescription.text.toString().trim(),
                barcode     = sb.etBarcode.text.toString().trim(),
                unitPrice   = price,
                taxPct      = sb.etTax.text.toString().toDoubleOrNull() ?: 18.0,
                unit        = sb.etUnit.text.toString().trim().ifEmpty { "pcs" },
                stock       = sb.etStock.text.toString().toIntOrNull() ?: 0
            ))
            pendingSheet = null
            dialog.dismiss()
        }
        sb.btnCancel.setOnClickListener { pendingSheet = null; dialog.dismiss() }
        dialog.show()
    }

    private fun confirmDelete(p: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Product")
            .setMessage("Delete ${p.name}?")
            .setPositiveButton("Delete") { _, _ -> vm.delete(p) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
