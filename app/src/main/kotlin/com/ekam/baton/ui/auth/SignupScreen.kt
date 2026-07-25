package com.ekam.baton.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowDropDown
import java.util.Calendar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupSuccess: (String, String, Long, String, String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var activeLegalTitle by remember { mutableStateOf<String?>(null) }
    var activeLegalContent by remember { mutableStateOf<String?>(null) }

    fun showLegal(title: String, fileName: String) {
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            activeLegalTitle = "Error"
            activeLegalContent = "Security Violation: Invalid file path."
            return
        }
        try {
            val content = context.assets.open(fileName).bufferedReader().use { it.readText() }
            activeLegalTitle = title
            activeLegalContent = content
        } catch (ignored: Exception) {
            activeLegalTitle = title
            activeLegalContent = "Failed to load document: ${ignored.localizedMessage}"
        }
    }

    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }
    
    // DPDP Act, 2023: Consent must be explicit, affirmative, and un-ticked by default.
    var hasExplicitConsent by remember { mutableStateOf(false) }

    // Global Compliance
    val regions = listOf("Global", "India", "US", "UK/EU", "UAE", "South Africa", "Brazil")
    var expandedRegion by remember { mutableStateOf(false) }
    var selectedRegion by remember { mutableStateOf(regions[0]) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = null
    )
    
    fun isOver18(selectedMillis: Long?): Boolean {
        if (selectedMillis == null) return false
        val dob = Calendar.getInstance().apply { timeInMillis = selectedMillis }
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        return age >= 18
    }

    fun validate(): Boolean {
        var isValid = true
        if (email.isBlank() || !email.contains("@") || !email.contains(".")) {
            emailError = "Please enter a valid email address"
            isValid = false
        } else {
            emailError = null
        }

        if (phone.isBlank() || phone.trim().length < 8) {
            phoneError = "Please enter a valid phone number"
            isValid = false
        } else {
            phoneError = null
        }
        
        if (!isOver18(datePickerState.selectedDateMillis)) {
            generalError = "You must be 18 or older to use BATON."
            isValid = false
        }
        
        return isValid
    }

    fun handleSignup() {
        if (!validate()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        val activity = context as? FragmentActivity
        if (activity == null) {
            generalError = "System error: Failed to initialize biometric login context."
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    generalError = errString.toString()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    generalError = null
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    
                    val consentTimestamp = System.currentTimeMillis()
                    val policyVersion = "v1.0"
                    
                    onSignupSuccess(email.trim(), phone.trim(), consentTimestamp, policyVersion, selectedRegion)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    generalError = "Biometric setup failed. Please try again."
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm Biometric setup")
            .setSubtitle("Confirm your biometric identity to lock and unlock BATON")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1424),
                        Color(0xFF000000)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon / Branding (Baton Logo)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = com.ekam.baton.R.drawable.ic_logo),
                    contentDescription = "Baton Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to BATON",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Just a few quick details to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Glassmorphic Card containing fields
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF131A2C)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.PersonOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Your Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Email Input
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            emailError = null
                        },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFF7A8B9E)
                            )
                        },
                        isError = emailError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedBorderColor = Color(0xFF232D4B),
                            focusedLabelColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedLabelColor = Color(0xFF7A8B9E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    emailError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Phone Input
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            phoneError = null
                        },
                        label = { Text("Phone Number") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color(0xFF7A8B9E)
                            )
                        },
                        isError = phoneError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedBorderColor = Color(0xFF232D4B),
                            focusedLabelColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedLabelColor = Color(0xFF7A8B9E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    phoneError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date of Birth (Age Gate)
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232D4B)),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF7A8B9E))
                            Spacer(modifier = Modifier.width(16.dp))
                            val dateStr = if (datePickerState.selectedDateMillis != null) {
                                val cal = Calendar.getInstance().apply { timeInMillis = datePickerState.selectedDateMillis!! }
                                "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
                            } else {
                                "Date of Birth (Must be 18+)"
                            }
                            Text(text = dateStr, color = if (datePickerState.selectedDateMillis != null) Color.White else Color(0xFF7A8B9E))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Region Selector
                    Box {
                        OutlinedButton(
                            onClick = { expandedRegion = true },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232D4B)),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Region: $selectedRegion", color = Color.White)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF7A8B9E))
                            }
                        }
                        DropdownMenu(
                            expanded = expandedRegion,
                            onDismissRequest = { expandedRegion = false },
                            modifier = Modifier.background(Color(0xFF131A2C))
                        ) {
                            regions.forEach { region ->
                                DropdownMenuItem(
                                    text = { Text(region, color = Color.White) },
                                    onClick = {
                                        selectedRegion = region
                                        expandedRegion = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    generalError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Button(
                        onClick = { handleSignup() },
                        enabled = hasExplicitConsent,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                            disabledContentColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Get Started",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // DPDP Act, 2023 – Explicit, affirmative consent block.
                    // Consent must be un-ticked by default and the user must actively check it.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = hasExplicitConsent,
                            onCheckedChange = { hasExplicitConsent = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.tertiary,
                                uncheckedColor = Color(0xFF7A8B9E),
                                checkmarkColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Wrap the legal text so it wraps cleanly next to the checkbox
                        Column {
                            Text(
                                text = "I have read and explicitly agree to the:",
                                fontSize = 11.sp,
                                color = Color(0xFF7A8B9E)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showLegal("Terms of Service", "terms_of_service.txt") },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "Terms",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                                Text(text = "•", fontSize = 11.sp, color = Color(0xFF7A8B9E))
                                TextButton(
                                    onClick = { showLegal("Privacy Policy", "privacy_policy.txt") },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "Privacy Policy",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                                Text(text = "•", fontSize = 11.sp, color = Color(0xFF7A8B9E))
                                TextButton(
                                    onClick = { showLegal("Biometric Disclosure", "biometric_disclosure.txt") },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "Biometrics",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                            }
                            
                            if (selectedRegion == "UAE") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "By selecting UAE, you confirm you will adhere to TRA regulations regarding VoIP and encryption.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { 
                    showDatePicker = false
                    generalError = null // Clear error on change
                }) {
                    Text("OK", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = Color(0xFF7A8B9E))
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color(0xFF0F1623)
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    titleContentColor = Color.White,
                    headlineContentColor = Color.White,
                    weekdayContentColor = Color(0xFFCFD8DC),
                    dayContentColor = Color.White,
                    selectedDayContainerColor = MaterialTheme.colorScheme.tertiary,
                    todayDateBorderColor = MaterialTheme.colorScheme.tertiary,
                    todayContentColor = MaterialTheme.colorScheme.tertiary
                )
            )
        }
    }

    if (activeLegalTitle != null && activeLegalContent != null) {
        AlertDialog(
            onDismissRequest = {
                activeLegalTitle = null
                activeLegalContent = null
            },
            title = {
                Text(
                    text = activeLegalTitle!!,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = activeLegalContent!!,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFCFD8DC),
                            lineHeight = 20.sp
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeLegalTitle = null
                        activeLegalContent = null
                    }
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            containerColor = Color(0xFF0F1623), // BatonSurface
            shape = RoundedCornerShape(24.dp)
        )
    }
}
