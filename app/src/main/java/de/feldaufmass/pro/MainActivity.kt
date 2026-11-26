package de.feldaufmass.pro

import android.os.Bundle
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
                var photos by remember { mutableStateOf(listOf<SitePhoto>()) }
                var photoStation by remember { mutableStateOf("") }
                var photoNote by remember { mutableStateOf("") }
                var photoStatus by remember { mutableStateOf("") }
                val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                    if (bitmap != null) {
                        try {
                            val file = File(filesDir, UUID.randomUUID().toString() + ".jpg")
                            file.outputStream().use { stream ->
                                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream))
                            }
                            photos = photos + SitePhoto(file.name, photoStation, photoNote)
                            photoStatus = "Foto hinzugefügt. Bitte Projekt speichern."
                        } catch (e: Exception) { photoStatus = "Foto konnte nicht gespeichert werden." }
                    }
                }
                var gps by remember { mutableStateOf(listOf<GpsPoint>()) }
                var gpsStatus by remember { mutableStateOf("") }
                var gpsBusy by remember { mutableStateOf(false) }
                val manager = getSystemService(LOCATION_SERVICE) as LocationManager
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                    gpsStatus = if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) "GPS erlaubt. Erneut auf GPS-Punkt tippen." else "Für GPS bitte genauen Standort erlauben."
                }
                var points by remember { mutableStateOf(listOf<Offset>()) }
                var extras by remember { mutableStateOf(listOf<Extra>()) }
                var kind by remember { mutableStateOf("Suchschachtung") }
                var station by remember { mutableStateOf("") }
                var el by remember { mutableStateOf("") }
                var ew by remember { mutableStateOf("") }
                var ed by remember { mutableStateOf("") }
                var note by remember { mutableStateOf("") }
                var pendingExcel by remember { mutableStateOf<ByteArray?>(null) }
                val excelPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
                    if (uri != null) {
                        try {
                            val bytes = pendingExcel ?: error("Keine Excel-Daten")
                            contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Datei nicht verfügbar")
                            android.widget.Toast.makeText(this@MainActivity, "Excel gespeichert", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) { android.widget.Toast.makeText(this@MainActivity, "Excel-Export fehlgeschlagen", android.widget.Toast.LENGTH_LONG).show() }
                    }
                    pendingExcel = null
                }
                var pendingPdf by remember { mutableStateOf<ByteArray?>(null) }
                var exportStatus by remember { mutableStateOf("") }
                val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                    if (uri != null) {
                        try {
                            val bytes = pendingPdf ?: error("Keine PDF-Daten")
                            contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Datei nicht verfügbar")
                            exportStatus = "PDF gespeichert"
                        } catch (e: Exception) { exportStatus = "PDF konnte nicht gespeichert werden." }
                    }
                    pendingPdf = null
                }
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
                        Text("Auf die Zeichenfläche tippen, um Trassenpunkte zu setzen.")
                        Canvas(Modifier.fillMaxWidth().height(240.dp).pointerInput(Unit) {
                            detectTapGestures { tap ->
                                points = points + Offset((tap.x / size.width).coerceIn(0f, 1f), (tap.y / size.height).coerceIn(0f, 1f))
                            }
                        }) {
                            drawRect(Color(0xFFF0F2F3))
                            for (i in 1..9) {
                                drawLine(Color.LightGray, Offset(size.width*i/10, 0f), Offset(size.width*i/10, size.height), 1f)
                                drawLine(Color.LightGray, Offset(0f, size.height*i/10), Offset(size.width, size.height*i/10), 1f)
                            }
                            val route = points.map { Offset(it.x * size.width, it.y * size.height) }
                            route.zipWithNext().forEach { (a,b) ->
                                drawLine(Color(0xFFB78C60), a, b, 16f)
                                drawLine(Color(0xFF174C65), a, b, 3f)
                            }
                            route.forEach { drawCircle(Color(0xFF174C65), 6f, it) }
                            val segments = route.zipWithNext()
                            val total = segments.sumOf { (a,b) -> (b-a).getDistance().toDouble() }
                            if (total > 0 && l != null && l > 0) {
                                extras.filter { it.station <= l }.forEach { item ->
                                    var remaining = item.station / l * total
                                    for ((a,b) in segments) {
                                        val dist = (b-a).getDistance().toDouble()
                                        if (dist > 0 && remaining <= dist) {
                                            val pos = a + (b-a) * (remaining/dist).toFloat()
                                            drawCircle(Color(0xFFB84B35), 9f, pos)
                                            break
                                        }
                                        remaining -= dist
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = points.isNotEmpty(), onClick = { points = points.dropLast(1) }) { Text("Zurück") }
                            OutlinedButton(onClick = { points = emptyList() }) { Text("Skizze löschen") }
                        }
                        Text("Rote Punkte: Zusatzpositionen. Stationen werden proportional auf die Skizze verteilt.")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0,00 m"); Text(fmt(l ?: 0.0) + " m") }
                        Text("GPS-Aufmaß", style = MaterialTheme.typography.titleLarge)
                        Text("GPS dient zur Orientierung, nicht zur zentimetergenauen Vermessung.")
                        Button(enabled = !gpsBusy, onClick = {
                            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                                gpsStatus = "Bitte GPS am Gerät einschalten."
                            } else if (Build.VERSION.SDK_INT >= 30) {
                                gpsBusy = true
                                gpsStatus = "GPS-Signal wird gesucht …"
                                try {
                                    manager.getCurrentLocation(LocationManager.GPS_PROVIDER, CancellationSignal(), mainExecutor) { loc ->
                                        gpsBusy = false
                                        if (loc != null) {
                                            gps = gps + GpsPoint(loc.latitude, loc.longitude, loc.accuracy.toDouble())
                                            gpsStatus = "GPS-Punkt erfasst"
                                        } else gpsStatus = "Kein GPS-Signal. Bitte im Freien erneut versuchen."
                                    }
                                } catch (e: Exception) { gpsBusy = false; gpsStatus = "GPS nicht verfügbar." }
                            } else gpsStatus = "GPS-Erfassung benötigt Android 11 oder neuer."
                        }) { Text("GPS-Punkt erfassen") }
                        Text(gpsStatus)
                        val gpsLength = gps.zipWithNext().sumOf { (a,b) ->
                            val result = FloatArray(1)
                            Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, result)
                            result[0].toDouble()
                        }
                        Text("GPS-Strecke: " + fmt(gpsLength) + " m")
                        gps.forEachIndexed { i, point ->
                            Text("Punkt " + (i+1) + ": " + String.format(Locale.US, "%.6f, %.6f", point.lat, point.lon) + " · ±" + fmt(point.accuracy) + " m")
                        }
                        OutlinedButton(enabled = !gpsBusy && gps.isNotEmpty(), onClick = { gps = gps.dropLast(1) }) { Text("Letzten GPS-Punkt entfernen") }
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
                        Text("Fotodokumentation", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(photoStation, { photoStation = it }, label = { Text("Fotostation (m)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(photoNote, { photoNote = it }, label = { Text("Fotobeschreibung") }, modifier = Modifier.fillMaxWidth())
                        Button(enabled = name.isNotBlank() && l != null && number(photoStation)?.let { it <= l } == true, onClick = {
                            try { camera.launch(null) } catch (e: Exception) { photoStatus = "Keine Kamera verfügbar." }
                        }) { Text("Foto aufnehmen") }
                        Text(photoStatus)
                        photos.forEachIndexed { index, photo ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    val bitmap = remember(photo.file) { BitmapFactory.decodeFile(File(filesDir, photo.file).absolutePath)?.asImageBitmap() }
                                    if (bitmap != null) Image(bitmap, contentDescription = photo.note, modifier = Modifier.fillMaxWidth().height(160.dp))
                                    Text("Station " + photo.station + " m · " + photo.note)
                                    TextButton(onClick = { photos = photos.filterIndexed { i, _ -> i != index } }) { Text("Aus Projekt entfernen") }
                                }
                            }
                        }
                        Text("Vorschaufotos; Originalauflösung folgt.")
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
                            try {
                                val lines = mutableListOf(
                                    "Projekt: " + name,
                                    "Trassenlänge: " + fmt(l!!) + " m",
                                    "Grabenbreite: " + fmt(w!!) + " m",
                                    "Grabentiefe: " + fmt(d!!) + " m",
                                    "Aufbruchbreite: " + fmt(s!!) + " m",
                                    "Asphaltstärke: " + fmt(a!!) + " m",
                                    "Grabenvolumen brutto: " + fmt(l!! * w!! * d!!) + " m³",
                                    "Oberflächenaufbruch: " + fmt(l!! * s!!) + " m²",
                                    "Asphaltaufbruch: " + fmt(l!! * s!! * a!!) + " m³",
                                    "Zusatzpositionen (separat, ohne Überschneidungsabzug):"
                                )
                                extras.forEach { item ->
                                    lines.add(item.kind + " | Station " + fmt(item.station) + " m")
                                    lines.add("L/B/T: " + listOf(item.length, item.width, item.depth).joinToString(" / ") { it?.let { v -> fmt(v) } ?: "-" } + " m")
                                    item.area()?.let { lines.add("Fläche: " + fmt(it) + " m²") }
                                    item.volume()?.let { lines.add("Volumen: " + fmt(it) + " m³") }
                                    if (item.note.isNotBlank()) lines.add(item.note)
                                }
                                lines.add("GPS-Strecke (Orientierung): " + fmt(gpsLength) + " m")
                                gps.forEach { lines.add(String.format(Locale.US, "%.6f / %.6f (±%.1f m)", it.lat, it.lon, it.accuracy)) }
                                pendingPdf = buildReport(lines, points, photos, filesDir)
                                pdfPicker.launch("Feldaufmass.pdf")
                            } catch (e: Exception) { exportStatus = "PDF-Erstellung fehlgeschlagen." }
                        }, modifier = Modifier.fillMaxWidth()) { Text("PDF exportieren") }
                        Button(enabled = valid && name.isNotBlank(), onClick = {
                            try {
                                val main = listOf<List<Any?>>(
                                    listOf("Projekt", name),
                                    listOf("Position", "Menge", "Einheit"),
                                    listOf("Trassenlänge", l!!, "m"),
                                    listOf("Grabenbreite", w!!, "m"),
                                    listOf("Grabentiefe", d!!, "m"),
                                    listOf("Aufbruchbreite", s!!, "m"),
                                    listOf("Asphaltstärke", a!!, "m"),
                                    listOf("Grabenvolumen brutto", l!! * w!! * d!!, "m³"),
                                    listOf("Oberflächenaufbruch", l!! * s!!, "m²"),
                                    listOf("Asphaltaufbruch", l!! * s!! * a!!, "m³"),
                                    listOf("Hinweis", "Geometrische Richtwerte; keine geprüfte Abrechnung")
                                )
                                val additional = listOf<List<Any?>>(listOf("Position", "Station (m)", "Länge (m)", "Breite (m)", "Tiefe (m)", "Fläche (m²)", "Volumen (m³)", "Bemerkung")) + extras.map { listOf(it.kind,it.station,it.length,it.width,it.depth,it.area(),it.volume(),it.note) }
                                val coordinates = listOf<List<Any?>>(listOf("Punkt", "Breitengrad", "Längengrad", "Genauigkeit (m)")) + gps.mapIndexed { i, it -> listOf(i+1,it.lat,it.lon,it.accuracy) }
                                val photoRows = listOf<List<Any?>>(listOf("Station (m)", "Beschreibung", "Dateiname (lokal)")) + photos.map { listOf(number(it.station),it.note,it.file) }
                                pendingExcel = excelReport(listOf("Aufmaß" to main, "Zusatzpositionen" to additional, "GPS" to coordinates, "Fotos" to photoRows))
                                excelPicker.launch("Feldaufmass.xlsx")
                            } catch (e: Exception) { exportStatus = "Excel-Erstellung fehlgeschlagen." }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Excel exportieren") }
                        Text(exportStatus)
                        Button(enabled = !gpsBusy && valid && name.isNotBlank(), onClick = {
                            val data = JSONObject().put("l", length).put("w", width).put("d", depth).put("s", surface).put("a", asphalt).put("photos", JSONArray().apply { photos.forEach { put(JSONObject().put("file", it.file).put("station", it.station).put("note", it.note)) } }).put("gps", JSONArray().apply { gps.forEach { put(JSONObject().put("lat", it.lat).put("lon", it.lon).put("accuracy", it.accuracy)) } }).put("points", JSONArray().apply { points.forEach { put(JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())) } }).put("extras", JSONArray().apply { extras.forEach { put(it.json()) } })
                            prefs.edit().putString(name.trim(), data.toString()).apply()
                            projects = prefs.all.keys.sorted()
                            message = "Projekt gespeichert"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Projekt speichern") }
                        OutlinedButton(enabled = !gpsBusy, onClick = { name = ""; length = ""; width = ""; depth = ""; surface = ""; asphalt = ""; extras = emptyList(); points = emptyList(); gps = emptyList(); photos = emptyList(); photoStation = ""; photoNote = ""; message = "" }) { Text("Neues Projekt") }
                        Text(message)
                        Text("Gespeicherte Projekte", style = MaterialTheme.typography.titleLarge)
                        projects.forEach { key ->
                            OutlinedButton(enabled = !gpsBusy, onClick = {
                                val obj = JSONObject(prefs.getString(key, "{}") ?: "{}")
                                name = key; length = obj.optString("l"); width = obj.optString("w"); depth = obj.optString("d"); surface = obj.optString("s"); asphalt = obj.optString("a"); photos = obj.optJSONArray("photos")?.let { arr -> (0 until arr.length()).map { i -> arr.getJSONObject(i).let { SitePhoto(it.getString("file"), it.optString("station"), it.optString("note")) } } } ?: emptyList(); gps = obj.optJSONArray("gps")?.let { arr -> (0 until arr.length()).map { i -> arr.getJSONObject(i).let { GpsPoint(it.getDouble("lat"), it.getDouble("lon"), it.getDouble("accuracy")) } } } ?: emptyList(); points = obj.optJSONArray("points")?.let { arr -> (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Offset(it.getDouble("x").toFloat(), it.getDouble("y").toFloat()) } } } ?: emptyList(); extras = obj.optJSONArray("extras")?.let { arr -> (0 until arr.length()).map { Extra.read(arr.getJSONObject(it)) } } ?: emptyList(); message = "Projekt geladen"
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

private data class GpsPoint(val lat: Double, val lon: Double, val accuracy: Double)

private data class SitePhoto(val file: String, val station: String, val note: String)

private fun buildReport(lines: List<String>, points: List<Offset>, photos: List<SitePhoto>, folder: File): ByteArray {
    val doc = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var pageNo = 0
    var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNo).create())
    var y = 0f
    fun header() {
        paint.color = android.graphics.Color.rgb(23,76,101)
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT_BOLD
        page.canvas.drawText("Feldaufmaß Pro", 40f, 48f, paint)
        paint.textSize = 10f
        page.canvas.drawText("Leitungsbau | Seite " + pageNo, 40f, 66f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 11f
        y = 92f
    }
    fun ensure(h: Float) {
        if (y + h > 800f) {
            doc.finishPage(page)
            page = doc.startPage(PdfDocument.PageInfo.Builder(595,842,++pageNo).create())
            header()
        }
    }
    fun line(value: String) {
        value.split('\n').forEach { part ->
            var rest = part
            while (rest.isNotEmpty()) {
                ensure(18f)
                val n = paint.breakText(rest, true, 515f, null).coerceAtLeast(1)
                page.canvas.drawText(rest.take(n),40f,y,paint)
                y += 16f
                rest = rest.drop(n)
            }
        }
    }
    try {
        header()
        line("Geometrische Richtwerte – keine geprüfte Abrechnung.")
        lines.take(6).forEach { line(it) }
        if (points.size >= 2) {
            ensure(210f)
            line("Trassenskizze (nicht maßstäblich)")
            val top = y
            paint.color = android.graphics.Color.rgb(230,235,238)
            page.canvas.drawRect(40f,top,555f,top+175f,paint)
            paint.color = android.graphics.Color.rgb(23,76,101)
            paint.strokeWidth = 3f
            points.zipWithNext().forEach { (a,b) ->
                page.canvas.drawLine(45f+a.x*505f,top+5f+a.y*165f,45f+b.x*505f,top+5f+b.y*165f,paint)
            }
            paint.color = android.graphics.Color.BLACK
            y += 190f
        }
        lines.drop(6).forEach { line(it) }
        photos.forEach { photo ->
            val bitmap = BitmapFactory.decodeFile(File(folder,photo.file).absolutePath)
            if (bitmap != null) {
                ensure(220f)
                line("Foto | Station " + photo.station + " m")
                line(photo.note)
                ensure(180f)
                val scale = minOf(515f / bitmap.width, 165f / bitmap.height)
                page.canvas.drawBitmap(bitmap,null,RectF(40f,y,40f+bitmap.width*scale,y+bitmap.height*scale),paint)
                y += bitmap.height*scale+12f
                bitmap.recycle()
            }
        }
        doc.finishPage(page)
        val output = java.io.ByteArrayOutputStream()
        doc.writeTo(output)
        return output.toByteArray()
    } finally { doc.close() }
}
