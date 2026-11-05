package com.rohan.raksha.geofence.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rohan.raksha.geofence.BuildConfig
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object MapConfig {
    /** MapTiler satellite + labels. Key comes from local.properties / env / gradle default. */
    val STYLE_URL: String
        get() = "https://api.maptiler.com/maps/hybrid/style.json?key=${BuildConfig.MAPTILER_KEY}"
}

/** A MapView whose lifecycle follows the composition's lifecycle owner. */
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState != Lifecycle.State.DESTROYED) {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }
    return mapView
}

/** Draws the red safe-zone circles on top of the map style. */
object SafeZoneLayer {
    private const val SOURCE = "safe-zone-source"
    private const val FILL = "safe-zone-fill"
    private const val LINE = "safe-zone-line"
    private const val RED = "#FF2541"

    private fun ensure(style: Style) {
        if (style.getSource(SOURCE) != null) return
        style.addSource(GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
        style.addLayer(
            FillLayer(FILL, SOURCE).withProperties(
                PropertyFactory.fillColor(RED),
                PropertyFactory.fillOpacity(0.22f)
            )
        )
        style.addLayer(
            LineLayer(LINE, SOURCE).withProperties(
                PropertyFactory.lineColor(RED),
                PropertyFactory.lineWidth(2.5f)
            )
        )
    }

    /** [circles] = list of (center, radius in metres). */
    fun update(style: Style, circles: List<Pair<LatLng, Double>>) {
        ensure(style)
        val features = circles.map { (center, radius) ->
            Feature.fromGeometry(Polygon.fromLngLats(listOf(circlePoints(center, radius))))
        }
        style.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun circlePoints(center: LatLng, radiusMeters: Double, steps: Int = 72): List<Point> {
        val earthRadius = 6_371_008.8
        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)
        val angular = radiusMeters / earthRadius
        return (0..steps).map { i ->
            val bearing = 2.0 * Math.PI * i / steps
            val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
            val lon2 = lon1 + atan2(
                sin(bearing) * sin(angular) * cos(lat1),
                cos(angular) - sin(lat1) * sin(lat2)
            )
            Point.fromLngLat(Math.toDegrees(lon2), Math.toDegrees(lat2))
        }
    }
}
