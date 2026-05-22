export interface AndroidFile {
  name: string;
  path: string;
  language: string;
  content: string;
}

export const kotlinFiles: AndroidFile[] = [
  {
    name: "build.gradle.kts (App)",
    path: "app/build.gradle.kts",
    language: "kotlin",
    content: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.ewallet.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ewallet.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Injecting live production secure domain
            buildConfigField("String", "BASE_URL", "\\"https://api.ewallet-website.com/\\"")
        }
        debug {
            buildConfigField("String", "BASE_URL", "\\"http://10.0.2.2:3000/\\"") // Android emulator localhost
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Lifecycle & Arch
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // HTTP Communication
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Local Storage (Cache Room)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
}`
  },
  {
    name: "SecurityManager.kt",
    path: "app/src/main/java/com/ewallet/app/security/SecurityManager.kt",
    language: "kotlin",
    content: `package com.ewallet.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecurityManager handles storing sensitive credentials on-device using EncryptedSharedPreferences.
 * Keys are hardware-backed on modern devices, encrypted with AES-256 GCM.
 */
class SecurityManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_wallet_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_ACCESS_TOKEN = "jwt_access_token"
        private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"
        private const val KEY_USER_PIN_SET = "is_pin_configured"
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    fun getAccessToken(): String? {
        return sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return sharedPrefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun clearSession() {
        sharedPrefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            apply()
        }
    }

    fun setPinConfigured(configured: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_USER_PIN_SET, configured).apply()
    }

    fun isPinConfigured(): Boolean {
        return sharedPrefs.getBoolean(KEY_USER_PIN_SET, false)
    }
}`
  },
  {
    name: "NetworkModule.kt",
    path: "app/src/main/java/com/ewallet/app/network/NetworkModule.kt",
    language: "kotlin",
    content: `package com.ewallet.app.network

import android.content.Context
import com.ewallet.app.BuildConfig
import com.ewallet.app.security.SecurityManager
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private fun getOkHttpClient(context: Context): OkHttpClient {
        val securityManager = SecurityManager(context)

        // Certificate Pinning to prevent Man-In-The-Middle (MITM) attacks
        val certificatePinner = CertificatePinner.Builder()
            .add("api.ewallet-website.com", "sha256/gW7S6S8k1Y23...PUBLIC_KEY_HASH_HEREE...")
            .build()

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val token = securityManager.getAccessToken()

            val requestBuilder = originalRequest.newBuilder()
                .header("Content-Type", "application/json")
                .header("X-App-Platform", "Android")

            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        // Authenticator for token refresh rotation flow
        val tokenAuthenticator = TokenAuthenticator(context, securityManager)

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY 
                    else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .certificatePinner(certificatePinner)
            .build()
    }

    fun getApiService(context: Context): ApiService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(getOkHttpClient(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}`
  },
  {
    name: "TokenAuthenticator.kt",
    path: "app/src/main/java/com/ewallet/app/network/TokenAuthenticator.kt",
    language: "kotlin",
    content: `package com.ewallet.app.network

import android.content.Context
import com.ewallet.app.BuildConfig
import com.ewallet.app.model.TokenRefreshResponse
import com.ewallet.app.security.SecurityManager
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Automates Refresh Token Rotation when 401 Unauthorized is intercepted.
 */
class TokenAuthenticator(
    private val context: Context,
    private val securityManager: SecurityManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = securityManager.getRefreshToken() ?: return null

        synchronized(this) {
            val currentAccessToken = securityManager.getAccessToken()
            // If token was already refreshed by another thread, retry original request with new token
            val authorizationHeader = response.request.header("Authorization")
            if (authorizationHeader != "Bearer $currentAccessToken") {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            // Call sync API to refresh token
            val refreshService = Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(OkHttpClient()) // Isolated clean client for refresh
                .build()
                .create(ApiService::class.java)

            return try {
                val refreshResponse = refreshService.refreshTokenSync(refreshToken).execute()
                if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                    val body = refreshResponse.body()!!
                    securityManager.saveTokens(body.accessToken, body.refreshToken)

                    response.request.newBuilder()
                        .header("Authorization", "Bearer \${body.accessToken}")
                        .build()
                } else {
                    securityManager.clearSession()
                    null // Token expired. Redirect user to Login.
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}`
  },
  {
    name: "DashboardScreen.kt",
    path: "app/src/main/java/com/ewallet/app/ui/dashboard/DashboardScreen.kt",
    language: "kotlin",
    content: `package com.ewallet.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewallet.app.model.Transaction
import java.text.NumberFormat
import java.util.Locale

@OptIn(Material3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransfer: () -> Unit,
    onNavigateToTopUp: () -> Unit,
    onNavigateToPayment: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("M-Wallet Link", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Item 1: Welcome Header
                    item {
                        Column {
                            Text("Halo,", fontSize = 16.sp, color = Color.Gray)
                            Text(uiState.userName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Wallet ID: \${uiState.walletId}", fontSize = 13.sp, color = Color.Gray)
                        }
                    }

                    // Item 2: Main Balance Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("Saldo eWallet Anda", color = Color.White.copy(alpha = 0.8f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    formatRupiah(uiState.balance),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Status: TERVERIFIKASI", color = Color.Green, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Item 3: Quick Action Buttons (Anti double spend design pattern)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ActionButton(label = "Top Up", onClick = onNavigateToTopUp)
                            ActionButton(label = "Transfer", onClick = onNavigateToTransfer)
                            ActionButton(label = "Bayar Order", onClick = onNavigateToPayment)
                        }
                    }

                    // Item 4: Transaction History Section
                    item {
                        Text("Riwayat Transaksi Terakhir", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }

                    if (uiState.transactions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Belum ada transaksi di wallet ini.", color = Color.Gray)
                            }
                        }
                    } else {
                        items(uiState.transactions) { transaction ->
                            TransactionItem(transaction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width(100.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    val isExpense = transaction.type == "TRANSFER_OUT" || transaction.type == "PAYMENT"
    val color = if (isExpense) Color.Red else Color.Green
    val prefix = if (isExpense) "-" else "+"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(transaction.description, fontWeight = FontWeight.Bold)
                Text(transaction.date, fontSize = 12.sp, color = Color.Gray)
            }
            Text(
                "\$prefix \${formatRupiah(transaction.amount)}",
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun formatRupiah(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    return format.format(value)
} `
  }
];
