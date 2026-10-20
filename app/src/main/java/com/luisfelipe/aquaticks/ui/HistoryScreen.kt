package com.luisfelipe.aquaticks.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.luisfelipe.aquaticks.data.DaySnapshot
import com.luisfelipe.aquaticks.data.Intake
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HistoryScreen(model: WaterViewModel, onBack: () -> Unit) {
    val today = model.repository.today()
    var day by remember { mutableStateOf(today) }
    val flow = remember(day) { model.repository.observe(day) }
    val history: DaySnapshot? by flow.collectAsStateWithLifecycle(initialValue = null)
    ScrollPage("history") {
        PageTitle("Histórico", onBack)
        Text(if (day == today) "Hoje" else day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            color = Gray, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Action("‹ Dia") { day = day.minusDays(1) } }
            Box(Modifier.weight(1f)) { Action("Dia ›", enabled = day < today) { day = day.plusDays(1) } }
        }
        val snapshot = history?.takeIf { it.day == day }
        if (snapshot == null) {
            Text("Carregando…", color = Gray)
        } else {
            Text("${formatMl(snapshot.totalMl)} ml", color = Color.White, fontSize = 24.sp)
            Text("de ${formatMl(snapshot.goalMl)} ml · ${(snapshot.progress * 100).toInt()}%",
                color = Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
            IntakeBars(snapshot.intakes)
            if (snapshot.intakes.isEmpty()) Text("Sem registros", color = Gray, fontSize = 15.sp)
            for (entry in snapshot.intakes) {
                val time = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.of(entry.zoneId))
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                Text("$time   +${entry.amountMl} ml", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}
@Composable
private fun IntakeBars(entries: List<Intake>) {
    val amounts = remember(entries) {
        IntArray(12).also { bins ->
            for (entry in entries) {
                val hour = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.of(entry.zoneId)).hour
                bins[hour / 2] += entry.amountMl
            }
        }
    }
    val description = amounts.mapIndexedNotNull { i, amount ->
        if (amount > 0) "${i * 2} horas: $amount mililitros" else null
    }.joinToString(". ").ifEmpty { "Nenhum consumo registrado" }
    Canvas(Modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = description }) {
        val slot = size.width / 12
        val width = slot * .45f
        val max = (amounts.maxOrNull() ?: 0).coerceAtLeast(1)
        amounts.forEachIndexed { i, amount ->
            val x = slot * i + (slot - width) / 2
            drawRoundRect(Surface, Offset(x, 0f), Size(width, size.height), CornerRadius(width / 2))
            if (amount > 0) {
                val height = size.height * amount / max
                drawRoundRect(Blue, Offset(x, size.height - height), Size(width, height), CornerRadius(width / 2))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0h", color = Gray, fontSize = 11.sp)
        Text("12h", color = Gray, fontSize = 11.sp)
        Text("24h", color = Gray, fontSize = 11.sp)
    }
}
