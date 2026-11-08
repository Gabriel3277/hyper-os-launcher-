@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.hyperos.ultimatelauncher

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

class AppItem(
    val label: String,
    val pkg: String,
    val activity: String,
    val icon: ImageBitmap
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var hidden by mutableStateOf(sp.getStringSet("hidden", emptySet())!!.toSet())
        private set
    var columns by mutableIntStateOf(sp.getInt("columns", 4))
        private set
    var iconSize by mutableIntStateOf(sp.getInt("iconSize", 52))
        private set
    var spacing by mutableIntStateOf(sp.getInt("spacing", 12))
        private set
    var glass by mutableFloatStateOf(sp.getFloat("glass", 0.18f))
        private set
    var dark by mutableStateOf(sp.getBoolean("dark", true))
        private set

    fun setHidden(pkg: String, hide: Boolean) {
        hidden = if (hide) hidden + pkg else hidden - pkg
        sp.edit().putStringSet("hidden", hidden).apply()
    }
    fun setColumns(v: Int) { columns = v; sp.edit().putInt("columns", v).apply() }
    fun setIconSize(v: Int) { iconSize = v; sp.edit().putInt("iconSize", v).apply() }
    fun setSpacing(v: Int) { spacing = v; sp.edit().putInt("spacing", v).apply() }
    fun setGlass(v: Float) { glass = v; sp.edit().putFloat("glass", v).apply() }
    fun setDark(v: Boolean) { dark = v; sp.edit().putBoolean("dark", v).apply() }
}

class MainActivity : ComponentActivity() {
    private var reload by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val prefs = remember { Prefs(ctx) }
            var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
            LaunchedEffect(reload) {
                apps = withContext(Dispatchers.Default) { loadApps(ctx) }
            }
            MaterialTheme(
                colorScheme = if (prefs.dark) darkColorScheme() else lightColorScheme()
            ) {
                Home(apps, prefs)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload++
    }
}

private fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.activityInfo.name,
                it.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
            )
        }
        .sortedBy { it.label.lowercase() }
}

private fun openApp(ctx: Context, view: View, app: AppItem) {
    val i = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.pkg, app.activity)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try {
        val opts = ActivityOptions.makeScaleUpAnimation(
            view, view.width / 2, view.height / 2, 0, 0
        )
        ctx.startActivity(i, opts.toBundle())
    } catch (e: ActivityNotFoundException) {
    }
}

private fun appInfo(ctx: Context, pkg: String) {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun uninstall(ctx: Context, pkg: String) {
    ctx.startActivity(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
fun AppIcon(
    app: AppItem,
    size: Int,
    ink: Color,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "press"
    )
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
                onLongClick = onLong
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp))
        )
        Spacer(Modifier.height(4.dp))
        Text(
            app.label, color = ink, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun Header(ink: Color, glass: Color) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            now = Date()
            battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val loc = Locale.getDefault()
    val time = SimpleDateFormat("HH:mm", loc).format(now)
    val weekday = SimpleDateFormat("EEEE", loc).format(now)
    val day = SimpleDateFormat("d", loc).format(now)
    val month = SimpleDateFormat("MMMM", loc).format(now)

    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(time, color = ink, fontSize = 64.sp, fontWeight = FontWeight.Medium)
        Text("$weekday, $day $month", color = ink.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                    .background(glass).padding(14.dp)
            ) {
                Column {
                    Text("Bateria", color = ink.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text("$battery%", color = ink, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { battery / 100f },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = Color(0xFF97C459),
                        trackColor = ink.copy(alpha = 0.2f)
                    )
                }
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                    .background(glass).padding(14.dp)
            ) {
                Column {
                    Text("Calendário", color = ink.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(day, color = ink, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(month, color = ink.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun Home(all: List<AppItem>, prefs: Prefs) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val ink = if (prefs.dark) Color.White else Color(0xFF1B1B1F)
    val glass = if (prefs.dark) Color.White.copy(alpha = prefs.glass)
    else Color.White.copy(alpha = (prefs.glass + 0.35f).coerceAtMost(0.8f))

    val visible = all.filter { it.pkg !in prefs.hidden }
    val dock = visible.take(4)
    val rest = visible.drop(4)
    val cols = prefs.columns
    val first = rest.take(cols * 3)
    val pages = listOf(first) + rest.drop(first.size).chunked(cols * 5)
    val pager = rememberPagerState { pages.size }

    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
            .background(if (prefs.dark) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.25f))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "Ajustes", color = ink, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(glass)
                    .clickable { showSettings = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        HorizontalPager(pager, Modifier.weight(1f)) { p ->
            AppPage(p, pager, pages.getOrElse(p) { emptyList() }, prefs, ink, glass,
                onOpen = { openApp(ctx, view, it) }, onLong = { menuApp = it })
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { i ->
                val on = pager.currentPage == i
                Box(
                    Modifier.padding(3.dp)
                        .size(width = if (on) 14.dp else 5.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = if (on) 1f else 0.4f))
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(glass)
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dock.forEach {
                AppIcon(it, prefs.iconSize - 4, ink,
                    { openApp(ctx, view, it) }, { menuApp = it })
            }
        }
    }

    menuApp?.let { app ->
        AlertDialog(
            onDismissRequest = { menuApp = null },
            title = { Text(app.label) },
            text = {
                Column {
                    TextButton({ prefs.setHidden(app.pkg, true); menuApp = null }) {
                        Text("Esconder app")
                    }
                    TextButton({ appInfo(ctx, app.pkg); menuApp = null }) {
                        Text("Informações do app")
                    }
                    TextButton({ uninstall(ctx, app.pkg); menuApp = null }) {
                        Text("Desinstalar")
                    }
                }
            },
            confirmButton = { TextButton({ menuApp = null }) { Text("Cancelar") } }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Ajustes do launcher") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Colunas: ${prefs.columns}")
                    Slider(
                        prefs.columns.toFloat(), { prefs.setColumns(it.roundToInt()) },
                        valueRange = 3f..6f, steps = 2
                    )
                    Text("Tamanho dos ícones: ${prefs.iconSize}")
                    Slider(
                        prefs.iconSize.toFloat(), { prefs.setIconSize(it.roundToInt()) },
                        valueRange = 40f..68f
                    )
                    Text("Espaçamento: ${prefs.spacing}")
                    Slider(
                        prefs.spacing.toFloat(), { prefs.setSpacing(it.roundToInt()) },
                        valueRange = 4f..28f
                    )
                    Text("Transparência do vidro")
                    Slider(prefs.glass, { prefs.setGlass(it) }, valueRange = 0.05f..0.5f)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tema escuro")
                        Switch(prefs.dark, { prefs.setDark(it) })
                    }
                    TextButton({ showSettings = false; showHidden = true }) {
                        Text("Apps ocultos (${prefs.hidden.size})")
                    }
                }
            },
            confirmButton = { TextButton({ showSettings = false }) { Text("Fechar") } }
        )
    }

    if (showHidden) {
        AlertDialog(
            onDismissRequest = { showHidden = false },
            title = { Text("Apps ocultos") },
            text = {
                val list = all.filter { it.pkg in prefs.hidden }
                if (list.isEmpty()) {
                    Text("Nenhum app oculto.")
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        list.forEach { a ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(a.label, Modifier.weight(1f))
                                TextButton({ prefs.setHidden(a.pkg, false) }) { Text("Mostrar") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showHidden = false }) { Text("Fechar") } }
        )
    }
}

@Composable
fun AppPage(
    p: Int,
    pager: PagerState,
    apps: List<AppItem>,
    prefs: Prefs,
    ink: Color,
    glass: Color,
    onOpen: (AppItem) -> Unit,
    onLong: (AppItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(prefs.columns),
        modifier = Modifier.graphicsLayer {
            val o = abs((pager.currentPage - p) + pager.currentPageOffsetFraction)
                .coerceIn(0f, 1f)
            alpha = 1f - o * 0.6f
            scaleX = 1f - o * 0.1f
            scaleY = 1f - o * 0.1f
        },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(prefs.spacing.dp)
    ) {
        if (p == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header(ink, glass) }
        }
        items(apps, key = { it.pkg + it.activity }) { app ->
            AppIcon(app, prefs.iconSize, ink, { onOpen(app) }, { onLong(app) })
        }
    }
}
