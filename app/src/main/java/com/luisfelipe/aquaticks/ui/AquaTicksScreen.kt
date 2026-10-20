package com.luisfelipe.aquaticks.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import com.luisfelipe.aquaticks.R
import com.luisfelipe.aquaticks.aquaApp
import com.luisfelipe.aquaticks.data.DaySnapshot
import com.luisfelipe.aquaticks.data.Settings
import com.luisfelipe.aquaticks.domain.WaterRules
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val Blue = Color(0xFF458FFF)
internal val Gray = Color(0xFFB5BAC3)
internal val Surface = Color(0xFF181B20)

@Composable
fun AquaTicksScreen(model: WaterViewModel) {
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf("home") }
    val homeRequest by model.homeRequests.collectAsStateWithLifecycle()
    LaunchedEffect(homeRequest) { screen = "home" }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var clockRevision by remember { mutableIntStateOf(0) }
    var notificationAllowed by remember { mutableStateOf(context.aquaApp.reminders.allowed()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationAllowed = granted
        model.update { it.copy(remindersEnabled = granted) }
    }
    val haptics = LocalHapticFeedback.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                model.refresh()
                clockRevision++
                notificationAllowed = context.aquaApp.reminders.allowed()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { clockRevision++; model.refresh() }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    // One timer while the UI is active, never a background polling loop.
    val active by lifecycle.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(snapshot?.day, active, clockRevision) {
        if (active.isAtLeast(Lifecycle.State.RESUMED)) {
            delay(WaterRules.untilMidnight(Instant.now(), ZoneId.systemDefault()) + 50)
            model.refresh()
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            if (message!!.startsWith("Boa!")) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(if (message!!.startsWith("Boa!")) 1300 else 3500)
            model.dismissMessage()
        }
    }
    BackHandler(screen != "home") { screen = "home" }
    MaterialTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val ringSize = minOf(maxWidth * .80f, maxHeight * .80f)
            if (snapshot == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("AquaTicks", color = Gray) }
            } else {
                when (screen) {
                    "home" -> ScrollPage("home") {
                            HomeRing(snapshot!!, ringSize, !busy) {
                                model.add(snapshot!!.settings.defaultMl)
                            }
                            Action("Outra quantidade") { screen = "amount" }
                            Action("Histórico") { screen = "history" }
                            Action("Configurações") { screen = "settings" }
                    }
                    "history" -> HistoryScreen(model) { screen = "home" }
                    "amount" -> ScrollPage("amount") {
                        PageTitle("Registrar água") { screen = "home" }
                        for (amount in WaterRules.amounts) {
                            Action("+$amount ml", enabled = !busy) {
                                model.add(amount)
                                screen = "home"
                            }
                        }
                    }
                    "dose" -> ScrollPage("dose") {
                        PageTitle("Dose padrão") { screen = "settings" }
                        for (amount in WaterRules.amounts) {
                            Action("${if (snapshot!!.settings.defaultMl == amount) "✓ " else ""}$amount ml",
                                primary = snapshot!!.settings.defaultMl == amount) {
                                model.update { it.copy(defaultMl = amount) }
                            }
                        }
                    }
                    "settings" -> ScrollPage("settings") {
                        PageTitle("Configurações") { screen = "home" }
                        GoalControl(snapshot!!.settings.goalMl) { delta ->
                            model.update { it.copy(goalMl = (it.goalMl + delta)
                                .coerceIn(WaterRules.MIN_GOAL, WaterRules.MAX_GOAL)) }
                        }
                        val settings = snapshot!!.settings
                        Action(if (settings.remindersEnabled) "Lembretes: ligados" else "Lembretes: desligados") {
                            context.aquaApp.reminders.createChannel()
                            notificationAllowed = context.aquaApp.reminders.allowed()
                            when {
                                settings.remindersEnabled -> model.update { it.copy(remindersEnabled = false) }
                                Build.VERSION.SDK_INT >= 33 && !notificationAllowed ->
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> model.update { it.copy(remindersEnabled = true) }
                            }
                        }
                        if (!notificationAllowed) Text(
                            "Permita notificações para receber lembretes.", color = Gray,
                            textAlign = TextAlign.Center, fontSize = 13.sp)
                        if (!notificationAllowed) Action("Permissão de notificações") {
                            context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                        Action("A cada ${settings.intervalHours} h") { screen = "interval" }
                        Action("Horário silencioso") { screen = "quiet" }
                        Action("Dose padrão: ${settings.defaultMl} ml") { screen = "dose" }
                    }
                    "interval" -> ScrollPage("interval") {
                        PageTitle("Intervalo") { screen = "settings" }
                        for (hours in WaterRules.intervals) {
                            val selected = snapshot!!.settings.intervalHours == hours
                            Action("${if (selected) "✓ " else ""}$hours ${if (hours == 1) "hora" else "horas"}",
                                primary = selected) {
                                model.update { it.copy(intervalHours = hours) }
                            }
                        }
                    }
                    "quiet" -> ScrollPage("quiet") {
                        PageTitle("Silêncio") { screen = "settings" }
                        val settings = snapshot!!.settings
                        Action(if (settings.quietEnabled) "Silêncio: ligado" else "Silêncio: desligado") {
                            model.update { it.copy(quietEnabled = !it.quietEnabled) }
                        }
                        TimeControl("Início", settings.quietStart) { delta ->
                            model.update { it.copy(quietStart = (it.quietStart + delta + 1440) % 1440) }
                        }
                        TimeControl("Fim", settings.quietEnd) { delta ->
                            model.update { it.copy(quietEnd = (it.quietEnd + delta + 1440) % 1440) }
                        }
                        Text("Ajuste em passos de 30 min. Horários iguais desativam o silêncio.",
                            fontSize = 12.sp, color = Gray, textAlign = TextAlign.Center)
                    }
                }
            }
            TimeText()
            if (message != null) {
                Box(Modifier.fillMaxSize().background(Color.Black)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Blue),
                            contentAlignment = Alignment.Center) {
                            Text(if (message!!.startsWith("Boa!")) "✓" else "!", color = Color.Black, fontSize = 36.sp)
                        }
                        Text(message!!, color = Color.White, textAlign = TextAlign.Center, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
@Composable
internal fun ScrollPage(key: String, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(key) { focus.requestFocus() }
    Column(
        Modifier.fillMaxSize().onRotaryScrollEvent {
            scope.launch { scroll.scrollBy(it.verticalScrollPixels) }; true
        }.focusRequester(focus).focusable()
            .verticalScroll(scroll).padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}
@Composable
internal fun Action(label: String, primary: Boolean = false, enabled: Boolean = true,
    description: String = label, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = description },
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) Blue else Surface,
            contentColor = if (primary) Color.Black else Color.White),
        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) })
}
@Composable
internal fun PageTitle(title: String, back: () -> Unit) {
    Action("‹ Voltar", onClick = back)
    Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Color.White,
        modifier = Modifier.semantics { heading() })
}
@Composable
internal fun GoalControl(goal: Int, change: (Int) -> Unit) {
    Text("Meta diária", color = Gray, fontSize = 15.sp)
    Text("${formatMl(goal)} ml", color = Color.White, fontSize = 23.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { Action("−", enabled = goal > WaterRules.MIN_GOAL, description = "Diminuir meta em 250 mililitros") { change(-250) } }
        Box(Modifier.weight(1f)) { Action("+", enabled = goal < WaterRules.MAX_GOAL, description = "Aumentar meta em 250 mililitros") { change(250) } }
    }
}
@Composable
internal fun TimeControl(label: String, minutes: Int, change: (Int) -> Unit) {
    Text(label, color = Gray)
    Text("%02d:%02d".format(minutes / 60, minutes % 60), color = Color.White, fontSize = 24.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { Action("−30 min") { change(-30) } }
        Box(Modifier.weight(1f)) { Action("+30 min") { change(30) } }
    }
}
@Composable
internal fun HomeRing(snapshot: DaySnapshot, size: Dp, enabled: Boolean, onAdd: () -> Unit) {
    val animatedProgress by animateFloatAsState(snapshot.progress, tween(300), label = "daily_progress")
    Box(Modifier.size(size).clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button,
            onClickLabel = "Registrar ${snapshot.settings.defaultMl} mililitros", onClick = onAdd)
        .semantics(mergeDescendants = true) {
        contentDescription = "${snapshot.totalMl} de ${snapshot.goalMl} mililitros. ${(snapshot.progress * 100).toInt()} por cento."
        progressBarRangeInfo = ProgressBarRangeInfo(snapshot.progress, 0f..1f)
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val diameter = this.size.minDimension - stroke
            val offset = Offset((this.size.width - diameter) / 2, (this.size.height - diameter) / 2)
            drawArc(Color(0xFF18212D), -90f, 360f, false, offset,
                Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
            if (animatedProgress > 0f) drawArc(Blue, -90f, animatedProgress * 360f, false,
                offset, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(13.dp).clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_drop), contentDescription = null, tint = Blue,
                modifier = Modifier.size(25.dp))
            Text("${formatMl(snapshot.totalMl)} ml", color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text("de ${formatMl(snapshot.goalMl)} ml", color = Gray, fontSize = 12.sp)
            Text("+${snapshot.settings.defaultMl} ml", color = Blue, fontSize = 15.sp,
                fontWeight = FontWeight.Medium)
        }
    }
}
internal fun formatMl(value: Int): String = NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("pt-BR")).format(value)
