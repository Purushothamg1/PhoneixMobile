package com.phoenix.invoice.ui.dashboard

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.phoenix.invoice.R
import com.phoenix.invoice.databinding.FragmentDashboardBinding
import com.phoenix.invoice.ui.DashboardViewModel
import com.phoenix.invoice.ui.RepoVMFactory
import com.phoenix.invoice.utils.*

class DashboardFragment : Fragment() {

    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!
    private val vm: DashboardViewModel by activityViewModels { RepoVMFactory(requireActivity().application) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDashboardBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        // Observe stats
        vm.revenue.observe(viewLifecycleOwner)      { b.tvRevenue.text   = it.toRupee() }
        vm.pending.observe(viewLifecycleOwner)      { b.tvPending.text   = it.toRupee() }
        vm.invoiceCount.observe(viewLifecycleOwner) { b.tvInvoiceCount.text = "$it" }
        vm.company.observe(viewLifecycleOwner)      { co ->
            b.tvCompanyName.text = co?.name?.ifEmpty { "Tap Settings to set up" } ?: "Loading…"
        }

        // Recent invoices
        val adapter = com.phoenix.invoice.adapter.InvoiceSmallAdapter { inv ->
            findNavController().navigate(
                R.id.action_dashboard_to_invoiceDetail,
                Bundle().apply { putLong("invoiceId", inv.id) }
            )
        }
        b.rvRecent.adapter = adapter
        vm.recent.observe(viewLifecycleOwner) { adapter.submitList(it) }

        // FAB
        b.fabNewInvoice.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_invoiceEdit)
        }

        // Quick actions
        b.cardInvoices.setOnClickListener  { findNavController().navigate(R.id.invoiceListFragment) }
        b.cardCustomers.setOnClickListener { findNavController().navigate(R.id.customerFragment) }
        b.cardProducts.setOnClickListener  { findNavController().navigate(R.id.productFragment) }
        b.cardSettings.setOnClickListener  { findNavController().navigate(R.id.settingsFragment) }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
