package com.hyperos.ultimatelauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppItem(
    val label: String,
    val pkg: String,
    val activity: String,
    val icon: ImageBitmap
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
            LaunchedEffect(Unit) {
                apps = withContext(Dispatchers.Default) { loadApps(ctx) }
            }
            Home(apps)
        }
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

private fun launch(ctx: Context, app: AppItem) {
    val i = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.pkg, app.activity)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    ctx.startActivity(i)
}

@Composable
fun AppIcon(app: AppItem, size: Int = 52) {
    val ctx = LocalContext.current
    Column(
        Modifier.clickable { launch(ctx, app) }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(4.dp))
        Text(
            app.label, color = Color.White, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun Home(apps: List<AppItem>) {
    val dock = apps.take(4)
    val pages = apps.drop(4).chunked(20).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState { pages.size }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF1E2638))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        HorizontalPager(pager, Modifier.weight(1f)) { p ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pages[p]) { AppIcon(it) }
            }
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
                        .background(Color.White.copy(alpha = if (on) 1f else 0.4f))
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dock.forEach { AppIcon(it, 48) }
        }
    }
}
