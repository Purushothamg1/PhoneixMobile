package com.phoenix.invoice.ui.customer

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phoenix.invoice.R
import com.phoenix.invoice.data.db.entities.Customer
import com.phoenix.invoice.databinding.FragmentCustomerBinding
import com.phoenix.invoice.databinding.SheetEditCustomerBinding
import com.phoenix.invoice.ui.CustomerViewModel
import com.phoenix.invoice.ui.RepoVMFactory
import com.phoenix.invoice.utils.toast

class CustomerFragment : Fragment() {

    private var _b: FragmentCustomerBinding? = null
    private val b get() = _b!!
    private val vm: CustomerViewModel by activityViewModels { RepoVMFactory(requireActivity().application) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCustomerBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        val adapter = com.phoenix.invoice.adapter.CustomerAdapter(
            onEdit   = { showEditSheet(it) },
            onDelete = { confirmDelete(it) }
        )
        b.rvCustomers.layoutManager = LinearLayoutManager(requireContext())
        b.rvCustomers.adapter = adapter

        vm.customers.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        vm.saved.observe(viewLifecycleOwner) { saved ->
            if (saved == true) { requireContext().toast("Customer saved"); vm.clearSaved() }
        }

        b.fabAdd.setOnClickListener { showEditSheet(null) }
        b.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        b.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean {
                val query = q.orEmpty().lowercase()
                vm.customers.value?.let { all ->
                    adapter.submitList(if (query.isEmpty()) all
                    else all.filter {
                        it.name.lowercase().contains(query) ||
                        it.phone.contains(query)
                    })
                }
                return true
            }
        })
    }

    private fun showEditSheet(customer: Customer?) {
        val dialog = BottomSheetDialog(requireContext())
        val sb = SheetEditCustomerBinding.inflate(layoutInflater)
        dialog.setContentView(sb.root)

        sb.tvTitle.text = if (customer != null) "Edit Customer" else "New Customer"
        customer?.let {
            sb.etName.setText(it.name)
            sb.etPhone.setText(it.phone)
            sb.etEmail.setText(it.email)
            sb.etAddress.setText(it.address)
            sb.etGstin.setText(it.gstin)
        }
        sb.btnSave.setOnClickListener {
            val name = sb.etName.text.toString().trim()
            if (name.isEmpty()) { requireContext().toast("Name is required"); return@setOnClickListener }
            vm.save(Customer(
                id      = customer?.id ?: 0L,
                name    = name,
                phone   = sb.etPhone.text.toString().trim(),
                email   = sb.etEmail.text.toString().trim(),
                address = sb.etAddress.text.toString().trim(),
                gstin   = sb.etGstin.text.toString().trim()
            ))
            dialog.dismiss()
        }
        sb.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun confirmDelete(c: Customer) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Customer")
            .setMessage("Delete ${c.name}?")
            .setPositiveButton("Delete") { _, _ -> vm.delete(c) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
