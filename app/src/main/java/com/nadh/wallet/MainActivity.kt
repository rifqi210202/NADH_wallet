package com.nadh.wallet

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas as ComposeCanvas
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.PI
import kotlin.math.sin

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

data class SavingsPocket(
    val id: String,
    val name: String,
    val targetAmount: Double,
    val balance: Double,
    val emoji: String = "💰"
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

    private val _selectedTransaction = MutableStateFlow<TransactionItem?>(null)
    val selectedTransaction: StateFlow<TransactionItem?> = _selectedTransaction.asStateFlow()

    fun selectTransaction(transaction: TransactionItem) {
        _selectedTransaction.value = transaction
    }

    private val _savingsBalance = MutableStateFlow(750000.0)
    val savingsBalance: StateFlow<Double> = _savingsBalance.asStateFlow()

    private val _savingsPockets = MutableStateFlow(
        listOf(
            SavingsPocket("p1", "Beli HP Baru", 4500000.0, 1250000.0, "📱"),
            SavingsPocket("p2", "Liburan", 2500000.0, 550000.0, "🏖️"),
            SavingsPocket("p3", "Dana Darurat", 3000000.0, 900000.0, "🛡️")
        )
    )
    val savingsPockets: StateFlow<List<SavingsPocket>> = _savingsPockets.asStateFlow()

    fun createSavingsPocket(name: String, targetAmount: Double, emoji: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val cleanedName = name.trim()
        if (cleanedName.length < 3) {
            onError("Nama kantong minimal 3 karakter.")
            return
        }
        if (targetAmount <= 0) {
            onError("Target tabungan harus lebih dari Rp 0.")
            return
        }

        val pocket = SavingsPocket(
            id = UUID.randomUUID().toString().take(8),
            name = cleanedName,
            targetAmount = targetAmount,
            balance = 0.0,
            emoji = emoji.ifBlank { "💰" }
        )
        _savingsPockets.value = listOf(pocket) + _savingsPockets.value
        onSuccess()
    }

    fun moveWalletToSavings(amount: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentUser = _userState.value ?: return
        if (amount <= 0) {
            onError("Nominal harus lebih dari Rp 0.")
            return
        }
        if (amount > currentUser.balance) {
            onError("Saldo utama eWallet tidak mencukupi.")
            return
        }

        _userState.value = currentUser.copy(balance = currentUser.balance - amount)
        _savingsBalance.value = _savingsBalance.value + amount

        val newTx = TransactionItem(
            id = UUID.randomUUID().toString().take(8),
            type = "SAVINGS",
            amount = amount,
            status = "SUCCESS",
            date = "Hari ini, " + java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            description = "Menabung ke Tabunganku",
            referenceId = "SAV-" + (100000..999999).random()
        )
        _transactions.value = listOf(newTx) + _transactions.value
        onSuccess()
    }

    fun moveSavingsToWallet(amount: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentUser = _userState.value ?: return
        if (amount <= 0) {
            onError("Nominal harus lebih dari Rp 0.")
            return
        }
        if (amount > _savingsBalance.value) {
            onError("Saldo Tabunganku tidak mencukupi.")
            return
        }

        _savingsBalance.value = _savingsBalance.value - amount
        _userState.value = currentUser.copy(balance = currentUser.balance + amount)
        onSuccess()
    }

    fun fundPocket(
        targetPocketId: String,
        amount: Double,
        source: String,
        sourcePocketId: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (amount <= 0) {
            onError("Nominal harus lebih dari Rp 0.")
            return
        }

        val pockets = _savingsPockets.value
        val targetPocket = pockets.find { it.id == targetPocketId }
        if (targetPocket == null) {
            onError("Kantong tujuan tidak ditemukan.")
            return
        }

        when (source) {
            "WALLET" -> {
                val currentUser = _userState.value ?: return
                if (amount > currentUser.balance) {
                    onError("Saldo utama eWallet tidak mencukupi.")
                    return
                }
                _userState.value = currentUser.copy(balance = currentUser.balance - amount)
            }
            "SAVINGS" -> {
                if (amount > _savingsBalance.value) {
                    onError("Saldo Tabunganku tidak mencukupi.")
                    return
                }
                _savingsBalance.value = _savingsBalance.value - amount
            }
            "POCKET" -> {
                val sourcePocket = pockets.find { it.id == sourcePocketId }
                if (sourcePocket == null) {
                    onError("Kantong sumber belum dipilih.")
                    return
                }
                if (sourcePocket.id == targetPocketId) {
                    onError("Kantong sumber dan tujuan tidak boleh sama.")
                    return
                }
                if (amount > sourcePocket.balance) {
                    onError("Saldo kantong sumber tidak mencukupi.")
                    return
                }
                _savingsPockets.value = _savingsPockets.value.map {
                    if (it.id == sourcePocket.id) it.copy(balance = it.balance - amount) else it
                }
            }
            else -> {
                onError("Sumber dana belum valid.")
                return
            }
        }

        _savingsPockets.value = _savingsPockets.value.map {
            if (it.id == targetPocketId) it.copy(balance = it.balance + amount) else it
        }

        val sourceName = when (source) {
            "WALLET" -> "Saldo Utama"
            "SAVINGS" -> "Saldo Tabunganku"
            "POCKET" -> pockets.find { it.id == sourcePocketId }?.name ?: "Kantong Lain"
            else -> "Sumber Dana"
        }

        val newTx = TransactionItem(
            id = UUID.randomUUID().toString().take(8),
            type = "SAVINGS",
            amount = amount,
            status = "SUCCESS",
            date = "Hari ini, " + java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            description = "Isi kantong ${targetPocket.name} dari $sourceName",
            referenceId = "KTG-" + (100000..999999).random()
        )
        _transactions.value = listOf(newTx) + _transactions.value
        onSuccess()
    }

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
// LOCAL NOTIFICATION HELPER
// ==========================================
object WalletNotificationHelper {
    private const val CHANNEL_ID = "nadh_wallet_payment_channel"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notifikasi Pembayaran & Tagihan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi transaksi dan pembayaran NADH Wallet"
                enableVibration(true)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun showPaymentNotification(context: Context, transaction: TransactionItem) {
        if (!canNotify(context)) return

        val title = when (transaction.status.uppercase(Locale.getDefault())) {
            "SUCCESS", "PAID" -> "Pembayaran berhasil"
            "PENDING" -> "Pembayaran diproses"
            "FAILED" -> "Pembayaran gagal"
            else -> "Update transaksi"
        }

        val signedAmount = if (transaction.type == "TOPUP" || transaction.type == "TRANSFER_IN") {
            "+ ${formatToIDR(transaction.amount)}"
        } else {
            "- ${formatToIDR(transaction.amount)}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText("$signedAmount • ${transaction.description}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$signedAmount\n${transaction.description}\nRef: ${transaction.referenceId}"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        NotificationManagerCompat.from(context).notify(transaction.id.hashCode(), notification)
    }
}

// ==========================================
// MAIN ACTIVITY
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WalletNotificationHelper.createChannel(this)
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
                onNavigateToSavings = { navController.navigate("savings") },
                onNavigateToPin = { navController.navigate("pin_settings") },
                onNavigateToEditProfile = { navController.navigate("edit_profile") },
                onNavigateToTransactionHistory = { navController.navigate("transaction_history") },
                onNavigateToTransactionDetail = { transaction ->
                    viewModel.selectTransaction(transaction)
                    navController.navigate("transaction_detail")
                },
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
        composable("savings") {
            SavingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("pin_settings") {
            PinSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("edit_profile") {
            EditProfileScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("transaction_history") {
            TransactionHistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenTransaction = { transaction ->
                    viewModel.selectTransaction(transaction)
                    navController.navigate("transaction_detail")
                }
            )
        }
        composable("transaction_detail") {
            TransactionReceiptScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}

// ==========================================
// SCREEN 1: SPLASH SCREEN (Glowing Futuristic Logo)
// ==========================================
@Composable
fun AnimatedSplashLogo(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_logo_motion")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_float"
    )
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splash_outer_rotation"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splash_inner_rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_pulse"
    )
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_sparkle"
    )

    Box(
        modifier = Modifier
            .size(150.dp)
            .offset(y = floatOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(138.dp)
                .graphicsLayer {
                    rotationZ = outerRotation
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF818CF8),
                            Color(0xFF38BDF8),
                            Color(0xFF22C55E),
                            Color(0xFF818CF8)
                        )
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(118.dp)
                .graphicsLayer {
                    rotationZ = innerRotation
                    alpha = 0.55f
                }
                .border(
                    width = 1.5.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(34.dp)
                )
        )

        // sparkle dots
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 6.dp)
                .size(10.dp)
                .background(Color(0xFF38BDF8).copy(alpha = sparkleAlpha), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 8.dp, y = 18.dp)
                .size(8.dp)
                .background(Color(0xFF22C55E).copy(alpha = sparkleAlpha * 0.85f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-6).dp, y = (-8).dp)
                .size(9.dp)
                .background(Color(0xFFFBBF24).copy(alpha = sparkleAlpha * 0.8f), CircleShape)
        )

        Surface(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
            shape = RoundedCornerShape(32.dp),
            color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.72f),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isDark) Color(0xFF818CF8).copy(alpha = 0.3f) else Color(0xFF6366F1).copy(alpha = 0.2f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.06f else 0.55f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Wallet,
                    contentDescription = "Wallet Logo",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(54.dp)
                )
            }
        }
    }
}

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
        val titleAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, delayMillis = 120),
            label = "splash_title_alpha"
        )
        val subtitleAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100, delayMillis = 280),
            label = "splash_subtitle_alpha"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedSplashLogo(isDark = isDark)
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                "NADH Wallet",
                color = if (isDark) Color.White else Color(0xFF0F172A),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.sp,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "eWallet aman, cepat, dan mudah digunakan",
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha }
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


@Composable
fun AnimatedMonthlyLimitBar(
    progress: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "monthly_limit_progress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "monthly_limit_shimmer")
    val shimmerFraction by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "monthly_limit_shimmer_fraction"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "monthly_limit_pulse"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
    ) {
        val barWidth = maxWidth

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF6366F1),
                            Color(0xFF8B5CF6),
                            Color(0xFF22C55E)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = pulseAlpha * 0.18f), CircleShape)
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(34.dp)
                    .offset(x = (barWidth * shimmerFraction).coerceAtLeast((-34).dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.0f),
                                Color.White.copy(alpha = 0.42f),
                                Color.White.copy(alpha = 0.0f)
                            )
                        ),
                        CircleShape
                    )
            )
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
    onNavigateToSavings: () -> Unit,
    onNavigateToPin: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToTransactionHistory: () -> Unit,
    onNavigateToTransactionDetail: (TransactionItem) -> Unit,
    onLogout: () -> Unit
) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val userProfile by viewModel.userState.collectAsState()
    val txs by viewModel.transactions.collectAsState()
    val invoices by viewModel.invoices.collectAsState()
    val coins by viewModel.coins.collectAsState()
    
    val context = LocalContext.current
    val gson = remember { Gson() }
    var knownTransactionKeys by remember { mutableStateOf<Set<String>?>(null) }
    var knownInvoiceKeys by remember { mutableStateOf<Set<String>?>(null) }
    var inAppNotificationText by remember { mutableStateOf<String?>(null) }
    var inAppNotificationSubtext by remember { mutableStateOf<String?>(null) }
    var inAppNotificationTx by remember { mutableStateOf<TransactionItem?>(null) }
    var lastSyncText by remember { mutableStateOf("Menunggu sinkronisasi transaksi...") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notifikasi pembayaran diaktifkan.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        WalletNotificationHelper.createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !WalletNotificationHelper.canNotify(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            lastSyncText = "Mengecek transaksi dari website..."
            viewModel.refreshFromWebsite(
                onError = { err ->
                    lastSyncText = "Sync gagal: ${err.take(42)}"
                }
            )
            lastSyncText = "Terakhir cek: " + java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }
    }

    LaunchedEffect(txs) {
        val previousKeys = knownTransactionKeys
        val currentKeys = txs.map { "${it.referenceId}|${it.status}|${it.amount}|${it.description}" }.toSet()

        if (previousKeys == null) {
            knownTransactionKeys = currentKeys
        } else {
            val newTransactions = txs.filter { tx ->
                val key = "${tx.referenceId}|${tx.status}|${tx.amount}|${tx.description}"
                key !in previousKeys
            }

            val importantTx = newTransactions
                .filter { it.status.uppercase(Locale.getDefault()) in listOf("SUCCESS", "PAID", "PENDING", "FAILED") }
                .firstOrNull()

            if (importantTx != null) {
                val title = when (importantTx.status.uppercase(Locale.getDefault())) {
                    "SUCCESS", "PAID" -> "Pembayaran berhasil"
                    "PENDING" -> "Pembayaran sedang diproses"
                    "FAILED" -> "Pembayaran gagal"
                    else -> "Update transaksi"
                }
                val signedAmount = if (importantTx.type == "TOPUP" || importantTx.type == "TRANSFER_IN") {
                    "+ ${formatToIDR(importantTx.amount)}"
                } else {
                    "- ${formatToIDR(importantTx.amount)}"
                }

                inAppNotificationText = "$title • $signedAmount"
                inAppNotificationSubtext = importantTx.description
                inAppNotificationTx = importantTx
                lastSyncText = "Transaksi baru diterima dari website"
                WalletNotificationHelper.showPaymentNotification(context, importantTx)

                kotlinx.coroutines.delay(9000)
                if (inAppNotificationTx?.referenceId == importantTx.referenceId) {
                    inAppNotificationText = null
                    inAppNotificationSubtext = null
                    inAppNotificationTx = null
                }
            }

            knownTransactionKeys = currentKeys
        }
    }


    LaunchedEffect(invoices) {
        val previousInvoiceKeys = knownInvoiceKeys
        val currentInvoiceKeys = invoices.map { "${it.id}|${it.orderId}|${it.amount}|${it.status}" }.toSet()

        if (previousInvoiceKeys == null) {
            knownInvoiceKeys = currentInvoiceKeys
        } else {
            val newInvoice = invoices.firstOrNull { invoice ->
                val key = "${invoice.id}|${invoice.orderId}|${invoice.amount}|${invoice.status}"
                key !in previousInvoiceKeys
            }

            if (newInvoice != null) {
                val fakeTx = TransactionItem(
                    id = newInvoice.id,
                    type = "PAYMENT",
                    amount = newInvoice.amount,
                    status = newInvoice.status.ifBlank { "PENDING" },
                    date = "Hari ini, " + java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                    description = "Tagihan baru dari ${newInvoice.merchantName} • Order #${newInvoice.orderId}",
                    referenceId = "INV-${newInvoice.orderId}"
                )

                inAppNotificationText = "Tagihan baru masuk • ${formatToIDR(newInvoice.amount)}"
                inAppNotificationSubtext = "${newInvoice.merchantName} • Order #${newInvoice.orderId}"
                inAppNotificationTx = fakeTx
                lastSyncText = "Tagihan baru diterima dari website"
                WalletNotificationHelper.showPaymentNotification(context, fakeTx)

                kotlinx.coroutines.delay(9000)
                if (inAppNotificationTx?.id == fakeTx.id) {
                    inAppNotificationText = null
                    inAppNotificationSubtext = null
                    inAppNotificationTx = null
                }
            }

            knownInvoiceKeys = currentInvoiceKeys
        }
    }


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
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.clickable(onClick = onNavigateToEditProfile)
                        ) {
                            ProfileAvatar(
                                fullName = userProfile?.fullName,
                                profileImageUri = userProfile?.profileImageUri,
                                size = 48.dp,
                                fontSize = 15.sp,
                                backgroundColor = Color(0xFF6366F1).copy(alpha = 0.15f),
                                borderColor = Color(0xFF818CF8),
                                textColor = WalletTheme.primaryAccent()
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                                    .align(Alignment.BottomEnd)
                                    .border(1.5.dp, WalletTheme.background(isDark), CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    userProfile?.fullName ?: "Rifqi Nadhir Altaz",
                                    color = WalletTheme.textPrimary(isDark),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                "ID: ${userProfile?.walletId ?: "-"}",
                                color = WalletTheme.textSecondary(isDark),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier.size(42.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                            )
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                contentDescription = "Switch theme",
                                tint = if (isDark) Color(0xFFFBBF24) else Color(0xFF4F46E5),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier.size(42.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isDark) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "Log out",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(19.dp)
                            )
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
            item {
                AnimatedVisibility(
                    visible = inAppNotificationText != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF22C55E), Color(0xFF14B8A6), Color(0xFF6366F1))
                                ),
                                RoundedCornerShape(22.dp)
                            )
                            .clickable {
                                inAppNotificationTx?.let { onNavigateToTransactionDetail(it) }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.20f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = inAppNotificationText ?: "",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = inAppNotificationSubtext ?: "Transaksi baru dari website berhasil diterima.",
                                color = Color.White.copy(alpha = 0.86f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WalletTheme.cardBg(isDark), RoundedCornerShape(18.dp))
                        .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Notifikasi Pembayaran & Tagihan",
                            color = WalletTheme.textPrimary(isDark),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            lastSyncText,
                            color = WalletTheme.textSecondary(isDark),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = {
                            val testTx = TransactionItem(
                                id = UUID.randomUUID().toString().take(8),
                                type = "PAYMENT",
                                amount = 89000.0,
                                status = "PENDING",
                                date = "Hari ini, " + java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                                description = "Test tagihan baru dari website",
                                referenceId = "INV-TEST-" + (100000..999999).random()
                            )
                            inAppNotificationText = "Tagihan baru masuk • ${formatToIDR(testTx.amount)}"
                            inAppNotificationSubtext = testTx.description
                            inAppNotificationTx = testTx
                            lastSyncText = "Test notifikasi tagihan berhasil ditampilkan"
                            WalletNotificationHelper.showPaymentNotification(context, testTx)
                        }
                    ) {
                        Text("Test", color = WalletTheme.primary(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

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
                                QuickActionItem(isDark = isDark, icon = Icons.Default.AccountBalance, title = "Tabunganku", color = Color(0xFF0EA5E9), onClick = onNavigateToSavings)
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
                        
                        AnimatedMonthlyLimitBar(
                            progress = 1250000f / 4500000f,
                            isDark = isDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Riwayat Preview Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Riwayat Aktivitas",
                            color = WalletTheme.textPrimary(isDark),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "3 transaksi terbaru",
                            color = WalletTheme.textSecondary(isDark),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(
                        onClick = onNavigateToTransactionHistory,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Lihat Semua",
                            color = WalletTheme.primary(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = WalletTheme.primary(),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            items(txs.take(3)) { tx ->
                CompactTransactionPreviewCard(
                    tx = tx,
                    isDark = isDark,
                    onClick = { onNavigateToTransactionDetail(tx) }
                )
            }

            if (txs.size > 3) {
                item {
                    Button(
                        onClick = onNavigateToTransactionHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE0E7FF)
                        )
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = WalletTheme.primary(),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Buka Halaman Riwayat Lengkap",
                            color = WalletTheme.primary(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
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
fun InvoiceReceiptHeroIllustration(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "invoice_receipt_hero")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "invoice_float"
    )
    val receiptRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "invoice_receipt_rotation"
    )
    val handOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "invoice_hand_offset"
    )

    Box(
        modifier = modifier.offset(y = floatOffset.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .align(Alignment.Center)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .align(Alignment.Center)
                .offset(x = 10.dp, y = 8.dp)
                .background(Color.White.copy(alpha = 0.07f), CircleShape)
        )

        // Receipt
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.94f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 6.dp)
                .size(width = 60.dp, height = 80.dp)
                .graphicsLayer { rotationZ = receiptRotation }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF8B5CF6), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .width(22.dp)
                            .background(Color(0xFFD8B4FE), RoundedCornerShape(50))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (it == 3) 0.58f else 1f)
                            .height(5.dp)
                            .background(Color(0xFFE5E7EB), RoundedCornerShape(50))
                    )
                    if (it != 3) Spacer(modifier = Modifier.height(5.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(7.dp)
                        .background(Color(0xFF22C55E), RoundedCornerShape(50))
                )
            }
        }

        // Body
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 2.dp)
                .size(width = 84.dp, height = 88.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF4F46E5))),
                    RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
        )

        // Neck
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-52).dp)
                .size(width = 14.dp, height = 14.dp)
                .background(Color(0xFFF7D0B2), RoundedCornerShape(8.dp))
        )

        // Head
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-77).dp)
                .size(42.dp)
                .background(Color(0xFFF7D0B2), CircleShape)
        )
        // Hair
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-83).dp)
                .size(width = 44.dp, height = 22.dp)
                .background(Color(0xFF111827), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
        )

        // Left arm
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = (-25).dp, y = (-25).dp)
                .size(width = 18.dp, height = 54.dp)
                .background(Color(0xFF7C3AED), RoundedCornerShape(14.dp))
        )
        // Right arm holding receipt
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 24.dp, y = (-32).dp + handOffset.dp)
                .size(width = 18.dp, height = 58.dp)
                .background(Color(0xFF6366F1), RoundedCornerShape(14.dp))
                .graphicsLayer { rotationZ = -16f }
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 35.dp, y = (-46).dp + handOffset.dp)
                .size(13.dp)
                .background(Color(0xFFF7D0B2), CircleShape)
        )
    }
}

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

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletTheme.background(isDark))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        )
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = WalletTheme.textPrimary(isDark))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tagihan Website",
                            color = WalletTheme.textPrimary(isDark),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Order dari NADH Store yang menunggu pembayaran",
                            color = WalletTheme.textSecondary(isDark),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh tagihan", tint = WalletTheme.primary())
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
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF06B6D4))
                            ),
                            RoundedCornerShape(28.dp)
                        )
                        .padding(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 134.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(0.58f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    "REALTIME SYNC",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(11.dp))

                            Text(
                                "${invoices.size}",
                                color = Color.White,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )

                            Text(
                                "Tagihan Aktif",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(7.dp))

                            Text(
                                "Bayar order website dari saldo NADH Wallet.",
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        InvoiceReceiptHeroIllustration(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(104.dp)
                                .height(108.dp)
                        )
                    }
                }
            }

            if (invoices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                            .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(26.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.13f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Lunas",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Tidak ada tagihan",
                            color = WalletTheme.textPrimary(isDark),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            "Semua order website sudah lunas atau belum ada pesanan baru.",
                            color = WalletTheme.textSecondary(isDark),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(invoices) { inv ->
                    val isSelected = selectedInvoice?.id == inv.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                            .border(
                                1.dp,
                                if (isSelected) WalletTheme.primary() else WalletTheme.border(isDark),
                                RoundedCornerShape(26.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = WalletTheme.primary().copy(alpha = 0.12f),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Store,
                                        contentDescription = null,
                                        tint = WalletTheme.primary(),
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(13.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        inv.merchantName,
                                        color = WalletTheme.textPrimary(isDark),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF97316).copy(alpha = 0.13f)
                                    ) {
                                        Text(
                                            inv.status.ifBlank { "PENDING" }.uppercase(Locale.getDefault()),
                                            color = Color(0xFFF97316),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                            maxLines = 1
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    "Order #${inv.orderId}",
                                    color = WalletTheme.textSecondary(isDark),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(15.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC), RoundedCornerShape(18.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                "Total Tagihan",
                                color = WalletTheme.textSecondary(isDark),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(5.dp))

                            Text(
                                formatToIDR(inv.amount),
                                color = Color(0xFFEF4444),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                InvoiceInfoPill(
                                    modifier = Modifier.weight(1f),
                                    title = "Merchant",
                                    value = inv.merchantName,
                                    isDark = isDark
                                )
                                InvoiceInfoPill(
                                    modifier = Modifier.weight(1f),
                                    title = "Metode",
                                    value = "eWallet",
                                    isDark = isDark
                                )
                            }
                        }

                        AnimatedVisibility(visible = isSelected) {
                            Column {
                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = payPinInput,
                                    onValueChange = { payPinInput = it.filter { ch -> ch.isDigit() }.take(6) },
                                    label = { Text("PIN eWallet 6 digit", color = WalletTheme.textSecondary(isDark)) },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalletTheme.primary()) },
                                    shape = RoundedCornerShape(16.dp),
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WalletTheme.primary(),
                                        unfocusedBorderColor = WalletTheme.border(isDark),
                                        focusedTextColor = WalletTheme.textPrimary(isDark),
                                        unfocusedTextColor = WalletTheme.textPrimary(isDark),
                                        focusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
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
                                                    Toast.makeText(context, "Pembayaran berhasil dikonfirmasi ke website.", Toast.LENGTH_LONG).show()
                                                    selectedInvoice = null
                                                    payPinInput = ""
                                                },
                                                onError = { err ->
                                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(17.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Bayar", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            selectedInvoice = null
                                            payPinInput = ""
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, WalletTheme.border(isDark))
                                    ) {
                                        Text(
                                            "Batal",
                                            fontSize = 13.sp,
                                            color = WalletTheme.textPrimary(isDark),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = !isSelected) {
                            Column {
                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        selectedInvoice = inv
                                        payPinInput = ""
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary()),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(17.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Bayar Lewat eWallet", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceInfoPill(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    isDark: Boolean
) {
    Column(
        modifier = modifier
            .background(WalletTheme.cardBg(isDark), RoundedCornerShape(14.dp))
            .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(
            title,
            color = WalletTheme.textSecondary(isDark),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            value,
            color = WalletTheme.textPrimary(isDark),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
fun CompactTransactionPreviewCard(
    tx: TransactionItem,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val isIncome = tx.type == "TOPUP" || tx.type == "TRANSFER_IN"
    val accent = if (isIncome) Color(0xFF10B981) else if (tx.type == "SAVINGS") Color(0xFF0EA5E9) else Color(0xFFEF4444)
    val icon = when {
        isIncome -> Icons.Default.Add
        tx.type == "SAVINGS" -> Icons.Default.AccountBalance
        tx.type == "PAYMENT" -> Icons.Default.ReceiptLong
        else -> Icons.Default.CallMade
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WalletTheme.cardBg(isDark), RoundedCornerShape(20.dp))
            .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = 0.13f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.description,
                color = WalletTheme.textPrimary(isDark),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                tx.date,
                color = WalletTheme.textSecondary(isDark),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (isIncome) "+" else "-") + " " + formatToIDR(tx.amount),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WalletTheme.textSecondary(isDark), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ReceiptHeroIllustration(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "receipt_hero")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )
    val receiptRotation by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "receipt_rotation"
    )
    val armOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arm_offset"
    )

    Box(
        modifier = modifier.offset(y = floatOffset.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .align(Alignment.Center)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.Center)
                .offset(x = 10.dp, y = 8.dp)
                .background(Color.White.copy(alpha = 0.07f), CircleShape)
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = 4.dp)
                .size(width = 58.dp, height = 74.dp)
                .graphicsLayer { rotationZ = receiptRotation }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF4F46E5), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .width(22.dp)
                            .background(Color(0xFFC7D2FE), RoundedCornerShape(50))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (it == 3) 0.55f else 1f)
                            .height(5.dp)
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(50))
                    )
                    if (it != 3) Spacer(modifier = Modifier.height(5.dp))
                }
            }
        }

        // Body
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 2.dp)
                .size(width = 82.dp, height = 88.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF4F46E5))),
                    RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
        )

        // Neck
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-52).dp)
                .size(width = 14.dp, height = 14.dp)
                .background(Color(0xFFF8D3B2), RoundedCornerShape(8.dp))
        )

        // Head
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-76).dp)
                .size(42.dp)
                .background(Color(0xFFF8D3B2), CircleShape)
        )
        // Hair cap
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-82).dp)
                .size(width = 44.dp, height = 22.dp)
                .background(Color(0xFF1F2937), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
        )

        // Left arm
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = (-25).dp, y = (-24).dp)
                .size(width = 18.dp, height = 54.dp)
                .background(Color(0xFF7C3AED), RoundedCornerShape(14.dp))
        )
        // Right arm holding receipt
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 24.dp, y = (-32).dp + armOffset.dp)
                .size(width = 18.dp, height = 58.dp)
                .background(Color(0xFF6366F1), RoundedCornerShape(14.dp))
                .graphicsLayer { rotationZ = -18f }
        )
        // Right hand
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 35.dp, y = (-46).dp + armOffset.dp)
                .size(13.dp)
                .background(Color(0xFFF8D3B2), CircleShape)
        )
    }
}

@Composable
fun TransactionHistoryScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit
) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val txs by viewModel.transactions.collectAsState()
    var selectedFilter by remember { mutableStateOf("SEMUA") }

    val filters = listOf(
        "SEMUA" to "Semua",
        "MASUK" to "Masuk",
        "KELUAR" to "Keluar",
        "SAVINGS" to "Tabungan"
    )

    val filteredTransactions = remember(txs, selectedFilter) {
        when (selectedFilter) {
            "MASUK" -> txs.filter { it.type == "TOPUP" || it.type == "TRANSFER_IN" }
            "KELUAR" -> txs.filter { it.type == "TRANSFER_OUT" || it.type == "PAYMENT" }
            "SAVINGS" -> txs.filter { it.type == "SAVINGS" }
            else -> txs
        }
    }

    val totalIn = txs.filter { it.type == "TOPUP" || it.type == "TRANSFER_IN" }.sumOf { it.amount }
    val totalOut = txs.filter { it.type == "TRANSFER_OUT" || it.type == "PAYMENT" }.sumOf { it.amount }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletTheme.background(isDark))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        )
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = WalletTheme.textPrimary(isDark))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Riwayat Transaksi",
                            color = WalletTheme.textPrimary(isDark),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Semua aktivitas wallet kamu",
                            color = WalletTheme.textSecondary(isDark),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.13f)
                    ) {
                        Text(
                            "${txs.size} Transaksi",
                            color = Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF111827), Color(0xFF4F46E5), Color(0xFF06B6D4))
                            ),
                            RoundedCornerShape(28.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Ringkasan Aktivitas",
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "${txs.size} transaksi aktif",
                                    color = Color.White,
                                    fontSize = 27.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Tap transaksi untuk lihat struk digital",
                                    color = Color.White.copy(alpha = 0.76f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            ReceiptHeroIllustration(
                                modifier = Modifier
                                    .width(118.dp)
                                    .height(112.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HistoryMiniStat(
                                modifier = Modifier.weight(1f),
                                label = "Dana Masuk",
                                value = formatToIDR(totalIn),
                                color = Color(0xFF10B981)
                            )
                            HistoryMiniStat(
                                modifier = Modifier.weight(1f),
                                label = "Dana Keluar",
                                value = formatToIDR(totalOut),
                                color = Color(0xFFF97316)
                            )
                        }
                    }
                }
            }

            item {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(filters) { filter ->
                        val selected = selectedFilter == filter.first
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) WalletTheme.primary() else WalletTheme.cardBg(isDark),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) WalletTheme.primary() else WalletTheme.border(isDark)
                            ),
                            modifier = Modifier.clickable { selectedFilter = filter.first }
                        ) {
                            Text(
                                filter.second,
                                color = if (selected) Color.White else WalletTheme.textPrimary(isDark),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WalletTheme.cardBg(isDark), RoundedCornerShape(26.dp))
                            .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(26.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = WalletTheme.primary().copy(alpha = 0.12f),
                            modifier = Modifier.size(66.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = WalletTheme.primary(), modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Belum ada transaksi", color = WalletTheme.textPrimary(isDark), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(5.dp))
                        Text("Transaksi sesuai filter ini akan tampil di sini.", color = WalletTheme.textSecondary(isDark), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(filteredTransactions) { tx ->
                    ModernTransactionHistoryCard(
                        tx = tx,
                        isDark = isDark,
                        onClick = { onOpenTransaction(tx) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryMiniStat(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(17.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(label, color = Color.White.copy(alpha = 0.76f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ModernTransactionHistoryCard(
    tx: TransactionItem,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val isIncome = tx.type == "TOPUP" || tx.type == "TRANSFER_IN"
    val accent = when {
        isIncome -> Color(0xFF10B981)
        tx.type == "SAVINGS" -> Color(0xFF0EA5E9)
        tx.status.equals("FAILED", true) -> Color(0xFFEF4444)
        else -> Color(0xFFF97316)
    }
    val icon = when {
        isIncome -> Icons.Default.AddCircle
        tx.type == "SAVINGS" -> Icons.Default.AccountBalance
        tx.type == "PAYMENT" -> Icons.Default.ShoppingCart
        tx.type == "TRANSFER_OUT" -> Icons.Default.Send
        else -> Icons.Default.ReceiptLong
    }
    val signedAmount = (if (isIncome) "+" else "-") + " " + formatToIDR(tx.amount)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WalletTheme.cardBg(isDark), RoundedCornerShape(24.dp))
            .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = accent.copy(alpha = 0.13f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(25.dp))
                }
            }

            Spacer(modifier = Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tx.description,
                    color = WalletTheme.textPrimary(isDark),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = WalletTheme.textSecondary(isDark), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        tx.date,
                        color = WalletTheme.textSecondary(isDark),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    signedAmount,
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(7.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = tx.status.toStatusColor().copy(alpha = 0.12f)
                ) {
                    Text(
                        tx.status.toStatusLabel(),
                        color = tx.status.toStatusColor(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(13.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Nomor Referensi", color = WalletTheme.textSecondary(isDark), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    tx.referenceId,
                    color = WalletTheme.textPrimary(isDark),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WalletTheme.primary(), modifier = Modifier.size(22.dp))
        }
    }
}



@Composable
fun SavingsPassbookIllustration(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "savings_passbook")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "savings_float"
    )
    val bookRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "passbook_rotation"
    )
    val handOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hand_offset"
    )

    Box(
        modifier = modifier.offset(y = floatOffset.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .align(Alignment.Center)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(74.dp)
                .align(Alignment.Center)
                .offset(x = 12.dp, y = 10.dp)
                .background(Color.White.copy(alpha = 0.07f), CircleShape)
        )

        // passbook
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFECFDF5),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = 8.dp)
                .size(width = 60.dp, height = 78.dp)
                .graphicsLayer { rotationZ = bookRotation }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .width(22.dp)
                            .background(Color(0xFFA7F3D0), RoundedCornerShape(50))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (it == 3) 0.6f else 1f)
                            .height(5.dp)
                            .background(Color(0xFFD1FAE5), RoundedCornerShape(50))
                    )
                    if (it != 3) Spacer(modifier = Modifier.height(5.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(7.dp)
                        .background(Color(0xFF10B981), RoundedCornerShape(50))
                )
            }
        }

        // body
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 2.dp)
                .size(width = 84.dp, height = 88.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF06B6D4), Color(0xFF2563EB))),
                    RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
        )

        // neck
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-52).dp)
                .size(width = 14.dp, height = 14.dp)
                .background(Color(0xFFF7D0B2), RoundedCornerShape(8.dp))
        )

        // head
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-77).dp)
                .size(42.dp)
                .background(Color(0xFFF7D0B2), CircleShape)
        )
        // hair
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-83).dp)
                .size(width = 44.dp, height = 22.dp)
                .background(Color(0xFF111827), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
        )

        // left arm
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = (-25).dp, y = (-25).dp)
                .size(width = 18.dp, height = 54.dp)
                .background(Color(0xFF0891B2), RoundedCornerShape(14.dp))
        )
        // right arm holding passbook
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 24.dp, y = (-32).dp + handOffset.dp)
                .size(width = 18.dp, height = 58.dp)
                .background(Color(0xFF1D4ED8), RoundedCornerShape(14.dp))
                .graphicsLayer { rotationZ = -16f }
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 35.dp, y = (-46).dp + handOffset.dp)
                .size(13.dp)
                .background(Color(0xFFF7D0B2), CircleShape)
        )
    }
}

@Composable
fun SavingsScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit
) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val userProfile by viewModel.userState.collectAsState()
    val savingsBalance by viewModel.savingsBalance.collectAsState()
    val pockets by viewModel.savingsPockets.collectAsState()
    val context = LocalContext.current

    var showAddSavingsDialog by remember { mutableStateOf(false) }
    var showWithdrawSavingsDialog by remember { mutableStateOf(false) }
    var showCreatePocketDialog by remember { mutableStateOf(false) }
    var selectedPocket by remember { mutableStateOf<SavingsPocket?>(null) }

    val totalPocketBalance = pockets.sumOf { it.balance }
    val totalSavingsAsset = savingsBalance + totalPocketBalance

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletTheme.background(isDark))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        )
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = WalletTheme.textPrimary(isDark))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tabunganku",
                            color = WalletTheme.textPrimary(isDark),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Pisahkan saldo buat tujuan impianmu",
                            color = WalletTheme.textSecondary(isDark),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.13f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Simulasi", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF06B6D4))
                            ),
                            RoundedCornerShape(28.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 138.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxWidth(0.64f)
                            ) {
                                Text(
                                    "Total Tabungan",
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    formatToIDR(totalSavingsAsset),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 28.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Saldo utama eWallet",
                                    color = Color.White.copy(alpha = 0.66f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    formatToIDR(userProfile?.balance ?: 0.0),
                                    color = Color.White.copy(alpha = 0.86f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            SavingsPassbookIllustration(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(102.dp)
                                    .height(106.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SavingsMiniStat(
                                modifier = Modifier.weight(1f),
                                label = "Saldo Tabungan",
                                value = formatToIDR(savingsBalance),
                                isDark = true
                            )
                            SavingsMiniStat(
                                modifier = Modifier.weight(1f),
                                label = "Isi Kantong",
                                value = formatToIDR(totalPocketBalance),
                                isDark = true
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showAddSavingsDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tambah Saldo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showWithdrawSavingsDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WalletTheme.border(isDark))
                    ) {
                        Icon(Icons.Default.CallMade, contentDescription = null, tint = WalletTheme.primary(), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tarik", color = WalletTheme.primary(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WalletTheme.cardBg(isDark), RoundedCornerShape(22.dp))
                        .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(22.dp))
                        .clickable { showCreatePocketDialog = true }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0EA5E9).copy(alpha = 0.13f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(13.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Buat Kantong Baru", color = WalletTheme.textPrimary(isDark), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Pisahkan dana untuk HP, liburan, bisnis, dan lainnya", color = WalletTheme.textSecondary(isDark), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WalletTheme.textSecondary(isDark), modifier = Modifier.size(22.dp))
                }
            }

            item {
                Text(
                    "Kantong Tabungan",
                    color = WalletTheme.textPrimary(isDark),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            items(pockets.chunked(2)) { pocketRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    pocketRow.forEach { pocket ->
                        Box(modifier = Modifier.weight(1f)) {
                            SavingsPocketCard(
                                pocket = pocket,
                                isDark = isDark,
                                onClick = { selectedPocket = pocket }
                            )
                        }
                    }
                    if (pocketRow.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDark) Color(0xFF0F172A) else Color(0xFFEFF6FF),
                            RoundedCornerShape(22.dp)
                        )
                        .border(1.dp, WalletTheme.border(isDark), RoundedCornerShape(22.dp))
                        .padding(16.dp)
                ) {
                    Text("Cara kerja Tabunganku", color = WalletTheme.textPrimary(isDark), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Saldo bisa dipindahkan dari saldo utama eWallet ke Tabunganku. Setiap kantong bisa diisi dari saldo utama, saldo Tabunganku, atau saldo kantong lain.",
                        color = WalletTheme.textSecondary(isDark),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }

    if (showAddSavingsDialog) {
        SavingsAmountDialog(
            title = "Tambah Saldo Tabunganku",
            subtitle = "Ambil dari saldo utama eWallet",
            confirmText = "Masukkan ke Tabungan",
            isDark = isDark,
            onDismiss = { showAddSavingsDialog = false },
            onConfirm = { amount ->
                viewModel.moveWalletToSavings(
                    amount = amount,
                    onSuccess = {
                        showAddSavingsDialog = false
                        Toast.makeText(context, "Saldo berhasil masuk ke Tabunganku.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                )
            }
        )
    }

    if (showWithdrawSavingsDialog) {
        SavingsAmountDialog(
            title = "Tarik dari Tabunganku",
            subtitle = "Pindahkan kembali ke saldo utama eWallet",
            confirmText = "Tarik ke eWallet",
            isDark = isDark,
            onDismiss = { showWithdrawSavingsDialog = false },
            onConfirm = { amount ->
                viewModel.moveSavingsToWallet(
                    amount = amount,
                    onSuccess = {
                        showWithdrawSavingsDialog = false
                        Toast.makeText(context, "Saldo berhasil dipindahkan ke eWallet.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                )
            }
        )
    }

    if (showCreatePocketDialog) {
        CreatePocketDialog(
            isDark = isDark,
            onDismiss = { showCreatePocketDialog = false },
            onConfirm = { name, target, emoji ->
                viewModel.createSavingsPocket(
                    name = name,
                    targetAmount = target,
                    emoji = emoji,
                    onSuccess = {
                        showCreatePocketDialog = false
                        Toast.makeText(context, "Kantong baru berhasil dibuat.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                )
            }
        )
    }

    selectedPocket?.let { pocket ->
        FundPocketDialog(
            pocket = pocket,
            allPockets = pockets,
            isDark = isDark,
            onDismiss = { selectedPocket = null },
            onConfirm = { amount, source, sourcePocketId ->
                viewModel.fundPocket(
                    targetPocketId = pocket.id,
                    amount = amount,
                    source = source,
                    sourcePocketId = sourcePocketId,
                    onSuccess = {
                        selectedPocket = null
                        Toast.makeText(context, "Kantong ${pocket.name} berhasil diisi.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                )
            }
        )
    }
}

@Composable
fun SavingsMiniStat(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isDark: Boolean
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun WaterPocketFill(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "water_fill_progress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "water_fill_wave")
    val waveShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_shift"
    )
    val waveShiftSecond by infiniteTransition.animateFloat(
        initialValue = (PI / 2).toFloat(),
        targetValue = (2.5f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_shift_second"
    )

    ComposeCanvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val baseWaterY = height * (1f - animatedProgress.coerceIn(0.04f, 0.98f))
        val amplitudePrimary = height * 0.04f
        val amplitudeSecondary = height * 0.025f

        val waterPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, height)
            lineTo(0f, baseWaterY)
            var x = 0f
            while (x <= width) {
                val y = baseWaterY + amplitudePrimary * sin((x / width) * (2f * PI).toFloat() * 2f + waveShift)
                lineTo(x, y)
                x += 5f
            }
            lineTo(width, height)
            close()
        }
        drawPath(
            path = waterPath,
            brush = Brush.verticalGradient(
                listOf(Color(0xFF7DD3FC), Color(0xFF38BDF8), Color(0xFF2563EB)),
                startY = baseWaterY,
                endY = height
            )
        )

        val secondPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, height)
            lineTo(0f, baseWaterY + 6f)
            var x = 0f
            while (x <= width) {
                val y = (baseWaterY + 6f) + amplitudeSecondary * sin((x / width) * (2f * PI).toFloat() * 2f + waveShiftSecond)
                lineTo(x, y)
                x += 5f
            }
            lineTo(width, height)
            close()
        }
        drawPath(path = secondPath, color = Color.White.copy(alpha = 0.16f))

        val linePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, baseWaterY)
            var x = 0f
            while (x <= width) {
                val y = baseWaterY + amplitudePrimary * sin((x / width) * (2f * PI).toFloat() * 2f + waveShift)
                lineTo(x, y)
                x += 5f
            }
        }
        drawPath(path = linePath, color = Color.Black.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
    }
}

@Composable
fun SavingsPocketCard(
    pocket: SavingsPocket,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val progress = if (pocket.targetAmount <= 0) 0f else (pocket.balance / pocket.targetAmount).toFloat().coerceIn(0f, 1f)
    val displayPercent by animateFloatAsState(
        targetValue = progress * 100f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "pocket_percent"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFFF8FAFC), RoundedCornerShape(18.dp))
                .border(4.dp, Color(0xFF0F172A), RoundedCornerShape(18.dp))
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
            ) {
                WaterPocketFill(
                    progress = progress,
                    modifier = Modifier.matchParentSize()
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "${displayPercent.toInt()}%",
                    color = Color(0xFF0F172A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            Text(
                text = pocket.emoji,
                fontSize = 20.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            )

            Text(
                text = pocket.name.uppercase(Locale.getDefault()),
                color = Color(0xFF111827),
                fontSize = if (pocket.name.length > 10) 12.sp else 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.86f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatToIDR(pocket.balance),
                    color = Color(0xFF0F172A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Target ${formatToIDR(pocket.targetAmount)}",
                    color = Color(0xFF475569),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tap untuk isi kantong",
            color = WalletTheme.primary(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SavingsAmountDialog(
    title: String,
    subtitle: String,
    confirmText: String,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalletTheme.cardBg(isDark),
        shape = RoundedCornerShape(26.dp),
        title = {
            Column {
                Text(title, color = WalletTheme.textPrimary(isDark), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = WalletTheme.textSecondary(isDark), fontSize = 12.sp, lineHeight = 17.sp)
            }
        },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Nominal") },
                prefix = { Text("Rp ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WalletTheme.primary(),
                    unfocusedBorderColor = WalletTheme.border(isDark),
                    focusedTextColor = WalletTheme.textPrimary(isDark),
                    unfocusedTextColor = WalletTheme.textPrimary(isDark),
                    focusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    unfocusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    onConfirm(amount)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = WalletTheme.textSecondary(isDark))
            }
        }
    )
}

@Composable
fun CreatePocketDialog(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("💰") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalletTheme.cardBg(isDark),
        shape = RoundedCornerShape(26.dp),
        title = {
            Text("Buat Kantong Baru", color = WalletTheme.textPrimary(isDark), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(2) },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WalletTheme.primary(),
                        unfocusedBorderColor = WalletTheme.border(isDark),
                        focusedTextColor = WalletTheme.textPrimary(isDark),
                        unfocusedTextColor = WalletTheme.textPrimary(isDark),
                        focusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                    )
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama kantong") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WalletTheme.primary(),
                        unfocusedBorderColor = WalletTheme.border(isDark),
                        focusedTextColor = WalletTheme.textPrimary(isDark),
                        unfocusedTextColor = WalletTheme.textPrimary(isDark),
                        focusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                    )
                )

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Target saldo") },
                    prefix = { Text("Rp ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WalletTheme.primary(),
                        unfocusedBorderColor = WalletTheme.border(isDark),
                        focusedTextColor = WalletTheme.textPrimary(isDark),
                        unfocusedTextColor = WalletTheme.textPrimary(isDark),
                        focusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, targetText.toDoubleOrNull() ?: 0.0, emoji) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
            ) {
                Text("Buat Kantong", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = WalletTheme.textSecondary(isDark))
            }
        }
    )
}

@Composable
fun FundPocketDialog(
    pocket: SavingsPocket,
    allPockets: List<SavingsPocket>,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("WALLET") }
    var selectedSourcePocketId by remember { mutableStateOf(allPockets.firstOrNull { it.id != pocket.id }?.id) }
    val otherPockets = allPockets.filter { it.id != pocket.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalletTheme.cardBg(isDark),
        shape = RoundedCornerShape(26.dp),
        title = {
            Column {
                Text("Isi Kantong ${pocket.name}", color = WalletTheme.textPrimary(isDark), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Pilih sumber dana untuk mengisi kantong ini.", color = WalletTheme.textSecondary(isDark), fontSize = 12.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Nominal") },
                    prefix = { Text("Rp ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WalletTheme.primary(),
                        unfocusedBorderColor = WalletTheme.border(isDark),
                        focusedTextColor = WalletTheme.textPrimary(isDark),
                        unfocusedTextColor = WalletTheme.textPrimary(isDark),
                        focusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                    )
                )

                Text("Sumber Dana", color = WalletTheme.textPrimary(isDark), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                SavingsSourceOption(
                    selected = selectedSource == "WALLET",
                    title = "Saldo utama eWallet",
                    subtitle = "Ambil langsung dari saldo utama",
                    isDark = isDark,
                    onClick = { selectedSource = "WALLET" }
                )
                SavingsSourceOption(
                    selected = selectedSource == "SAVINGS",
                    title = "Saldo Tabunganku",
                    subtitle = "Ambil dari saldo tabungan bebas",
                    isDark = isDark,
                    onClick = { selectedSource = "SAVINGS" }
                )
                SavingsSourceOption(
                    selected = selectedSource == "POCKET",
                    title = "Kantong lain",
                    subtitle = otherPockets.firstOrNull { it.id == selectedSourcePocketId }?.name ?: "Belum ada kantong lain",
                    isDark = isDark,
                    enabled = otherPockets.isNotEmpty(),
                    onClick = { if (otherPockets.isNotEmpty()) selectedSource = "POCKET" }
                )

                if (selectedSource == "POCKET" && otherPockets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        otherPockets.forEach { sourcePocket ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (selectedSourcePocketId == sourcePocket.id) WalletTheme.primary().copy(alpha = 0.12f) else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .border(1.dp, if (selectedSourcePocketId == sourcePocket.id) WalletTheme.primary() else WalletTheme.border(isDark), RoundedCornerShape(14.dp))
                                    .clickable { selectedSourcePocketId = sourcePocket.id }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sourcePocket.emoji, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sourcePocket.name, color = WalletTheme.textPrimary(isDark), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatToIDR(sourcePocket.balance), color = WalletTheme.textSecondary(isDark), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        amountText.toDoubleOrNull() ?: 0.0,
                        selectedSource,
                        if (selectedSource == "POCKET") selectedSourcePocketId else null
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
            ) {
                Text("Isi Kantong", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = WalletTheme.textSecondary(isDark))
            }
        }
    )
}

@Composable
fun SavingsSourceOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    isDark: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val borderColor = if (selected) WalletTheme.primary() else WalletTheme.border(isDark)
    val bgColor = if (selected) WalletTheme.primary().copy(alpha = 0.10f) else if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor.copy(alpha = if (enabled) 1f else 0.45f), RoundedCornerShape(16.dp))
            .border(1.dp, borderColor.copy(alpha = if (enabled) 1f else 0.35f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) WalletTheme.primary() else WalletTheme.border(isDark),
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = WalletTheme.textPrimary(isDark).copy(alpha = if (enabled) 1f else 0.45f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = WalletTheme.textSecondary(isDark).copy(alpha = if (enabled) 1f else 0.45f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
fun TransactionReceiptScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit
) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val tx by viewModel.selectedTransaction.collectAsState()
    val userProfile by viewModel.userState.collectAsState()
    val context = LocalContext.current

    val transaction = tx
    val isIncome = transaction?.type?.equals("TOPUP", true) == true || transaction?.type?.equals("TRANSFER_IN", true) == true
    val amountPrefix = if (isIncome) "+" else "-"
    val accentColor = transaction?.status?.toStatusColor() ?: WalletTheme.primary()
    val brandGradient = if (isDark) {
        listOf(Color(0xFF111827), Color(0xFF312E81), Color(0xFF0E7490))
    } else {
        listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF06B6D4))
    }
    val receiptText = remember(transaction, userProfile) {
        if (transaction == null) {
            ""
        } else {
            """
            NADH WALLET - DIGITAL RECEIPT

            Status       : ${transaction.status.toStatusLabel()}
            Jenis        : ${transaction.type.toReceiptTypeLabel()}
            Nominal      : $amountPrefix ${formatToIDR(transaction.amount)}
            Tanggal      : ${transaction.date}
            Keterangan   : ${transaction.description}
            Referensi    : ${transaction.referenceId}
            ID Transaksi : ${transaction.id}
            Wallet ID    : ${userProfile?.walletId ?: "-"}
            Nama Akun    : ${userProfile?.fullName ?: "-"}

            Struk ini dibuat otomatis oleh NADH Wallet.
            """.trimIndent()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color(0xFF050816), Color(0xFF0F172A), Color(0xFF111827))
                    } else {
                        listOf(Color(0xFFF8FAFC), Color(0xFFEFF6FF), Color(0xFFFDF2F8))
                    }
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .align(Alignment.TopEnd)
                .offset(x = 95.dp, y = (-70).dp)
                .background(Color(0xFF8B5CF6).copy(alpha = if (isDark) 0.16f else 0.10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(190.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-85).dp, y = 95.dp)
                .background(Color(0xFF06B6D4).copy(alpha = if (isDark) 0.12f else 0.08f), CircleShape)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.82f),
                                CircleShape
                            )
                            .border(1.dp, WalletTheme.border(isDark).copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = WalletTheme.textPrimary(isDark))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Detail Struk",
                            color = WalletTheme.textPrimary(isDark),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Bukti digital transaksi",
                            color = WalletTheme.textSecondary(isDark),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (transaction == null) {
                item {
                    ModernReceiptEmptyState(isDark = isDark, onBack = onBack)
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.linearGradient(brandGradient))
                                .padding(horizontal = 18.dp, vertical = 20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(125.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 48.dp, y = (-46).dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .align(Alignment.BottomStart)
                                    .offset(x = (-30).dp, y = 28.dp)
                                    .background(Color.White.copy(alpha = 0.10f), CircleShape)
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ReceiptMiniBadge(
                                        icon = Icons.Default.Wallet,
                                        text = "NADH WALLET",
                                        color = Color.White.copy(alpha = 0.16f),
                                        contentColor = Color.White
                                    )
                                    ReceiptStatusBadge(
                                        text = transaction.status.toShortStatusLabel(),
                                        color = accentColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier.size(78.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.26f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = transaction.status.toStatusHeroIcon(),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(13.dp))

                                Text(
                                    transaction.status.toStatusLabel(),
                                    color = Color.White.copy(alpha = 0.86f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 0.3.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    "$amountPrefix ${formatToIDR(transaction.amount)}",
                                    color = Color.White,
                                    fontSize = 31.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(9.dp))

                                Text(
                                    transaction.description,
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ReceiptHeroMiniRow(
                                        label = "Tanggal",
                                        value = transaction.date,
                                        alignEnd = false
                                    )
                                    ReceiptHeroMiniDivider()
                                    ReceiptHeroMiniRow(
                                        label = "Referensi",
                                        value = transaction.referenceId,
                                        alignEnd = false,
                                        mono = true
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF111827).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WalletTheme.border(isDark).copy(alpha = 0.85f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = WalletTheme.primary().copy(alpha = 0.12f)
                                ) {
                                    Icon(
                                        Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = WalletTheme.primaryAccent(),
                                        modifier = Modifier.padding(10.dp).size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Rincian Transaksi",
                                        color = WalletTheme.textPrimary(isDark),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        "Informasi transaksi tersusun rapi",
                                        color = WalletTheme.textSecondary(isDark),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            ReceiptSoftDivider(isDark = isDark)
                            Spacer(modifier = Modifier.height(6.dp))

                            ReceiptInfoRow(icon = Icons.Default.Description, label = "Keterangan", value = transaction.description, isDark = isDark)
                            ReceiptInfoRow(icon = Icons.Default.Payments, label = "Jenis Transaksi", value = transaction.type.toReceiptTypeLabel(), isDark = isDark)
                            ReceiptInfoRow(icon = Icons.Default.Schedule, label = "Waktu Transaksi", value = transaction.date, isDark = isDark)
                            ReceiptInfoRow(icon = Icons.Default.Tag, label = "Nomor Referensi", value = transaction.referenceId, isDark = isDark, mono = true)
                            ReceiptInfoRow(icon = Icons.Default.Fingerprint, label = "ID Transaksi", value = transaction.id, isDark = isDark, mono = true)
                            ReceiptInfoRow(icon = Icons.Default.AccountBalanceWallet, label = "Wallet ID", value = userProfile?.walletId ?: "-", isDark = isDark, mono = true)
                            ReceiptInfoRow(icon = Icons.Default.Person, label = "Nama Akun", value = userProfile?.fullName ?: "-", isDark = isDark)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReceiptStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Reward",
                            value = if (transaction.status.equals("SUCCESS", true)) "+${(transaction.amount * 0.02).toInt()} Coins" else "Menunggu",
                            icon = Icons.Default.AutoAwesome,
                            color = Color(0xFFF59E0B),
                            isDark = isDark
                        )
                        ReceiptStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Mode",
                            value = "Simulasi Lokal",
                            icon = Icons.Default.Wifi,
                            color = Color(0xFF06B6D4),
                            isDark = isDark
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.78f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WalletTheme.border(isDark).copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.14f)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.padding(10.dp).size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Catatan Demo",
                                    color = WalletTheme.textPrimary(isDark),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Struk ini dibuat untuk simulasi lokal NADH Wallet pada jaringan WiFi yang sama.",
                                    color = WalletTheme.textSecondary(isDark),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Struk NADH Wallet", receiptText))
                                    Toast.makeText(context, "Struk berhasil disalin", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WalletTheme.border(isDark))
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = WalletTheme.textPrimary(isDark), modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Salin", color = WalletTheme.textPrimary(isDark), fontWeight = FontWeight.ExtraBold)
                            }
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Struk NADH Wallet")
                                        putExtra(Intent.EXTRA_TEXT, receiptText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Bagikan struk"))
                                },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))), RoundedCornerShape(17.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Bagikan", color = Color.White, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                try {
                                    shareTransactionReceiptPdf(
                                        context = context,
                                        transaction = transaction,
                                        userProfile = userProfile,
                                        amountPrefix = amountPrefix
                                    )
                                } catch (error: Throwable) {
                                    Toast.makeText(context, error.message ?: "Gagal membuat PDF struk", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B))), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                                    Spacer(modifier = Modifier.width(9.dp))
                                    Text("Kirim sebagai PDF", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernReceiptEmptyState(isDark: Boolean, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = WalletTheme.cardBg(isDark)),
        border = androidx.compose.foundation.BorderStroke(1.dp, WalletTheme.border(isDark))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                modifier = Modifier.size(78.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(38.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Belum ada transaksi dipilih", color = WalletTheme.textPrimary(isDark), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Kembali ke dashboard, lalu tap salah satu kartu di Riwayat Aktivitas untuk membuka struk.",
                color = WalletTheme.textSecondary(isDark),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletTheme.primary())
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kembali", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ReceiptMiniBadge(
    icon: ImageVector,
    text: String,
    color: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text,
                color = contentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ReceiptStatusBadge(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.20f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text.uppercase(Locale.getDefault()),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ReceiptHeroMiniRow(
    label: String,
    value: String,
    alignEnd: Boolean = false,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(76.dp)
        )
        Text(
            value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            lineHeight = 16.sp,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ReceiptHeroMiniDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

@Composable
fun ReceiptInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    isDark: Boolean,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFFF1F5F9)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WalletTheme.primaryAccent(),
                modifier = Modifier.padding(9.dp).size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = WalletTheme.textSecondary(isDark),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value.ifBlank { "-" },
                color = WalletTheme.textPrimary(isDark),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                lineHeight = 18.sp,
                maxLines = if (mono) 2 else 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ReceiptSoftDivider(isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WalletTheme.border(isDark).copy(alpha = 0.65f))
    )
}

@Composable
fun ReceiptStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    isDark: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF111827).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, WalletTheme.border(isDark).copy(alpha = 0.8f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = color.copy(alpha = 0.14f)) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(9.dp).size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = WalletTheme.textSecondary(isDark), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(value, color = WalletTheme.textPrimary(isDark), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

fun String.toReceiptTypeLabel(): String = when (uppercase(Locale.getDefault())) {
    "TOPUP" -> "Top Up Saldo"
    "TRANSFER_IN" -> "Transfer Masuk"
    "TRANSFER_OUT" -> "Transfer Keluar"
    "PAYMENT" -> "Pembayaran"
    else -> this
}

fun String.toReceiptIcon(): ImageVector = when (uppercase(Locale.getDefault())) {
    "TOPUP" -> Icons.Default.AddCircle
    "TRANSFER_IN" -> Icons.Default.CallReceived
    "TRANSFER_OUT" -> Icons.Default.CallMade
    "PAYMENT" -> Icons.Default.ReceiptLong
    else -> Icons.Default.Payments
}

fun String.toStatusLabel(): String = when (uppercase(Locale.getDefault())) {
    "SUCCESS" -> "Transaksi Berhasil"
    "PENDING" -> "Transaksi Pending"
    "FAILED" -> "Transaksi Gagal"
    else -> this
}

fun String.toShortStatusLabel(): String = when (uppercase(Locale.getDefault())) {
    "SUCCESS" -> "Berhasil"
    "PENDING" -> "Pending"
    "FAILED" -> "Gagal"
    else -> this
}

fun String.toStatusHeroIcon(): ImageVector = when (uppercase(Locale.getDefault())) {
    "SUCCESS" -> Icons.Default.CheckCircle
    "PENDING" -> Icons.Default.Schedule
    "FAILED" -> Icons.Default.Cancel
    else -> Icons.Default.Info
}

fun String.toStatusColor(): Color = when (uppercase(Locale.getDefault())) {
    "SUCCESS" -> Color(0xFF10B981)
    "PENDING" -> Color(0xFFF59E0B)
    "FAILED" -> Color(0xFFEF4444)
    else -> Color(0xFF818CF8)
}


fun shareTransactionReceiptPdf(
    context: Context,
    transaction: TransactionItem,
    userProfile: UserProfile?,
    amountPrefix: String
) {
    val pdfFile = createTransactionReceiptPdf(
        context = context,
        transaction = transaction,
        userProfile = userProfile,
        amountPrefix = amountPrefix
    )
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pdfFile
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Struk NADH Wallet ${transaction.referenceId}")
        putExtra(Intent.EXTRA_TEXT, "Berikut struk transaksi NADH Wallet dalam format PDF.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Kirim struk PDF"))
}

fun createTransactionReceiptPdf(
    context: Context,
    transaction: TransactionItem,
    userProfile: UserProfile?,
    amountPrefix: String
): File {
    val pdfDocument = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(15, 23, 42)
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(100, 116, 139)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(100, 116, 139)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(15, 23, 42)
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(79, 70, 229)
        textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = transaction.status.toPdfStatusColor()
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(226, 232, 240)
        strokeWidth = 1.5f
    }
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(248, 250, 252)
        style = Paint.Style.FILL
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(79, 70, 229)
        style = Paint.Style.FILL
    }
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(100, 116, 139)
        textSize = 11f
        textAlign = Paint.Align.CENTER
    }

    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 118f, accentPaint)

    val whiteTitlePaint = Paint(titlePaint).apply { color = android.graphics.Color.WHITE }
    val whiteSubPaint = Paint(subtitlePaint).apply { color = android.graphics.Color.argb(220, 255, 255, 255) }
    canvas.drawText("NADH Wallet", 42f, 52f, whiteTitlePaint)
    canvas.drawText("Struk transaksi digital", 42f, 76f, whiteSubPaint)
    canvas.drawText("${transaction.referenceId}", 42f, 96f, whiteSubPaint)

    canvas.drawRoundRect(42f, 144f, 553f, 285f, 22f, 22f, cardPaint)
    canvas.drawText(transaction.status.toStatusLabel(), pageWidth / 2f, 177f, statusPaint)
    canvas.drawText("$amountPrefix ${formatToIDR(transaction.amount)}", pageWidth / 2f, 224f, amountPaint)
    drawPdfCenteredWrappedText(canvas, transaction.description, pageWidth / 2f, 252f, 470f, subtitlePaint)

    var y = 333f
    canvas.drawText("Rincian Transaksi", 42f, y, titlePaint.apply { textSize = 18f })
    y += 28f
    canvas.drawLine(42f, y, 553f, y, linePaint)
    y += 28f

    val rows = listOf(
        "Status" to transaction.status.toStatusLabel(),
        "Jenis Transaksi" to transaction.type.toReceiptTypeLabel(),
        "Waktu Transaksi" to transaction.date,
        "Nomor Referensi" to transaction.referenceId,
        "ID Transaksi" to transaction.id,
        "Wallet ID" to (userProfile?.walletId ?: "-"),
        "Nama Akun" to (userProfile?.fullName ?: "-"),
        "Mode" to "Simulasi Lokal WiFi"
    )

    rows.forEach { (label, value) ->
        canvas.drawText(label, 42f, y, labelPaint)
        val nextY = drawPdfWrappedText(canvas, value.ifBlank { "-" }, 210f, y, 320f, valuePaint)
        y = nextY + 18f
        canvas.drawLine(42f, y - 7f, 553f, y - 7f, linePaint)
        y += 10f
    }

    canvas.drawText(
        "Struk ini dibuat otomatis oleh NADH Wallet untuk kebutuhan simulasi lokal.",
        pageWidth / 2f,
        795f,
        footerPaint
    )

    pdfDocument.finishPage(page)

    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val safeRef = transaction.referenceId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val file = File(dir, "struk_nadh_wallet_$safeRef.pdf")
    FileOutputStream(file).use { output -> pdfDocument.writeTo(output) }
    pdfDocument.close()
    return file
}

fun drawPdfWrappedText(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    paint: Paint
): Float {
    val words = text.split(" ")
    var line = ""
    var currentY = y
    words.forEach { word ->
        val testLine = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(testLine) <= maxWidth) {
            line = testLine
        } else {
            canvas.drawText(line, x, currentY, paint)
            line = word
            currentY += 18f
        }
    }
    if (line.isNotEmpty()) canvas.drawText(line, x, currentY, paint)
    return currentY
}

fun drawPdfCenteredWrappedText(
    canvas: Canvas,
    text: String,
    centerX: Float,
    y: Float,
    maxWidth: Float,
    paint: Paint
): Float {
    val centerPaint = Paint(paint).apply { textAlign = Paint.Align.CENTER }
    val words = text.split(" ")
    var line = ""
    var currentY = y
    words.forEach { word ->
        val testLine = if (line.isEmpty()) word else "$line $word"
        if (centerPaint.measureText(testLine) <= maxWidth) {
            line = testLine
        } else {
            canvas.drawText(line, centerX, currentY, centerPaint)
            line = word
            currentY += 17f
        }
    }
    if (line.isNotEmpty()) canvas.drawText(line, centerX, currentY, centerPaint)
    return currentY
}

fun String.toPdfStatusColor(): Int = when (uppercase(Locale.getDefault())) {
    "SUCCESS" -> android.graphics.Color.rgb(16, 185, 129)
    "PENDING" -> android.graphics.Color.rgb(245, 158, 11)
    "FAILED" -> android.graphics.Color.rgb(239, 68, 68)
    else -> android.graphics.Color.rgb(99, 102, 241)
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
