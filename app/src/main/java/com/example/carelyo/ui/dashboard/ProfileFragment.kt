package com.example.carelyo.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.session.SessionManager
import com.example.carelyo.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: SessionManager
    private lateinit var childrenAdapter: ChildrenAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        val user = sessionManager.getUserSession()

        // Set user data
        binding.tvUserName.text = user?.full_name ?: "User Name"
        binding.tvUserEmail.text = user?.email ?: "user@example.com"
        binding.tvUserPhone.text = user?.phone_number ?: "Not provided"

        // Setup manage account click
        binding.tvManageAccount.setOnClickListener {
            Toast.makeText(requireContext(), "Manage account clicked", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to manage account screen
        }

        // Setup add child click
        binding.tvAddChild.setOnClickListener {
            Toast.makeText(requireContext(), "Add child clicked", Toast.LENGTH_SHORT).show()
            // TODO: Show add child dialog or navigate to add child screen
        }

        // Setup children RecyclerView
        setupChildrenRecyclerView()

        // Setup settings cards
        setupSettingsCards()

        // Setup help & support
        setupHelpSupport()
    }

    private fun setupChildrenRecyclerView() {
        childrenAdapter = ChildrenAdapter { child ->
            Toast.makeText(requireContext(), "Selected: ${child.full_name}", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to child details screen
        }

        binding.rvChildren.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChildren.adapter = childrenAdapter

        // Load children data (replace with actual data from API/Repository)
        loadChildrenData()
    }

    private fun loadChildrenData() {
        // TODO: Replace with actual data from your repository
        val sampleChildren = listOf(
            Child(
                ChildID = 1,
                Parent_ID = 1,
                full_name = "Emma Watson",
                date_of_birth = "2020-05-15",
                gender = "Female",
                blood_type = "O+"
            ),
            Child(
                ChildID = 2,
                Parent_ID = 1,
                full_name = "Liam Watson",
                date_of_birth = "2022-08-22",
                gender = "Male",
                blood_type = "A+"
            )
        )
        childrenAdapter.submitList(sampleChildren)
    }

    private fun setupSettingsCards() {
        // Notifications card
        binding.cvNotifications.setOnClickListener {
            Toast.makeText(requireContext(), "Notifications settings", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to notifications settings
        }

        // Privacy & Security card
        binding.cvPrivacySecurity.setOnClickListener {
            Toast.makeText(requireContext(), "Privacy & Security", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to privacy & security settings
        }

        // App Settings card
        binding.cvAppSettings.setOnClickListener {
            Toast.makeText(requireContext(), "App settings", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to app settings (language, theme, etc.)
        }
    }

    private fun setupHelpSupport() {
        binding.cvHelpSupport.setOnClickListener {
            Toast.makeText(requireContext(), "FAQs and Support", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to FAQ/Support screen
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}