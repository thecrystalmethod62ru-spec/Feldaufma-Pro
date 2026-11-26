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
import org.json.JSONArray
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
                var extras by remember { mutableStateOf(listOf<Extra>()) }
                var kind by remember { mutableStateOf("Suchschachtung") }
                var station by remember { mutableStateOf("") }
                var el by remember { mutableStateOf("") }
                var ew by remember { mutableStateOf("") }
                var ed by remember { mutableStateOf("") }
                var note by remember { mutableStateOf("") }
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
                        Text("Zusatzleistungen / Abschnitte", style = MaterialTheme.typography.titleLarge)
                        val types = listOf("Suchschachtung", "Hausanschluss", "Startgrube", "Zielgrube", "Handschachtung", "Verbau", "Großpflaster", "Gehwegplatten", "KVS", "Kernbohrung", "Hindernis")
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }) { Text(kind) }
                            DropdownMenu(expanded, { expanded = false }) {
                                types.forEach { type -> DropdownMenuItem(text = { Text(type) }, onClick = { kind = type; expanded = false }) }
                            }
                        }
                        OutlinedTextField(station, { station = it }, label = { Text("Station (m)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(el, { el = it }, label = { Text("Länge (m), optional") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(ew, { ew = it }, label = { Text("Breite (m), optional") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(ed, { ed = it }, label = { Text("Tiefe / Stärke (m), optional") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(note, { note = it }, label = { Text("Bemerkung / DN / Richtung") }, modifier = Modifier.fillMaxWidth())
                        Button(enabled = number(station) != null && l != null && (number(station) ?: 0.0) <= l && listOf(el, ew, ed).all { it.isBlank() || number(it) != null }, onClick = {
                            extras = extras + Extra(kind, number(station)!!, number(el), number(ew), number(ed), note)
                            station = ""; el = ""; ew = ""; ed = ""; note = ""
                        }) { Text("Position hinzufügen") }
                        extras.forEachIndexed { index, item ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(item.kind + " · Station " + fmt(item.station) + " m")
                                    Text(item.note)
                                    item.area()?.let { Text("Fläche: " + fmt(it) + " m²") }
                                    item.volume()?.let { Text("Volumen: " + fmt(it) + " m³") }
                                    TextButton(onClick = { extras = extras.filterIndexed { i, _ -> i != index } }) { Text("Entfernen") }
                                }
                            }
                        }
                        Text("Zusatzmengen separat; Überschneidungen nicht automatisch abgezogen.")
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
                            val data = JSONObject().put("l", length).put("w", width).put("d", depth).put("s", surface).put("a", asphalt).put("extras", JSONArray().apply { extras.forEach { put(it.json()) } })
                            prefs.edit().putString(name.trim(), data.toString()).apply()
                            projects = prefs.all.keys.sorted()
                            message = "Projekt gespeichert"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Projekt speichern") }
                        OutlinedButton(onClick = { name = ""; length = ""; width = ""; depth = ""; surface = ""; asphalt = ""; extras = emptyList(); message = "" }) { Text("Neues Projekt") }
                        Text(message)
                        Text("Gespeicherte Projekte", style = MaterialTheme.typography.titleLarge)
                        projects.forEach { key ->
                            OutlinedButton(onClick = {
                                val obj = JSONObject(prefs.getString(key, "{}") ?: "{}")
                                name = key; length = obj.optString("l"); width = obj.optString("w"); depth = obj.optString("d"); surface = obj.optString("s"); asphalt = obj.optString("a"); extras = obj.optJSONArray("extras")?.let { arr -> (0 until arr.length()).map { Extra.read(arr.getJSONObject(it)) } } ?: emptyList(); message = "Projekt geladen"
                            }, modifier = Modifier.fillMaxWidth()) { Text(key) }
                        }
                    }
                }
            }
        }
    }
}

private data class Extra(val kind: String, val station: Double, val length: Double?, val width: Double?, val depth: Double?, val note: String) {
    fun area(): Double? = if (length != null && width != null) length * width else null
    fun volume(): Double? = if (depth != null) area()?.times(depth) else null
    fun json() = JSONObject().put("kind", kind).put("station", station).put("length", length ?: JSONObject.NULL).put("width", width ?: JSONObject.NULL).put("depth", depth ?: JSONObject.NULL).put("note", note)
    companion object {
        fun read(o: JSONObject): Extra {
            fun n(k: String) = if (o.isNull(k)) null else o.getDouble(k)
            return Extra(o.getString("kind"), o.getDouble("station"), n("length"), n("width"), n("depth"), o.optString("note"))
        }
    }
}
