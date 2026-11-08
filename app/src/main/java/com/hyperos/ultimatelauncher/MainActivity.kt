@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.hyperos.ultimatelauncher

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

class AppItem(
    val label: String,
    val pkg: String,
    val activity: String,
    val icon: ImageBitmap
)

object IconCache {
    val map = ConcurrentHashMap<String, ImageBitmap>()
}

class GlassStyle(val alpha: Float, val liquid: Boolean)

private fun Color.al(a: Float) = copy(alpha = a.coerceIn(0f, 1f))

fun Modifier.glass(shape: Shape, g: GlassStyle): Modifier {
    val a = g.alpha
    return if (!g.liquid) {
        this.clip(shape).background(Color.White.al(a))
    } else {
        this.clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.al(a * 1.8f),
                        Color.White.al(a * 0.8f),
                        Color.White.al(a * 1.2f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.al(0.75f),
                        Color.White.al(0.08f),
                        Color.White.al(0.4f)
                    )
                ),
                shape
            )
    }
}

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
    var liquid by mutableStateOf(sp.getBoolean("liquid", true))
        private set
    var blur by mutableIntStateOf(sp.getInt("blurV2", 0))
        private set
    var pageFx by mutableStateOf(sp.getBoolean("pageFx", true))
        private set

    fun changeHidden(pkg: String, hide: Boolean) {
        hidden = if (hide) hidden + pkg else hidden - pkg
        sp.edit().putStringSet("hidden", hidden).apply()
    }
    fun changeColumns(v: Int) {
        columns = v
        sp.edit().putInt("columns", v).apply()
    }
    fun changeIconSize(v: Int) {
        iconSize = v
        sp.edit().putInt("iconSize", v).apply()
    }
    fun changeSpacing(v: Int) {
        spacing = v
        sp.edit().putInt("spacing", v).apply()
    }
    fun changeGlass(v: Float) {
        glass = v
        sp.edit().putFloat("glass", v).apply()
    }
    fun changeDark(v: Boolean) {
        dark = v
        sp.edit().putBoolean("dark", v).apply()
    }
    fun changeLiquid(v: Boolean) {
        liquid = v
        sp.edit().putBoolean("liquid", v).apply()
    }
    fun changeBlur(v: Int) {
        blur = v
        sp.edit().putInt("blurV2", v).apply()
    }
    fun changePageFx(v: Boolean) {
        pageFx = v
        sp.edit().putBoolean("pageFx", v).apply()
    }
}

class MainActivity : ComponentActivity() {
    private var reload by mutableIntStateOf(0)
    private var homeSignal by mutableIntStateOf(0)

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
            LaunchedEffect(prefs.blur) {
                window.setBackgroundBlurRadius(prefs.blur)
            }
            DisposableEffect(Unit) {
                val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                fun changed(pkg: String?) {
                    if (pkg != null) {
                        IconCache.map.keys.removeAll { it.startsWith("$pkg/") }
                    }
                    reload++
                }
                val cb = object : LauncherApps.Callback() {
                    override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                        changed(packageName)
                    }
                    override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                        changed(packageName)
                    }
                    override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                        changed(packageName)
                    }
                    override fun onPackagesAvailable(
                        packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean
                    ) {
                        packageNames?.forEach { changed(it) }
                    }
                    override fun onPackagesUnavailable(
                        packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean
                    ) {
                        packageNames?.forEach { changed(it) }
                    }
                }
                la.registerCallback(cb)
                onDispose { la.unregisterCallback(cb) }
            }

            MaterialTheme(
                colorScheme = if (prefs.dark) darkColorScheme() else lightColorScheme()
            ) {
                Home(apps, prefs, homeSignal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal++
    }
}

private fun roundedIcon(d: Drawable): ImageBitmap {
    val s = 144
    val pad = 12f
    val box = RectF(pad, pad, s - pad, s - pad)
    val r = (s - 2 * pad) * 0.3f
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)

    val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    shadow.color = 0xFFFFFFFF.toInt()
    shadow.setShadowLayer(9f, 0f, 5f, 0x66000000)
    c.drawRoundRect(box, r, r, shadow)

    val saved = c.save()
    val path = Path()
    path.addRoundRect(box, r, r, Path.Direction.CW)
    c.clipPath(path)
    d.setBounds(pad.toInt(), pad.toInt(), (s - pad).toInt(), (s - pad).toInt())
    d.draw(c)
    val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    gloss.shader = LinearGradient(
        0f, pad, 0f, s * 0.55f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
    )
    c.drawRect(box, gloss)
    c.restoreToCount(saved)

    val rim = Paint(Paint.ANTI_ALIAS_FLAG)
    rim.style = Paint.Style.STROKE
    rim.strokeWidth = 2f
    rim.color = 0x66FFFFFF
    c.drawRoundRect(box, r, r, rim)
    return bmp.asImageBitmap()
}

private fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            val key = it.activityInfo.packageName + "/" + it.activityInfo.name
            val icon = IconCache.map.getOrPut(key) { roundedIcon(it.loadIcon(pm)) }
            AppItem(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.activityInfo.name,
                icon
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
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size((size * 1.2f).dp)
        )
        Text(
            app.label, color = ink, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun Header(ink: Color, gs: GlassStyle) {
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
    val parts = remember(now) {
        val loc = Locale.getDefault()
        listOf("HH:mm", "EEEE", "d", "MMMM").map { SimpleDateFormat(it, loc).format(now) }
    }
    val (time, weekday, day, month) = parts

    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(time, color = ink, fontSize = 76.sp, fontWeight = FontWeight.Light)
        Text("$weekday, $day $month", color = ink.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f)
                    .glass(RoundedCornerShape(28.dp), gs)
                    .padding(16.dp)
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
                Modifier.weight(1f)
                    .glass(RoundedCornerShape(28.dp), gs)
                    .padding(16.dp)
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
fun PageDots(pager: PagerState, count: Int, ink: Color) {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { i ->
            val on = pager.currentPage == i
            Box(
                Modifier.padding(3.dp)
                    .size(width = if (on) 14.dp else 5.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = if (on) 1f else 0.4f))
            )
        }
    }
}

@Composable
fun Drawer(
    apps: List<AppItem>,
    prefs: Prefs,
    cols: Int,
    ink: Color,
    gs: GlassStyle,
    onOpen: (AppItem) -> Unit,
    onLong: (AppItem) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val grouped = remember(filtered) {
        filtered.groupBy {
            val c = it.label.firstOrNull()?.uppercaseChar()
            if (c != null && c.isLetter()) c else '#'
        }
    }
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val base = if (prefs.dark) Color(0xFF11141B) else Color(0xFFF2F3F7)
    val headTop = if (prefs.dark) Color.White.al(0.10f) else Color.White.al(0.7f)

    Column(
        Modifier.fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp)
            .imePadding()
            .clip(shape)
            .background(base)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(headTop, Color.Transparent)))
        ) {
            Box(
                Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 60f) onClose() },
                        onVerticalDrag = { _, dy -> total += dy }
                    )
                },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape).background(ink.al(0.4f))
                )
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Pesquisar apps", color = ink.al(0.6f)) },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = ink,
                    unfocusedTextColor = ink,
                    cursorColor = ink,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .glass(RoundedCornerShape(28.dp), GlassStyle(gs.alpha + 0.06f, gs.liquid))
            )
        }
        if (filtered.isEmpty()) {
            Text(
                "Nenhum app encontrado.", color = ink.al(0.7f),
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.weight(1f).navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(prefs.spacing.dp)
            ) {
                grouped.forEach { (letter, list) ->
                    item(key = "h$letter", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text(
                                letter.toString(), color = ink,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .glass(RoundedCornerShape(14.dp), gs)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                    items(list, key = { it.pkg + it.activity }) { app ->
                        AppIcon(app, prefs.iconSize, ink, { onOpen(app) }, { onLong(app) })
                    }
                }
            }
        }
    }
}

@Composable
fun Home(all: List<AppItem>, prefs: Prefs, homeTick: Int) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cols = if (land) prefs.columns * 2 else prefs.columns
    val ink = if (prefs.dark) Color.White else Color(0xFF1B1B1F)
    val glassAlpha = if (prefs.dark) prefs.glass
    else (prefs.glass + 0.35f).coerceAtMost(0.8f)
    val gs = GlassStyle(glassAlpha, prefs.liquid)

    val visible = remember(all, prefs.hidden) { all.filter { it.pkg !in prefs.hidden } }
    val dock = remember(visible) { visible.take(4) }
    val pages = remember(visible, cols, land) {
        val rest = visible.drop(4)
        val first = rest.take(cols * (if (land) 1 else 3))
        listOf(first) + rest.drop(first.size).chunked(cols * (if (land) 2 else 5))
    }
    val pager = rememberPagerState { pages.size }

    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }

    BackHandler { showDrawer = false }

    LaunchedEffect(homeTick) {
        showDrawer = false
        menuApp = null
        showSettings = false
        showHidden = false
        pager.animateScrollToPage(0)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .background(if (prefs.dark) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.25f))
                .statusBarsPadding().navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                Text(
                    "Apps", color = ink, fontSize = 12.sp,
                    modifier = Modifier
                        .glass(RoundedCornerShape(20.dp), gs)
                        .clickable { showDrawer = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Text(
                    "Ajustes", color = ink, fontSize = 12.sp,
                    modifier = Modifier
                        .glass(RoundedCornerShape(20.dp), gs)
                        .clickable { showSettings = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            HorizontalPager(pager, Modifier.weight(1f)) { p ->
                AppPage(p, pager, pages.getOrElse(p) { emptyList() }, cols, prefs, ink, gs,
                    onOpen = { openApp(ctx, view, it) }, onLong = { menuApp = it })
            }

            Column(
                Modifier.pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -60f) showDrawer = true },
                        onVerticalDrag = { _, dy -> total += dy }
                    )
                }
            ) {
                PageDots(pager, pages.size, ink)

                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(34.dp), gs)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    dock.forEach {
                        AppIcon(it, prefs.iconSize - 4, ink,
                            { openApp(ctx, view, it) }, { menuApp = it })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showDrawer,
            enter = slideInVertically(tween(280)) { it },
            exit = slideOutVertically(tween(220)) { it }
        ) {
            Drawer(
                visible, prefs, cols, ink, gs,
                onOpen = { openApp(ctx, view, it); showDrawer = false },
                onLong = { menuApp = it },
                onClose = { showDrawer = false }
            )
        }
    }

    menuApp?.let { app ->
        AlertDialog(
            onDismissRequest = { menuApp = null },
            title = { Text(app.label) },
            text = {
                Column {
                    TextButton({ prefs.changeHidden(app.pkg, true); menuApp = null }) {
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
                        prefs.columns.toFloat(), { prefs.changeColumns(it.roundToInt()) },
                        valueRange = 3f..6f, steps = 2
                    )
                    Text("Tamanho dos ícones: ${prefs.iconSize}")
                    Slider(
                        prefs.iconSize.toFloat(), { prefs.changeIconSize(it.roundToInt()) },
                        valueRange = 40f..68f
                    )
                    Text("Espaçamento: ${prefs.spacing}")
                    Slider(
                        prefs.spacing.toFloat(), { prefs.changeSpacing(it.roundToInt()) },
                        valueRange = 4f..28f
                    )
                    Text("Transparência do vidro")
                    Slider(prefs.glass, { prefs.changeGlass(it) }, valueRange = 0.05f..0.5f)
                    Text("Desfoque do fundo (pesado, 0 = desligado): ${prefs.blur}")
                    Slider(
                        prefs.blur.toFloat(), { prefs.changeBlur(it.roundToInt()) },
                        valueRange = 0f..80f
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Liquid Glass")
                        Switch(prefs.liquid, { prefs.changeLiquid(it) })
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Animação entre páginas")
                        Switch(prefs.pageFx, { prefs.changePageFx(it) })
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tema escuro")
                        Switch(prefs.dark, { prefs.changeDark(it) })
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
                                TextButton({ prefs.changeHidden(a.pkg, false) }) { Text("Mostrar") }
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
    cols: Int,
    prefs: Prefs,
    ink: Color,
    gs: GlassStyle,
    onOpen: (AppItem) -> Unit,
    onLong: (AppItem) -> Unit
) {
    val fx = if (prefs.pageFx) {
        Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
            val o = abs((pager.currentPage - p) + pager.currentPageOffsetFraction)
                .coerceIn(0f, 1f)
            alpha = 1f - o * 0.6f
            scaleX = 1f - o * 0.1f
            scaleY = 1f - o * 0.1f
        }
    } else {
        Modifier
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = fx,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(prefs.spacing.dp)
    ) {
        if (p == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header(ink, gs) }
        }
        items(apps, key = { it.pkg + it.activity }) { app ->
            AppIcon(app, prefs.iconSize, ink, { onOpen(app) }, { onLong(app) })
        }
    }
}
