package com.example.carelyo.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.carelyo.databinding.FragmentHomeBinding
import com.example.carelyo.data.session.SessionManager
import com.example.carelyo.ui.auth.LoginActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels()
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())

        setupUserData()
        observeViewModel()

        binding.btnLogout.setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    private fun setupUserData() {
        val user = sessionManager.getUserSession()
        binding.tvWelcomeTitle.text = "Selamat Datang, ${user?.full_name ?: "Parent"}"
    }

    private fun observeViewModel() {
        viewModel.childrenList.observe(viewLifecycleOwner) { children ->
            if (children.isNotEmpty()) {
                val childInfo = buildString {
                    append("Active Profiles Located:\n")
                    children.forEach { child ->
                        append("• ${child.full_name}")
                        child.blood_type?.let { append(" ($it)") }
                        append("\n")
                    }
                }
                binding.tvChildInfo.text = childInfo
            } else {
                binding.tvChildInfo.text = "No linked child medical cards found. Please contact administrator."
            }
        }

        viewModel.systemLogs.observe(viewLifecycleOwner) { logOutput ->
            binding.tvVaccineAuditLogs.text = logOutput
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // You can show a progress indicator if needed
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}