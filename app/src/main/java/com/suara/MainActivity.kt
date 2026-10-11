package com.suara

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    // Variabel callback yang dibutuhkan oleh build variant pro/gratis & NovanService
    private var updateStatusCallback: ((String) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startNavService()
        } else {
            updateStatusCallback?.invoke("Izin lokasi ditolak.")
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        NovanService.statusCallback = object : NovanService.StatusCallback {
            override fun onUpdate(status: String) {
                updateStatusCallback?.invoke(status)
            }
        }

        setContent {
            var selectedBottomTab by remember { mutableStateOf(0) }
            var isRunning by remember { mutableStateOf(false) }
            var statusText by remember { mutableStateOf("Tekan tombol untuk mulai navigasi") }

            // Menghubungkan callback status ke UI state
            updateStatusCallback = { newStatus ->
                statusText = newStatus
            }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFD32F2F),
                    secondary = Color(0xFFD32F2F),
                    background = Color(0xFFF2F2F7),
                    surface = Color.White
                )
            ) {
                Scaffold(
                    topBar = {
                        if (selectedBottomTab == 0) {
                            LazarusTopBar()
                        } else if (selectedBottomTab == 3) {
                            Surface(
                                color = Color(0xFFD32F2F),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Pengaturan",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        LazarusBottomBar(
                            selectedTab = selectedBottomTab,
                            onTabSelected = { selectedBottomTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedBottomTab) {
                            0 -> LayarUtama(
                                statusText = statusText,
                                onCategoryClick = { categoryName ->
                                    isRunning = true
                                    requestPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                        )
                                    )
                                }
                            )
                            1 -> PlaceholderScreen(title = "Favorit")
                            2 -> PlaceholderScreen(title = "Berita")
                            3 -> SettingsAudioSection()
                        }
                    }
                }
            }
        }
    }

    private fun startNavService() {
        val intent = Intent(this, NovanService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NovanService.statusCallback = null
    }
}

data class MenuCategory(
    val title: String,
    val icon: ImageVector
)

@Composable
fun LazarusTopBar() {
    Surface(
        color = Color(0xFFD32F2F),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Eksplorasi",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(28.dp).clickable { })
                Icon(Icons.Default.MyLocation, contentDescription = "GPS", tint = Color.White, modifier = Modifier.size(26.dp).clickable { })
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(28.dp).clickable { })
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(28.dp).clickable { })
            }
        }
    }
}

@Composable
fun LayarUtama(
    statusText: String,
    modifier: Modifier = Modifier,
    onCategoryClick: (String) -> Unit
) {
    val categories = listOf(
        MenuCategory("Baru-baru ini diumumkan", Icons.Default.Refresh),
        MenuCategory("Mengangkut", Icons.Default.Place),
        MenuCategory("Bank dan ATM", Icons.Default.Info),
        MenuCategory("Kesehatan", Icons.Default.Add),
        MenuCategory("Makanan", Icons.Default.Call),
        MenuCategory("Toko", Icons.Default.ShoppingCart),
        MenuCategory("Seni dan hiburan", Icons.Default.Star),
        MenuCategory("Bangunan umum", Icons.Default.Home),
        MenuCategory("Fasilitas pendidikan", Icons.Default.DateRange),
        MenuCategory("Pub dan klub", Icons.Default.Build),
        MenuCategory("Penginapan", Icons.Default.Place)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEFEFEF))
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF9E9E9E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location Pin",
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Jalan Haji Oemar Said Cokroaminoto, Sinjai, Sulawesi Selatan, Sulawesi",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "14 kaki",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status teks layanan navigasi suara
        Text(
            text = statusText,
            color = Color.DarkGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(categories) { category ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onCategoryClick(category.title) }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD32F2F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = category.title,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = category.title,
                        color = Color.Black,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsAudioSection() {
    val context = LocalContext.current

    var speechRate by remember { mutableStateOf(1.25f) }
    var engineName by remember { mutableStateOf("Layanan Ucapan by Google") }
    var unitSystem by remember { mutableStateOf("Imperial") }
    var announcementFormat by remember { mutableStateOf("Arah relatif") }
    var isBluetoothEnabled by remember { mutableStateOf(true) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showUnitDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Audio",
            color = Color(0xFF00796B),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SettingItem(
            title = "Kecepatan bicara",
            subtitle = "${speechRate}x",
            onClick = { showSpeedDialog = true }
        )

        SettingItem(
            title = "Mesin suara",
            subtitle = engineName,
            onClick = {
                val intent = Intent("com.android.settings.TTS_SETTINGS")
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pengumuman suara",
            color = Color(0xFF00796B),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SettingItem(
            title = "Satuan ukuran",
            subtitle = unitSystem,
            onClick = { showUnitDialog = true }
        )

        SettingItem(
            title = "Format pengumuman",
            subtitle = announcementFormat,
            onClick = { showFormatDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Konektivitas",
            color = Color(0xFF00796B),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SettingItem(
            title = "Nyalakan Bluetooth di tempat yang mudah dijangkau",
            subtitle = if (isBluetoothEnabled) "Diaktifkan" else "Dinonaktifkan",
            onClick = {
                isBluetoothEnabled = !isBluetoothEnabled
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                context.startActivity(intent)
            }
        )
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Pilih Kecepatan Bicara") },
            text = {
                Column {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    speechRate = rate
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (speechRate == rate),
                                onClick = {
                                    speechRate = rate
                                    showSpeedDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${rate}x")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Batal") }
            }
        )
    }

    if (showUnitDialog) {
        AlertDialog(
            onDismissRequest = { showUnitDialog = false },
            title = { Text("Pilih Satuan Ukuran") },
            text = {
                Column {
                    listOf("Imperial (Kaki / Mil)", "Metrik (Meter / KM)").forEach { unit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    unitSystem = if (unit.startsWith("Imperial")) "Imperial" else "Metrik"
                                    showUnitDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = unitSystem.startsWith(unit.take(5)),
                                onClick = {
                                    unitSystem = if (unit.startsWith("Imperial")) "Imperial" else "Metrik"
                                    showUnitDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(unit)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnitDialog = false }) { Text("Batal") }
            }
        )
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Pilih Format Pengumuman") },
            text = {
                Column {
                    listOf("Arah relatif", "Arah jarum jam").forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    announcementFormat = format
                                    showFormatDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (announcementFormat == format),
                                onClick = {
                                    announcementFormat = format
                                    showFormatDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(format)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFormatDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Text(text = title, fontSize = 16.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = subtitle, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Halaman $title", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LazarusBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Explore, contentDescription = "Eksplorasi") },
            label = { Text("Eksplorasi") },
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFD32F2F),
                selectedTextColor = Color(0xFFD32F2F),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Star, contentDescription = "Favorit") },
            label = { Text("Favorit") },
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFD32F2F),
                selectedTextColor = Color(0xFFD32F2F),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Notifications, contentDescription = "Berita") },
            label = { Text("Berita") },
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFD32F2F),
                selectedTextColor = Color(0xFFD32F2F),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
            label = { Text("Pengaturan") },
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFD32F2F),
                selectedTextColor = Color(0xFFD32F2F),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
    }
}