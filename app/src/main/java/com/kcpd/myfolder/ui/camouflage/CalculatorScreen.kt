package com.kcpd.myfolder.ui.camouflage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.security.PasswordManager
import com.kcpd.myfolder.security.SecurityPinManager
import com.kcpd.myfolder.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import javax.inject.Inject

/**
 * Calculator ViewModel - handles both calculator logic AND secret PIN detection.
 * 
 * The secret code is the Security PIN (numeric) followed by "=".
 * Example: if PIN is "1234", entering "1234=" unlocks the vault.
 * 
 * The calculator is fully functional to avoid any suspicion.
 */
@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val securityPinManager: SecurityPinManager,
    private val passwordManager: PasswordManager,
    private val vaultManager: VaultManager
) : ViewModel() {
    
    data class CalculatorState(
        val display: String = "0",
        val previousNumber: Double? = null,
        val operation: String? = null,
        val waitingForOperand: Boolean = false,
        val lastButtonWasEquals: Boolean = false,
        val isUnlocking: Boolean = false,
        val isUnlocked: Boolean = false,
        val error: String? = null
    )
    
    private val _state = MutableStateFlow(CalculatorState())
    val state: StateFlow<CalculatorState> = _state.asStateFlow()
    
    // Buffer to track typed input for secret code detection
    private val inputBuffer = StringBuilder()
    
    // Decimal formatter for display
    private val decimalFormat = DecimalFormat("#,##0.##########")
    
    fun onDigit(digit: String) {
        inputBuffer.append(digit)
        
        val current = _state.value
        
        if (current.waitingForOperand || current.lastButtonWasEquals) {
            _state.value = current.copy(
                display = digit,
                waitingForOperand = false,
                lastButtonWasEquals = false
            )
        } else {
            val newDisplay = if (current.display == "0" && digit != ".") {
                digit
            } else if (digit == "." && current.display.contains(".")) {
                current.display // Ignore duplicate decimal
            } else {
                current.display + digit
            }
            _state.value = current.copy(display = newDisplay)
        }
    }
    
    fun onOperation(op: String) {
        inputBuffer.append(op)
        
        val current = _state.value
        val currentNumber = current.display.replace(",", "").toDoubleOrNull() ?: return
        
        if (current.previousNumber != null && current.operation != null && !current.waitingForOperand) {
            // Chain calculation
            val result = calculate(current.previousNumber, currentNumber, current.operation)
            _state.value = current.copy(
                display = formatNumber(result),
                previousNumber = result,
                operation = op,
                waitingForOperand = true,
                lastButtonWasEquals = false
            )
        } else {
            _state.value = current.copy(
                previousNumber = currentNumber,
                operation = op,
                waitingForOperand = true,
                lastButtonWasEquals = false
            )
        }
    }
    
    fun onEquals() {
        // Check for secret PIN first!
        val secretCode = inputBuffer.toString()
        inputBuffer.clear()
        
        // Extract just the digits typed (the PIN)
        // Filter out operations to get the pure numeric sequence
        val potentialPin = secretCode.filter { it.isDigit() }
        
        if (potentialPin.isNotEmpty()) {
            checkSecretPin(potentialPin)
        }
        
        // Also perform normal calculation
        val current = _state.value
        val currentNumber = current.display.replace(",", "").toDoubleOrNull() ?: return
        
        if (current.previousNumber != null && current.operation != null) {
            val result = calculate(current.previousNumber, currentNumber, current.operation)
            _state.value = current.copy(
                display = formatNumber(result),
                previousNumber = null,
                operation = null,
                waitingForOperand = false,
                lastButtonWasEquals = true
            )
        }
    }
    
    fun onClear() {
        inputBuffer.clear()
        _state.value = CalculatorState()
    }
    
    fun onClearEntry() {
        // Clear current entry but keep the operation chain
        val current = _state.value
        _state.value = current.copy(
            display = "0",
            waitingForOperand = false
        )
    }
    
    fun onBackspace() {
        val current = _state.value
        if (current.display.length > 1) {
            _state.value = current.copy(display = current.display.dropLast(1))
        } else {
            _state.value = current.copy(display = "0")
        }
        
        if (inputBuffer.isNotEmpty()) {
            inputBuffer.deleteCharAt(inputBuffer.length - 1)
        }
    }
    
    fun onPercentage() {
        val current = _state.value
        val currentNumber = current.display.replace(",", "").toDoubleOrNull() ?: return
        val result = currentNumber / 100.0
        _state.value = current.copy(display = formatNumber(result))
    }
    
    fun onPlusMinus() {
        val current = _state.value
        val currentNumber = current.display.replace(",", "").toDoubleOrNull() ?: return
        val result = currentNumber * -1
        _state.value = current.copy(display = formatNumber(result))
    }
    
    private fun calculate(a: Double, b: Double, op: String): Double {
        return when (op) {
            "+" -> a + b
            "-" -> a - b
            "×" -> a * b
            "÷" -> if (b != 0.0) a / b else Double.NaN
            else -> b
        }
    }
    
    private fun formatNumber(number: Double): String {
        return if (number.isNaN() || number.isInfinite()) {
            "Error"
        } else {
            decimalFormat.format(number)
        }
    }
    
    private fun checkSecretPin(pin: String) {
        if (_state.value.isUnlocking) return
        
        // Verify against the Security PIN
        val isPinValid = securityPinManager.verifyPin(pin)
        
        if (isPinValid) {
            _state.value = _state.value.copy(isUnlocking = true)
            
            viewModelScope.launch {
                try {
                    // PIN is valid - unlock the vault using biometric-style unlock
                    // (since we've verified the stealth PIN, we can unlock the vault)
                    if (passwordManager.unlockWithBiometrics()) {
                        _state.value = _state.value.copy(
                            isUnlocking = false,
                            isUnlocked = true
                        )
                    } else {
                        // Fallback: try to load master key directly
                        // This handles edge cases where biometric unlock isn't configured
                        _state.value = _state.value.copy(isUnlocking = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CalculatorViewModel", "Error unlocking vault", e)
                    _state.value = _state.value.copy(isUnlocking = false)
                }
            }
        }
        // If PIN is wrong, silently continue as a normal calculator (no error shown)
    }
}

/**
 * Calculator Screen - A fully functional calculator that disguises the vault app.
 * 
 * Design inspired by iOS Calculator with a dark theme that looks premium
 * and doesn't raise suspicion. Entering the vault password + = unlocks the vault.
 */
@Composable
fun CalculatorScreen(
    onUnlocked: () -> Unit,
    viewModel: CalculatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // Navigate when unlocked
    LaunchedEffect(state.isUnlocked) {
        if (state.isUnlocked) {
            onUnlocked()
        }
    }
    
    // Calculator color scheme (iOS-inspired dark theme)
    val backgroundColor = Color(0xFF000000)
    val displayColor = Color.White
    val numberButtonColor = Color(0xFF333333)
    val operatorButtonColor = Color(0xFFFF9F0A) // Orange
    val functionButtonColor = Color(0xFFA5A5A5) // Light gray
    val buttonTextColor = Color.White
    val functionTextColor = Color.Black
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Display area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(
                    text = state.display,
                    color = displayColor,
                    fontSize = calculateDisplayFontSize(state.display),
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Button grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: AC, +/-, %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalculatorButton(
                        text = if (state.display == "0" && state.previousNumber == null) "AC" else "C",
                        backgroundColor = functionButtonColor,
                        textColor = functionTextColor,
                        modifier = Modifier.weight(1f),
                        onClick = { 
                            if (state.display == "0" && state.previousNumber == null) {
                                viewModel.onClear()
                            } else {
                                viewModel.onClearEntry()
                            }
                        }
                    )
                    CalculatorButton(
                        text = "±",
                        backgroundColor = functionButtonColor,
                        textColor = functionTextColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onPlusMinus() }
                    )
                    CalculatorButton(
                        text = "%",
                        backgroundColor = functionButtonColor,
                        textColor = functionTextColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onPercentage() }
                    )
                    CalculatorButton(
                        text = "÷",
                        backgroundColor = operatorButtonColor,
                        modifier = Modifier.weight(1f),
                        isSelected = state.operation == "÷" && state.waitingForOperand,
                        onClick = { viewModel.onOperation("÷") }
                    )
                }
                
                // Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalculatorButton(
                        text = "7",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit("7") }
                    )
                    CalculatorButton(
                        text = "8",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit("8") }
                    )
                    CalculatorButton(
                        text = "9",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit("9") }
                    )
                    CalculatorButton(
                        text = "×",
                        backgroundColor = operatorButtonColor,
                        modifier = Modifier.weight(1f),
                        isSelected = state.operation == "×" && state.waitingForOperand,
                        onClick = { viewModel.onOperation("×") }
                    )
                }
                
                // Row 3: 4, 5, 6, -
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalculatorButton(
                        text = "4",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit("4") }
                    )
                    CalculatorButton(
                        text = "5",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit("5") }
                    )
                    CalculatorButton(
                        text = "6",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit("6") }
                    )
                    CalculatorButton(
                        text = "−",
                        backgroundColor = operatorButtonColor,
                        modifier = Modifier.weight(1f),
                        isSelected = state.operation == "-" && state.waitingForOperand,
                        onClick = { viewModel.onOperation("-") }
                    )
                }
                
                // Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalculatorButton(
                        text = "1",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit("1") }
                    )
                    CalculatorButton(
                        text = "2",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit("2") }
                    )
                    CalculatorButton(
                        text = "3",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit("3") }
                    )
                    CalculatorButton(
                        text = "+",
                        backgroundColor = operatorButtonColor,
                        modifier = Modifier.weight(1f),
                        isSelected = state.operation == "+" && state.waitingForOperand,
                        onClick = { viewModel.onOperation("+") }
                    )
                }
                
                // Row 5: 0 (wide), ., =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalculatorButton(
                        text = "0",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(2f),
                        isWide = true,
                        onClick = { viewModel.onDigit("0") }
                    )
                    CalculatorButton(
                        text = ".",
                        backgroundColor = numberButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onDigit(".") }
                    )
                    CalculatorButton(
                        text = "=",
                        backgroundColor = operatorButtonColor,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onEquals() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CalculatorButton(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    isWide: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val actualBackgroundColor = if (isSelected) {
        Color.White
    } else {
        backgroundColor
    }
    
    val actualTextColor = if (isSelected) {
        backgroundColor
    } else {
        textColor
    }
    
    val buttonHeight = 76.dp
    
    Surface(
        modifier = modifier
            .height(buttonHeight)
            .clip(if (isWide) RoundedCornerShape(buttonHeight / 2) else CircleShape)
            .clickable(onClick = onClick),
        color = actualBackgroundColor,
        shape = if (isWide) RoundedCornerShape(buttonHeight / 2) else CircleShape
    ) {
        Box(
            contentAlignment = if (isWide) Alignment.CenterStart else Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isWide) 28.dp else 0.dp)
        ) {
            Text(
                text = text,
                color = actualTextColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun calculateDisplayFontSize(display: String): androidx.compose.ui.unit.TextUnit {
    return when {
        display.length <= 6 -> 80.sp
        display.length <= 9 -> 60.sp
        display.length <= 12 -> 48.sp
        else -> 36.sp
    }
}
