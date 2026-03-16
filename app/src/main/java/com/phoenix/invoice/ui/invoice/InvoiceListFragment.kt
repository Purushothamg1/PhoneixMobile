package com.phoenix.invoice.ui.invoice

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.phoenix.invoice.R
import com.phoenix.invoice.databinding.FragmentInvoiceListBinding
import com.phoenix.invoice.ui.InvoiceListViewModel
import com.phoenix.invoice.ui.RepoVMFactory
import com.phoenix.invoice.utils.*

class InvoiceListFragment : Fragment() {

    private var _b: FragmentInvoiceListBinding? = null
    private val b get() = _b!!
    private val vm: InvoiceListViewModel by activityViewModels { RepoVMFactory(requireActivity().application) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentInvoiceListBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        val adapter = com.phoenix.invoice.adapter.InvoiceAdapter(
            onClickItem  = { inv ->
                val args = Bundle().apply { putLong("invoiceId", inv.id) }
                findNavController().navigate(R.id.action_invoiceList_to_invoiceDetail, args)
            },
            onDeleteItem = { inv ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Invoice")
                    .setMessage("Delete ${inv.invoiceNumber}? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> vm.delete(inv) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onShareItem  = { inv ->
                vm.generatePdf(requireContext(), inv)
            }
        )

        b.rvInvoices.layoutManager = LinearLayoutManager(requireContext())
        b.rvInvoices.adapter = adapter

        vm.invoices.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        vm.pdfEvent.observe(viewLifecycleOwner) { path ->
            if (path != null) {
                vm.clearPdf()
                requireContext().toast("✓ PDF saved — sharing via WhatsApp")
                // Share happens from detail screen
            }
        }

        b.fabNewInvoice.setOnClickListener {
            findNavController().navigate(R.id.action_invoiceList_to_invoiceEdit)
        }

        b.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // Search
        b.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean {
                val query = q.orEmpty().lowercase()
                vm.invoices.value?.let { all ->
                    val filtered = if (query.isEmpty()) all
                    else all.filter {
                        it.invoiceNumber.lowercase().contains(query) ||
                        it.customerName.lowercase().contains(query)
                    }
                    adapter.submitList(filtered)
                }
                return true
            }
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
