/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.assistant.fragment

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantThirdPartySipAccountLoginFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.GenericFragment
import org.linphone.ui.assistant.model.AcceptConditionsAndPolicyDialogModel
import org.linphone.ui.assistant.viewmodel.ThirdPartySipAccountLoginViewModel
import org.linphone.ui.sso.SingleSignOnActivity
import org.linphone.utils.DialogUtils
import org.linphone.utils.PhoneNumberUtils

@UiThread
class ThirdPartySipAccountLoginFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Third Party SIP Account Login Fragment]"
    }

    private lateinit var binding: AssistantThirdPartySipAccountLoginFragmentBinding

    private val viewModel: ThirdPartySipAccountLoginViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    private val dropdownListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val transport = viewModel.availableTransports[position]
            Log.i("$TAG Selected transport updated [$transport]")
            viewModel.transport.value = transport
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private lateinit var adapter: ArrayAdapter<String>

    private val accessLocalNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG ACCESS_LOCAL_NETWORK permission has been granted")
        } else {
            Log.w("$TAG ACCESS_LOCAL_NETWORK permission has been denied!")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantThirdPartySipAccountLoginFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        adapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.availableTransports
        )
        adapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.transport.adapter = adapter
        binding.transport.onItemSelectedListener = dropdownListener

        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        binding.setHelpClickListener {
            if (findNavController().currentDestination?.id == R.id.thirdPartySipAccountLoginFragment) {
                val action =
                    ThirdPartySipAccountLoginFragmentDirections.actionThirdPartySipAccountLoginFragmentToHelpFragment()
                findNavController().navigate(action)
            }
        }

        binding.setQrCodeClickListener {
            if (findNavController().currentDestination?.id == R.id.thirdPartySipAccountLoginFragment) {
                val action =
                    ThirdPartySipAccountLoginFragmentDirections.actionThirdPartySipAccountLoginFragmentToQrCodeScannerFragment()
                findNavController().navigate(action)
            }
        }

        binding.setOutboundProxyTooltipClickListener {
            showOutboundProxyInfoDialog()
        }

        viewModel.showPassword.observe(viewLifecycleOwner) {
            lifecycleScope.launch {
                delay(50)
                binding.password.setSelection(binding.password.text?.length ?: 0)
            }
        }

        viewModel.accountLoggedInEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Account successfully logged-in, leaving assistant")
                requireActivity().finish()
            }
        }

        viewModel.accountLoginErrorEvent.observe(viewLifecycleOwner) {
            it.consume { message ->
                (requireActivity() as GenericActivity).showRedToast(
                    message,
                    R.drawable.warning_circle
                )
                if (!Compatibility.isAccessLocalNetworkPermissionGranted(requireContext())) {
                    Log.w("$TAG Asking for ACCESS_LOCAL_NETWORK permission")
                    accessLocalNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }
            }
        }

        viewModel.defaultTransportIndexEvent.observe(viewLifecycleOwner) {
            it.consume { index ->
                binding.transport.setSelection(index)
            }
        }

        coreContext.bearerAuthenticationRequestedEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val serverUrl = pair.first
                val username = pair.second

                Log.i(
                    "$TAG Bearer auth request, navigating to Single Sign On Fragment with server URL [$serverUrl] and username [$username]"
                )
                val intent = Intent(requireContext(), SingleSignOnActivity::class.java)
                intent.putExtra(SingleSignOnActivity.INTENT_EXTRA_USERNAME, username)
                intent.putExtra(SingleSignOnActivity.INTENT_EXTRA_SERVER_URL, serverUrl)
                startActivity(intent)
            }
        }

        val telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val countryIso = telephonyManager.networkCountryIso
        coreContext.postOnCoreThread {
            val dialPlan = PhoneNumberUtils.getDeviceDialPlan(countryIso)
            if (dialPlan != null) {
                viewModel.internationalPrefix.postValue(dialPlan.countryCallingCode)
                viewModel.internationalPrefixIsoCountryCode.postValue(dialPlan.isoCountryCode)
            }
        }

        coreContext.postOnCoreThread {
            val accepted = corePreferences.conditionsAndPrivacyPolicyAccepted
            coreContext.postOnMainThread {
                if (viewModel.conditionsAccepted.value != true && !accepted) {
                    Log.i("$TAG Conditions & Privacy policy not accepted yet, showing dialog")
                    showAcceptConditionsAndPrivacyDialog()
                }
            }
        }
    }

    private fun goBack() {
        findNavController().popBackStack()
    }

    private fun showOutboundProxyInfoDialog() {
        val dialog = DialogUtils.getAccountOutboundProxyHelpDialog(requireActivity())
        dialog.show()
    }

    private fun showAcceptConditionsAndPrivacyDialog() {
        val model = AcceptConditionsAndPolicyDialogModel()
        val dialog = DialogUtils.getAcceptConditionsAndPrivacyDialog(
            requireActivity(),
            model
        )
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        // Direct wiring bypass: LiveData events on this dialog post but never
        // dispatch to observers. These run synchronously on tap.
        dialog.findViewById<View>(R.id.confirm)?.setOnClickListener {
            Log.i("$TAG Direct accept click: saving preference and closing dialog")
            viewModel.conditionsAccepted.value = true
            coreContext.postOnCoreThread {
                corePreferences.conditionsAndPrivacyPolicyAccepted = true
            }
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.cancel)?.setOnClickListener {
            Log.i("$TAG Direct deny click: closing dialog")
            dialog.dismiss()
        }

        // Note: observers use fragment lifecycle (not viewLifecycleOwner):
        // dialog is activity-hosted and must survive fragment view recreation.
        model.dismissEvent.observe(this) {
            it.consume {
                Log.i("$TAG Dismiss event received, closing dialog")
                dialog.dismiss()
            }
        }

        model.conditionsAcceptedEvent.observe(this) {
            it.consume {
                Log.i("$TAG Conditions & Privacy policy have been accepted")
                viewModel.conditionsAccepted.value = true
                coreContext.postOnCoreThread {
                    corePreferences.conditionsAndPrivacyPolicyAccepted = true
                }
                dialog.dismiss()
            }
        }

        model.privacyPolicyClickedEvent.observe(this) {
            it.consume {
                val url = getString(R.string.website_privacy_policy_url)
                openUrlInBrowser(url)
            }
        }

        model.generalTermsClickedEvent.observe(this) {
            it.consume {
                val url = getString(R.string.website_terms_and_conditions_url)
                openUrlInBrowser(url)
            }
        }

        dialog.show()
    }

    private fun openUrlInBrowser(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(browserIntent)
        } catch (ise: IllegalStateException) {
            Log.e(
                "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
            )
        } catch (anfe: ActivityNotFoundException) {
            Log.e(
                "$TAG Can't start ACTION_VIEW intent for URL [$url], ActivityNotFoundException: $anfe"
            )
        } catch (e: Exception) {
            Log.e(
                "$TAG Can't start ACTION_VIEW intent for URL [$url]: $e"
            )
        }
    }
}
