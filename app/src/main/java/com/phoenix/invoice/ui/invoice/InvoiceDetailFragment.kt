package com.phoenix.invoice.ui.invoice

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.phoenix.invoice.R
import com.phoenix.invoice.data.db.entities.InvoiceItem
import com.phoenix.invoice.databinding.FragmentInvoiceDetailBinding
import com.phoenix.invoice.ui.InvoiceEditViewModel
import com.phoenix.invoice.ui.RepoVMFactory
import com.phoenix.invoice.utils.*
import kotlinx.coroutines.launch

class InvoiceDetailFragment : Fragment() {

    private var _b: FragmentInvoiceDetailBinding? = null
    private val b get() = _b!!
    private val vm: InvoiceEditViewModel by activityViewModels { RepoVMFactory(requireActivity().application) }
    private var invoiceId: Long = 0L

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentInvoiceDetailBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        invoiceId = arguments?.getLong("invoiceId", 0L) ?: 0L
        if (invoiceId > 0L) vm.load(invoiceId)

        vm.invoice.observe(viewLifecycleOwner) { inv ->
            inv ?: return@observe
            b.toolbar.title        = inv.invoiceNumber
            b.tvCustomerName.text  = inv.customerName
            b.tvCustomerPhone.text = inv.customerPhone.ifEmpty { "—" }
            b.tvDate.text          = inv.invoiceDate.toDateLong()
            b.tvDueDate.text       = inv.dueDate?.toDateLong() ?: "—"
            b.tvSubtotal.text      = inv.subtotal.toRupee()
            b.tvTax.text           = inv.totalTax.toRupee()
            b.tvDiscount.text      = "− ${inv.totalDiscount.toRupee()}"
            b.tvGrandTotal.text    = inv.grandTotal.toRupee()
            b.tvAmountPaid.text    = if (inv.amountPaid > 0) inv.amountPaid.toRupee() else "—"
            b.tvBalanceDue.text    = inv.balanceDue.toRupee()
            b.tvNotes.text         = inv.notes.ifEmpty { "—" }
            b.tvStatus.text        = inv.status

            val statusColor = when (inv.status) {
                "PAID"    -> requireContext().getColor(R.color.success)
                "PARTIAL" -> requireContext().getColor(R.color.warning)
                else      -> requireContext().getColor(R.color.error)
            }
            b.tvStatus.setTextColor(statusColor)
            b.tvBalanceDue.setTextColor(
                if (inv.balanceDue > 0) requireContext().getColor(R.color.error)
                else requireContext().getColor(R.color.success)
            )

            b.layoutDiscountRow.visibility = if (inv.totalDiscount > 0) View.VISIBLE else View.GONE
            b.layoutTaxRow.visibility      = if (inv.totalTax > 0) View.VISIBLE else View.GONE
            b.layoutPaidRow.visibility     = if (inv.amountPaid > 0) View.VISIBLE else View.GONE
        }

        vm.items.observe(viewLifecycleOwner) { items ->
            b.rvItems.layoutManager = LinearLayoutManager(requireContext())
            b.rvItems.adapter = com.phoenix.invoice.adapter.InvoiceItemDisplayAdapter(items)
        }

        vm.pdfPath.observe(viewLifecycleOwner) { path ->
            if (path != null) {
                vm.clearPdf()
                requireContext().toast("✓ PDF saved to PhoenixInvoices")
                openPdfFile(requireContext(), path)
            }
        }

        b.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        b.btnEdit.setOnClickListener {
            val args = Bundle().apply { putLong("invoiceId", invoiceId) }
            findNavController().navigate(R.id.action_invoiceDetail_to_invoiceEdit, args)
        }

        b.btnDownload.setOnClickListener {
            b.btnDownload.isEnabled = false
            b.btnDownload.text = "Generating…"
            vm.generateAndDownload(requireContext(), invoiceId)
            b.btnDownload.isEnabled = true
            b.btnDownload.text = "⬇ Download PDF"
        }

        b.btnWhatsapp.setOnClickListener {
            val inv = vm.invoice.value ?: return@setOnClickListener
            lifecycleScope.launch {
                val ctx  = requireContext()
                // Generate PDF first if not already done
                var pdfPath = inv.pdfPath
                if (pdfPath.isEmpty()) {
                    val items = vm.items.value ?: emptyList()
                    val co    = (requireActivity().application as com.phoenix.invoice.PhoenixApp).repo.getCompany()
                    pdfPath   = com.phoenix.invoice.pdf.PdfEngine.generate(ctx, co, inv, items) ?: ""
                    if (pdfPath.isNotEmpty()) {
                        (requireActivity().application as com.phoenix.invoice.PhoenixApp).repo.updatePdfPath(invoiceId, pdfPath)
                    }
                }
                val co = (requireActivity().application as com.phoenix.invoice.PhoenixApp).repo.getCompany()
                shareViaWhatsApp(ctx, inv.customerPhone, inv.customerName,
                    inv.invoiceNumber, inv.grandTotal, pdfPath, co.name)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
