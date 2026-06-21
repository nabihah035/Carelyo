package com.example.carelyo.ui.summary

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carelyo.R
import com.example.carelyo.data.entity.Child
import com.example.carelyo.data.entity.DoctorVisit
import com.example.carelyo.data.session.SessionManager
import com.example.carelyo.databinding.FragmentDoctorSummaryBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class DoctorSummaryFragment : Fragment() {

    private var _binding: FragmentDoctorSummaryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DoctorSummaryViewModel by viewModels()
    private lateinit var sessionManager: SessionManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var selectedChildId: Int = -1
    private var transcribedText = ""
    private var voiceDialog: BottomSheetDialog? = null
    private var tvRecordHintDialog: android.widget.TextView? = null
    private var tvLivePreviewDialog: android.widget.TextView? = null

    private lateinit var summaryAdapter: DoctorSummaryAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startSpeechListening()
        } else {
            Toast.makeText(context, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDoctorSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        summaryAdapter = DoctorSummaryAdapter { visit ->
            showDetailDialog(visit)
        }
        binding.rvPreviousSummaries.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = summaryAdapter
        }
    }

    private fun setupClickListeners() {
        // Upper action buttons
        binding.btnRecord.setOnClickListener {
            showVoiceRecordingDialog()
        }

        binding.btnType.setOnClickListener {
            showTypeNotesDialog()
        }
    }

    private fun startSpeechListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Attempt to increase timeout for devices that support it
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p0: Bundle?) {
                tvRecordHintDialog?.text = "Listening... Tap mic circle to stop."
                isRecording = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(err: Int) {
                if (isRecording && (err == SpeechRecognizer.ERROR_NO_MATCH || err == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    speechRecognizer?.startListening(intent)
                } else if (err != SpeechRecognizer.ERROR_CLIENT) {
                    tvRecordHintDialog?.text = "Tap to start recording"
                    isRecording = false
                }
            }
            override fun onResults(bundle: Bundle?) {
                val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val newText = matches[0]
                    transcribedText = if (transcribedText.isEmpty()) newText else "$transcribedText $newText"
                    tvLivePreviewDialog?.text = transcribedText
                }
                
                if (isRecording) {
                    speechRecognizer?.startListening(intent)
                } else {
                    tvRecordHintDialog?.text = "Tap to start recording"
                }
            }
            override fun onPartialResults(bundle: Bundle?) {
                val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    val displayText = if (transcribedText.isEmpty()) partialText else "$transcribedText $partialText"
                    tvLivePreviewDialog?.text = displayText
                }
            }
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun showVoiceRecordingDialog() {
        transcribedText = ""
        val dialog = BottomSheetDialog(requireContext())
        voiceDialog = dialog
        val view = layoutInflater.inflate(R.layout.dialog_voice_recording, null)
        dialog.setContentView(view)

        val btnClose = view.findViewById<View>(R.id.btnClose)
        val spinnerChild = view.findViewById<android.widget.Spinner>(R.id.spinnerChild)
        val etDoctorName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDoctorName)
        val etClinicName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etClinicName)
        val btnToggleRecord = view.findViewById<View>(R.id.btnToggleRecord)
        tvRecordHintDialog = view.findViewById(R.id.tvRecordHint)
        tvLivePreviewDialog = view.findViewById(R.id.tvLiveTranscriptionPreview)
        val btnSaveRecording = view.findViewById<View>(R.id.btnSaveRecording)

        btnClose.setOnClickListener {
            stopSpeechListening()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            stopSpeechListening()
            voiceDialog = null
            tvRecordHintDialog = null
            tvLivePreviewDialog = null
        }

        // Setup Child Spinner for Dialog
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.childrenList.collectLatest { children ->
                if (children.isNotEmpty()) {
                    val names = children.map { it.full_name ?: "Child ID: ${it.ChildID}" }
                    val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names).apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
                    spinnerChild.adapter = spinnerAdapter
                    val initialIndex = children.indexOfFirst { it.ChildID == selectedChildId }.coerceAtLeast(0)
                    spinnerChild.setSelection(initialIndex)

                    spinnerChild.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                            selectedChildId = children[position].ChildID
                        }
                        override fun onNothingSelected(p0: AdapterView<*>?) {}
                    }
                }
            }
        }

        btnToggleRecord.setOnClickListener {
            if (!isRecording) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    startSpeechListening()
                }
            } else {
                stopSpeechListening()
            }
        }

        btnSaveRecording.setOnClickListener {
            val doctorName = etDoctorName.text.toString().trim()
            val clinicName = etClinicName.text.toString().trim()
            if (doctorName.isEmpty()) {
                etDoctorName.error = "Doctor name required"
                return@setOnClickListener
            }
            if (transcribedText.isEmpty()) {
                Toast.makeText(context, "Please record raw notes first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedChildId == -1) return@setOnClickListener

            val finalNotes = if (clinicName.isNotEmpty()) {
                "Clinic: $clinicName\n$transcribedText"
            } else {
                transcribedText
            }

            viewModel.generateSummaryFromNotes(selectedChildId, doctorName, "", finalNotes)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTypeNotesDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_type_notes, null)
        dialog.setContentView(view)

        val btnCancelType = view.findViewById<View>(R.id.btnCancelType)
        val spinnerChild = view.findViewById<android.widget.Spinner>(R.id.spinnerChild)
        val etDocNameInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDocNameInput)
        val etClinicName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etClinicName)
        val etRawNotesInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRawNotesInput)
        val btnGenerateAiSummary = view.findViewById<View>(R.id.btnGenerateAiSummary)

        btnCancelType.setOnClickListener {
            dialog.dismiss()
        }

        // Setup Child Spinner for Dialog
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.childrenList.collectLatest { children ->
                if (children.isNotEmpty()) {
                    val names = children.map { it.full_name ?: "Child ID: ${it.ChildID}" }
                    val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names).apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
                    spinnerChild.adapter = spinnerAdapter
                    val initialIndex = children.indexOfFirst { it.ChildID == selectedChildId }.coerceAtLeast(0)
                    spinnerChild.setSelection(initialIndex)

                    spinnerChild.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                            selectedChildId = children[position].ChildID
                        }
                        override fun onNothingSelected(p0: AdapterView<*>?) {}
                    }
                }
            }
        }

        btnGenerateAiSummary.setOnClickListener {
            val doctorName = etDocNameInput.text.toString().trim()
            val clinicName = etClinicName.text.toString().trim()
            val rawNotes = etRawNotesInput.text.toString().trim()

            if (doctorName.isEmpty()) {
                etDocNameInput.error = "Doctor name required"
                return@setOnClickListener
            }
            if (rawNotes.isEmpty()) {
                etRawNotesInput.error = "Notes cannot be empty"
                return@setOnClickListener
            }
            if (selectedChildId == -1) return@setOnClickListener

            val finalNotes = if (clinicName.isNotEmpty()) {
                "Clinic: $clinicName\n$rawNotes"
            } else {
                rawNotes
            }

            viewModel.generateSummaryFromNotes(selectedChildId, doctorName, "", finalNotes)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun stopSpeechListening() {
        if (isRecording) {
            isRecording = false
            speechRecognizer?.stopListening()
        }
        tvRecordHintDialog?.text = "Tap to start recording"
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.doctorVisits.collectLatest { visits ->
                summaryAdapter.submitList(visits)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.summaryState.collectLatest { state ->
                when (state) {
                    is UiState.Loading -> binding.progressBar.visibility = View.VISIBLE
                    else -> binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun showDetailDialog(visit: DoctorVisit) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.dialog_doctor_summary_detail, null)
        dialog.setContentView(sheetView)

        sheetView.findViewById<android.widget.TextView>(R.id.tvDetailDoctorName).text = visit.doctor_name
        sheetView.findViewById<android.widget.TextView>(R.id.tvDetailVisitDate).text = visit.visit_date
        sheetView.findViewById<android.widget.TextView>(R.id.tvDetailAiSummaryText).text = visit.ai_summary
        sheetView.findViewById<android.widget.TextView>(R.id.tvDetailRawNotes).text = visit.raw_notes

        val pointsContainer = sheetView.findViewById<android.widget.LinearLayout>(R.id.llDetailKeyPointsContainer)
        pointsContainer.removeAllViews()

        val parsedPoints = parseKeyPoints(visit.ai_summary ?: "")
        parsedPoints.forEachIndexed { index, point ->
            val itemKeyView = layoutInflater.inflate(R.layout.item_key_point, pointsContainer, false)
            itemKeyView.findViewById<android.widget.TextView>(R.id.tvKeyPoint).text = "${index + 1}. $point"
            pointsContainer.addView(itemKeyView)
        }

        sheetView.findViewById<View>(R.id.btnBack).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun parseKeyPoints(summary: String): List<String> {
        val lines = summary.split("\n")
        val keyPoints = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("•") || trimmed.startsWith("-")) {
                keyPoints.add(trimmed.drop(1).trim())
            }
        }
        return keyPoints.ifEmpty { summary.split(".").map { it.trim() }.filter { it.length > 5 } }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        _binding = null
    }
}