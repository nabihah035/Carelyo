package com.example.carelyo.ui.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.carelyo.R

class MedicalHistoryDetailsBottomSheet(
    private val conditionName: String,
    private val diagnosisDate: String,
    private val treatment: String,
    private val notes: String
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_medical_history_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tvDetailsTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvDetailsDate)
        val tvDiagnosis = view.findViewById<TextView>(R.id.tvDetailsDiagnosis)
        val tvNotes = view.findViewById<TextView>(R.id.tvDetailsNotes)
        val ivClose = view.findViewById<ImageView>(R.id.ivCloseDetails)

        tvTitle.text = conditionName
        tvDate.text = diagnosisDate
        tvDiagnosis.text = treatment
        tvNotes.text = notes.ifBlank { "No additional notes" }

        ivClose.setOnClickListener {
            dismiss()
        }
    }
}