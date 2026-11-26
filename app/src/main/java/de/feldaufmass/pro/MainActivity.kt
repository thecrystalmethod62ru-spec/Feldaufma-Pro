package de.feldaufmass.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("aufmass", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF174C65))) {
                var name by remember { mutableStateOf("") }
                var length by remember { mutableStateOf("") }
                var width by remember { mutableStateOf("") }
                var depth by remember { mutableStateOf("") }
                var surface by remember { mutableStateOf("") }
                var asphalt by remember { mutableStateOf("") }
                var message by remember { mutableStateOf("") }
                var projects by remember { mutableStateOf(prefs.all.keys.sorted()) }
                fun number(s: String) = s.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
                fun fmt(v: Double) = String.format(Locale.GERMANY, "%.2f", v)
                val l = number(length)
                val w = number(width)
                val d = number(depth)
                val s = number(surface)
                val a = number(asphalt)
                val valid = l != null && w != null && d != null && s != null && a != null && l > 0 && w > 0 && d > 0 && s >= w && a <= d
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Feldaufmaß Pro", style = MaterialTheme.typography.headlineMedium)
                        Text("Leitungsbau · Digitales Aufmaß")
                        OutlinedTextField(name, { name = it }, label = { Text("Projekt / Baustelle") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text("Graben und Oberfläche", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(length, { length = it }, label = { Text("Trassenlänge (m)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(width, { width = it }, label = { Text("Grabenbreite (m)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(depth, { depth = it }, label = { Text("Grabentiefe ab OK Oberfläche (m)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(surface, { surface = it }, label = { Text("Aufbruchbreite inkl. Nachschnitt (m)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(asphalt, { asphalt = it }, label = { Text("Asphaltstärke (m)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Trassenskizze · nicht maßstäblich")
                        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                            drawRect(Color(0xFFD6DCE0), Offset(12f, size.height * .2f), Size(size.width - 24f, size.height * .6f))
                            drawRect(Color(0xFFB78C60), Offset(12f, size.height * .35f), Size(size.width - 24f, size.height * .3f))
                            drawLine(Color(0xFF174C65), Offset(12f, size.height / 2), Offset(size.width - 12f, size.height / 2), 3f)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0,00 m"); Text(fmt(l ?: 0.0) + " m") }
                        Text("Mengenberechnung", style = MaterialTheme.typography.titleLarge)
                        if (valid) {
                            val ll = l!!; val ww = w!!; val dd = d!!; val ss = s!!; val aa = a!!
                            Text("Grabenvolumen brutto: " + fmt(ll * ww * dd) + " m³")
                            Text("Oberflächenaufbruch: " + fmt(ll * ss) + " m²")
                            Text("Asphaltaufbruch: " + fmt(ll * ss * aa) + " m³")
                            Text("Verbau beidseitig (geometrisch): " + fmt(2 * ll * dd) + " m²")
                            Text("Geometrische Richtwerte, keine geprüfte Abrechnung.")
                        } else Text("Bitte gültige Maße eingeben. Aufbruchbreite ≥ Grabenbreite; Asphaltstärke ≤ Grabentiefe.")
                        Button(enabled = valid && name.isNotBlank(), onClick = {
                            val data = JSONObject().put("l", length).put("w", width).put("d", depth).put("s", surface).put("a", asphalt)
                            prefs.edit().putString(name.trim(), data.toString()).apply()
                            projects = prefs.all.keys.sorted()
                            message = "Projekt gespeichert"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Projekt speichern") }
                        OutlinedButton(onClick = { name = ""; length = ""; width = ""; depth = ""; surface = ""; asphalt = ""; message = "" }) { Text("Neues Projekt") }
                        Text(message)
                        Text("Gespeicherte Projekte", style = MaterialTheme.typography.titleLarge)
                        projects.forEach { key ->
                            OutlinedButton(onClick = {
                                val obj = JSONObject(prefs.getString(key, "{}") ?: "{}")
                                name = key; length = obj.optString("l"); width = obj.optString("w"); depth = obj.optString("d"); surface = obj.optString("s"); asphalt = obj.optString("a"); message = "Projekt geladen"
                            }, modifier = Modifier.fillMaxWidth()) { Text(key) }
                        }
                    }
                }
            }
        }
    }
}
