package ca.gbc.comp3074.delacruz_jezrel.lab5

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import ca.gbc.comp3074.delacruz_jezrel.lab5.ui.theme.Lab5Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Lab5Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationComponent(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun LocationComponent(modifier: Modifier = Modifier) {

    val context = LocalContext.current

    val locationManager = remember(context) {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    val location = remember { mutableStateOf<Location?>(null) }
    val status = remember { mutableStateOf("") }

    val locationListener = remember { mutableStateOf<LocationListenerCompat?>(null) }

    // Action to run once the user answers the permission dialog
    val pendingAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // GPS needs precise location, so only FINE counts as granted
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun fetchLocation() {
        if (!hasPermission()) {
            status.value = "Location permission not granted"
            return
        }
        if (!isGpsEnabled()) {
            status.value = "GPS is turned off. Turn on location in Settings."
            return
        }

        status.value = "Fetching location from GPS..."
        // Gives up and returns null after about 30 seconds without a fix
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            LocationManager.GPS_PROVIDER,
            CancellationSignal(),
            ContextCompat.getMainExecutor(context)
        ) { l ->
            if (l == null) {
                status.value = "No GPS fix. Try again with a clear view of the sky."
            } else {
                location.value = l
                status.value = "Location: (%.5f, %.5f)".format(l.latitude, l.longitude)
            }
        }
    }

    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun startTracking() {
        if (!hasPermission()) {
            status.value = "Location permission not granted"
            return
        }
        if (!isGpsEnabled()) {
            status.value = "GPS is turned off. Turn on location in Settings."
            return
        }
        if (locationListener.value != null) {
            status.value = "Already tracking location"
            return
        }

        status.value = "Tracking location from GPS..."
        val locationRequest = LocationRequestCompat.Builder(5000L)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        val listener = object : LocationListenerCompat {
            override fun onLocationChanged(l: Location) {
                location.value = l
                status.value = "Location: (%.5f, %.5f)".format(l.latitude, l.longitude)
            }

            override fun onProviderDisabled(provider: String) {
                status.value = "GPS was turned off. Turn on location in Settings."
            }
        }
        locationListener.value = listener

        LocationManagerCompat.requestLocationUpdates(
            locationManager,
            LocationManager.GPS_PROVIDER,
            locationRequest,
            listener,
            Looper.getMainLooper()
        )
    }

    fun stopTracking() {
        val listener = locationListener.value
        if (listener == null) {
            status.value = "Location tracking is not running"
            return
        }
        LocationManagerCompat.removeUpdates(locationManager, listener)
        locationListener.value = null
        status.value = "Location tracking stopped"
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val action = pendingAction.value
        pendingAction.value = null
        when {
            res[Manifest.permission.ACCESS_FINE_LOCATION] == true -> action?.invoke()
            res[Manifest.permission.ACCESS_COARSE_LOCATION] == true ->
                status.value = "Only approximate location granted. Allow precise location to use GPS."
            else -> status.value = "Location permission not granted"
        }
    }

    // Runs the action now if permission is granted, otherwise asks first
    fun withPermission(action: () -> Unit) {
        if (hasPermission()) {
            action()
        } else {
            pendingAction.value = action
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Stop location updates when this screen goes away
    DisposableEffect(locationManager) {
        onDispose {
            locationListener.value?.let { LocationManagerCompat.removeUpdates(locationManager, it) }
            locationListener.value = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = status.value,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { withPermission { fetchLocation() } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Get Location")
        }
        Button(
            onClick = { withPermission { startTracking() } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Start Tracking")
        }
        Button(
            onClick = { stopTracking() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Stop Tracking")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LocationComponentPreview() {
    Lab5Theme {
        LocationComponent()
    }
}
