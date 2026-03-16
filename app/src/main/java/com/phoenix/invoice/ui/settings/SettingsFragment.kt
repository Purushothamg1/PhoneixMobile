package com.phoenix.invoice.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import coil.load
import com.phoenix.invoice.data.db.entities.CompanyProfile
import com.phoenix.invoice.databinding.FragmentSettingsBinding
import com.phoenix.invoice.ui.RepoVMFactory
import com.phoenix.invoice.ui.SettingsViewModel
import com.phoenix.invoice.utils.toast

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private val vm: SettingsViewModel by activityViewModels { RepoVMFactory(requireActivity().application) }
    private var logoUri: String = ""

    private val logoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            logoUri = it.toString()
            b.ivLogo.load(it)
            b.ivLogo.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        vm.company.observe(viewLifecycleOwner) { co ->
            val profile = co ?: CompanyProfile()
            logoUri = profile.logoUri
            b.etName.setText(profile.name)
            b.etAddress.setText(profile.address)
            b.etPhone.setText(profile.phone)
            b.etEmail.setText(profile.email)
            b.etGstin.setText(profile.gstin)
            b.etPrefix.setText(profile.invoicePrefix.ifEmpty { "PHX" })
            b.etDefaultTax.setText(profile.defaultTaxPct.toString())
            b.etBankDetails.setText(profile.bankDetails)
            b.etUpi.setText(profile.upiId)
            if (profile.logoUri.isNotEmpty()) {
                b.ivLogo.load(profile.logoUri)
                b.ivLogo.visibility = View.VISIBLE
            }
        }

        vm.saved.observe(viewLifecycleOwner) { saved ->
            if (saved == true) {
                requireContext().toast("✓ Settings saved")
                vm.clearSaved()
            }
        }

        b.btnUploadLogo.setOnClickListener { logoPicker.launch("image/*") }

        b.btnSave.setOnClickListener { save() }
        b.toolbar.menu?.let { /* menu items if any */ }
        b.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun save() {
        val name = b.etName.text.toString().trim()
        if (name.isEmpty()) { requireContext().toast("Company name is required"); return }
        vm.save(CompanyProfile(
            name           = name,
            address        = b.etAddress.text.toString().trim(),
            phone          = b.etPhone.text.toString().trim(),
            email          = b.etEmail.text.toString().trim(),
            gstin          = b.etGstin.text.toString().trim(),
            logoUri        = logoUri,
            invoicePrefix  = b.etPrefix.text.toString().trim().ifEmpty { "PHX" },
            defaultTaxPct  = b.etDefaultTax.text.toString().toDoubleOrNull() ?: 18.0,
            bankDetails    = b.etBankDetails.text.toString().trim(),
            upiId          = b.etUpi.text.toString().trim()
        ))
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
