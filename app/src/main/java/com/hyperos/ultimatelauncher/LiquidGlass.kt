package com.seuapp.launcher.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

// Liquid Glass: borda curva que refrata o fundo, aberração cromática leve,
// brilho de borda e tinta clara. Requer Android 13+ (RuntimeShader/AGSL).
private const val LIQUID_GLASS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float refraction;
uniform float chroma;
uniform float tint;

float sdf(float2 p) {
    float2 c = size * 0.5;
    float2 q = abs(p - c) - (c - float2(radius));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

half4 main(float2 p) {
    float d = sdf(p);
    if (d > 0.0) return half4(0.0);

    float2 g = float2(
        sdf(p + float2(1.0, 0.0)) - sdf(p - float2(1.0, 0.0)),
        sdf(p + float2(0.0, 1.0)) - sdf(p - float2(0.0, 1.0))
    );
    float2 n = normalize(g + float2(0.0001));

    float edge = 1.0 - clamp(-d / bevel, 0.0, 1.0);
    float bend = edge * edge * edge;
    float2 off = -n * bend * refraction;

    half4 col;
    col.r = content.eval(p + off * (1.0 + chroma)).r;
    col.g = content.eval(p + off).g;
    col.b = content.eval(p + off * (1.0 - chroma)).b;
    col.a = 1.0;

    col.rgb = mix(col.rgb, half3(1.0), tint);

    float2 light = normalize(float2(-0.6, -0.8));
    float rim = pow(edge, 4.0);
    float spec = max(dot(n, light), 0.0) * rim;
    float shade = max(dot(n, -light), 0.0) * rim;
    col.rgb += half3(spec * 0.55) - half3(shade * 0.12);

    float border = smoothstep(2.0, 0.0, -d);
    col.rgb += half3(border * 0.18);
    return col;
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun LiquidGlass(
    wallpaper: ImageBitmap,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    bevel: Dp = 22.dp,
    refraction: Dp = 16.dp,
    blurRadius: Float = 6f,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val metrics = LocalContext.current.resources.displayMetrics
    var pos by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val shader = remember { RuntimeShader(LIQUID_GLASS_AGSL) }

    val effect = remember(size, enabled, blurRadius) {
        if (!enabled || size.width == 0) return@remember null
        shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("radius", with(density) { cornerRadius.toPx() })
        shader.setFloatUniform("bevel", with(density) { bevel.toPx() })
        shader.setFloatUniform("refraction", with(density) { refraction.toPx() })
        shader.setFloatUniform("chroma", 0.06f)
        shader.setFloatUniform("tint", 0.10f)
        val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
        RenderEffect.createChainEffect(glass, blur).asComposeRenderEffect()
    }

    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .onGloballyPositioned {
                pos = it.positionInRoot()
                size = it.size
            }
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { renderEffect = effect }
        ) {
            drawImage(
                image = wallpaper,
                dstOffset = IntOffset(-pos.x.toInt(), -pos.y.toInt()),
                dstSize = IntSize(metrics.widthPixels, metrics.heightPixels)
            )
        }
        content()
    }
}
