package com.ekam.baton.core.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.ekam.baton.core.data.preferences.AppPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class BillingManager(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val httpClient: OkHttpClient,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : PurchasesUpdatedListener {

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _connectionState = MutableStateFlow<Int>(BillingClient.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    companion object {
        const val PREMIUM_PRODUCT_ID = "baton_premium_subscription"
    }

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                _connectionState.value = billingClient.connectionState
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                    queryProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingClient.ConnectionState.DISCONNECTED
                // Retry connection logic can be added here
            }
        })
    }

    private fun queryProductDetails() {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = productDetailsList
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: "")
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            coroutineScope.launch {
                val isValid = verifyPurchaseWithBackend(purchase.purchaseToken)
                if (isValid) {
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                _isPremium.value = true
                                coroutineScope.launch { appPreferences.setPremiumUnlocked(true) }
                            }
                        }
                    } else {
                        _isPremium.value = true
                        appPreferences.setPremiumUnlocked(true)
                    }
                }
            }
        }
    }
    
    private suspend fun verifyPurchaseWithBackend(purchaseToken: String): Boolean {
        val backendUrlStr = appPreferences.backendUrl.first()
        val jwtSecretStr = appPreferences.jwtSecret.first()
        
        // If no backend is configured, fallback to local trust (infant-stage app behavior)
        if (jwtSecretStr.isBlank()) {
            // WARNING: Client-side only verification is vulnerable to spoofing (e.g., Lucky Patcher).
            // This fallback exists only for local/decentralized setups without a billing validation server.
            return true 
        }

        return try {
            val verifyUrl = if (backendUrlStr.endsWith("/")) "${backendUrlStr}verify-purchase" else "${backendUrlStr}/verify-purchase"
            val jsonInput = JSONObject().apply {
                put("secret", jwtSecretStr)
                put("purchaseToken", purchaseToken)
            }.toString()
            
            val body = RequestBody.create("application/json".toMediaTypeOrNull(), jsonInput)
            val request = Request.Builder()
                .url(verifyUrl)
                .post(body)
                .build()
            
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun queryPurchases() {
        coroutineScope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val hasActiveSub = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    _isPremium.value = hasActiveSub
                    if (hasActiveSub) {
                        coroutineScope.launch { appPreferences.setPremiumUnlocked(true) }
                    }
                }
            }
        }
    }
}
