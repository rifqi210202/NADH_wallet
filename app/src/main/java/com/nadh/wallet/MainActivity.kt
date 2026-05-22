package com.nadh.wallet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nadh.wallet.ui.theme.NadhWalletTheme
import com.google.gson.Gson
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewModelScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.URI
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ==========================================
// MODELS (REST API Data Schemas)
// ==========================================
data class UserProfile(
    val id: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val isVerified: Boolean,
    val walletId: String,
    val balance: Double,
    val profileImageUri: String? = null
)

data class TransactionItem(
    val id: String,
    val type: String, // "TOPUP", "TRANSFER_IN", "TRANSFER_OUT", "PAYMENT"
    val amount: Double,
    val status: String, // "PENDING", "SUCCESS", "FAILED"
    val date: String,
    val description: String,
    val referenceId: String
)

data class PaymentInvoice(
    val id: String,
    val amount: Double,
    val merchantName: String,
    val orderId: String,
    val status: String // "PENDING", "PAID"
)

data class VirtualAccountInfo(
    val id: String,
    val vaNumber: String,
    val bankName: String,
    val amount: Double,
    val status: String,
    val expiresAt: String,
    val merchantName: String,
    val orderNumber: String,
    val customerName: String
)

data class QrisPayloadInfo(
    val type: String = "",
    val code: String = "",
    val merchant: String = "",
    val amount: Double = 0.0,
    val orderNumber: String = ""
)

data class WalletLoginRequest(val email: String, val password: String)
data class WalletPinRequest(val pin: String)
data class WalletTopUpRequest(val amount: Double)
data class VirtualAccountPayRequest(val vaNumber: String, val pin: String)
data class QrisPayRequest(val qrisCode: String, val pin: String)

data class WalletUserDto(
    val id: String,
    val name: String?,
    val email: String,
    val phone: String?,
    val image: String? = null
)

data class WalletDto(
    val id: String,
    val walletNumber: String,
    val balance: Double
)

data class WalletTransactionDto(
    val id: String,
    val referenceId: String,
    val type: String,
    val amount: Double,
    val status: String,
    val description: String,
    val createdAt: String
)

data class WalletOrderDto(
    val id: String,
    val orderNumber: String,
    val total: Double,
    val paymentStatus: String,
    val createdAt: String
)

data class WalletPaymentRequestDto(
    val id: String,
    val orderId: String,
    val merchantName: String,
    val merchantOrderId: String,
    val amount: Double,
    val status: String,
    val order: WalletOrderDto? = null
)

data class WalletLoginResponse(
    val success: Boolean = false,
    val token: String?,
    val user: WalletUserDto?,
    val wallet: WalletDto?,
    val error: String? = null
)

data class WalletProfileResponse(
    val success: Boolean = false,
    val user: WalletUserDto?,
    val wallet: WalletDto?,
    val transactions: List<WalletTransactionDto> = emptyList(),
    val error: String? = null
)

data class WalletPaymentRequestsResponse(
    val success: Boolean = false,
    val data: List<WalletPaymentRequestDto> = emptyList(),
    val error: String? = null
)

data class WalletMutationResponse(
    val success: Boolean = false,
    val error: String? = null
)

data class VirtualAccountLookupResponse(
    val success: Boolean = false,
    val data: VirtualAccountInfo? = null,
    val error: String? = null
)

interface WalletApiService {
    @POST("api/wallet/auth/login")
    suspend fun login(@Body body: WalletLoginRequest): Response<WalletLoginResponse>

    @GET("api/wallet/profile")
    suspend fun profile(@Header("Authorization") authorization: String): WalletProfileResponse

    @GET("api/wallet/payment-requests")
    suspend fun paymentRequests(@Header("Authorization") authorization: String): WalletPaymentRequestsResponse

    @POST("api/wallet/payment-requests/{id}/pay")
    suspend fun payInvoice(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body body: WalletPinRequest
    ): WalletMutationResponse

    @POST("api/wallet/topup")
    suspend fun topUp(
        @Header("Authorization") authorization: String,
        @Body body: WalletTopUpRequest
    ): WalletMutationResponse

    @GET("api/va/lookup")
    suspend fun lookupVirtualAccount(
        @Header("Authorization") authorization: String,
        @Query("vaNumber") vaNumber: String
    ): Response<VirtualAccountLookupResponse>

    @POST("api/va/pay")
    suspend fun payVirtualAccount(
        @Header("Authorization") authorization: String,
        @Body body: VirtualAccountPayRequest
    ): Response<WalletMutationResponse>

    @POST("api/qris/pay")
    suspend fun payQris(
        @Header("Authorization") authorization: String,
        @Body body: QrisPayRequest
    ): Response<WalletMutationResponse>
}

object WalletApiClient {
    fun create(baseUrl: String): WalletApiService {
        val cleanBaseUrl = normalizeServerUrl(baseUrl)
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val gson = com.google.gson.GsonBuilder()
            .setLenient()
            .serializeNulls()
            .create()

        return Retrofit.Builder()
            .baseUrl(cleanBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WalletApiService::class.java)
    }

    fun normalizeServerUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val uri = URI(withScheme)
        require(uri.host != null) { "Alamat server tidak valid" }
        return withScheme + "/"
    }
}

// ==========================================
// SECURE TOKEN STORAGE (EncryptedSharedPreferences)
// ==========================================
class SecureStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "nadh_secure_storage",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPrefs.edit().putString("access_token", accessToken).putString("refresh_token", refreshToken).apply()
    }

    fun getAccessToken(): String? = sharedPrefs.getString("access_token", null)
    fun getRefreshToken(): String? = sharedPrefs.getString("refresh_token", null)
    
    fun clearSession() {
        sharedPrefs.edit().remove("access_token").remove("refresh_token").apply()
    }
}

// ==========================================
// MVVM STATE HOLDERS (ViewModel)
// ==========================================
class WalletViewModel : ViewModel() {
    private var apiService: WalletApiService? = null
    private var accessToken: String? = null

    private val _userState = MutableStateFlow<UserProfile?>(null)
    val userState: StateFlow<UserProfile?> = _userState.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true) // Start with premium dark mode/cyber style
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    private val _coins = MutableStateFlow(125300)
    val coins: StateFlow<Int> = _coins.asStateFlow()

    fun addCoins(amount: Int) {
        _coins.value = _coins.value + amount
    }

    fun updateProfile(fullName: String, email: String, phoneNumber: String, profileImageUri: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val cleanedName = fullName.trim()
        val cleanedEmail = email.trim()
        val cleanedPhone = phoneNumber.trim()

        if (cleanedName.length < 3) {
            onError("Nama lengkap minimal 3 karakter.")
            return
        }
        if (!cleanedEmail.contains("@") || !cleanedEmail.contains(".")) {
            onError("Alamat email belum valid.")
            return
        }
        if (cleanedPhone.length < 10 || cleanedPhone.any { !it.isDigit() }) {
            onError("Nomor HP harus berisi minimal 10 digit angka.")
            return
        }

        val current = _userState.value ?: return
        _userState.value = current.copy(
            fullName = cleanedName,
            email = cleanedEmail,
            phoneNumber = cleanedPhone,
            profileImageUri = profileImageUri
        )
        onSuccess()
    }

    private val _transactions = MutableStateFlow<List<TransactionItem>>(emptyList())
    val transactions: StateFlow<List<TransactionItem>> = _transactions.asStateFlow()

    private val _invoices = MutableStateFlow<List<PaymentInvoice>>(emptyList())
    val invoices: StateFlow<List<PaymentInvoice>> = _invoices.asStateFlow()

    private val _currentPin = MutableStateFlow("123456") // Default Secure PIN
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        _userState.value = UserProfile(
            id = "8ca5-d72b",
            fullName = "Rifqi Nadhir Altaz",
            email = "rifqinadhiraltaz25@gmail.com",
            phoneNumber = "081234567890",
            isVerified = true,
            walletId = "889981234567890",
            balance = 3250000.0
        )

        _transactions.value = listOf(
            TransactionItem("t1", "TOPUP", 500000.0, "SUCCESS", "2026-05-19 14:20", "Topup via Bank Mandiri VA", "TOP-293812938"),
            TransactionItem("t2", "TRANSFER_OUT", 150000.0, "SUCCESS", "2026-05-19 18:45", "Transfer ke Ahmad Fauzi", "TRF-928374823"),
            TransactionItem("t3", "PAYMENT", 89000.0, "SUCCESS", "2026-05-20 09:12", "Pembayaran Order Website #INV-92831", "PAY-9283120")
        )

        // Invoices dikosongkan - akan diisi dari server setelah login
        _invoices.value = emptyList()
    }

    private fun authorizationHeader(): String? = accessToken?.let { "Bearer $it" }

    private fun apiError(response: Response<*>): String {
        val body = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        return Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
            ?: "Koneksi server gagal. Kode server: ${response.code()}"
    }

    private fun applyProfile(user: WalletUserDto, wallet: WalletDto) {
        _userState.value = UserProfile(
            id = user.id,
            fullName = user.name ?: "Pengguna NADH Wallet",
            email = user.email,
            phoneNumber = user.phone ?: "-",
            isVerified = true,
            walletId = wallet.walletNumber,
            balance = wallet.balance,
            profileImageUri = user.image
        )
    }

    private fun applyTransactions(items: List<WalletTransactionDto>) {
        _transactions.value = items.map {
            TransactionItem(
                id = it.id,
                type = it.type,
                amount = it.amount,
                status = it.status,
                date = it.createdAt.take(16).replace("T", " "),
                description = it.description,
                referenceId = it.referenceId
            )
        }
    }

    private fun applyInvoices(items: List<WalletPaymentRequestDto>) {
        _invoices.value = items.map {
            PaymentInvoice(
                id = it.id,
                amount = it.amount,
                merchantName = it.merchantName,
                orderId = it.merchantOrderId,
                status = it.status
            )
        }
    }

    fun loginToWebsite(baseUrl: String, email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val nextApiService = WalletApiClient.create(baseUrl)
                val httpResponse = nextApiService.login(WalletLoginRequest(email.trim(), password))
                val response = httpResponse.body()

                if (!httpResponse.isSuccessful || response == null) {
                    onError(
                        when (httpResponse.code()) {
                            401 -> "Email atau password website salah."
                            404 -> "Endpoint wallet tidak ditemukan. Pastikan server website terbaru sudah jalan."
                            else -> "Login wallet gagal. Kode server: ${httpResponse.code()}"
                        }
                    )
                    return@launch
                }

                val token = response.token
                val user = response.user
                val wallet = response.wallet

                if (!response.success || token == null || user == null || wallet == null) {
                    onError(response.error ?: "Login wallet gagal")
                    return@launch
                }

                apiService = nextApiService
                accessToken = token
                applyProfile(user, wallet)
                refreshFromWebsite()
                onSuccess()
            } catch (error: Throwable) {
                onError(error.message ?: "Tidak bisa terhubung ke website. Cek IP server, WiFi, dan firewall.")
            }
        }
    }

    fun refreshFromWebsite(onError: ((String) -> Unit)? = null) {
        val api = apiService
        val auth = authorizationHeader()
        if (api == null || auth == null) return

        viewModelScope.launch {
            try {
                val profile = api.profile(auth)
                if (profile.success && profile.user != null && profile.wallet != null) {
                    applyProfile(profile.user, profile.wallet)
                    applyTransactions(profile.transactions)
                }
            } catch (error: Throwable) {
                android.util.Log.e("NADHWallet", "Profile fetch error: ${error.message}", error)
                onError?.invoke(error.message ?: "Gagal sinkronisasi profile.")
            }

            try {
                val paymentRequests = api.paymentRequests(auth)
                android.util.Log.d("NADHWallet", "Payment requests response: success=${paymentRequests.success}, count=${paymentRequests.data.size}")
                if (paymentRequests.success) {
                    applyInvoices(paymentRequests.data)
                }
            } catch (error: Throwable) {
                android.util.Log.e("NADHWallet", "Payment requests fetch error: ${error.message}", error)
                onError?.invoke(error.message ?: "Gagal mengambil tagihan dari website.")
            }
        }
    }

    // Live atomic transfer logic
    fun executeTransfer(amount: Double, targetWallet: String, pin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (pin != _currentPin.value) {
            onError("PIN eWallet yang anda masukkan salah!")
            return
        }
        val userCurrent = _userState.value ?: return
        if (amount > userCurrent.balance) {
            onError("Saldo dompet digital Anda tidak mencukupi!")
            return
        }

        // Subtract balance & write ledger (Atomic Simulation)
        _userState.value = userCurrent.copy(balance = userCurrent.balance - amount)
        _coins.value = _coins.value + 1500 // Cash back award
        val newTx = TransactionItem(
            id = UUID.randomUUID().toString().take(8),
            type = "TRANSFER_OUT",
            amount = amount,
            status = "SUCCESS",
            date = "Hari ini, " + java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            description = "Transfer ke $targetWallet (Terverifikasi)",
            referenceId = "TRF-" + (100000..999999).random()
        )
        _transactions.value = listOf(newTx) + _transactions.value
        onSuccess()
    }

    // Web Invoice payment logic
    fun payInvoice(invoice: PaymentInvoice, pin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val api = apiService
        val auth = authorizationHeader()
        if (api != null && auth != null) {
            viewModelScope.launch {
                try {
                    val response = api.payInvoice(auth, invoice.id, WalletPinRequest(pin))
                    if (!response.success) {
                        onError(response.error ?: "Pembayaran wallet gagal")
                        return@launch
                    }

                    refreshFromWebsite()
                    onSuccess()
                } catch (error: Throwable) {
                    onError(error.message ?: "Pembayaran gagal dikirim ke website. Cek koneksi WiFi.")
                }
            }
            return
        }

        if (pin != _currentPin.value) {
            onError("PIN eWallet salah!")
            return
        }
        val userCurrent = _userState.value ?: return
        if (invoice.amount > userCurrent.balance) {
            onError("Saldo Anda tidak mencukupi untuk bayar order!")
            return
        }

        _userState.value = userCurrent.copy(balance = userCurrent.balance - invoice.amount)
        _coins.value = _coins.value + 2500 // Premium merchant cash back points
        _invoices.value = _invoices.value.filter { it.id != invoice.id }
        
        val newTx = TransactionItem(
            id = UUID.randomUUID().toString().take(8),
            type = "PAYMENT",
            amount = invoice.amount,
            status = "SUCCESS",
            date = "Hari ini, " + java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            description = "Pembayaran Order #${invoice.orderId} (${invoice.merchantName})",
            referenceId = "PAY-" + (1000000..9999999).random()
        )
        _transactions.value = listOf(newTx) + _transactions.value
        onSuccess()
    }

    fun executeTopUp(amount: Double, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        val api = apiService
        val auth = authorizationHeader()
        if (api != null && auth != null) {
            viewModelScope.launch {
                try {
                    val response = api.topUp(auth, WalletTopUpRequest(amount))
                    if (!response.success) return@launch
                    refreshFromWebsite()
                    onSuccess()
                } catch (error: Throwable) {
                    onError(error.message ?: "Top up gagal dikirim ke website. Cek koneksi WiFi.")
                }
            }
            return
        }

        val userCurrent = _userState.value ?: return
        _userState.value = userCurrent.copy(balance = userCurrent.balance + amount)
        _coins.value = _coins.value + 500 // Small reward for top-up
        val newTx = TransactionItem(
            id = UUID.randomUUID().toString().take(8),
            type = "TOPUP",
            amount = amount,
            status = "SUCCESS",
            date = "Hari ini, " + java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            description = "Top up dari Virtual Account",
            referenceId = "TOP-" + (100000..999999).random()
        )
        _transactions.value = listOf(newTx) + _transactions.value
        onSuccess()
    }

    fun lookupVirtualAccount(vaNumber: String, onSuccess: (VirtualAccountInfo) -> Unit, onError: (String) -> Unit) {
        val api = apiService
        val auth = authorizationHeader()
        val cleanedVa = vaNumber.filter { it.isDigit() }
        if (cleanedVa.length != 16) {
            onError("Nomor VA harus 16 digit.")
            return
        }
        if (api == null || auth == null) {
            onError("Hubungkan APK ke website dulu sebelum membayar VA.")
            return
        }

        viewModelScope.launch {
            try {
                val response = api.lookupVirtualAccount(auth, cleanedVa)
                val body = response.body()
                val vaInfo = body?.data
                if (!response.isSuccessful || body == null || !body.success || vaInfo == null) {
                    onError(body?.error ?: apiError(response))
                    return@launch
                }
                onSuccess(vaInfo)
            } catch (error: Throwable) {
                onError(error.message ?: "Gagal mencari nomor VA. Cek koneksi website.")
            }
        }
    }

    fun payVirtualAccount(vaNumber: String, pin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val api = apiService
        val auth = authorizationHeader()
        val cleanedVa = vaNumber.filter { it.isDigit() }
        if (cleanedVa.length != 16) {
            onError("Nomor VA harus 16 digit.")
            return
        }
        if (pin.length != 6 || pin.any { !it.isDigit() }) {
            onError("PIN eWallet harus 6 digit.")
            return
        }
        if (api == null || auth == null) {
            onError("Hubungkan APK ke website dulu sebelum membayar VA.")
            return
        }

        viewModelScope.launch {
            try {
                val response = api.payVirtualAccount(auth, VirtualAccountPayRequest(cleanedVa, pin))
                val body = response.body()
                if (!response.isSuccessful || body == null || !body.success) {
                    onError(body?.error ?: apiError(response))
                    return@launch
                }
                refreshFromWebsite()
                onSuccess()
            } catch (error: Throwable) {
                onError(error.message ?: "Pembayaran VA gagal dikirim ke website.")
            }
        }
    }

    fun payQris(qrisPayload: QrisPayloadInfo, pin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val api = apiService
        val auth = authorizationHeader()
        if (qrisPayload.type != "NADH_QRIS" || qrisPayload.code.isBlank()) {
            onError("QRIS tidak valid untuk NADH Wallet.")
            return
        }
        if (pin.length != 6 || pin.any { !it.isDigit() }) {
            onError("PIN eWallet harus 6 digit.")
            return
        }
        if (api == null || auth == null) {
            onError("Hubungkan APK ke website dulu sebelum scan QRIS.")
            return
        }

        viewModelScope.launch {
            try {
                val response = api.payQris(auth, QrisPayRequest(qrisPayload.code, pin))
                val body = response.body()
                if (!response.isSuccessful || body == null || !body.success) {
                    onError(body?.error ?: apiError(response))
                    return@launch
                }
                refreshFromWebsite()
                onSuccess()
            } catch (error: Throwable) {
                onError(error.message ?: "Pembayaran QRIS gagal dikirim ke website.")
            }
        }
    }

    fun executePurchase(amount: Double, description: String, pin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (pin != _currentPin.value) {
            onError("PIN eWallet salah!")
            return
        }
        val userCurrent = _userState.value ?: return
        if (amount > userCurrent.balance) {
            onError("Saldo dompet digital Anda tidak cukup!")
            return
        }

        _userState.value = userCurrent.copy(balance = userCurrent.balance - amount)
        val earned = (amount * 0.05).toInt() // 5% cashback coins
        _coins.value = _coins.value + earned
        
        val newTx = TransactionItem(
            id = UUID.randomUUID().toString().take(8),
            type = "PAYMENT",
            amount = amount,
            status = "SUCCESS",
            date = "Hari ini, " + java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            description = description,
            referenceId = "PAY-" + (100000..999999).random()
        )
        _transactions.value = listOf(newTx) + _transactions.value
        onSuccess()
    }

    fun updatePin(oldPin: String, newPin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (oldPin != _currentPin.value) {
            onError("PIN Lama salah!")
            return
        }
        if (newPin.length != 6) {
            onError("PIN baru harus berupa 6 digit")
            return
        }
        _currentPin.value = newPin
        onSuccess()
    }
}

// ==========================================
// CENTRAL WALLET THEME PALETTE FOR GEN-Z
// ==========================================
object WalletTheme {
    @Composable
    fun background(isDark: Boolean) = if (isDark) Color(0xFF070B19) else Color(0xFFF1F5F9)
    @Composable
    fun cardBg(isDark: Boolean) = if (isDark) Color(0xFF131B35) else Color(0xFFFFFFFF)
    @Composable
    fun textPrimary(isDark: Boolean) = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    @Composable
    fun textSecondary(isDark: Boolean) = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    @Composable
    fun border(isDark: Boolean) = if (isDark) Color(0xFF22314E) else Color(0xFFE2E8F0)
    @Composable
    fun primary() = Color(0xFF6366F1)
    @Composable
    fun primaryAccent() = Color(0xFF818CF8)
}

// ==========================================
// MAIN ACTIVITY
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NadhWalletTheme {
                val navController = rememberNavController()
                val viewModel: WalletViewModel = viewModel()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(navController, viewModel)
                }
            }
        }
    }
}

// ==========================================
// NAVIGATION GRAPH
// ==========================================
@Composable
fun AppNavigation(navController: NavHostController, viewModel: WalletViewModel) {
    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(viewModel = viewModel, onFinish = { navController.navigate("login") { popUpTo("splash") { inclusive = true } } })
        }
        composable("login") { 
            LoginScreen(viewModel = viewModel, onLoginSuccess = { navController.navigate("home") { popUpTo("login") { inclusive = true } } })
        }
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToTransfer = { navController.navigate("transfer") },
                onNavigateToTopUp = { navController.navigate("topup") },
                onNavigateToPayment = { navController.navigate("payment_list") },
                onNavigateToVirtualAccount = { navController.navigate("virtual_account") },
                onNavigateToPin = { navController.navigate("pin_settings") },
                onNavigateToEditProfile = { navController.navigate("edit_profile") },
                onLogout = { navController.navigate("login") { popUpTo("home") { inclusive = true } } }
            )
        }
        composable("transfer") {
            TransferScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("topup") {
            TopUpScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("payment_list") {
            PaymentInvoiceListScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("virtual_account") {
            VirtualAccountPaymentScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("pin_settings") {
            PinSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("edit_profile") {
            EditProfileScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}

// ==========================================
// SCREEN 1: SPLASH SCREEN (Glowing Futuristic Logo)
// ==========================================
@Composable
fun SplashScreen(viewModel: WalletViewModel, onFinish: () -> Unit) {
    val isDark by viewModel.isDarkMode.collectAsState()
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        onFinish()
    }
    
    val bgGradients = if (isDark) {
        listOf(Color(0xFF070B19), Color(0xFF131B35), Color(0xFF1E1B4B))
    } else {
        listOf(Color(0xFFEEF2F6), Color(0xFFE0E7FF), Color(0xFFC7D2FE))
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = bgGradients)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Radiant Logo Ring Glow
            Surface(
                modifier = Modifier
                    .size(110.dp),
                shape = RoundedCornerShape(32.dp),
                color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp, 
                    if (isDark) Color(0xFF818CF8).copy(alpha = 0.3f) else Color(0xFF6366F1).copy(alpha = 0.2f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Wallet,
                        contentDescription = "Wallet Logo",
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(54.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "NADH Wallet",
                color = if (isDark) Color.White else Color(0xFF0F172A),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "eWallet aman, cepat, dan mudah digunakan",
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ==========================================
// SCREEN 2: LOGIN SCREEN (Elegant & Polished Portal)
// ==========================================
@Composable
fun LoginScreen(viewModel: WalletViewModel, onLoginSuccess: () -> Unit) {
    val isDark by viewModel.isDarkMode.collectAsState()
    var email by remember { mutableStateFlowOf("rifqinadhiraltaz25@gmail.com") }
    var password by remember { mutableStateFlowOf("password123") }
    var serverUrl by remember { mutableStateFlowOf("http://192.168.1.14:4000") }
    var isLoading by remember { mutableStateFlowOf(false) }
    var connectionStatus by remember { mutableStateFlowOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletTheme.background(isDark))
            .padding(28.dp)
    ) {
        // High-fidelity Floating Theme Toggle Switch in corner
        IconButton(
            onClick = { viewModel.toggleTheme() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp)
                .background(
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isDark) Icons.Default.WbSunny else Icons.Default.NightsStay,
                contentDescription = "Toggle Theme",
                tint = if (isDark) Color(0xFFFBBF24) else Color(0xFF4F46E5)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Branding Icon
            Surface(
                modifier = Modifier
                    .size(68.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF6366F1).copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Security",
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Selamat Datang Kembali",
                color = WalletTheme.textPrimary(isDark),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                "Akses dompet digital andalan anak muda Indonesia",
                color = WalletTheme.textSecondary(isDark),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )

            // Cyber Styled Input container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                    .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(26.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email akun website", color = WalletTheme.textSecondary(isDark)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WalletTheme.primary(),
                        unfocusedBorderColor = WalletTheme.border(isDark),
                        focusedTextColor = WalletTheme.textPrimary(isDark),
                        unfocusedTextColor = WalletTheme.textPrimary(isDark),
                        focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                    ),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email", tint = WalletTheme.primary()) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password Akun", color = WalletTheme.textSecondary(isDark)) },
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WalletTheme.primary(),
                        unfocusedBorderColor = WalletTheme.border(isDark),
                        focusedTextColor = WalletTheme.textPrimary(isDark),
                        unfocusedTextColor = WalletTheme.textPrimary(isDark),
                        focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                    ),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password", tint = WalletTheme.primary()) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        connectionStatus = ""
                    },
                    label = { Text("Alamat website di WiFi", color = WalletTheme.textSecondary(isDark)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WalletTheme.primary(),
                        unfocusedBorderColor = WalletTheme.border(isDark),
                        focusedTextColor = WalletTheme.textPrimary(isDark),
                        unfocusedTextColor = WalletTheme.textPrimary(isDark),
                        focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                    ),
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = "Server", tint = WalletTheme.primary()) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )



                if (connectionStatus.isNotBlank()) {
                    Text(
                        text = connectionStatus,
                        color = if (connectionStatus.startsWith("Terhubung")) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank() || serverUrl.isBlank()) {
                        Toast.makeText(context, "Email, password, dan alamat website wajib diisi.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    connectionStatus = "Menghubungkan ke server website..."
                    viewModel.loginToWebsite(
                        baseUrl = serverUrl,
                        email = email,
                        password = password,
                        onSuccess = {
                            isLoading = false
                            connectionStatus = "Terhubung ke NADH Store."
                            Toast.makeText(context, "Terhubung ke NADH Store.", Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        },
                        onError = { err ->
                            isLoading = false
                            connectionStatus = err
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary()),
                enabled = !isLoading
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isLoading) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White)
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (isLoading) "Menghubungkan..." else "Hubungkan ke Website",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 3: HOME DASHBOARD (Elegant Metal Finishes / Glassmorphism)
// ==========================================
@Composable
fun HomeScreen(
    viewModel: WalletViewModel,
    onNavigateToTransfer: () -> Unit,
    onNavigateToTopUp: () -> Unit,
    onNavigateToPayment: () -> Unit,
    onNavigateToVirtualAccount: () -> Unit,
    onNavigateToPin: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val userProfile by viewModel.userState.collectAsState()
    val txs by viewModel.transactions.collectAsState()
    val coins by viewModel.coins.collectAsState()
    
    val context = LocalContext.current
    val gson = remember { Gson() }

    // Dialog state variables
    var showScanQrisDialog by remember { mutableStateFlowOf(false) }
    var scannedQris by remember { mutableStateFlowOf<QrisPayloadInfo?>(null) }
    var qrisPinInput by remember { mutableStateFlowOf("") }
    var qrisPayLoading by remember { mutableStateFlowOf(false) }
    var showManualQrisDialog by remember { mutableStateFlowOf(false) }
    var manualQrisCodeInput by remember { mutableStateFlowOf("") }
    var manualQrisPinInput by remember { mutableStateFlowOf("") }
    var manualQrisPayLoading by remember { mutableStateFlowOf(false) }
    var showQrisSourceDialog by remember { mutableStateFlowOf(false) }
    fun handleQrisPayload(rawPayload: String): Boolean {
        val parsed = runCatching { gson.fromJson(rawPayload, QrisPayloadInfo::class.java) }.getOrNull()
        if (parsed == null || parsed.type != "NADH_QRIS" || parsed.code.isBlank()) {
            Toast.makeText(context, "QR code bukan QRIS NADH Store yang valid.", Toast.LENGTH_LONG).show()
            return false
        }

        scannedQris = parsed
        qrisPinInput = ""
        showScanQrisDialog = true
        return true
    }
    val qrisScannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val rawPayload = result.contents
        if (rawPayload.isNullOrBlank()) {
            Toast.makeText(context, "Scan QRIS dibatalkan.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        handleQrisPayload(rawPayload)
    }
    val qrisGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            Toast.makeText(context, "Pemilihan gambar QRIS dibatalkan.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        val qrText = runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: error("Gambar tidak bisa dibaca")
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        }.getOrNull()

        if (qrText.isNullOrBlank()) {
            Toast.makeText(context, "QRIS tidak terbaca dari gambar. Coba download ulang gambar QRIS.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        handleQrisPayload(qrText)
    }
    fun launchQrisScanner() {
        qrisScannerLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan QRIS NADH Store")
                .setBeepEnabled(true)
                .setOrientationLocked(false)
                .setCaptureActivity(QrisCaptureActivity::class.java)
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchQrisScanner()
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk scan QRIS.", Toast.LENGTH_LONG).show()
        }
    }

    var showPulsaDialog by remember { mutableStateFlowOf(false) }
    var pulsaPhoneInput by remember { mutableStateFlowOf("081234567890") }
    var pulsaAmountInput by remember { mutableStateFlowOf("25000") }
    var pulsaPinInput by remember { mutableStateFlowOf("") }

    var showPlnDialog by remember { mutableStateFlowOf(false) }
    var plnMeterInput by remember { mutableStateFlowOf("320199283748") }
    var plnAmountInput by remember { mutableStateFlowOf("50000") }
    var plnPinInput by remember { mutableStateFlowOf("") }

    var showGameDialog by remember { mutableStateFlowOf(false) }
    var gameIdInput by remember { mutableStateFlowOf("928312019 (2041)") }
    var gameSelectedName by remember { mutableStateFlowOf("Mobile Legends (MLBB)") }
    var gamePackageSelected by remember { mutableStateFlowOf("86 Diamonds - Rp 22.000") }
    var gameCostAmount by remember { mutableStateFlowOf(22000.0) }
    var gamePinInput by remember { mutableStateFlowOf("") }

    var showCharityDialog by remember { mutableStateFlowOf(false) }
    var charityCauseSelected by remember { mutableStateFlowOf("NADH Peduli Anak Yatim") }
    var charityAmountInput by remember { mutableStateFlowOf("10000") }
    var charityPinInput by remember { mutableStateFlowOf("") }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletTheme.background(isDark))
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.clickable(onClick = onNavigateToEditProfile)
                        ) {
                            ProfileAvatar(
                                fullName = userProfile?.fullName,
                                profileImageUri = userProfile?.profileImageUri,
                                size = 46.dp,
                                fontSize = 15.sp,
                                backgroundColor = Color(0xFF6366F1).copy(alpha = 0.15f),
                                borderColor = Color(0xFF818CF8),
                                textColor = WalletTheme.primaryAccent()
                            )
                            // Active status pulse indicator
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                                    .align(Alignment.BottomEnd)
                                    .border(1.5.dp, WalletTheme.background(isDark), CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    userProfile?.fullName ?: "Rifqi Nadhir Altaz",
                                    color = WalletTheme.textPrimary(isDark),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            }
                            Text(
                                "ID Wallet: ${userProfile?.walletId}",
                                color = WalletTheme.textSecondary(isDark),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onNavigateToEditProfile,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isDark) Color(0xFF6366F1).copy(alpha = 0.18f) else Color(0xFF6366F1).copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit profile",
                                tint = WalletTheme.primaryAccent(),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                            )
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                contentDescription = "Switch theme",
                                tint = if (isDark) Color(0xFFFBBF24) else Color(0xFF4F46E5),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = onLogout,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isDark) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "Log out", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        containerColor = WalletTheme.background(isDark)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // Indonesia Premium Balance Card & Points Tag (GoPay & OVO hybrid visual style)
            item {
                val cardGradients = if (isDark) {
                    listOf(Color(0xFF2E006A), Color(0xFF4C0099), Color(0xFF0F0822)) // Royal OVO Purple / Cyber neon
                } else {
                    listOf(Color(0xFF4F46E5), Color(0xFF6366F1), Color(0xFF00C6FF)) // Vibrant Indigo/Cyan GoPay style
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToEditProfile),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(colors = cardGradients))
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SALDO NADH WALLET", 
                                        color = Color.White.copy(alpha = 0.9f), 
                                        fontSize = 11.sp, 
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                
                                // OVO Premier or GoPay Plus Verified Tag
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("PREMIER STATUS", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Cash Balance
                            Text(
                                formatToIDR(userProfile?.balance ?: 0.0),
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Cash secondary tracker: NADH COINS (Equivalent to GoPay Coins / OVO Points)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                                    .padding(vertical = 10.dp, horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircle, 
                                        contentDescription = "Coins", 
                                        tint = Color(0xFFFBBF24), // Golden 
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("NADH Coins", color = Color(0xFFE2E8F0), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${NumberFormat.getNumberInstance(Locale.US).format(coins)} Poin", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text("1 Poin = Rp 1", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // Quick Actions Bar (Modern Pill Layout)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WalletTheme.cardBg(isDark), RoundedCornerShape(24.dp))
                        .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(24.dp))
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickActionItem(isDark = isDark, icon = Icons.Default.Send, title = "Kirim", color = Color(0xFF10B981), onClick = onNavigateToTransfer)
                    QuickActionItem(isDark = isDark, icon = Icons.Default.AddCircle, title = "Top Up", color = Color(0xFF6366F1), onClick = onNavigateToTopUp)
                    QuickActionItem(isDark = isDark, icon = Icons.Default.ReceiptLong, title = "Tagihan", color = Color(0xFFFBBF24), onClick = onNavigateToPayment)
                    QuickActionItem(isDark = isDark, icon = Icons.Default.Fingerprint, title = "Sandi", color = Color(0xFFEF4444), onClick = onNavigateToPin)
                }
            }

            // LAYANAN PILIHAN (BENTO GRID - Indonesian Favorit Utilities like GoPay, OVO, ShopeePay)
            item {
                Column {
                    Text(
                        text = "Layanan Pilihan", 
                        color = WalletTheme.textPrimary(isDark), 
                        fontSize = 15.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WalletTheme.cardBg(isDark), RoundedCornerShape(24.dp))
                            .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(24.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Service 1: Pulsa & Data
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                QuickActionItem(isDark = isDark, icon = Icons.Default.Smartphone, title = "Beli Pulsa", color = Color(0xFF10B981)) {
                                    showPulsaDialog = true
                                }
                            }
                            // Service 2: Token Listrik
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                QuickActionItem(isDark = isDark, icon = Icons.Default.FlashOn, title = "Listrik PLN", color = Color(0xFFFBBF24)) {
                                    showPlnDialog = true
                                }
                            }
                            // Service 3: Voucher Game
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                QuickActionItem(isDark = isDark, icon = Icons.Default.PlayArrow, title = "Game Shop", color = Color(0xFFEC4899)) {
                                    showGameDialog = true
                                }
                            }
                            // Service 4: Scan QRIS Merchant
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                QuickActionItem(isDark = isDark, icon = Icons.Default.CenterFocusStrong, title = "Scan QRIS", color = Color(0xFF818CF8)) {
                                    showQrisSourceDialog = true
                                }
                            }
                        }

                        // Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Service 5: Ojek Online Ride
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                QuickActionItem(isDark = isDark, icon = Icons.Default.DirectionsCar, title = "NADH-Ride", color = Color(0xFF0EA5E9)) {
                                    Toast.makeText(context, "Mencari Driver Ojek NADH-Ride terdekat...", Toast.LENGTH_LONG).show()
                                }
                            }
                            // Service 6: Sedekah Charity
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                QuickActionItem(isDark = isDark, icon = Icons.Default.Favorite, title = "Donasi", color = Color(0xFFEC4899)) {
                                    showCharityDialog = true
                                }
                            }
                            // Service 7: Manual QRIS code
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                QuickActionItem(isDark = isDark, icon = Icons.Default.QrCode, title = "Kode QRIS", color = Color(0xFFF97316)) {
                                    manualQrisCodeInput = ""
                                    manualQrisPinInput = ""
                                    showManualQrisDialog = true
                                }
                            }
                            // Service 8: Buka Lainnya
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                QuickActionItem(isDark = isDark, icon = Icons.Default.AccountBalance, title = "Bayar VA", color = Color(0xFF14B8A6), onClick = onNavigateToVirtualAccount)
                            }
                        }
                    }
                }
            }

            // PROMO CAROUSEL (Horizontal sliding posters like GoPay / ShopeePay)
            item {
                Column {
                    Text(
                        text = "Promo Spesial", 
                        color = WalletTheme.textPrimary(isDark), 
                        fontSize = 15.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .width(260.dp)
                                    .clickable {
                                        viewModel.addCoins(5000)
                                        Toast.makeText(context, "Klaim Voucher Berhasil! Cashback +5.000 NADH Coins dimasukkan!", Toast.LENGTH_LONG).show()
                                    },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF97316)), // Shopee Orange style
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("CASHBACK 50%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Kopi Kenangan Senayan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Jajan boba kopi susu bayar pakai QRIS NADH hemat setengah harga!", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 16.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("TAP UNTUK KLAIM VOUCHER >", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier
                                    .width(260.dp)
                                    .clickable {
                                        viewModel.addCoins(10000)
                                        Toast.makeText(context, "Klaim Cashback MLBB Sukses! +10.000 Coins ditambahkan!", Toast.LENGTH_LONG).show()
                                    },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEC4899)), // Game Pink Style
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("BONUS MAKSIMAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Top Up Game Hemat 80%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Diamonds MLBB & Welkin Generator instan. Lebih murah dari Codashop!", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 16.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("TAP UNTUK AMBIL BONUS COINS >", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier
                                    .width(260.dp)
                                    .clickable {
                                        Toast.makeText(context, "Bebas Biaya Admin Aktif! Kuota tersisa: 10 Kali Kirim.", Toast.LENGTH_LONG).show()
                                    },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1)), // Indigo Classic style
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("BIAYA ADMIN RP 0", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Bebas Kirim ke Semua Bank", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Transfer saldo kemana saja tanpa potongan sepeserpun. Instant realtime!", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 16.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("LIHAT KUOTA GRATIS TRANSFER >", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }

            // Target Limit / Budget spending Tracker Card (Bento style)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = WalletTheme.cardBg(isDark)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WalletTheme.border(isDark))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Limit Bulanan", color = WalletTheme.textPrimary(isDark), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "Keuangan Aman", 
                                    color = Color(0xFF10B981), 
                                    fontSize = 10.sp, 
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Belanja: Rp 1.250.000", color = WalletTheme.textSecondary(isDark), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Limit Total: Rp 4.500.000", color = WalletTheme.textSecondary(isDark), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        LinearProgressIndicator(
                            progress = 1250000f / 4500000f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = Color(0xFF6366F1),
                            trackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            // Riwayat Title Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Riwayat Aktivitas", color = WalletTheme.textPrimary(isDark), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Auto Synced", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Transaction rows with beautiful status chips
            items(txs) { tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WalletTheme.cardBg(isDark), RoundedCornerShape(20.dp))
                        .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (tx.type == "TOPUP") Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (tx.type == "TOPUP") Icons.Default.Add else Icons.Default.CallMade,
                                    contentDescription = null,
                                    tint = if (tx.type == "TOPUP") Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tx.description,
                                color = WalletTheme.textPrimary(isDark),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                tx.date,
                                color = WalletTheme.textSecondary(isDark),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = (if (tx.type == "TOPUP") "+" else "-") + " " + formatToIDR(tx.amount),
                            color = if (tx.type == "TOPUP") Color(0xFF10B981) else Color(0xFFEF4444),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // Real-time Ledger tag
                        Text(
                            text = tx.referenceId,
                            color = WalletTheme.textSecondary(isDark).copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL DIALOGS (Highly Interactive payment emulation powered by executePurchase)
    // =========================================================================

    // 1. SCAN QRIS DIALOG
    if (showQrisSourceDialog) {
        AlertDialog(
            onDismissRequest = { showQrisSourceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Pilih Sumber QRIS", fontWeight = FontWeight.Bold, color = WalletTheme.textPrimary(isDark), fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Scan langsung dengan kamera atau ambil gambar QRIS yang sudah didownload dari website.",
                        color = WalletTheme.textSecondary(isDark),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Button(
                        onClick = {
                            showQrisSourceDialog = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                launchQrisScanner()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan dengan Kamera", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            showQrisSourceDialog = false
                            qrisGalleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ambil dari Galeri", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQrisSourceDialog = false }) {
                    Text("Batal", color = WalletTheme.textSecondary(isDark))
                }
            },
            containerColor = WalletTheme.cardBg(isDark),
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showScanQrisDialog) {
        val qris = scannedQris
        AlertDialog(
            onDismissRequest = {
                if (!qrisPayLoading) {
                    showScanQrisDialog = false
                    scannedQris = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Konfirmasi QRIS", fontWeight = FontWeight.Bold, color = WalletTheme.textPrimary(isDark), fontSize = 18.sp)
                }
            },
            text = {
                if (qris == null) {
                    Text("Data QRIS belum tersedia.", color = WalletTheme.textSecondary(isDark), fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) Color(0xFF0F172A).copy(alpha = 0.65f) else Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                                .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(qris.merchant.ifBlank { "NADH Store" }, color = WalletTheme.textPrimary(isDark), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Order #${qris.orderNumber}", color = WalletTheme.textSecondary(isDark), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text(formatToIDR(qris.amount), color = Color(0xFF10B981), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                            Text(qris.code, color = WalletTheme.textSecondary(isDark), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedTextField(
                            value = qrisPinInput,
                            onValueChange = { qrisPinInput = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("PIN eWallet 6 Digit", color = WalletTheme.textSecondary(isDark)) },
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WalletTheme.primary(),
                                unfocusedBorderColor = WalletTheme.border(isDark),
                                focusedTextColor = WalletTheme.textPrimary(isDark),
                                unfocusedTextColor = WalletTheme.textPrimary(isDark),
                                focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                                unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                                disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                            ),
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentQris = scannedQris ?: return@Button
                        qrisPayLoading = true
                        viewModel.payQris(
                            qrisPayload = currentQris,
                            pin = qrisPinInput,
                            onSuccess = {
                                qrisPayLoading = false
                                Toast.makeText(context, "QRIS berhasil dibayar. Order website sudah terkonfirmasi.", Toast.LENGTH_LONG).show()
                                showScanQrisDialog = false
                                scannedQris = null
                                qrisPinInput = ""
                            },
                            onError = { err ->
                                qrisPayLoading = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !qrisPayLoading && scannedQris != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (qrisPayLoading) "Memproses..." else "Bayar QRIS", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showScanQrisDialog = false
                        scannedQris = null
                    },
                    enabled = !qrisPayLoading
                ) {
                    Text("Batal", color = WalletTheme.textSecondary(isDark))
                }
            },
            containerColor = WalletTheme.cardBg(isDark),
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showManualQrisDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!manualQrisPayLoading) showManualQrisDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Input Kode QRIS", fontWeight = FontWeight.Bold, color = WalletTheme.textPrimary(isDark), fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "Gunakan ini kalau kamera HP sulit membaca QR. Salin kode QRIS yang tampil di website.",
                        color = WalletTheme.textSecondary(isDark),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = manualQrisCodeInput,
                        onValueChange = { manualQrisCodeInput = it.trim().take(80) },
                        label = { Text("Kode QRIS Website", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                            unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                            disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null, tint = WalletTheme.primary()) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = manualQrisPinInput,
                        onValueChange = { manualQrisPinInput = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("PIN eWallet 6 Digit", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                            unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                            disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qrisCode = manualQrisCodeInput.trim()
                        if (!qrisCode.startsWith("NADHQRIS")) {
                            Toast.makeText(context, "Kode QRIS NADH tidak valid.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        manualQrisPayLoading = true
                        viewModel.payQris(
                            qrisPayload = QrisPayloadInfo(
                                type = "NADH_QRIS",
                                code = qrisCode,
                                merchant = "NADH Store",
                                amount = 0.0,
                                orderNumber = "Manual"
                            ),
                            pin = manualQrisPinInput,
                            onSuccess = {
                                manualQrisPayLoading = false
                                Toast.makeText(context, "QRIS berhasil dibayar. Order website sudah terkonfirmasi.", Toast.LENGTH_LONG).show()
                                showManualQrisDialog = false
                                manualQrisCodeInput = ""
                                manualQrisPinInput = ""
                            },
                            onError = { err ->
                                manualQrisPayLoading = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !manualQrisPayLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (manualQrisPayLoading) "Memproses..." else "Bayar QRIS", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualQrisDialog = false }, enabled = !manualQrisPayLoading) {
                    Text("Batal", color = WalletTheme.textSecondary(isDark))
                }
            },
            containerColor = WalletTheme.cardBg(isDark),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 2. DIALOG PULSA & DATA
    if (showPulsaDialog) {
        AlertDialog(
            onDismissRequest = { showPulsaDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Beli Pulsa dan Paket Data", fontWeight = FontWeight.Bold, color = WalletTheme.textPrimary(isDark), fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Isi pulsa prabayar atau paket internet Anda instan langsung masuk dalam semenit.", color = WalletTheme.textSecondary(isDark), fontSize = 12.sp)

                    OutlinedTextField(
                        value = pulsaPhoneInput,
                        onValueChange = { pulsaPhoneInput = it },
                        label = { Text("Nomor Handphone (e.g. 0812...)", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.Smartphone, contentDescription = null, tint = WalletTheme.primary()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Nominal Buttons
                    Text("Pilih Nominal Pulsa:", color = WalletTheme.textPrimary(isDark), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val denoms = listOf("25000", "50000", "100000")
                        denoms.forEach { den ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { pulsaAmountInput = den },
                                shape = RoundedCornerShape(12.dp),
                                color = if (pulsaAmountInput == den) Color(0xFF10B981).copy(alpha = 0.2f) else WalletTheme.cardBg(isDark),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp, 
                                    if (pulsaAmountInput == den) Color(0xFF10B981) else WalletTheme.border(isDark)
                                )
                            ) {
                                Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                    Text("Rp " + NumberFormat.getNumberInstance(Locale.US).format(den.toDouble()), color = WalletTheme.textPrimary(isDark), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = pulsaPinInput,
                        onValueChange = { pulsaPinInput = it },
                        label = { Text("PIN Transaksi (6 Digit)", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountVal = pulsaAmountInput.toDoubleOrNull() ?: 0.0
                        if (amountVal <= 0.0) {
                            Toast.makeText(context, "Jumlah nominal pulsa tidak valid!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (pulsaPhoneInput.length < 10) {
                            Toast.makeText(context, "Masukkan nomor handphone yang valid!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (pulsaPinInput.length != 6) {
                            Toast.makeText(context, "PIN harus berupa 6 digit angka!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        viewModel.executePurchase(
                            amount = amountVal,
                            description = "Isi Pulsa ke $pulsaPhoneInput",
                            pin = pulsaPinInput,
                            onSuccess = {
                                Toast.makeText(context, "Transaksi pulsa sukses. Pulsa Rp " + NumberFormat.getNumberInstance(Locale.US).format(amountVal) + " dikirim ke nomor Anda.", Toast.LENGTH_LONG).show()
                                showPulsaDialog = false
                                pulsaPinInput = ""
                            },
                            onError = { err -> 
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Beli Pulsa", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPulsaDialog = false }) {
                    Text("Batal", color = WalletTheme.textSecondary(isDark))
                }
            },
            containerColor = WalletTheme.cardBg(isDark),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 3. DIALOG TOKEN PLN ELECTRICITY
    if (showPlnDialog) {
        AlertDialog(
            onDismissRequest = { showPlnDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Token Listrik PLN Prabayar", fontWeight = FontWeight.Bold, color = WalletTheme.textPrimary(isDark), fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Beli token listrik prabayar PLN secara elektronik. Kode token 20 digit dihasilkan seketika.", color = WalletTheme.textSecondary(isDark), fontSize = 12.sp)

                    OutlinedTextField(
                        value = plnMeterInput,
                        onValueChange = { plnMeterInput = it },
                        label = { Text("Nomor Meter / ID Pelanggan (11-12 Digit)", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = WalletTheme.primary()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Select Denom Token
                    Text("Pilih Denominasi Token Listrik:", color = WalletTheme.textPrimary(isDark), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val plnDenoms = listOf("50000", "100000", "200000")
                        plnDenoms.forEach { den ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { plnAmountInput = den },
                                shape = RoundedCornerShape(12.dp),
                                color = if (plnAmountInput == den) Color(0xFFFBBF24).copy(alpha = 0.2f) else WalletTheme.cardBg(isDark),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp, 
                                    if (plnAmountInput == den) Color(0xFFFBBF24) else WalletTheme.border(isDark)
                                )
                            ) {
                                Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                    Text("Rp " + NumberFormat.getNumberInstance(Locale.US).format(den.toDouble()), color = WalletTheme.textPrimary(isDark), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = plnPinInput,
                        onValueChange = { plnPinInput = it },
                        label = { Text("PIN Transaksi (6 Digit)", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountVal = plnAmountInput.toDoubleOrNull() ?: 0.0
                        if (amountVal <= 0.0) {
                            Toast.makeText(context, "Denominasi token tidak valid!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (plnMeterInput.length < 8) {
                            Toast.makeText(context, "Masukkan ID Pelanggan yang valid!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (plnPinInput.length != 6) {
                            Toast.makeText(context, "PIN harus berupa 6 digit angka!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        viewModel.executePurchase(
                            amount = amountVal,
                            description = "Beli Token Listrik PLN (ID: $plnMeterInput)",
                            pin = plnPinInput,
                            onSuccess = {
                                val generatedToken = (1000..9999).random().toString() + " " + 
                                                     (1000..9999).random().toString() + " " + 
                                                     (1000..9999).random().toString() + " " + 
                                                     (1000..9999).random().toString() + " " + 
                                                     (1000..9999).random().toString()

                                showPlnDialog = false
                                plnPinInput = ""
                                
                                // Show Token Receipt in custom persistent dialog/alert
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Token Listrik PLN Sukses Terbit")
                                    .setMessage("Silakan masukkan 20 Digit kode PLN ini ke meteran listrik rumah Anda:\n\n$generatedToken\n\nID Pelanggan: $plnMeterInput\nNominal: Rp " + NumberFormat.getNumberInstance(Locale.US).format(amountVal))
                                    .setPositiveButton("Salin Kode") { _, _ ->
                                        Toast.makeText(context, "Kode token berhasil disalin!", Toast.LENGTH_SHORT).show()
                                    }
                                    .show()
                            },
                            onError = { err -> 
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Beli Token Listrik", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlnDialog = false }) {
                    Text("Batal", color = WalletTheme.textSecondary(isDark))
                }
            },
            containerColor = WalletTheme.cardBg(isDark),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 4. DIALOG VOUCHER GAME SHOP (Mobile Legends & Genshin Impact specialized top up)
    if (showGameDialog) {
        AlertDialog(
            onDismissRequest = { showGameDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("NADH Mobile Game Shop", fontWeight = FontWeight.Bold, color = WalletTheme.textPrimary(isDark), fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Top up game mobile favorit anak jaman sekarang. Instan terkirim 24 jam non-stop.", color = WalletTheme.textSecondary(isDark), fontSize = 12.sp)

                    // Game selection
                    Text("Pilih Game Seluler:", color = WalletTheme.textPrimary(isDark), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val games = listOf("MLBB", "Genshin", "Free Fire")
                        games.forEach { g ->
                            val fullname = when(g) {
                                "MLBB" -> "Mobile Legends (MLBB)"
                                "Genshin" -> "Genshin Impact (Hoyoverse)"
                                else -> "Free Fire (Garena)"
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { 
                                        gameSelectedName = fullname 
                                        if (g == "MLBB") {
                                            gamePackageSelected = "86 Diamonds - Rp 22.000"
                                            gameCostAmount = 22000.0
                                        } else if (g == "Genshin") {
                                            gamePackageSelected = "Welkin Moon - Rp 79.000"
                                            gameCostAmount = 79000.0
                                        } else {
                                            gamePackageSelected = "140 Diamonds - Rp 20.000"
                                            gameCostAmount = 20000.0
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (gameSelectedName.contains(g)) Color(0xFFEC4899).copy(alpha = 0.2f) else WalletTheme.cardBg(isDark),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp, 
                                    if (gameSelectedName.contains(g)) Color(0xFFEC4899) else WalletTheme.border(isDark)
                                )
                            ) {
                                Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                    Text(g, color = WalletTheme.textPrimary(isDark), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = gameIdInput,
                        onValueChange = { gameIdInput = it },
                        label = { Text("ID Karakter / UID & Server ID Game", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = WalletTheme.primary()) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Package selection buttons
                    Text("Pilih Paket Item Game ($gameSelectedName):", color = WalletTheme.textPrimary(isDark), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val packs = if (gameSelectedName.contains("MLBB")) {
                        listOf("86 Diamonds - Rp 22.000" to 22000.0, "172 Diamonds - Rp 44.000" to 44000.0, "257 Diamonds - Rp 66.000" to 66000.0)
                    } else if (gameSelectedName.contains("Hoyoverse")) {
                        listOf("Welkin Moon - Rp 79.000" to 79000.0, "300 Crystals - Rp 79.000" to 79000.0, "980 Crystals - Rp 239.000" to 239000.0)
                    } else {
                        listOf("140 Diamonds - Rp 20.000" to 20000.0, "355 Diamonds - Rp 50.000" to 50000.0, "720 Diamonds - Rp 100.000" to 100000.0)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        packs.forEach { (name, cost) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        gamePackageSelected = name
                                        gameCostAmount = cost
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (gamePackageSelected == name) Color(0xFFEC4899).copy(alpha = 0.2f) else WalletTheme.cardBg(isDark),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp, 
                                    if (gamePackageSelected == name) Color(0xFFEC4899) else WalletTheme.border(isDark)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(name, color = WalletTheme.textPrimary(isDark), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    if (gamePackageSelected == name) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Terpilih", tint = Color(0xFFEC4899), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = gamePinInput,
                        onValueChange = { gamePinInput = it },
                        label = { Text("PIN Transaksi (6 Digit)", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (gameIdInput.trim().isEmpty()) {
                            Toast.makeText(context, "Masukkan ID Akun Game Anda!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (gamePinInput.length != 6) {
                            Toast.makeText(context, "PIN harus berupa 6 digit angka!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        viewModel.executePurchase(
                            amount = gameCostAmount,
                            description = "Top Up $gameSelectedName - $gamePackageSelected (UID: $gameIdInput)",
                            pin = gamePinInput,
                            onSuccess = {
                                Toast.makeText(context, "Top up sukses. $gamePackageSelected telah dikreditkan ke ID $gameIdInput.", Toast.LENGTH_LONG).show()
                                showGameDialog = false
                                gamePinInput = ""
                            },
                            onError = { err -> 
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Beli Voucher Game", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGameDialog = false }) {
                    Text("Batal", color = WalletTheme.textSecondary(isDark))
                }
            },
            containerColor = WalletTheme.cardBg(isDark),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 5. DIALOG CHARITY DONASI (NADH Peduli)
    if (showCharityDialog) {
        AlertDialog(
            onDismissRequest = { showCharityDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("NADH Peduli Donasi", fontWeight = FontWeight.Bold, color = WalletTheme.textPrimary(isDark), fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Berbagi kebaikan bersama NADH Wallet. Saldo disalurkan langsung melalui BAZNAS & Yayasan terakreditasi real-time.", color = WalletTheme.textSecondary(isDark), fontSize = 12.sp)

                    // Target cause Selection
                    Text("Pilih Sasaran Donasi:", color = WalletTheme.textPrimary(isDark), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val causes = listOf("NADH Peduli Anak Yatim", "Solidaritas Kemanusiaan", "Dana Medis Dhuafa")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        causes.forEach { cause ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { charityCauseSelected = cause },
                                shape = RoundedCornerShape(10.dp),
                                color = if (charityCauseSelected == cause) Color(0xFFEC4899).copy(alpha = 0.15f) else WalletTheme.cardBg(isDark),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp, 
                                    if (charityCauseSelected == cause) Color(0xFFEC4899) else WalletTheme.border(isDark)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cause, color = WalletTheme.textPrimary(isDark), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    if (charityCauseSelected == cause) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFFEC4899), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = charityAmountInput,
                        onValueChange = { charityAmountInput = it },
                        label = { Text("Nominal Donasi (Rupiah)", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = WalletTheme.primary()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = charityPinInput,
                        onValueChange = { charityPinInput = it },
                        label = { Text("PIN Transaksi (6 Digit)", color = WalletTheme.textSecondary(isDark)) },
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletTheme.primary(),
                            unfocusedBorderColor = WalletTheme.border(isDark),
                            focusedTextColor = WalletTheme.textPrimary(isDark),
                            unfocusedTextColor = WalletTheme.textPrimary(isDark),
                            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountVal = charityAmountInput.toDoubleOrNull() ?: 0.0
                        if (amountVal <= 0.0) {
                            Toast.makeText(context, "Jumlah nominal donasi tidak valid!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (charityPinInput.length != 6) {
                            Toast.makeText(context, "PIN harus berupa 6 digit angka!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        viewModel.executePurchase(
                            amount = amountVal,
                            description = "Donasi Kebaikan ($charityCauseSelected)",
                            pin = charityPinInput,
                            onSuccess = {
                                Toast.makeText(context, "Terima kasih. Donasi sebesar " + formatToIDR(amountVal) + " berhasil disalurkan.", Toast.LENGTH_LONG).show()
                                showCharityDialog = false
                                charityPinInput = ""
                            },
                            onError = { err -> 
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salurkan Donasi", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCharityDialog = false }) {
                    Text("Batal", color = WalletTheme.textSecondary(isDark))
                }
            },
            containerColor = WalletTheme.cardBg(isDark),
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun QuickActionItem(isDark: Boolean, icon: ImageVector, title: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            title,
            color = WalletTheme.textPrimary(isDark),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// SCREEN 4: P2P TRANSFER (Beautiful Recipient Checker)
// ==========================================
@Composable
fun TransferScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val isDark by viewModel.isDarkMode.collectAsState()
    var targetWallet by remember { mutableStateFlowOf("") }
    var amountText by remember { mutableStateFlowOf("") }
    var pinText by remember { mutableStateFlowOf("") }
    var isLoading by remember { mutableStateFlowOf(false) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletTheme.background(isDark))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                )
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WalletTheme.textPrimary(isDark))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Transfer Antar Dompet", color = WalletTheme.textPrimary(isDark), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Youth Informational Guide Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E1B4B).copy(alpha = 0.4f) else Color(0xFFEEF2F6)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                if (isDark) Color(0xFF6366F1).copy(alpha = 0.2f) else Color(0xFF6366F1).copy(alpha = 0.1f)
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info, 
                    contentDescription = "Verified Notification", 
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Penerima divalidasi secara real-time sebelum saldo dipindahkan secara asinkron super aman.",
                    color = if (isDark) Color(0xFFC7D2FE) else Color(0xFF475569),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(26.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = targetWallet,
                onValueChange = { targetWallet = it },
                label = { Text("ID Wallet / No. HP Penerima", color = WalletTheme.textSecondary(isDark)) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WalletTheme.primary(),
                    unfocusedBorderColor = WalletTheme.border(isDark),
                    focusedTextColor = WalletTheme.textPrimary(isDark),
                    unfocusedTextColor = WalletTheme.textPrimary(isDark),
                    focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                ),
                leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = WalletTheme.primary()) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Jumlah Nominal (Rupiah)", color = WalletTheme.textSecondary(isDark)) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WalletTheme.primary(),
                    unfocusedBorderColor = WalletTheme.border(isDark),
                    focusedTextColor = WalletTheme.textPrimary(isDark),
                    unfocusedTextColor = WalletTheme.textPrimary(isDark),
                    focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                ),
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = WalletTheme.primary()) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = pinText,
                onValueChange = { pinText = it },
                label = { Text("PIN Transaksi (6 Digit)", color = WalletTheme.textSecondary(isDark)) },
                shape = RoundedCornerShape(14.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WalletTheme.primary(),
                    unfocusedBorderColor = WalletTheme.border(isDark),
                    focusedTextColor = WalletTheme.textPrimary(isDark),
                    unfocusedTextColor = WalletTheme.textPrimary(isDark),
                    focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                ),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                if (amt <= 0) {
                    Toast.makeText(context, "Nominal transfer harus lebih besar dari 0!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                viewModel.executeTransfer(
                    amount = amt,
                    targetWallet = targetWallet,
                    pin = pinText,
                    onSuccess = {
                        isLoading = false
                        Toast.makeText(context, "Transfer Aman Terkonfirmasi & Berhasil!", Toast.LENGTH_LONG).show()
                        onBack()
                    },
                    onError = { err ->
                        isLoading = false
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SendToMobile, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Kirim Saldo Sekarang", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// SCREEN 5: TOP UP SCREEN (Seamless Nominal Selectors)
// ==========================================
@Composable
fun TopUpScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val isDark by viewModel.isDarkMode.collectAsState()
    var topupAmtText by remember { mutableStateFlowOf("100000") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletTheme.background(isDark))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                )
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WalletTheme.textPrimary(isDark))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Top Up Deposit", color = WalletTheme.textPrimary(isDark), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Pilih Nominal Cepat:", color = WalletTheme.textPrimary(isDark), fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("50000", "100000", "200000").forEach { item ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { topupAmtText = item },
                    shape = RoundedCornerShape(16.dp),
                    color = if (topupAmtText == item) WalletTheme.primary() else WalletTheme.cardBg(isDark),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp, 
                        if (topupAmtText == item) WalletTheme.primaryAccent() else WalletTheme.border(isDark)
                    )
                ) {
                    Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = formatToIDR(item.toDouble()), 
                            color = if (topupAmtText == item) Color.White else WalletTheme.textPrimary(isDark), 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = topupAmtText,
            onValueChange = { topupAmtText = it },
            label = { Text("Nominal Khusus", color = WalletTheme.textSecondary(isDark)) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WalletTheme.primary(),
                unfocusedBorderColor = WalletTheme.border(isDark),
                focusedTextColor = WalletTheme.textPrimary(isDark),
                unfocusedTextColor = WalletTheme.textPrimary(isDark),
                focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
            ),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                val valAmt = topupAmtText.toDoubleOrNull() ?: 0.0
                if (valAmt <= 0) {
                    Toast.makeText(context, "Masukkan nominal pembayaran valid!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.executeTopUp(
                    amount = valAmt,
                    onSuccess = {
                        Toast.makeText(context, "Top Up Virtual Account Sukses!", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddCard, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Konfirmasi VA Perbankan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ==========================================
// SCREEN 6: WEB INVOICE PAYMENT LISTS
// ==========================================
@Composable
fun PaymentInvoiceListScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val invoices by viewModel.invoices.collectAsState()
    var selectedInvoice by remember { mutableStateFlowOf<PaymentInvoice?>(null) }
    var payPinInput by remember { mutableStateFlowOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshFromWebsite()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletTheme.background(isDark))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                )
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WalletTheme.textPrimary(isDark))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Tagihan Order Website",
                color = WalletTheme.textPrimary(isDark),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    viewModel.refreshFromWebsite(
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                    )
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh tagihan", tint = WalletTheme.textPrimary(isDark))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Bayar belanja online di website merchant terpercaya Anda secara instan menggunakan sinkronisasi API saldo real-time.",
            color = WalletTheme.textSecondary(isDark),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (invoices.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Lunas", tint = Color(0xFF10B981), modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Semua tagihan sudah lunas. Tidak ada tunggakan.", color = WalletTheme.textSecondary(isDark), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(invoices) { inv ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                            .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(26.dp))
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(inv.merchantName, color = WalletTheme.textPrimary(isDark), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Order ID: ${inv.orderId}", color = Color(0xFF6366F1), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Text(formatToIDR(inv.amount), color = Color(0xFFEF4444), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (selectedInvoice?.id == inv.id) {
                            OutlinedTextField(
                                value = payPinInput,
                                onValueChange = { payPinInput = it },
                                label = { Text("Masukkan 6 Digit PIN eWallet", color = WalletTheme.textSecondary(isDark)) },
                                shape = RoundedCornerShape(14.dp),
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = WalletTheme.primary(),
                                    unfocusedBorderColor = WalletTheme.border(isDark),
                                    focusedTextColor = WalletTheme.textPrimary(isDark),
                                    unfocusedTextColor = WalletTheme.textPrimary(isDark),
                                    focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.payInvoice(
                                            invoice = inv,
                                            pin = payPinInput,
                                            onSuccess = {
                                                Toast.makeText(context, "Selesai! Webhook merchant dikonfirmasi aman.", Toast.LENGTH_LONG).show()
                                                selectedInvoice = null
                                                payPinInput = ""
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Bayar Tagihan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { selectedInvoice = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                                    modifier = Modifier.weight(1.0f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Batal", fontSize = 13.sp, color = if (isDark) Color.White else Color(0xFF334155), fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = { 
                                    selectedInvoice = inv 
                                    payPinInput = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary()),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bayar Lewat eWallet", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 7: VIRTUAL ACCOUNT PAYMENT
// ==========================================
@Composable
fun VirtualAccountPaymentScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val isDark by viewModel.isDarkMode.collectAsState()
    var vaNumberInput by remember { mutableStateFlowOf("") }
    var pinInput by remember { mutableStateFlowOf("") }
    var vaInfo by remember { mutableStateFlowOf<VirtualAccountInfo?>(null) }
    var isLoading by remember { mutableStateFlowOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletTheme.background(isDark))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                )
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WalletTheme.textPrimary(isDark))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Bayar Virtual Account", color = WalletTheme.textPrimary(isDark), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Masukkan kode VA dari checkout NADH Store", color = WalletTheme.textSecondary(isDark), fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(26.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = vaNumberInput,
                onValueChange = {
                    vaNumberInput = it.filter { ch -> ch.isDigit() }.take(16)
                    vaInfo = null
                    pinInput = ""
                },
                label = { Text("Nomor Virtual Account", color = WalletTheme.textSecondary(isDark)) },
                leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = WalletTheme.primary()) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WalletTheme.primary(),
                    unfocusedBorderColor = WalletTheme.border(isDark),
                    focusedTextColor = WalletTheme.textPrimary(isDark),
                    unfocusedTextColor = WalletTheme.textPrimary(isDark),
                    focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                    unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                    disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                ),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Button(
                onClick = {
                    isLoading = true
                    viewModel.lookupVirtualAccount(
                        vaNumber = vaNumberInput,
                        onSuccess = { info ->
                            isLoading = false
                            vaInfo = info
                            Toast.makeText(context, "VA ditemukan. Silakan konfirmasi PIN.", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            isLoading = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
            ) {
                Icon(if (isLoading) Icons.Default.Sync else Icons.Default.Search, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLoading) "Mencari VA..." else "Cek Nomor VA", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        val currentVa = vaInfo
        if (currentVa != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(26.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(currentVa.merchantName, color = WalletTheme.textPrimary(isDark), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Order #${currentVa.orderNumber}", color = WalletTheme.textSecondary(isDark), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(formatToIDR(currentVa.amount), color = Color(0xFF10B981), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }

                Divider(color = WalletTheme.border(isDark))

                Text("Bank: ${currentVa.bankName}", color = WalletTheme.textSecondary(isDark), fontSize = 12.sp)
                Text("VA: ${currentVa.vaNumber}", color = WalletTheme.textPrimary(isDark), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Customer: ${currentVa.customerName}", color = WalletTheme.textSecondary(isDark), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("PIN eWallet 6 Digit", color = WalletTheme.textSecondary(isDark)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WalletTheme.primary(),
                        unfocusedBorderColor = WalletTheme.border(isDark),
                        focusedTextColor = WalletTheme.textPrimary(isDark),
                        unfocusedTextColor = WalletTheme.textPrimary(isDark),
                        focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )

                Button(
                    onClick = {
                        isLoading = true
                        viewModel.payVirtualAccount(
                            vaNumber = currentVa.vaNumber,
                            pin = pinInput,
                            onSuccess = {
                                isLoading = false
                                Toast.makeText(context, "Pembayaran VA berhasil. Order website sudah terbayar.", Toast.LENGTH_LONG).show()
                                onBack()
                            },
                            onError = { err ->
                                isLoading = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bayar VA Sekarang", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// SCREEN 8: PIN SECURITY & ENCRYPTED DATABASE SETTINGS
// ==========================================
@Composable
fun PinSettingsScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val isDark by viewModel.isDarkMode.collectAsState()
    var oldPin by remember { mutableStateFlowOf("") }
    var newPin by remember { mutableStateFlowOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletTheme.background(isDark))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                )
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WalletTheme.textPrimary(isDark))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Pengaturan PIN Keamanan", color = WalletTheme.textPrimary(isDark), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(26.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = oldPin,
                onValueChange = { oldPin = it },
                label = { Text("PIN Saat Ini", color = WalletTheme.textSecondary(isDark)) },
                shape = RoundedCornerShape(14.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WalletTheme.primary(),
                    unfocusedBorderColor = WalletTheme.border(isDark),
                    focusedTextColor = WalletTheme.textPrimary(isDark),
                    unfocusedTextColor = WalletTheme.textPrimary(isDark),
                    focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                ),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it },
                label = { Text("PIN Baru Anda (6 Digit)", color = WalletTheme.textSecondary(isDark)) },
                shape = RoundedCornerShape(14.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WalletTheme.primary(),
                    unfocusedBorderColor = WalletTheme.border(isDark),
                    focusedTextColor = WalletTheme.textPrimary(isDark),
                    unfocusedTextColor = WalletTheme.textPrimary(isDark),
                    focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                ),
                leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null, tint = WalletTheme.primary()) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                viewModel.updatePin(
                    oldPin = oldPin,
                    newPin = newPin,
                    onSuccess = {
                        Toast.makeText(context, "PIN Baru Berhasil Diubah & Disimpan!", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Simpan PIN Baru", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ==========================================
// SCREEN 9: EDIT PROFILE
// ==========================================
@Composable
fun EditProfileScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val userProfile by viewModel.userState.collectAsState()
    val context = LocalContext.current

    var fullName by remember(userProfile?.id) { mutableStateFlowOf(userProfile?.fullName ?: "") }
    var email by remember(userProfile?.id) { mutableStateFlowOf(userProfile?.email ?: "") }
    var phoneNumber by remember(userProfile?.id) { mutableStateFlowOf(userProfile?.phoneNumber ?: "") }
    var selectedProfileImageUri by remember(userProfile?.id) { mutableStateFlowOf(userProfile?.profileImageUri) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedProfileImageUri = it.toString()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletTheme.background(isDark))
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                    )
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WalletTheme.textPrimary(isDark))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Edit Profile",
                        color = WalletTheme.textPrimary(isDark),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Perbarui identitas akun NADH Wallet",
                        color = WalletTheme.textSecondary(isDark),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4F46E5), Color(0xFF0EA5E9), Color(0xFF10B981))
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.clickable { imagePickerLauncher.launch(arrayOf("image/*")) }
                    ) {
                        ProfileAvatar(
                            fullName = fullName,
                            profileImageUri = selectedProfileImageUri,
                            size = 82.dp,
                            fontSize = 24.sp,
                            backgroundColor = Color.White.copy(alpha = 0.18f),
                            borderColor = Color.White.copy(alpha = 0.45f),
                            textColor = Color.White
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp),
                            shape = CircleShape,
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = "Ganti foto profile",
                                    tint = WalletTheme.primary(),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            fullName.ifBlank { "Nama Pengguna" },
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            email.ifBlank { "email@domain.com" },
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Black.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    "Terverifikasi",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            TextButton(
                                onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Icon(
                                    Icons.Default.AddAPhoto,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    if (selectedProfileImageUri == null) "Tambah Foto" else "Ganti Foto",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                    .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(26.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProfileTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = "Nama Lengkap",
                    icon = Icons.Default.Person,
                    isDark = isDark,
                    keyboardType = KeyboardType.Text
                )
                ProfileTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    icon = Icons.Default.Email,
                    isDark = isDark,
                    keyboardType = KeyboardType.Email
                )
                ProfileTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it.filter { ch -> ch.isDigit() }.take(14) },
                    label = "Nomor HP",
                    icon = Icons.Default.Phone,
                    isDark = isDark,
                    keyboardType = KeyboardType.Phone
                )

                OutlinedTextField(
                    value = userProfile?.walletId ?: "-",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("ID Wallet", color = WalletTheme.textSecondary(isDark)) },
                    leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = WalletTheme.primary()) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WalletTheme.border(isDark),
                        unfocusedBorderColor = WalletTheme.border(isDark),
                        focusedTextColor = WalletTheme.textSecondary(isDark),
                        unfocusedTextColor = WalletTheme.textSecondary(isDark),
                        focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.45f) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.45f) else Color(0xFFF8FAFC),
                        disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.45f) else Color(0xFFF8FAFC)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Button(
                onClick = {
                    viewModel.updateProfile(
                        fullName = fullName,
                        email = email,
                        phoneNumber = phoneNumber,
                        profileImageUri = selectedProfileImageUri,
                        onSuccess = {
                            Toast.makeText(context, "Profile berhasil diperbarui.", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Simpan Perubahan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun ProfileAvatar(
    fullName: String?,
    profileImageUri: String?,
    size: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color
) {
    val context = LocalContext.current
    val bitmap = remember(profileImageUri) {
        profileImageUri?.let { uriText ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriText))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Foto profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fullName
                        ?.split(" ")
                        ?.mapNotNull { it.firstOrNull() }
                        ?.joinToString("")
                        ?.take(2)
                        ?.ifBlank { "NW" } ?: "NW",
                    color = textColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize
                )
            }
        }
    }
}

@Composable
fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isDark: Boolean,
    keyboardType: KeyboardType
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = WalletTheme.textSecondary(isDark)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = WalletTheme.primary()) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WalletTheme.primary(),
            unfocusedBorderColor = WalletTheme.border(isDark),
            focusedTextColor = WalletTheme.textPrimary(isDark),
            unfocusedTextColor = WalletTheme.textPrimary(isDark),
            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
            unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
            disabledContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

// Helper Currency
fun formatToIDR(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    format.maximumFractionDigits = 0
    format.minimumFractionDigits = 0
    return format.format(amount)
}

// Inline helper supporting compose delegation
fun <T> mutableStateFlowOf(value: T): MutableState<T> = mutableStateOf(value)
