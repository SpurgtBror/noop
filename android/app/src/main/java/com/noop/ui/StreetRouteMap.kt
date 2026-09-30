package com.noop.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.noop.analytics.RouteMath
import com.noop.analytics.TimedWorkoutPoint
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File
import java.util.concurrent.TimeUnit

/** Created only after the user opts in. The route itself is local; tile requests go to OSM. */
@Composable
fun StreetRouteMap(polyline: String, timed: List<TimedWorkoutPoint>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val points = remember(polyline) { RouteMath.decode(polyline) }
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        val cache = Cache(File(context.cacheDir, "street-tiles"), 50L * 1024 * 1024)
        val client = OkHttpClient.Builder().cache(cache).addInterceptor(Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "NOOP Android (https://github.com/ryanbr/noop)")
                .build()
            chain.proceed(request)
        }).addNetworkInterceptor(Interceptor { chain ->
            val response = chain.proceed(chain.request())
            // Honor OSM's caching headers. Its policy asks for seven days if none are supplied.
            if (response.request.url.host == "tile.openstreetmap.org" &&
                response.header("Cache-Control") == null && response.header("Expires") == null) {
                response.newBuilder().header("Cache-Control", "public, max-age=" +
                    TimeUnit.DAYS.toSeconds(7)).build()
            } else response
        }).build()
        HttpRequestUtil.setOkHttpClient(client)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> mapView.onStart()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    Box(modifier.fillMaxWidth().height(Metrics.chartHeight)) {
        AndroidView(factory = {
            mapView.getMapAsync { map ->
                map.setPrefetchZoomDelta(0)
                val tiles = TileSet("2.2.0", "https://tile.openstreetmap.org/{z}/{x}/{y}.png").apply {
                    attribution = "© OpenStreetMap contributors"
                }
                val style = Style.Builder()
                    .withLayer(BackgroundLayer("offline-background").withProperties(
                        PropertyFactory.backgroundColor(Palette.surfaceBase.toArgb())))
                    .withSource(RasterSource("osm", tiles, 256))
                    .withLayer(RasterLayer("streets", "osm"))
                val route = if (timed.size >= 2) timed.map { LatLng(it.latE6 / 1e6, it.lonE6 / 1e6) }
                    else points.map { LatLng(it.lat, it.lon) }
                val segments = if (timed.size >= 2) route.zip(timed).groupBy { it.second.segment }
                    .values.map { group -> group.map { it.first } }
                    else listOf(route)
                val features = segments.filter { it.size >= 2 }.map { segment ->
                    Feature.fromGeometry(LineString.fromLngLats(segment.map { Point.fromLngLat(it.longitude, it.latitude) }))
                }
                if (features.isNotEmpty()) {
                    style.withSource(GeoJsonSource("route", FeatureCollection.fromFeatures(features)))
                        .withLayer(LineLayer("route-line", "route").withProperties(
                            PropertyFactory.lineColor(Palette.accent.toArgb()),
                            PropertyFactory.lineWidth(5f)))
                    style.withSource(GeoJsonSource("route-start", Point.fromLngLat(route.first().longitude, route.first().latitude)))
                        .withLayer(CircleLayer("start-dot", "route-start").withProperties(
                            PropertyFactory.circleColor(Palette.accent.toArgb()), PropertyFactory.circleRadius(7f)))
                    style.withSource(GeoJsonSource("route-end", Point.fromLngLat(route.last().longitude, route.last().latitude)))
                        .withLayer(CircleLayer("end-dot", "route-end").withProperties(
                            PropertyFactory.circleColor(Palette.statusCritical.toArgb()), PropertyFactory.circleRadius(7f)))
                }
                map.setStyle(style) {
                    if (route.isNotEmpty()) {
                        mapView.post {
                            if (!mapView.isAttachedToWindow) return@post
                            val hasExtent = route.any { it.latitude != route.first().latitude ||
                                it.longitude != route.first().longitude }
                            val update = if (mapView.width > 0 && mapView.height > 0 && hasExtent) {
                                val bounds = LatLngBounds.Builder().includes(route).build()
                                CameraUpdateFactory.newLatLngBounds(bounds, 40)
                            } else {
                                CameraUpdateFactory.newLatLngZoom(route.first(), 14.0)
                            }
                            map.moveCamera(update)
                        }
                    }
                }
            }
            mapView
        }, modifier = Modifier.fillMaxWidth().height(Metrics.chartHeight))
    }
}
