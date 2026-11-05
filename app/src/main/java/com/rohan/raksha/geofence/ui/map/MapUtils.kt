package com.rohan.raksha.geofence.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.Gravity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.rohan.raksha.geofence.ui.components.FloatingBarSpace
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.CircleLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
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

/**
 * Full-screen map: MapTiler hybrid style, live location puck, MapTiler logo + attribution tucked under the
 * floating top bar (they must stay visible per MapTiler's terms). [onReady] runs once the style is loaded.
 */
@SuppressLint("MissingPermission")
@Composable
fun RakshaMap(
    modifier: Modifier = Modifier,
    followUser: Boolean = true,
    followZoom: Double = 16.0,
    onReady: (MapLibreMap, Style) -> Unit = { _, _ -> }
) {
    val mapView = rememberMapView()
    val density = LocalDensity.current
    val topMarginPx = with(density) { WindowInsets.statusBars.getTop(this) + FloatingBarSpace.roundToPx() }
    val sideMarginPx = with(density) { 16.dp.roundToPx() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            mapView.apply {
                getMapAsync { map ->
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.isRotateGesturesEnabled = true
                    map.uiSettings.isTiltGesturesEnabled = false
                    map.uiSettings.logoGravity = Gravity.TOP or Gravity.START
                    map.uiSettings.setLogoMargins(sideMarginPx, topMarginPx, 0, 0)
                    map.uiSettings.attributionGravity = Gravity.TOP or Gravity.END
                    map.uiSettings.setAttributionMargins(0, topMarginPx, sideMarginPx, 0)
                    map.setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { style ->
                        activateLocation(ctx, map, style, followUser, followZoom)
                        SafeZoneLayer.ensure(style)
                        onReady(map, style)
                    }
                }
            }
        }
    )
}

@SuppressLint("MissingPermission")
private fun activateLocation(ctx: Context, map: MapLibreMap, style: Style, follow: Boolean, zoom: Double) {
    if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) return
    val request = LocationEngineRequest.Builder(1000L)
        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
        .setMaxWaitTime(1000L)
        .build()
    val component = map.locationComponent
    component.activateLocationComponent(
        LocationComponentActivationOptions.builder(ctx, style)
            .useDefaultLocationEngine(true)
            .locationEngineRequest(request)
            .build()
    )
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.COMPASS
    component.cameraMode = if (follow) CameraMode.TRACKING else CameraMode.NONE
    if (follow) component.zoomWhileTracking(zoom)
}

/** Draws the red (#FF2541) safe-zone circles, plus a centre dot, on top of the map style. */
object SafeZoneLayer {
    private const val SOURCE = "safe-zone-source"
    private const val FILL = "safe-zone-fill"
    private const val LINE = "safe-zone-line"
    private const val CENTER_SOURCE = "safe-zone-center-source"
    private const val CENTER = "safe-zone-center"
    private const val RED = "#FF2541"

    fun ensure(style: Style) {
        if (style.getSource(SOURCE) != null) return
        style.addSource(GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
        style.addSource(GeoJsonSource(CENTER_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
        style.addLayer(
            FillLayer(FILL, SOURCE).withProperties(
                PropertyFactory.fillColor(RED),
                PropertyFactory.fillOpacity(0.20f)
            )
        )
        style.addLayer(
            LineLayer(LINE, SOURCE).withProperties(
                PropertyFactory.lineColor(RED),
                PropertyFactory.lineWidth(2.5f)
            )
        )
        style.addLayer(
            CircleLayer(CENTER, CENTER_SOURCE).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(RED),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2.5f)
            )
        )
    }

    /** [circles] = list of (center, radius in metres). */
    fun update(style: Style, circles: List<Pair<LatLng, Double>>, showCenters: Boolean = true) {
        ensure(style)
        val features = circles.map { (center, radius) ->
            Feature.fromGeometry(Polygon.fromLngLats(listOf(circlePoints(center, radius))))
        }
        style.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
        val centers = if (showCenters) {
            circles.map { (center, _) -> Feature.fromGeometry(Point.fromLngLat(center.longitude, center.latitude)) }
        } else emptyList()
        style.getSourceAs<GeoJsonSource>(CENTER_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(centers))
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
