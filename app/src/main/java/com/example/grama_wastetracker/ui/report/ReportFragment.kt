package com.example.grama_wastetracker.ui.report

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.FragmentReportBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Bitmap

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private var imageUri: Uri? = null
    private var reportLat = 12.9716
    private var reportLng = 77.5946

    // ── Gallery Picker ────────────────────────────────────────────────────────
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    val tempFile = java.io.File(requireContext().cacheDir, "temp_upload.jpg")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    imageUri = Uri.fromFile(tempFile)
                    showImagePreview(uri)
                    fetchLiveLocationAndShowMetadata()
                } catch (e: Exception) {
                    Snackbar.make(binding.root, "Error loading image", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

    // ── Camera Capture ────────────────────────────────────────────────────────
    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                try {
                    val tempFile = java.io.File(requireContext().cacheDir, "camera_upload.jpg")
                    val outputStream = tempFile.outputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    outputStream.flush()
                    outputStream.close()
                    imageUri = Uri.fromFile(tempFile)
                    binding.imagePreview.setImageBitmap(bitmap)
                    showImagePreview(null)
                    fetchLiveLocationAndShowMetadata()
                } catch (e: Exception) {
                    Snackbar.make(binding.root, "Camera error", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

    // ── Permission launchers ──────────────────────────────────────────────────
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startSubmit()
            else Snackbar.make(binding.root, R.string.camera_permission_denied, Snackbar.LENGTH_LONG).show()
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) takePhotoLauncher.launch(null)
            else Snackbar.make(binding.root, "Camera permission denied", Snackbar.LENGTH_LONG).show()
        }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)

        // The whole photo zone card opens the picker dialog
        binding.cardPhotoZone.setOnClickListener { showImagePickerDialog() }

        // Hidden "Change Photo" button (shown after a photo is selected)
        binding.btnUploadPhoto.setOnClickListener { showImagePickerDialog() }

        binding.btnSubmit.setOnClickListener { checkLocationPermission() }

        return binding.root
    }

    // ── Image picker dialog ───────────────────────────────────────────────────
    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) takePhotoLauncher.launch(null)
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    // ── Show the chosen image and reveal "Change Photo" button ───────────────
    private fun showImagePreview(uri: Uri?) {
        if (uri != null) binding.imagePreview.setImageURI(uri)
        binding.imagePreview.visibility = View.VISIBLE
        binding.layoutPhotoPlaceholder.visibility = View.GONE
        // Show "Change Photo" button below the submit button
        binding.btnUploadPhoto.visibility = View.VISIBLE
    }

    // ── Fetch GPS then show location card ────────────────────────────────────
    private fun fetchLiveLocationAndShowMetadata() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(requireActivity())
                .lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) { reportLat = loc.latitude; reportLng = loc.longitude }
                    showMetadata()
                }
                .addOnFailureListener { showMetadata() }
        } else {
            showMetadata()
        }
    }

    private fun showMetadata() {
        binding.tvTimestamp.text =
            SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())

        try {
            val addresses = Geocoder(requireContext(), Locale.getDefault())
                .getFromLocation(reportLat, reportLng, 1)
            binding.tvLocation.text = addresses?.firstOrNull()?.let {
                "${it.subLocality ?: ""}, ${it.locality ?: ""}".trim(',', ' ')
            } ?: "Ashok Nagar, Bengaluru"
        } catch (e: Exception) {
            binding.tvLocation.text = "Ashok Nagar, Bengaluru"
        }

        binding.layoutMetadata.visibility = View.VISIBLE
    }

    // ── Location permission → submit ──────────────────────────────────────────
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) startSubmit()
        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // ── Compress + base64 + upload ────────────────────────────────────────────
    private fun startSubmit() {
        if (imageUri == null) {
            Snackbar.make(binding.root, R.string.select_photo_first, Snackbar.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anon"

        try {
            val bytes = requireContext().contentResolver.openInputStream(imageUri!!)?.readBytes()
            if (bytes != null) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val baos   = java.io.ByteArrayOutputStream()
                val maxDim = 800
                val scale  = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                val scaledBmp = if (scale < 1)
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width  * scale).toInt(),
                        (bitmap.height * scale).toInt(),
                        true
                    ) else bitmap
                scaledBmp.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                val base64  = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                val dataUri = "data:image/jpeg;base64,$base64"
                saveReportToDb(uid, dataUri)
            } else {
                setLoading(false)
                Snackbar.make(binding.root, "Error reading image file", Snackbar.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            setLoading(false)
            Snackbar.make(binding.root, "Upload Failed: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun saveReportToDb(uid: String, imageUrl: String) {
        // Resolve selected waste category chip text
        val category = when (binding.chipGroupCategory.checkedChipId) {
            R.id.chipHazardous    -> "Hazardous"
            R.id.chipConstruction -> "Construction"
            else                  -> "General Waste"
        }
        val extraDetails = binding.etDetails.text.toString().trim()

        val dbRef = FirebaseDatabase.getInstance().getReference("reports")
        val reportId = dbRef.push().key!!

        dbRef.child(reportId).setValue(
            mapOf(
                "lat"          to reportLat,
                "lng"          to reportLng,
                "timestamp"    to System.currentTimeMillis(),
                "status"       to "pending",
                "image_url"    to imageUrl,
                "reported_by"  to uid,
                "location"     to binding.tvLocation.text.toString(),
                "category"     to category,
                "details"      to extraDetails
            )
        ).addOnSuccessListener {
            setLoading(false)
            binding.cardSuccess.visibility = View.VISIBLE
        }.addOnFailureListener { e ->
            setLoading(false)
            Snackbar.make(binding.root, "Save Failed: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setLoading(on: Boolean) {
        binding.progressUpload.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled       = !on
        binding.btnSubmit.text            = if (on) getString(R.string.uploading) else "Submit Garbage Report ➤"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}