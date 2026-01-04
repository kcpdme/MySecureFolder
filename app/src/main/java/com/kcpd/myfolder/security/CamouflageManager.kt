package com.kcpd.myfolder.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Calculator Camouflage (Stealth Mode) launcher icon switching.
 * 
 * This class is now a thin wrapper that delegates PIN management to SecurityPinManager
 * and only handles the launcher icon/name switching.
 * 
 * When camouflage is enabled:
 * - App icon changes to "Calculator" with a calculator icon
 * - Opening the app shows a fully functional calculator
 * - Entering the Security PIN + "=" unlocks and reveals the actual vault
 */
@Singleton
class CamouflageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityPinManager: SecurityPinManager
) {
    companion object {
        // Activity aliases defined in AndroidManifest.xml
        private const val ALIAS_DEFAULT = "com.kcpd.myfolder.MainActivityDefault"
        private const val ALIAS_CALCULATOR = "com.kcpd.myfolder.MainActivityCalculator"
    }
    
    // Delegate to SecurityPinManager
    val stealthModeEnabled: StateFlow<Boolean> = securityPinManager.camouflageEnabled
    
    /**
     * Enables or disables stealth mode (calculator camouflage).
     * 
     * @throws IllegalStateException if enabling without Security PIN set
     */
    fun setStealthModeEnabled(enabled: Boolean) {
        securityPinManager.setCamouflageEnabled(enabled)
        switchLauncherAlias(enabled)
    }
    
    /**
     * Checks if stealth mode is currently enabled.
     */
    fun isStealthModeEnabled(): Boolean {
        return securityPinManager.camouflageEnabled.value
    }
    
    /**
     * Verifies the Calculator PIN (delegates to SecurityPinManager).
     */
    fun verifyCalculatorPin(pin: String): Boolean {
        return securityPinManager.verifyPin(pin)
    }
    
    /**
     * Checks if Calculator PIN is set (delegates to SecurityPinManager).
     */
    fun isCalculatorPinSet(): Boolean {
        return securityPinManager.isPinSet()
    }
    
    /**
     * Syncs the launcher alias state with the stored preference.
     * Call this on app startup to ensure consistency.
     */
    fun syncLauncherState() {
        switchLauncherAlias(isStealthModeEnabled())
    }
    
    /**
     * Switches the launcher activity alias to show either:
     * - Calculator icon/name (stealth mode)
     * - Original vault icon/name (normal mode)
     * 
     * This works by enabling/disabling activity-aliases in the manifest.
     */
    private fun switchLauncherAlias(useCalculator: Boolean) {
        val pm = context.packageManager
        
        val defaultComponent = ComponentName(context, ALIAS_DEFAULT)
        val calculatorComponent = ComponentName(context, ALIAS_CALCULATOR)
        
        try {
            if (useCalculator) {
                // Enable Calculator alias, disable Default
                pm.setComponentEnabledSetting(
                    calculatorComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    defaultComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                // Enable Default alias, disable Calculator
                pm.setComponentEnabledSetting(
                    defaultComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    calculatorComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            android.util.Log.i("CamouflageManager", "Switched launcher alias: calculator=$useCalculator")
        } catch (e: Exception) {
            android.util.Log.e("CamouflageManager", "Failed to switch launcher alias", e)
        }
    }
    
    /**
     * Gets the current launcher icon state.
     * Useful for verifying the switch was successful.
     */
    fun getCurrentLauncherState(): String {
        val pm = context.packageManager
        
        return try {
            val defaultState = pm.getComponentEnabledSetting(
                ComponentName(context, ALIAS_DEFAULT)
            )
            val calcState = pm.getComponentEnabledSetting(
                ComponentName(context, ALIAS_CALCULATOR)
            )
            
            "Default: ${stateToString(defaultState)}, Calculator: ${stateToString(calcState)}"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun stateToString(state: Int): String {
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "Enabled"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "Disabled"
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "Default"
            else -> "Unknown($state)"
        }
    }
}
