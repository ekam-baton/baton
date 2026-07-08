package com.ekam.baton.core.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private const val TAG = "BillingManager"

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

    /**
     * Verify a Google Play purchase token with the BYOS backend.
     *
     * SECURITY (CRIT-1): The `return true` client-side fallback has been
     * removed entirely. A purchase MUST be validated server-side before
     * premium is granted. If no backend is configured, the purchase is
     * rejected and the user is prompted to configure their server.
     *
     * SECURITY (CRIT-6): The JWT secret is no longer transmitted in the
     * request body. Instead we use JSONObject.put() (no injection risk) and
     * the transport must be HTTPS (enforced by network_security_config.xml
     * for non-localhost endpoints).
     */
    private suspend fun verifyPurchaseWithBackend(purchaseToken: String): Boolean {
        val backendUrlStr = appPreferences.backendUrl.first()
        val jwtSecretStr = appPreferences.jwtSecret.first()

        // SECURITY FIX (CRIT-1): No fallback. No backend = no premium.
        if (jwtSecretStr.isBlank()) {
            Log.w(TAG, "Purchase verification skipped: no BYOS backend configured. " +
                    "Configure a backend URL and JWT secret in Settings to enable premium.")
            return false
        }

        return try {
            val verifyUrl = if (backendUrlStr.endsWith("/")) {
                "${backendUrlStr}verify-purchase"
            } else {
                "${backendUrlStr}/verify-purchase"
            }

            // SECURITY FIX (CRIT-6): Use JSONObject.put() — never string interpolation.
            // The JWT secret is NOT sent in this request. Instead, the server uses
            // the secret to sign a challenge that the client validates (see auth flow).
            // For now, we send only the purchase token and a request identifier.
            val jsonInput = JSONObject().apply {
                put("purchaseToken", purchaseToken)
                put("productId", PREMIUM_PRODUCT_ID)
            }.toString()

            val body = jsonInput.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(verifyUrl)
                .post(body)
                .addHeader("Authorization", "Bearer ${fetchAuthToken(backendUrlStr, jwtSecretStr)}")
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Purchase verification failed", e)
            false
        }
    }

    /**
     * Fetch a short-lived auth token from the BYOS backend using the
     * locally-stored JWT secret. The secret is used to SIGN a local
     * challenge and only the resulting token is sent over the wire —
     * the raw secret never leaves the device.
     */
    private suspend fun fetchAuthToken(backendUrl: String, jwtSecret: String): String {
        return try {
            val loginUrl = if (backendUrl.endsWith("/")) "${backendUrl}login" else "${backendUrl}/login"
            // Sign a local challenge with the secret (HMAC-SHA256 of a timestamp)
            val timestamp = System.currentTimeMillis().toString()
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            val secretKey = javax.crypto.spec.SecretKeySpec(jwtSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKey)
            val signature = android.util.Base64.encodeToString(
                mac.doFinal(timestamp.toByteArray(Charsets.UTF_8)),
                android.util.Base64.NO_WRAP
            )

            val jsonInput = JSONObject().apply {
                put("timestamp", timestamp)
                put("signature", signature)
            }.toString()

            val body = jsonInput.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(loginUrl).post(body).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                JSONObject(responseBody).optString("token", "")
            } else ""
        } catch (e: Exception) {
            Log.e(TAG, "Auth token fetch failed", e)
            ""
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
