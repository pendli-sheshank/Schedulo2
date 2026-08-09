package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.example.ui.theme.PrimaryGreen
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.schedulo.shared.model.Feedback
import com.schedulo.shared.model.FeedbackCategory
import com.schedulo.shared.model.FeedbackLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Where bug reports are emailed. Every report is also stored in Firestore, so a
 * change here only affects the mail hand-off, not the record of the submission.
 */
const val DEVELOPER_FEEDBACK_EMAIL = "sheshank3336@gmail.com"

private fun categoryLabel(category: String): String = when (category) {
    FeedbackCategory.BUG -> "Bug"
    FeedbackCategory.FEATURE -> "Feature request"
    else -> "Other"
}

class FeedbackViewModel : ViewModel() {

    private val auth by lazy { try { FirebaseAuth.getInstance() } catch (e: Exception) { null } }
    private val db by lazy {
        try {
            FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FIRESTORE_DB_NAME)
        } catch (e: Exception) { null }
    }
    private val storage by lazy { try { FirebaseStorage.getInstance() } catch (e: Exception) { null } }

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    /** Set once the report is safely in Firestore; drives the confirmation screen. */
    private val _submitted = MutableStateFlow<Feedback?>(null)
    val submitted = _submitted.asStateFlow()

    fun clearError() { _errorMessage.value = null }

    fun reportError(message: String) { _errorMessage.value = message }

    /** Reset so the screen can be reopened for another report without stale state. */
    fun reset() {
        _submitted.value = null
        _errorMessage.value = null
        _isSubmitting.value = false
    }

    fun submit(
        category: String,
        description: String,
        stepsToReproduce: String,
        screenshotUri: Uri?,
        context: Context
    ) {
        val user = auth?.currentUser
        val uid = user?.uid
        val database = db
        if (uid == null || database == null) {
            _errorMessage.value = "Please sign in to send feedback."
            return
        }
        if (!FeedbackLimits.isValidDescription(description)) {
            _errorMessage.value = "Please describe the issue before sending."
            return
        }

        _isSubmitting.value = true
        _errorMessage.value = null

        val feedback = Feedback(
            id = UUID.randomUUID().toString(),
            userId = uid,
            userEmail = user.email ?: "",
            category = category,
            description = description.trim(),
            stepsToReproduce = stepsToReproduce.trim(),
            appVersion = BuildConfig.VERSION_NAME,
            platform = "android",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            createdAt = System.currentTimeMillis()
        )

        if (screenshotUri == null) {
            write(feedback)
            return
        }

        // The screenshot has to land in Storage before the document is written:
        // feedback documents are immutable, so there is no second write to attach
        // the URL afterwards. Decode off the main thread — a large bitmap ANRs.
        val appContext = context.applicationContext
        val storageRef = storage
        if (storageRef == null) {
            _errorMessage.value = "Attachments are unavailable right now. Try again without the screenshot."
            _isSubmitting.value = false
            return
        }
        Thread {
            val compressed = try {
                compressImageForUpload(appContext, screenshotUri)
            } catch (t: Throwable) {
                _isSubmitting.value = false
                _errorMessage.value = "Failed to process screenshot: ${t.message ?: t.javaClass.simpleName}"
                return@Thread
            }
            if (compressed == null) {
                _isSubmitting.value = false
                _errorMessage.value = "Failed to process screenshot (unsupported or empty file)"
                return@Thread
            }
            val ref = storageRef.reference.child("feedback_screenshots/$uid/${feedback.id}.jpg")
            ref.putBytes(compressed)
                .addOnSuccessListener {
                    ref.downloadUrl
                        .addOnSuccessListener { url ->
                            feedback.screenshotUrl = url.toString()
                            write(feedback)
                        }
                        .addOnFailureListener { e ->
                            _isSubmitting.value = false
                            _errorMessage.value = "Failed to attach screenshot: ${e.message}"
                        }
                }
                .addOnFailureListener { e ->
                    _isSubmitting.value = false
                    _errorMessage.value = "Failed to upload screenshot: ${e.message}"
                }
        }.start()
    }

    private fun write(feedback: Feedback) {
        val database = db ?: return
        val data = hashMapOf(
            "id" to feedback.id,
            "userId" to feedback.userId,
            "userEmail" to feedback.userEmail,
            "category" to feedback.category,
            "description" to feedback.description,
            "stepsToReproduce" to feedback.stepsToReproduce,
            "screenshotUrl" to feedback.screenshotUrl,
            "appVersion" to feedback.appVersion,
            "platform" to feedback.platform,
            "osVersion" to feedback.osVersion,
            "deviceModel" to feedback.deviceModel,
            "status" to feedback.status,
            "createdAt" to feedback.createdAt
        )
        database.collection("feedback").document(feedback.id)
            .set(data)
            .addOnSuccessListener {
                _isSubmitting.value = false
                _submitted.value = feedback
            }
            .addOnFailureListener { e ->
                _isSubmitting.value = false
                _errorMessage.value = "Failed to send feedback: ${e.message}"
            }
    }
}

/** The plain-text report that goes into the developer's inbox. */
internal fun buildFeedbackEmailBody(feedback: Feedback): String = buildString {
    appendLine(feedback.description)
    appendLine()
    if (feedback.stepsToReproduce.isNotEmpty()) {
        appendLine("Steps to reproduce:")
        appendLine(feedback.stepsToReproduce)
        appendLine()
    }
    if (feedback.screenshotUrl.isNotEmpty()) {
        appendLine("Screenshot: ${feedback.screenshotUrl}")
        appendLine()
    }
    appendLine("---")
    appendLine("Category: ${categoryLabel(feedback.category)}")
    appendLine("App version: ${feedback.appVersion}")
    appendLine("Device: ${feedback.deviceModel}")
    appendLine("OS: ${feedback.osVersion}")
    appendLine("Reported by: ${feedback.userEmail.ifEmpty { "unknown" }} (${feedback.userId})")
    appendLine("Report ID: ${feedback.id}")
}

/**
 * Hand the report to whatever mail app the user has. Returns false when nothing
 * on the device can send mail — the report is already saved by then, so the
 * caller only needs to tell the user that the email step was skipped.
 */
internal fun sendFeedbackEmail(context: Context, feedback: Feedback): Boolean {
    val subject = "Shifnex ${categoryLabel(feedback.category)} — ${feedback.id.take(8)}"
    val body = buildFeedbackEmailBody(feedback)

    // ACTION_SENDTO with a mailto: URI targets mail apps specifically; it needs
    // the matching <queries> entry in the manifest to resolve on Android 11+.
    val sendTo = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(sendTo)
        return true
    } catch (_: Exception) {
        // No mail app, or the activity refused to start — fall through.
    }

    // A plain send chooser isn't subject to package-visibility filtering, so it
    // still offers whatever can handle text even when the mailto: lookup fails.
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        context.startActivity(Intent.createChooser(send, "Email feedback"))
        true
    } catch (_: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendFeedbackScreen(
    viewModel: FeedbackViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val submitted by viewModel.submitted.collectAsState()

    var category by remember { mutableStateOf(FeedbackCategory.BUG) }
    var description by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var mailAppOpened by remember { mutableStateOf(true) }

    // Reaching the confirmation state hands the report straight to the mail app;
    // the Firestore record already exists whether or not that succeeds.
    LaunchedEffect(submitted) {
        submitted?.let { mailAppOpened = sendFeedbackEmail(context, it) }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) screenshotUri = uri }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) screenshotUri = uri }

    // ACTION_OPEN_DOCUMENT is handled by the system DocumentsUI app which is
    // always present on every Android device — the most reliable final fallback.
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) screenshotUri = uri }

    // Every launch() is guarded: a launcher can throw (no handler, or a framework
    // requestCode error) and an uncaught exception here crashes the whole app.
    val launchImagePicker = {
        try {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                getContentLauncher.launch("image/*")
            }
        } catch (_: Exception) {
            try {
                getContentLauncher.launch("image/*")
            } catch (_: Exception) {
                try {
                    openDocumentLauncher.launch(arrayOf("image/*"))
                } catch (_: Exception) {
                    viewModel.reportError("No gallery app available to pick a screenshot.")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Feedback") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
        ) {
            val report = submitted
            if (report != null) {
                FeedbackConfirmation(
                    mailAppOpened = mailAppOpened,
                    onEmailAgain = { mailAppOpened = sendFeedbackEmail(context, report) },
                    onDone = {
                        viewModel.reset()
                        onBack()
                    }
                )
                return@Column
            }

            Text(
                "Found a bug or have an idea? Tell us what happened and it goes straight to the developer.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("What kind of feedback is this?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackCategory.ALL.forEach { option ->
                    val selected = category == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { category = option }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            categoryLabel(option),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = {
                    if (it.length <= FeedbackLimits.MAX_DESCRIPTION) description = it
                },
                label = { Text("What went wrong?") },
                placeholder = { Text("Describe the issue or idea") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 4,
                maxLines = 8,
                supportingText = {
                    Text("${description.length} / ${FeedbackLimits.MAX_DESCRIPTION}", fontSize = 12.sp)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = steps,
                onValueChange = { if (it.length <= FeedbackLimits.MAX_STEPS) steps = it },
                label = { Text("Steps to reproduce (optional)") },
                placeholder = { Text("1. Open Pay\n2. Switch to last week\n3. Total is wrong") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSubmitting) { launchImagePicker() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AttachFile, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (screenshotUri != null) "Screenshot attached" else "Attach a screenshot",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            if (screenshotUri != null) "Tap to choose a different image" else "Optional, but it helps a lot",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (screenshotUri != null) {
                        IconButton(onClick = { screenshotUri = null }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove screenshot",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Your app version, device model and account email are included so the " +
                    "report can be reproduced.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { viewModel.submit(category, description, steps, screenshotUri, context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = description.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send Feedback", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeedbackConfirmation(
    mailAppOpened: Boolean,
    onEmailAgain: () -> Unit,
    onDone: () -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = PrimaryGreen,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Report submitted", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (mailAppOpened) {
                "Thanks — your report was saved and handed to your email app. " +
                    "Send the draft to finish delivering it."
            } else {
                "Thanks — your report was saved. We couldn't find an email app on " +
                    "this device, so the developer will pick it up from the report list."
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onEmailAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Email, null, modifier = Modifier.size(18.dp), tint = PrimaryGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Email it again", fontWeight = FontWeight.SemiBold, color = PrimaryGreen)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
