/**
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.sources

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.TransactionTooLargeException
import androidx.annotation.RequiresApi
import androidx.core.location.LocationManagerCompat
import breezyweather.data.location.LocationRepository
import breezyweather.data.weather.WeatherRepository
import breezyweather.domain.location.model.Location
import breezyweather.domain.location.model.LocationAddressInfo
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.Base
import breezyweather.domain.weather.model.Weather
import breezyweather.domain.weather.reference.Month
import breezyweather.domain.weather.reference.WeatherCode
import breezyweather.domain.weather.wrappers.WeatherWrapper
import com.google.maps.android.SphericalUtil
import com.google.maps.android.model.LatLng
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.rx3.awaitFirstOrElse
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.breezyweather.BreezyWeather
import org.breezyweather.BuildConfig
import org.breezyweather.common.exceptions.ApiKeyMissingException
import org.breezyweather.common.exceptions.LocationException
import org.breezyweather.common.exceptions.NoNetworkException
import org.breezyweather.common.exceptions.ReverseGeocodingException
import org.breezyweather.common.exceptions.WeatherException
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.getIsoFormattedDate
import org.breezyweather.common.extensions.hasPermission
import org.breezyweather.common.extensions.isOnline
import org.breezyweather.common.extensions.locationManager
import org.breezyweather.common.extensions.roundDecimals
import org.breezyweather.common.extensions.shortcutManager
import org.breezyweather.common.extensions.sizeInBytes
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.extensions.toDateNoHour
import org.breezyweather.common.source.AddressSource
import org.breezyweather.common.source.BroadcastSource
import org.breezyweather.common.source.ConfigurableSource
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.LocationParametersSource
import org.breezyweather.common.source.LocationResult
import org.breezyweather.common.source.NonFreeNetSource
import org.breezyweather.common.source.RefreshError
import org.breezyweather.common.source.ReverseGeocodingSource
import org.breezyweather.common.source.WeatherResult
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.common.utils.helpers.LogHelper
import org.breezyweather.common.utils.helpers.ShortcutsHelper
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.location.model.isDaylight
import org.breezyweather.domain.settings.CurrentLocationStore
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.settings.SourceConfigStore
import org.breezyweather.remoteviews.presenters.ClockDayDetailsWidgetIMP
import org.breezyweather.remoteviews.presenters.ClockDayHorizontalWidgetIMP
import org.breezyweather.remoteviews.presenters.ClockDayVerticalWidgetIMP
import org.breezyweather.remoteviews.presenters.ClockDayWeekWidgetIMP
import org.breezyweather.remoteviews.presenters.DailyTrendWidgetIMP
import org.breezyweather.remoteviews.presenters.DayWeekWidgetIMP
import org.breezyweather.remoteviews.presenters.DayWidgetIMP
import org.breezyweather.remoteviews.presenters.HourlyTrendWidgetIMP
import org.breezyweather.remoteviews.presenters.MaterialYouCurrentWidgetIMP
import org.breezyweather.remoteviews.presenters.MaterialYouForecastWidgetIMP
import org.breezyweather.remoteviews.presenters.MultiCityWidgetIMP
import org.breezyweather.remoteviews.presenters.TextWidgetIMP
import org.breezyweather.remoteviews.presenters.WeekWidgetIMP
import org.breezyweather.remoteviews.presenters.notification.WidgetNotificationIMP
import org.breezyweather.ui.main.utils.RefreshErrorType
import org.breezyweather.ui.theme.resource.ResourcesProviderFactory
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RefreshHelper @Inject constructor(
    private val sourceManager: SourceManager,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val currentLocationStore: CurrentLocationStore,
) {

    /**
     * Get updated coordinates from the location service
     * Update the store and returns the result, including the potential errors
     */
    suspend fun updateCurrentCoordinates(
        context: Context,
        background: Boolean,
    ): List<RefreshError> {
        val locationSource = SettingsManager.getInstance(context).locationSource
        val locationService = sourceManager.getLocationSourceOrDefault(locationSource)
        val errors = mutableListOf<RefreshError>()
        if (!context.isOnline()) {
            errors.add(RefreshError(RefreshErrorType.NETWORK_UNAVAILABLE))
        }
        if (locationService.permissions.isNotEmpty()) {
            // if needs any location permission.
            if (!context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                !context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                errors.add(RefreshError(RefreshErrorType.ACCESS_LOCATION_PERMISSION_MISSING))
            }
            if (background) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                ) {
                    errors.add(RefreshError(RefreshErrorType.ACCESS_BACKGROUND_LOCATION_PERMISSION_MISSING))
                }
            }
            if (!LocationManagerCompat.isLocationEnabled(context.locationManager)) {
                errors.add(RefreshError(RefreshErrorType.LOCATION_ACCESS_OFF))
            }
        }
        if (errors.isNotEmpty()) {
            return errors
        }

        return try {
            if (locationService is ConfigurableSource && !locationService.isConfigured) {
                throw ApiKeyMissingException()
            }
            val result = locationService
                .requestLocation(context)
                .awaitFirstOrElse {
                    throw LocationException()
                }

            // Some sources do not accept more than 6 decimals, so truncating it here
            currentLocationStore.updateCurrentLocation(
                longitude = result.longitude.roundDecimals(6)!!.toFloat(),
                latitude = result.latitude.roundDecimals(6)!!.toFloat()
            )

            return emptyList()
        } catch (e: Throwable) {
            listOf(
                RefreshError(
                    RefreshErrorType.getTypeFromThrowable(context, e, RefreshErrorType.LOCATION_FAILED),
                    locationService.name
                )
            )
        }
    }

    /**
     * Performs the following task on a location if it is current location:
     * - Apply updated coordinates
     * - Reverse geocoding (if current location)
     * On non-current location, just returns the location
     *
     * TODO: Remove redundancy with default reverse geocoding calls
     */
    suspend fun getLocation(
        context: Context,
        location: Location,
    ): LocationResult {
        // Longitude and latitude incorrect? Let’s return earlier
        if (location.isCurrentPosition && !currentLocationStore.isUsable) {
            // There was already an error earlier in the process, so no errors
            return LocationResult(location, emptyList())
        }

        var needsCountryCodeRefresh = false
        var needsSavingToDb = false

        val currentErrors = mutableListOf<RefreshError>()

        // STEP 1 - Update coordinates if current position
        val locationWithUpdatedCoordinates = if (location.isCurrentPosition) {
            val coordinatesChanged = location.latitude != currentLocationStore.lastKnownLatitude.toDouble() ||
                location.longitude != currentLocationStore.lastKnownLongitude.toDouble()
            if (coordinatesChanged) {
                needsSavingToDb = true
                location.copy(
                    latitude = currentLocationStore.lastKnownLatitude.toDouble(),
                    longitude = currentLocationStore.lastKnownLongitude.toDouble(),
                    /*
                     * Don’t keep old data as the user can have changed position
                     * It avoids keeping old data from a reverse geocoding-compatible weather source
                     * onto a weather source without reverse geocoding
                     */
                    timeZone = TimeZone.getTimeZone("GMT"),
                    country = "",
                    countryCode = "",
                    admin1 = "",
                    admin1Code = "",
                    admin2 = "",
                    admin2Code = "",
                    admin3 = "",
                    admin3Code = "",
                    admin4 = "",
                    admin4Code = "",
                    city = "",
                    district = "",
                    needsGeocodeRefresh = true
                )
            } else {
                location
            }
        } else {
            location
        }

        // STEP 2 - Add address info if needed
        val locationGeocoded = if (locationWithUpdatedCoordinates.needsGeocodeRefresh) {
            val reverseGeocodingService = sourceManager.getReverseGeocodingSourceOrDefault(
                location.reverseGeocodingSource ?: BuildConfig.DEFAULT_GEOCODING_SOURCE
            )
            try {
                // Getting the address for this
                requestReverseGeocoding(reverseGeocodingService, locationWithUpdatedCoordinates, context).let {
                    if (
                        SphericalUtil.computeDistanceBetween(
                            LatLng(it.latitude, it.longitude),
                            LatLng(locationWithUpdatedCoordinates.latitude, locationWithUpdatedCoordinates.longitude)
                        ) > REVERSE_GEOCODING_DISTANCE_LIMIT
                    ) {
                        LogHelper.log(
                            msg = "Nearest location found is too far away from the user-provided location"
                        )
                        currentErrors.add(
                            RefreshError(
                                RefreshErrorType.REVERSE_GEOCODING_FAILED,
                                reverseGeocodingService.name,
                                SourceFeature.REVERSE_GEOCODING
                            )
                        )
                        location
                    } else if (reverseGeocodingService.id != BuildConfig.DEFAULT_GEOCODING_SOURCE &&
                        !it.hasValidCountryCode
                    ) {
                        /**
                         * If country code is missing or invalid, don't accept the result and reverse to
                         * previous valid location
                         * Exception: The default geocoding source is allowed to send an empty countryCode
                         */
                        LogHelper.log(
                            msg = "Found invalid country code during reverse geocoding: ${it.countryCode}"
                        )
                        currentErrors.add(
                            RefreshError(
                                RefreshErrorType.REVERSE_GEOCODING_FAILED,
                                reverseGeocodingService.name,
                                SourceFeature.REVERSE_GEOCODING
                            )
                        )
                        location
                    } else {
                        needsCountryCodeRefresh = reverseGeocodingService.knownAmbiguousCountryCodes?.any { cc ->
                            it.countryCode.equals(cc, ignoreCase = true)
                        } != false ||
                            it.countryCode.equals("AN", ignoreCase = true)
                        needsSavingToDb = true
                        it
                    }
                }
            } catch (e: Throwable) {
                currentErrors.add(
                    RefreshError(
                        RefreshErrorType.getTypeFromThrowable(
                            context,
                            e,
                            RefreshErrorType.REVERSE_GEOCODING_FAILED
                        ),
                        reverseGeocodingService.name,
                        SourceFeature.REVERSE_GEOCODING
                    )
                )

                // Fallback to offline reverse geocoding
                if (reverseGeocodingService.id != BuildConfig.DEFAULT_GEOCODING_SOURCE) {
                    val defaultReverseGeocodingSource = sourceManager.getReverseGeocodingSourceOrDefault(
                        BuildConfig.DEFAULT_GEOCODING_SOURCE
                    )
                    try {
                        // Getting the address for this from the fallback reverse geocoding source
                        requestReverseGeocoding(
                            defaultReverseGeocodingSource,
                            locationWithUpdatedCoordinates,
                            context
                        ).copy(
                            // We failed to refresh, so retry reverse geocoding next time
                            needsGeocodeRefresh = true
                        ).also {
                            needsSavingToDb = true
                        }
                    } catch (_: Throwable) {
                        /**
                         * Returns the original location
                         * Previously, we used to return the new coordinates without the reverse geocoding,
                         * leading to issues when reverse geocoding fails (because the mandatory countryCode
                         * -for some sources- would be missing)
                         * However, if both the reverse geocoding source + the offline fallback reverse geocoding
                         * source are failing, it safes to assume that the longitude and latitude are completely
                         * junky and should be discarded
                         */
                        location
                    }
                } else {
                    /**
                     * Returns the original location
                     * Same comment as above
                     */
                    location
                }
            }
        } else {
            // If no need for reverse geocoding, just return the current location which already has the info
            locationWithUpdatedCoordinates // Same as "location"
        }

        // STEP 3 - Validate ambiguous ISO 3166 codes
        val locationInfoFromDefaultSource = if (needsCountryCodeRefresh) {
            needsSavingToDb = true
            getLocationWithDisambiguatedCountryCode(locationGeocoded, context)
        } else {
            locationGeocoded
        }

        // STEP 4 - Add timezone if missing
        val locationWithTimeZone = if (locationGeocoded.isTimeZoneInvalid) {
            needsSavingToDb = true
            locationInfoFromDefaultSource.copy(
                timeZone = getTimeZoneForLocation(context, locationGeocoded)
            )
        } else {
            locationInfoFromDefaultSource
        }

        // STEP 5 - If there was any change, update in database
        if (needsSavingToDb) {
            locationRepository.update(locationWithTimeZone)
        }

        return LocationResult(locationWithTimeZone, currentErrors)
    }

    /**
     * @param context
     * @param location a location with a valid country code, or it will use the fallback strategy
     */
    suspend fun getTimeZoneForLocation(
        context: Context,
        location: Location,
    ): TimeZone {
        val timezone = sourceManager
            .getTimeZoneSource()
            .requestTimezone(context, location)
            .awaitFirstOrElse {
                TimeZone.getTimeZone("GMT")
            }

        return if (timezone.id == "GMT") {
            if (location.isCurrentPosition) {
                TimeZone.getDefault()
            } else {
                getOceanTimeZoneForLocation(location.longitude)
            }
        } else {
            timezone
        }
    }

    private fun getOceanTimeZoneForLocation(longitude: Double): TimeZone {
        // Sign is intentionally inverted. See https://github.com/eggert/tz/blob/2025b/etcetera#L37-L43
        return when (longitude) {
            in 172.5..180.0 -> TimeZone.getTimeZone("Etc/GMT-12")
            in 157.5..172.5 -> TimeZone.getTimeZone("Etc/GMT-11")
            in 142.5..157.5 -> TimeZone.getTimeZone("Etc/GMT-10")
            in 127.5..142.5 -> TimeZone.getTimeZone("Etc/GMT-9")
            in 112.5..127.5 -> TimeZone.getTimeZone("Etc/GMT-8")
            in 97.5..112.5 -> TimeZone.getTimeZone("Etc/GMT-7")
            in 82.5..97.5 -> TimeZone.getTimeZone("Etc/GMT-6")
            in 67.5..82.5 -> TimeZone.getTimeZone("Etc/GMT-5")
            in 52.5..67.5 -> TimeZone.getTimeZone("Etc/GMT-4")
            in 37.5..52.5 -> TimeZone.getTimeZone("Etc/GMT-3")
            in 22.5..37.5 -> TimeZone.getTimeZone("Etc/GMT-2")
            in 7.5..22.5 -> TimeZone.getTimeZone("Etc/GMT-1")
            in -7.5..7.5 -> TimeZone.getTimeZone("Etc/GMT")
            in -22.5..-7.5 -> TimeZone.getTimeZone("Etc/GMT+1")
            in -37.5..-22.5 -> TimeZone.getTimeZone("Etc/GMT+2")
            in -52.5..-37.5 -> TimeZone.getTimeZone("Etc/GMT+3")
            in -67.5..-52.5 -> TimeZone.getTimeZone("Etc/GMT+4")
            in -82.5..-67.5 -> TimeZone.getTimeZone("Etc/GMT+5")
            in -97.5..-82.5 -> TimeZone.getTimeZone("Etc/GMT+6")
            in -112.5..-97.5 -> TimeZone.getTimeZone("Etc/GMT+7")
            in -127.5..-112.5 -> TimeZone.getTimeZone("Etc/GMT+8")
            in -142.5..-127.5 -> TimeZone.getTimeZone("Etc/GMT+9")
            in -157.5..-142.5 -> TimeZone.getTimeZone("Etc/GMT+10")
            in -172.5..-157.5 -> TimeZone.getTimeZone("Etc/GMT+11")
            in -180.0..-172.5 -> TimeZone.getTimeZone("Etc/GMT+12")
            else -> TimeZone.getTimeZone("GMT")
        }
    }

    suspend fun requestReverseGeocoding(
        reverseGeocodingService: ReverseGeocodingSource,
        currentLocation: Location,
        context: Context,
    ): Location {
        if (reverseGeocodingService is ConfigurableSource && !reverseGeocodingService.isConfigured) {
            throw ApiKeyMissingException()
        }

        return reverseGeocodingService
            .requestNearestLocation(context, currentLocation.latitude, currentLocation.longitude)
            .map { locationList ->
                if (locationList.isNotEmpty()) {
                    currentLocation.toLocationWithAddressInfo(
                        context.currentLocale,
                        locationList[0],
                        overwriteCoordinates = false
                    )
                } else {
                    throw ReverseGeocodingException()
                }
            }.awaitFirstOrElse {
                throw ReverseGeocodingException()
            }
    }

    suspend fun getLocationWithDisambiguatedCountryCode(
        location: Location,
        context: Context,
    ): Location {
        return if (AddressSource.ambiguousCountryCodes.any { cc ->
                location.countryCode.equals(cc, ignoreCase = true)
            }
        ) {
            try {
                // Getting the address for this from the fallback reverse geocoding source
                requestReverseGeocoding(
                    sourceManager.getReverseGeocodingSourceOrDefault(BuildConfig.DEFAULT_GEOCODING_SOURCE),
                    location,
                    context
                ).let {
                    if (!it.countryCode.equals(location.countryCode, ignoreCase = true)) {
                        location.copy(
                            // Don't replace country as it doesn’t make sense when it’s a territory
                            // country = it.country,
                            countryCode = it.countryCode
                        )
                    } else {
                        location
                    }
                }
            } catch (_: Throwable) {
                location
            }
        } else {
            location
        }
    }

    suspend fun updateLocation(location: Location, oldFormattedId: String? = null) {
        locationRepository.update(location, oldFormattedId)
    }

    fun getPermissions(context: Context): List<String> {
        // if IP:    none.
        // else:
        //      R:   foreground location. (set background location enabled manually)
        //      Q:   foreground location + background location.
        //      K-P: foreground location.
        val locationSource = SettingsManager.getInstance(context).locationSource
        val service = sourceManager.getLocationSourceOrDefault(locationSource)
        val permissions: MutableList<String> = service.permissions.toMutableList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || permissions.isEmpty()) {
            // device has no background location permission or locate by IP.
            return permissions
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        return permissions
    }

    suspend fun getWeather(
        context: Context,
        location: Location,
        coordinatesChanged: Boolean,
        ignoreCaching: Boolean = false,
    ): WeatherResult {
        try {
            if (!location.isUsable || location.needsGeocodeRefresh) {
                return WeatherResult(
                    location.weather,
                    listOf(RefreshError(RefreshErrorType.INVALID_LOCATION))
                )
            }

            // Group data requested to sources by source
            val featuresBySources: MutableMap<String, MutableList<SourceFeature>> = mutableMapOf()
            with(location) {
                listOf(
                    Pair(forecastSource, SourceFeature.FORECAST),
                    Pair(currentSource, SourceFeature.CURRENT),
                    Pair(airQualitySource, SourceFeature.AIR_QUALITY),
                    Pair(pollenSource, SourceFeature.POLLEN),
                    Pair(minutelySource, SourceFeature.MINUTELY),
                    Pair(alertSource, SourceFeature.ALERT),
                    Pair(normalsSource, SourceFeature.NORMALS)
                ).forEach {
                    if (!it.first.isNullOrEmpty()) {
                        if (featuresBySources.containsKey(it.first)) {
                            featuresBySources[it.first]!!.add(it.second)
                        } else {
                            featuresBySources[it.first!!] = mutableListOf(it.second)
                        }
                    }
                }
            }

            // Always update refresh time displayed to the user, even if just re-using cached data
            val base = location.weather?.base?.copy(
                refreshTime = Date()
            ) ?: Base(
                refreshTime = Date()
            )

            val languageUpdateTime = SettingsManager.getInstance(context).languageUpdateLastTimestamp
            val locationParameters = location.parameters.toMutableMap()

            // COMPLETE BACK TO YESTERDAY 00:00 MAX
            // TODO: Use Calendar to handle DST
            val yesterdayMidnight = Date(Date().time - 1.days.inWholeMilliseconds)
                .getIsoFormattedDate(location)
                .toDateNoHour(location.timeZone)!!
            var forecastUpdateTime = base.forecastUpdateTime
            var currentUpdateTime = base.currentUpdateTime
            var airQualityUpdateTime = base.airQualityUpdateTime
            var pollenUpdateTime = base.pollenUpdateTime
            var minutelyUpdateTime = base.minutelyUpdateTime
            var alertsUpdateTime = base.alertsUpdateTime
            var normalsUpdateTime = base.normalsUpdateTime
            var normalsUpdateLatitude = base.normalsUpdateLatitude
            var normalsUpdateLongitude = base.normalsUpdateLongitude

            // TODO: Debug source is not online, don't use this check in that case
            // Can't return from inside `async`
            if (!context.isOnline()) {
                return WeatherResult(
                    location.weather,
                    listOf(
                        RefreshError(RefreshErrorType.NETWORK_UNAVAILABLE)
                    )
                )
            }

            val errors = CopyOnWriteArrayList<RefreshError>()
            val weatherWrapper = if (featuresBySources.isNotEmpty()) {
                val semaphore = Semaphore(5)
                val sourceCalls = mutableMapOf<String, WeatherWrapper?>()
                coroutineScope {
                    featuresBySources
                        .map { entry ->
                            async {
                                semaphore.withPermit {
                                    val service = sourceManager.getWeatherSource(entry.key)
                                    if (service == null) {
                                        errors.add(RefreshError(RefreshErrorType.SOURCE_NOT_INSTALLED, entry.key))
                                    } else {
                                        val featuresToUpdate = entry.value
                                            .filter {
                                                // Remove sources that are not configured
                                                if (BuildConfig.FLAVOR == "freenet" && service is NonFreeNetSource) {
                                                    errors.add(
                                                        RefreshError(
                                                            RefreshErrorType.NON_FREE_NETWORK_SOURCE,
                                                            entry.key
                                                        )
                                                    )
                                                    false
                                                } else if (service is ConfigurableSource && !service.isConfigured) {
                                                    errors.add(
                                                        RefreshError(
                                                            RefreshErrorType.API_KEY_REQUIRED_MISSING,
                                                            entry.key
                                                        )
                                                    )
                                                    false
                                                } else {
                                                    true
                                                }
                                            }
                                            .filter {
                                                // Remove sources that no longer supports the feature
                                                if (!service.supportedFeatures.containsKey(it)) {
                                                    errors.add(
                                                        RefreshError(
                                                            RefreshErrorType.UNSUPPORTED_FEATURE,
                                                            entry.key
                                                        )
                                                    )
                                                    false
                                                } else {
                                                    true
                                                }
                                            }
                                            .filter {
                                                // Remove sources that no longer supports the feature for that location
                                                if (!service.isFeatureSupportedForLocation(location, it)) {
                                                    errors.add(
                                                        RefreshError(
                                                            RefreshErrorType.UNSUPPORTED_FEATURE,
                                                            entry.key
                                                        )
                                                    )
                                                    false
                                                } else {
                                                    true
                                                }
                                            }
                                            .filter {
                                                service !is HttpSource ||
                                                    ignoreCaching ||
                                                    !isWeatherDataStillValid(
                                                        location,
                                                        it,
                                                        isRestricted = !BreezyWeather.instance.debugMode &&
                                                            service is ConfigurableSource &&
                                                            service.isRestricted,
                                                        minimumTime = languageUpdateTime
                                                    )
                                            }
                                        if (featuresToUpdate.isEmpty()) {
                                            // Setting to null will make it use previous data
                                            sourceCalls[entry.key] = null
                                        } else {
                                            sourceCalls[entry.key] = try {
                                                if (service is LocationParametersSource &&
                                                    service.needsLocationParametersRefresh(
                                                        location,
                                                        coordinatesChanged,
                                                        featuresToUpdate
                                                    )
                                                ) {
                                                    locationParameters[service.id] = buildMap {
                                                        if (locationParameters.getOrElse(service.id) { null } != null) {
                                                            putAll(locationParameters[service.id]!!)
                                                        }
                                                        putAll(
                                                            service
                                                                .requestLocationParameters(context, location.copy())
                                                                .awaitFirstOrElse {
                                                                    throw WeatherException()
                                                                }
                                                        )
                                                    }
                                                }
                                                service
                                                    .requestWeather(
                                                        context,
                                                        location.copy(parameters = locationParameters),
                                                        featuresToUpdate
                                                    ).awaitFirstOrElse {
                                                        featuresToUpdate.forEach {
                                                            errors.add(
                                                                RefreshError(
                                                                    RefreshErrorType.DATA_REFRESH_FAILED,
                                                                    entry.key,
                                                                    it
                                                                )
                                                            )
                                                        }
                                                        null
                                                    }
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                                featuresToUpdate.forEach {
                                                    errors.add(
                                                        RefreshError(
                                                            RefreshErrorType.getTypeFromThrowable(
                                                                context,
                                                                e,
                                                                RefreshErrorType.DATA_REFRESH_FAILED
                                                            ),
                                                            entry.key,
                                                            it
                                                        )
                                                    )
                                                }
                                                null
                                            }
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                }

                for ((k, v) in sourceCalls) {
                    v?.failedFeatures?.entries?.forEach { entry ->
                        errors.add(
                            RefreshError(
                                RefreshErrorType.getTypeFromThrowable(
                                    context,
                                    entry.value,
                                    RefreshErrorType.DATA_REFRESH_FAILED
                                ),
                                k,
                                entry.key
                            )
                        )
                    }
                }

                /**
                 * Make sure we return data from the correct source
                 */
                WeatherWrapper(
                    dailyForecast = if (location.forecastSource.isNotEmpty()) {
                        if (errors.any {
                                it.feature == SourceFeature.FORECAST &&
                                    it.source == location.forecastSource
                            }
                        ) {
                            null
                        } else {
                            sourceCalls.getOrElse(location.forecastSource) { null }?.dailyForecast?.let {
                                if (it.isEmpty()) {
                                    errors.add(
                                        RefreshError(
                                            RefreshErrorType.INVALID_INCOMPLETE_DATA,
                                            location.forecastSource,
                                            SourceFeature.FORECAST
                                        )
                                    )
                                    null
                                } else {
                                    forecastUpdateTime = Date()
                                    it
                                }
                            }
                        }
                    } else {
                        null
                    } ?: location.weather?.toDailyWrapperList(yesterdayMidnight),
                    hourlyForecast = if (location.forecastSource.isNotEmpty()) {
                        if (errors.any {
                                it.feature == SourceFeature.FORECAST &&
                                    it.source == location.forecastSource
                            }
                        ) {
                            null
                        } else {
                            sourceCalls.getOrElse(location.forecastSource) { null }?.hourlyForecast
                        }
                    } else {
                        null
                    } ?: location.weather?.toHourlyWrapperList(yesterdayMidnight),
                    current = if (!location.currentSource.isNullOrEmpty()) {
                        if (errors.any {
                                it.feature == SourceFeature.CURRENT &&
                                    it.source == location.currentSource!!
                            }
                        ) {
                            null
                        } else {
                            sourceCalls.getOrElse(location.currentSource!!) { null }?.current?.let {
                                currentUpdateTime = Date()
                                it
                            }
                        }
                    } else {
                        null
                    }, // Fallback will be handled later
                    airQuality = if (!location.airQualitySource.isNullOrEmpty()) {
                        if (errors.any {
                                it.feature == SourceFeature.AIR_QUALITY &&
                                    it.source == location.airQualitySource!!
                            }
                        ) {
                            null
                        } else {
                            sourceCalls.getOrElse(location.airQualitySource!!) { null }?.airQuality?.let {
                                airQualityUpdateTime = Date()
                                it
                            }
                        } ?: location.weather?.toAirQualityWrapperList(yesterdayMidnight)
                    } else {
                        null
                    },
                    pollen = if (!location.pollenSource.isNullOrEmpty()) {
                        if (errors.any {
                                it.feature == SourceFeature.POLLEN &&
                                    it.source == location.pollenSource!!
                            }
                        ) {
                            null
                        } else {
                            sourceCalls.getOrElse(location.pollenSource!!) { null }?.pollen?.let {
                                pollenUpdateTime = Date()
                                it
                            }
                        } ?: location.weather?.toPollenWrapperList(yesterdayMidnight)
                    } else {
                        null
                    },
                    minutelyForecast = if (!location.minutelySource.isNullOrEmpty()) {
                        if (errors.any {
                                it.feature == SourceFeature.MINUTELY &&
                                    it.source == location.minutelySource!!
                            }
                        ) {
                            null
                        } else {
                            sourceCalls.getOrElse(location.minutelySource!!) { null }?.minutelyForecast?.let {
                                minutelyUpdateTime = Date()
                                it
                            }
                        } ?: location.weather?.toMinutelyWrapper()
                    } else {
                        null
                    },
                    alertList = if (!location.alertSource.isNullOrEmpty()) {
                        // Special case: if we had errors, but still received at least 1 alert, accept the newer data
                        if (errors.any {
                                it.feature == SourceFeature.ALERT &&
                                    it.source == location.alertSource!!
                            } && sourceCalls.getOrElse(location.alertSource!!) { null }?.alertList?.isEmpty() != false
                        ) {
                            null
                        } else {
                            sourceCalls.getOrElse(location.alertSource!!) { null }?.alertList?.let {
                                alertsUpdateTime = Date()
                                it
                            }
                        } ?: location.weather?.toAlertsWrapper()
                    } else {
                        null
                    },
                    normals = if (!location.normalsSource.isNullOrEmpty()) {
                        if (errors.any {
                                it.feature == SourceFeature.NORMALS &&
                                    it.source == location.normalsSource!!
                            }
                        ) {
                            null
                        } else {
                            // Combine with previous stored months if not current location
                            sourceCalls.getOrElse(location.normalsSource!!) { null }?.normals?.let {
                                normalsUpdateTime = Date()
                                normalsUpdateLatitude = location.latitude
                                normalsUpdateLongitude = location.longitude
                                ((if (!location.isCurrentPosition) location.weather?.normals else null) ?: emptyMap()) +
                                    it
                            }
                        } ?: location.weather?.normals
                    } else {
                        null
                    }
                )
            } else {
                return WeatherResult(
                    location.weather,
                    listOf(RefreshError(RefreshErrorType.INVALID_LOCATION))
                )
            }

            // COMPLETING DATA

            // 1) Creates hours/days back to yesterday 00:00 if they are missing from the new refresh
            val weatherWrapperCompleted = completeNewWeatherWithPreviousData(
                weatherWrapper,
                location.weather,
                yesterdayMidnight,
                location.airQualitySource,
                location.pollenSource
            )

            // 2) Computes as many data as possible (weather code, weather text, dew point, feels like temp., etc)
            val hourlyComputedMissingData = computeMissingHourlyData(
                weatherWrapperCompleted.hourlyForecast
            ) ?: emptyList()

            // 3) Create the daily object with air quality/pollen data + computes missing data
            val dailyForecast = completeDailyListFromHourlyList(
                convertDailyWrapperToDailyList(weatherWrapperCompleted),
                hourlyComputedMissingData,
                weatherWrapperCompleted.airQuality?.hourlyForecast ?: emptyMap(),
                weatherWrapperCompleted.pollen?.hourlyForecast ?: emptyMap(),
                weatherWrapperCompleted.hourlyForecast?.associate { it.date to it.sunshineDuration } ?: emptyMap(),
                weatherWrapperCompleted.pollen?.current,
                location
            )

            // 4) Complete UV and isDaylight + air quality in hourly
            val hourlyForecast = completeHourlyListFromDailyList(
                hourlyComputedMissingData,
                dailyForecast,
                weatherWrapperCompleted.airQuality?.hourlyForecast ?: emptyMap(),
                location
            )

            // Detect incompatible times between forecast hourly and air quality hourly
            // No need to do this for pollen at the moment, as we don't store hourly pollen
            if (weatherWrapperCompleted.airQuality?.hourlyForecast?.isNotEmpty() == true &&
                hourlyForecast.isNotEmpty() &&
                !hourlyForecast.any { hourly ->
                    weatherWrapperCompleted.airQuality!!.hourlyForecast!!.contains(hourly.date)
                }
            ) {
                errors.add(
                    RefreshError(
                        RefreshErrorType.INCOMPATIBLE_FORECAST_TIMES,
                        location.airQualitySource,
                        SourceFeature.AIR_QUALITY
                    )
                )
            }

            // Example: 15:01 -> starts at 15:00, 15:59 -> starts at 15:00
            val currentHour = hourlyForecast.firstOrNull {
                it.date.time >= System.currentTimeMillis() - 1.hours.inWholeMilliseconds
            }
            val currentDay = dailyForecast.firstOrNull {
                // Adding 23 hours just to be safe in case of DST
                it.date.time >= yesterdayMidnight.time + 23.hours.inWholeMilliseconds
            }

            val weather = Weather(
                base = base.copy(
                    forecastUpdateTime = forecastUpdateTime,
                    currentUpdateTime = currentUpdateTime,
                    airQualityUpdateTime = airQualityUpdateTime,
                    pollenUpdateTime = pollenUpdateTime,
                    minutelyUpdateTime = minutelyUpdateTime,
                    alertsUpdateTime = alertsUpdateTime,
                    normalsUpdateTime = normalsUpdateTime,
                    normalsUpdateLatitude = normalsUpdateLatitude,
                    normalsUpdateLongitude = normalsUpdateLongitude
                ),
                current = completeCurrentFromHourlyData(
                    weatherWrapperCompleted.current
                        ?: if (isUpdateStillValid(base.currentUpdateTime, wait = 30)) {
                            // Allow to re-use current data if it was successfully refreshed less than 30 min ago
                            location.weather?.current?.toCurrentWrapper()
                        } else {
                            null
                        },
                    currentHour,
                    currentDay,
                    weatherWrapperCompleted.airQuality?.current
                        ?: if (isUpdateStillValid(base.currentUpdateTime, wait = 30)) {
                            // Allow to re-use current data if it was successfully refreshed less than 30 min ago
                            location.weather?.current?.airQuality
                        } else {
                            null
                        } ?: weatherWrapperCompleted.airQuality?.hourlyForecast?.entries?.firstOrNull {
                            it.key.time >= System.currentTimeMillis() - 1.hours.inWholeMilliseconds
                        }?.value, // Workaround for incompatibility with hourly forecast times
                    location
                ),
                dailyForecast = dailyForecast,
                hourlyForecast = hourlyForecast,
                minutelyForecast = weatherWrapperCompleted.minutelyForecast
                    ?.mapNotNull { it.toValidOrNull() }
                    ?: emptyList(),
                alertList = weatherWrapperCompleted.alertList ?: emptyList(),
                normals = weatherWrapperCompleted.normals ?: emptyMap()
            )
            locationRepository.insertParameters(location.formattedId, locationParameters)
            weatherRepository.insert(location, weather)
            return WeatherResult(weather, errors)
        } catch (e: Throwable) {
            e.printStackTrace()
            return WeatherResult(
                location.weather,
                listOf(RefreshError(RefreshErrorType.DATA_REFRESH_FAILED))
            )
        }
    }

    fun requestSearchLocations(
        context: Context,
        query: String,
        locationSearchSource: String,
    ): Observable<List<LocationAddressInfo>> {
        val searchService = sourceManager.getLocationSearchSourceOrDefault(locationSearchSource)

        // Debug source is not online
        if (searchService is HttpSource && !context.isOnline()) {
            return Observable.error(NoNetworkException())
        }

        return try {
            searchService.requestLocationSearch(context, query).map { locationList ->
                locationList.map {
                    it.copy(
                        longitude = it.longitude?.roundDecimals(6),
                        latitude = it.latitude?.roundDecimals(6)
                    )
                }
            }
        } catch (e: Throwable) {
            return Observable.error(e)
        }
    }

    fun updateWidgetIfNecessary(context: Context, locationList: List<Location>) {
        if (DayWidgetIMP.isInUse(context)) {
            DayWidgetIMP.updateWidgetView(
                context,
                locationList[0],
                sourceManager.getPollenIndexSource(
                    (locationList[0].pollenSource ?: "").ifEmpty { locationList[0].forecastSource }
                )
            )
        }
        if (WeekWidgetIMP.isInUse(context)) {
            WeekWidgetIMP.updateWidgetView(context, locationList[0])
        }
        if (DayWeekWidgetIMP.isInUse(context)) {
            DayWeekWidgetIMP.updateWidgetView(
                context,
                locationList[0],
                sourceManager.getPollenIndexSource(
                    (locationList[0].pollenSource ?: "").ifEmpty { locationList[0].forecastSource }
                )
            )
        }
        if (ClockDayHorizontalWidgetIMP.isInUse(context)) {
            ClockDayHorizontalWidgetIMP.updateWidgetView(context, locationList[0])
        }
        if (ClockDayVerticalWidgetIMP.isInUse(context)) {
            ClockDayVerticalWidgetIMP.updateWidgetView(
                context,
                locationList[0],
                sourceManager.getPollenIndexSource(
                    (locationList[0].pollenSource ?: "").ifEmpty { locationList[0].forecastSource }
                )
            )
        }
        if (ClockDayWeekWidgetIMP.isInUse(context)) {
            ClockDayWeekWidgetIMP.updateWidgetView(context, locationList[0])
        }
        if (ClockDayDetailsWidgetIMP.isInUse(context)) {
            ClockDayDetailsWidgetIMP.updateWidgetView(context, locationList[0])
        }
        if (TextWidgetIMP.isInUse(context)) {
            TextWidgetIMP.updateWidgetView(
                context,
                locationList[0],
                sourceManager.getPollenIndexSource(
                    (locationList[0].pollenSource ?: "").ifEmpty { locationList[0].forecastSource }
                )
            )
        }
        if (DailyTrendWidgetIMP.isInUse(context)) {
            DailyTrendWidgetIMP.updateWidgetView(context, locationList[0])
        }
        if (HourlyTrendWidgetIMP.isInUse(context)) {
            HourlyTrendWidgetIMP.updateWidgetView(context, locationList[0])
        }
        if (MaterialYouForecastWidgetIMP.isEnabled(context)) {
            MaterialYouForecastWidgetIMP.updateWidgetView(context, locationList[0])
        }
        if (MaterialYouCurrentWidgetIMP.isEnabled(context)) {
            MaterialYouCurrentWidgetIMP.updateWidgetView(context, locationList[0])
        }
        if (MultiCityWidgetIMP.isInUse(context)) {
            MultiCityWidgetIMP.updateWidgetView(context, locationList)
        }
    }

    suspend fun updateWidgetIfNecessary(context: Context) {
        val locationList = locationRepository.getXLocations(3, withParameters = false).toMutableList()
        if (locationList.isNotEmpty()) {
            for (i in locationList.indices) {
                locationList[i] = locationList[i].copy(
                    weather = weatherRepository.getWeatherByLocationId(
                        locationList[i].formattedId,
                        withDaily = true,
                        withHourly = i == 0, // Not needed in multi city
                        withMinutely = false,
                        withAlerts = i == 0, // Not needed in multi city
                        withNormals = false
                    )
                )
            }
            updateWidgetIfNecessary(context, locationList)
        }
    }

    fun updateNotificationIfNecessary(context: Context, locationList: List<Location>) {
        if (WidgetNotificationIMP.isEnabled(context)) {
            WidgetNotificationIMP.buildNotificationAndSendIt(context, locationList)
        }
    }

    suspend fun updateNotificationIfNecessary(context: Context) {
        if (WidgetNotificationIMP.isEnabled(context)) {
            val locationList = locationRepository.getXLocations(4, withParameters = false).toMutableList()
            for (i in locationList.indices) {
                locationList[i] = locationList[i].copy(
                    weather = weatherRepository.getWeatherByLocationId(
                        locationList[i].formattedId,
                        withDaily = true,
                        withHourly = i == 0, // Not needed in multi city
                        withMinutely = false,
                        withAlerts = i == 0, // Not needed in multi city
                        withNormals = false
                    )
                )
            }
            updateNotificationIfNecessary(context, locationList)
        }
    }

    /**
     * @param context
     * @param sourceId if you only want to send data for a specific source
     */
    suspend fun broadcastDataIfNecessary(
        context: Context,
        sourceId: String? = null,
    ) {
        val locationList = locationRepository.getAllLocations(withParameters = false)
            .map {
                it.copy(
                    weather = weatherRepository.getWeatherByLocationId(it.formattedId)
                )
            }
        return broadcastDataIfNecessary(context, locationList, sourceId = sourceId)
    }

    fun isBroadcastSourcesEnabled(context: Context): Boolean {
        return sourceManager.isBroadcastSourcesEnabled(context)
    }

    /**
     * @param context
     * @param locationList
     * @param sourceId if you only want to send data for a specific source
     */
    fun broadcastDataIfNecessary(
        context: Context,
        locationList: List<Location>,
        updatedLocationIds: Array<String>? = null,
        sourceId: String? = null,
    ) {
        sourceManager.getBroadcastSources()
            .filter { sourceId == null || sourceId == it.id }
            .forEach { source ->
                val config = SourceConfigStore(context, source.id)
                val enabledPackages = (config.getString("packages", null) ?: "").let {
                    if (it.isNotEmpty()) it.split(",") else emptyList()
                }

                if (enabledPackages.isNotEmpty()) {
                    val packageInfoList = context.packageManager.queryBroadcastReceivers(
                        Intent(source.intentAction),
                        PackageManager.GET_RESOLVED_FILTER
                    )
                    val enabledAndAvailablePackages = enabledPackages
                        .filter { enabledPackage ->
                            packageInfoList.any { it.activityInfo.applicationInfo.packageName == enabledPackage }
                        }
                    if (enabledPackages.size != enabledAndAvailablePackages.size) {
                        LogHelper.log(
                            msg = "[${source.name}] Updating packages setting as some packages are no longer available"
                        )
                        // Update to remove unavailable packages
                        config.edit().putString("packages", enabledAndAvailablePackages.joinToString(",")).apply()
                        // Don't notify settings changed, we are already sending data!
                    }

                    if (enabledAndAvailablePackages.isNotEmpty()) {
                        sendBroadcastSafely(
                            context,
                            enabledAndAvailablePackages,
                            source,
                            locationList,
                            updatedLocationIds
                        )
                    }
                }
            }
    }

    private fun sendBroadcastSafely(
        context: Context,
        enabledAndAvailablePackages: List<String>,
        source: BroadcastSource,
        locationList: List<Location>,
        updatedLocationIds: Array<String>?,
    ) {
        if (locationList.isNotEmpty()) {
            val data = source.getExtras(context, locationList, updatedLocationIds)
            if (data != null) {
                if (data.sizeInBytes > 1000000) {
                    if (BreezyWeather.instance.debugMode) {
                        LogHelper.log(msg = "[${source.name}] Parcel size is too large, retrying with less locations")
                    }
                    sendBroadcastSafely(
                        context,
                        enabledAndAvailablePackages,
                        source,
                        locationList.dropLast(1),
                        updatedLocationIds
                    )
                    return
                }

                try {
                    enabledAndAvailablePackages.forEach {
                        if (BreezyWeather.instance.debugMode) {
                            LogHelper.log(
                                msg = "[${source.name}] Sending data for ${locationList.size} locations to $it"
                            )
                        }
                        context.sendBroadcast(
                            Intent(source.intentAction)
                                .setPackage(it)
                                .putExtras(data)
                                .setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        )
                    }
                } catch (e: RuntimeException) {
                    if (e.cause is TransactionTooLargeException) {
                        if (BreezyWeather.instance.debugMode) {
                            LogHelper.log(
                                msg = "[${source.name}] Transaction too large for ${locationList.size} locations"
                            )
                        }
                        // Retry with one less location, until location list is empty
                        sendBroadcastSafely(
                            context,
                            enabledAndAvailablePackages,
                            source,
                            locationList.dropLast(1),
                            updatedLocationIds
                        )
                    } else {
                        if (BreezyWeather.instance.debugMode) {
                            LogHelper.log(msg = "[${source.name}] Uncaught exception")
                        }
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    if (BreezyWeather.instance.debugMode) {
                        LogHelper.log(msg = "[${source.name}] Uncaught exception")
                    }
                    e.printStackTrace()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    suspend fun refreshShortcuts(context: Context, locationList: List<Location>) {
        val shortcutManager = context.shortcutManager ?: return
        val provider = ResourcesProviderFactory.newInstance
        val shortcutList = mutableListOf<ShortcutInfo>()

        // location list.
        val count = min(shortcutManager.maxShortcutCountPerActivity - 1, locationList.size)
        for (i in 0 until count) {
            val weather = locationList[i].weather
                ?: weatherRepository.getWeatherByLocationId(locationList[i].formattedId)
            val icon =
                weather?.current?.weatherCode?.let { weatherCode ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ShortcutsHelper.getAdaptiveIcon(
                            provider,
                            weatherCode,
                            locationList[i].isDaylight
                        )
                    } else {
                        ShortcutsHelper.getIcon(
                            provider,
                            weatherCode,
                            locationList[i].isDaylight
                        )
                    }
                } ?: ShortcutsHelper.getIcon(provider, WeatherCode.CLEAR, true)
            val title = locationList[i].getPlace(context, true)
            shortcutList.add(
                ShortcutInfo.Builder(context, locationList[i].formattedId)
                    .setIcon(icon)
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIntent(IntentHelper.buildMainActivityIntent(locationList[i]))
                    .build()
            )
        }
        try {
            shortcutManager.dynamicShortcuts = shortcutList
        } catch (ignore: Exception) {
            // do nothing.
        }
    }

    /**
     * For a given location, will tell if data is still valid or needs a refresh
     * @param feature if null, will tell for main weather
     * @param isRestricted some sources will prefer a longer wait, make it true if that’s the case
     * @param minimumTime if the last update was before this minimum time, it will be forced refreshed (except for normals)
     */
    private fun isWeatherDataStillValid(
        location: Location,
        feature: SourceFeature? = null,
        isRestricted: Boolean = false,
        minimumTime: Long = 0,
    ): Boolean {
        if (location.weather?.base == null) return false

        when (feature) {
            SourceFeature.CURRENT -> {
                return isUpdateStillValid(
                    location.weather!!.base.currentUpdateTime,
                    if (isRestricted) WAIT_CURRENT_RESTRICTED else WAIT_CURRENT,
                    minimumTime
                )
            }
            SourceFeature.AIR_QUALITY -> {
                return isUpdateStillValid(
                    location.weather!!.base.airQualityUpdateTime,
                    if (isRestricted) WAIT_AIR_QUALITY_RESTRICTED else WAIT_AIR_QUALITY,
                    minimumTime
                )
            }
            SourceFeature.POLLEN -> {
                return isUpdateStillValid(
                    location.weather!!.base.pollenUpdateTime,
                    if (isRestricted) WAIT_POLLEN_RESTRICTED else WAIT_POLLEN,
                    minimumTime
                )
            }
            SourceFeature.MINUTELY -> {
                return isUpdateStillValid(
                    location.weather!!.base.minutelyUpdateTime,
                    if (location.weather!!.minutelyForecast.none {
                            (it.precipitationIntensity?.inMicrometers ?: 0.0) > 0
                        }
                    ) {
                        if (isRestricted) WAIT_MINUTELY_RESTRICTED else WAIT_MINUTELY
                    } else {
                        if (isRestricted) WAIT_MINUTELY_RESTRICTED_ONGOING else WAIT_MINUTELY_ONGOING
                    },
                    minimumTime
                )
            }
            SourceFeature.ALERT -> {
                return isUpdateStillValid(
                    location.weather!!.base.alertsUpdateTime,
                    if (location.weather!!.currentAlertList.isEmpty()) {
                        if (isRestricted) WAIT_ALERTS_RESTRICTED else WAIT_ALERTS
                    } else {
                        if (isRestricted) WAIT_ALERTS_RESTRICTED_ONGOING else WAIT_ALERTS_ONGOING
                    },
                    minimumTime
                )
            }
            SourceFeature.NORMALS -> {
                val base = location.weather!!.base
                if ((base.normalsUpdateTime ?: 0) == 0 ||
                    base.normalsUpdateLongitude == 0.0 ||
                    base.normalsUpdateLatitude == 0.0
                ) {
                    return false
                }

                if (location.isCurrentPosition) {
                    val distance = SphericalUtil.computeDistanceBetween(
                        LatLng(base.normalsUpdateLatitude, base.normalsUpdateLongitude),
                        LatLng(location.latitude, location.longitude)
                    )
                    return distance <= CACHING_DISTANCE_LIMIT
                } else {
                    if (location.weather!!.normals.isEmpty()) return false
                    val cal = Date().toCalendarWithTimeZone(location.timeZone)
                    return location.weather!!.normals
                        .getOrElse(Month.fromCalendarMonth(cal[Calendar.MONTH])) { null }
                        ?.let {
                            if (it.daytimeTemperature != null || it.nighttimeTemperature != null) it else null
                        } != null
                }
            }
            else -> {
                return isUpdateStillValid(
                    location.weather!!.base.forecastUpdateTime,
                    if (isRestricted) WAIT_MAIN_RESTRICTED else WAIT_MAIN,
                    minimumTime
                )
            }
        }
    }

    private fun isUpdateStillValid(
        updateTime: Date?,
        wait: Int,
        minimumTime: Long = 0,
    ): Boolean {
        if (updateTime == null || updateTime.time < minimumTime) return false

        val currentTime = System.currentTimeMillis()

        return currentTime >= updateTime.time && currentTime - updateTime.time < wait.minutes.inWholeMilliseconds
    }

    companion object {
        private const val WAIT_MINIMUM = 1
        private const val WAIT_REGULAR = 5
        private const val WAIT_RESTRICTED = 15
        private const val WAIT_ONE_HOUR = 60

        const val WAIT_MAIN = WAIT_REGULAR // 5 min
        const val WAIT_MAIN_RESTRICTED = WAIT_RESTRICTED // 15 min
        const val WAIT_CURRENT = WAIT_MINIMUM // 1 min
        const val WAIT_CURRENT_RESTRICTED = WAIT_RESTRICTED // 15 min
        const val WAIT_AIR_QUALITY = WAIT_REGULAR // 5 min
        const val WAIT_AIR_QUALITY_RESTRICTED = WAIT_ONE_HOUR // 1 hour
        const val WAIT_POLLEN = WAIT_REGULAR // 5 min
        const val WAIT_POLLEN_RESTRICTED = WAIT_ONE_HOUR // 1 hour
        const val WAIT_MINUTELY = WAIT_REGULAR // 5 min
        const val WAIT_MINUTELY_ONGOING = WAIT_MINIMUM // 1 min
        const val WAIT_MINUTELY_RESTRICTED = WAIT_RESTRICTED // 15 min
        const val WAIT_MINUTELY_RESTRICTED_ONGOING = WAIT_REGULAR // 5 min
        const val WAIT_ALERTS = WAIT_REGULAR // 5 min
        const val WAIT_ALERTS_ONGOING = WAIT_MINIMUM // 1 min
        const val WAIT_ALERTS_RESTRICTED = WAIT_ONE_HOUR // 1 hour
        const val WAIT_ALERTS_RESTRICTED_ONGOING = WAIT_REGULAR // 5 min

        const val CACHING_DISTANCE_LIMIT = 5000 // 5 km
        const val REVERSE_GEOCODING_DISTANCE_LIMIT = 50000 // 50 km
    }
}
