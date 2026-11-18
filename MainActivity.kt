package com.qiblanoor.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

const val KAABA_LAT = 21.4225
const val KAABA_LON = 39.8262

/** Initial great-circle bearing (degrees from true north) from a point to the Kaaba. */
fun qiblaBearing(lat: Double, lon: Double): Double {
    val p1 = Math.toRadians(lat)
    val p2 = Math.toRadians(KAABA_LAT)
    val dl = Math.toRadians(KAABA_LON - lon)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0B5D3B))) {
                Surface(Modifier.fillMaxSize()) { QiblaScreen() }
            }
        }
    }
}

@Composable
fun QiblaScreen() {
    val ctx = LocalContext.current
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var granted by remember {
        mutableStateOf(has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r.values.any { it }
    }
    var loc by remember { mutableStateOf<Location?>(null) }
    var magHeading by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }

    DisposableEffect(granted) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { l -> loc = l }
        if (granted) {
            try {
                for (p in lm.getProviders(true)) {
                    lm.getLastKnownLocation(p)?.let { if (loc == null) loc = it }
                    lm.requestLocationUpdates(p, 5000L, 10f, listener)
                }
            } catch (e: SecurityException) { granted = false }
        }
        onDispose { lm.removeUpdates(listener) }
    }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val l = object : SensorEventListener {
            val r = FloatArray(9); val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                SensorManager.getOrientation(r, o)
                magHeading = ((Math.toDegrees(o[0].toDouble()) + 360.0) % 360.0).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor == null) hasSensor = false else sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        val l = loc
        when {
            !granted -> {
                Text(stringResource(R.string.need_perm), textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(stringResource(R.string.grant)) }
            }
            l == null -> Text(stringResource(R.string.locating))
            else -> {
                val bearing = qiblaBearing(l.latitude, l.longitude)
                val declination = GeomagneticField(
                    l.latitude.toFloat(), l.longitude.toFloat(), l.altitude.toFloat(), System.currentTimeMillis()
                ).declination
                val trueHeading = magHeading + declination
                Text(stringResource(R.string.bearing, bearing), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))
                if (hasSensor) {
                    Canvas(Modifier.size(280.dp)) {
                        val r = size.minDimension / 2
                        drawCircle(Color(0xFF0B5D3B), r, style = Stroke(10f))
                        rotate((bearing - trueHeading).toFloat()) {
                            val tip = Offset(center.x, center.y - r * 0.85f)
                            drawLine(Color(0xFFC9A227), center, tip, strokeWidth = 14f, cap = StrokeCap.Round)
                            drawCircle(Color(0xFFC9A227), 18f, tip)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.hint), textAlign = TextAlign.Center)
                } else {
                    Text(stringResource(R.string.no_sensor), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
