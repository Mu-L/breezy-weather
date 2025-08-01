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

import android.content.Context
import breezyweather.domain.source.SourceFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.breezyweather.BreezyWeather
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.source.BroadcastSource
import org.breezyweather.common.source.ConfigurableSource
import org.breezyweather.common.source.FeatureSource
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.LocationSearchSource
import org.breezyweather.common.source.LocationSource
import org.breezyweather.common.source.PollenIndexSource
import org.breezyweather.common.source.ReverseGeocodingSource
import org.breezyweather.common.source.Source
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.sources.accu.AccuService
import org.breezyweather.sources.aemet.AemetService
import org.breezyweather.sources.android.AndroidGeocoderService
import org.breezyweather.sources.android.AndroidLocationService
import org.breezyweather.sources.atmo.AtmoAuraService
import org.breezyweather.sources.atmo.AtmoFranceService
import org.breezyweather.sources.atmo.AtmoGrandEstService
import org.breezyweather.sources.atmo.AtmoHdfService
import org.breezyweather.sources.atmo.AtmoSudService
import org.breezyweather.sources.baiduip.BaiduIPLocationService
import org.breezyweather.sources.bmd.BmdService
import org.breezyweather.sources.bmkg.BmkgService
import org.breezyweather.sources.brightsky.BrightSkyService
import org.breezyweather.sources.china.ChinaService
import org.breezyweather.sources.climweb.AnamBfService
import org.breezyweather.sources.climweb.AnametService
import org.breezyweather.sources.climweb.DccmsService
import org.breezyweather.sources.climweb.DmnNeService
import org.breezyweather.sources.climweb.DwrGmService
import org.breezyweather.sources.climweb.EthioMetService
import org.breezyweather.sources.climweb.GMetService
import org.breezyweather.sources.climweb.IgebuService
import org.breezyweather.sources.climweb.InmgbService
import org.breezyweather.sources.climweb.MaliMeteoService
import org.breezyweather.sources.climweb.MeteoBeninService
import org.breezyweather.sources.climweb.MeteoTchadService
import org.breezyweather.sources.climweb.MettelsatService
import org.breezyweather.sources.climweb.MsdZwService
import org.breezyweather.sources.climweb.SmaScService
import org.breezyweather.sources.climweb.SmaSuService
import org.breezyweather.sources.climweb.SsmsService
import org.breezyweather.sources.cwa.CwaService
import org.breezyweather.sources.debug.DebugService
import org.breezyweather.sources.dmi.DmiService
import org.breezyweather.sources.eccc.EcccService
import org.breezyweather.sources.ekuk.EkukService
import org.breezyweather.sources.epdhk.EpdHkService
import org.breezyweather.sources.fpas.FpasService
import org.breezyweather.sources.gadgetbridge.GadgetbridgeService
import org.breezyweather.sources.geonames.GeoNamesService
import org.breezyweather.sources.geosphereat.GeoSphereAtService
import org.breezyweather.sources.here.HereService
import org.breezyweather.sources.hko.HkoService
import org.breezyweather.sources.ilmateenistus.IlmateenistusService
import org.breezyweather.sources.imd.ImdService
import org.breezyweather.sources.ims.ImsService
import org.breezyweather.sources.ipma.IpmaService
import org.breezyweather.sources.ipsb.IpSbLocationService
import org.breezyweather.sources.jma.JmaService
import org.breezyweather.sources.lhmt.LhmtService
import org.breezyweather.sources.lvgmc.LvgmcService
import org.breezyweather.sources.meteoam.MeteoAmService
import org.breezyweather.sources.meteolux.MeteoLuxService
import org.breezyweather.sources.metie.MetIeService
import org.breezyweather.sources.metno.MetNoService
import org.breezyweather.sources.metoffice.MetOfficeService
import org.breezyweather.sources.mf.MfService
import org.breezyweather.sources.mgm.MgmService
import org.breezyweather.sources.namem.NamemService
import org.breezyweather.sources.naturalearth.NaturalEarthService
import org.breezyweather.sources.ncdr.NcdrService
import org.breezyweather.sources.ncei.NceiService
import org.breezyweather.sources.nlsc.NlscService
import org.breezyweather.sources.nominatim.NominatimService
import org.breezyweather.sources.nws.NwsService
import org.breezyweather.sources.openmeteo.OpenMeteoService
import org.breezyweather.sources.openweather.OpenWeatherService
import org.breezyweather.sources.pagasa.PagasaService
import org.breezyweather.sources.pirateweather.PirateWeatherService
import org.breezyweather.sources.recosante.RecosanteService
import org.breezyweather.sources.smg.SmgService
import org.breezyweather.sources.smhi.SmhiService
import org.breezyweather.sources.wmosevereweather.WmoSevereWeatherService
import java.text.Collator
import javax.inject.Inject

class SourceManager @Inject constructor(
    @ApplicationContext context: Context,
    accuService: AccuService,
    aemetService: AemetService,
    anamBfService: AnamBfService,
    anametService: AnametService,
    androidGeocoderService: AndroidGeocoderService,
    androidLocationService: AndroidLocationService,
    atmoAuraService: AtmoAuraService,
    atmoFranceService: AtmoFranceService,
    atmoGrandEstService: AtmoGrandEstService,
    atmoHdfService: AtmoHdfService,
    atmoSudService: AtmoSudService,
    baiduIPService: BaiduIPLocationService,
    bmdService: BmdService,
    bmkgService: BmkgService,
    brightSkyService: BrightSkyService,
    chinaService: ChinaService,
    cwaService: CwaService,
    dccmsService: DccmsService,
    debugService: DebugService,
    dmnNeService: DmnNeService,
    dmiService: DmiService,
    dwrGmService: DwrGmService,
    ecccService: EcccService,
    ekukService: EkukService,
    epdHkService: EpdHkService,
    ethioMetService: EthioMetService,
    fpasService: FpasService,
    gadgetbridgeService: GadgetbridgeService,
    geoNamesService: GeoNamesService,
    geoSphereAtService: GeoSphereAtService,
    gMetService: GMetService,
    hereService: HereService,
    hkoService: HkoService,
    igebuService: IgebuService,
    ilmateenistusService: IlmateenistusService,
    imdService: ImdService,
    imsService: ImsService,
    inmgbService: InmgbService,
    ipmaService: IpmaService,
    ipSbService: IpSbLocationService,
    jmaService: JmaService,
    lhmtService: LhmtService,
    lvgmcService: LvgmcService,
    maliMeteoService: MaliMeteoService,
    meteoAmService: MeteoAmService,
    meteoBeninService: MeteoBeninService,
    meteoLuxService: MeteoLuxService,
    meteoTchadService: MeteoTchadService,
    metIeService: MetIeService,
    metNoService: MetNoService,
    metOfficeService: MetOfficeService,
    mettelsatService: MettelsatService,
    mfService: MfService,
    mgmService: MgmService,
    msdZwService: MsdZwService,
    namemService: NamemService,
    naturalEarthService: NaturalEarthService,
    ncdrService: NcdrService,
    nceiService: NceiService,
    nlscService: NlscService,
    nominatimService: NominatimService,
    nwsService: NwsService,
    openMeteoService: OpenMeteoService,
    openWeatherService: OpenWeatherService,
    pagasaService: PagasaService,
    pirateWeatherService: PirateWeatherService,
    recosanteService: RecosanteService,
    smaScService: SmaScService,
    smaSuService: SmaSuService,
    smgService: SmgService,
    smhiService: SmhiService,
    ssmsService: SsmsService,
    wmoSevereWeatherService: WmoSevereWeatherService,
) {
    // TODO: Initialize lazily

    // Location sources
    private val locationSourceList = persistentListOf(
        androidLocationService,
        ipSbService,
        baiduIPService
    )

    // Location search sources
    private val locationSearchSourceList = persistentListOf(
        geoNamesService
    )

    // Reverse geocoding sources
    private val reverseGeocodingSourceList = persistentListOf(
        naturalEarthService,
        androidGeocoderService,
        nominatimService
    )

    // Worldwide weather sources, excluding national sources with worldwide support,
    // with the exception of MET Norway
    private val worldwideWeatherSourceList = persistentListOf(
        openMeteoService,
        accuService,
        fpasService,
        hereService,
        metNoService,
        nceiService,
        openWeatherService,
        pirateWeatherService,
        wmoSevereWeatherService
    )

    // Region-specific or national weather sources
    private val nationalWeatherSourceList = persistentListOf(
        aemetService,
        anamBfService,
        anametService,
        atmoAuraService,
        atmoFranceService,
        atmoGrandEstService,
        atmoHdfService,
        atmoSudService,
        bmdService,
        bmkgService,
        brightSkyService,
        chinaService,
        cwaService,
        dccmsService,
        dmiService,
        dmnNeService,
        dwrGmService,
        ecccService,
        ekukService,
        epdHkService,
        ethioMetService,
        geoSphereAtService,
        gMetService,
        hkoService,
        igebuService,
        ilmateenistusService,
        imdService,
        imsService,
        inmgbService,
        ipmaService,
        jmaService,
        lhmtService,
        lvgmcService,
        maliMeteoService,
        meteoAmService,
        meteoBeninService,
        meteoLuxService,
        meteoTchadService,
        metIeService,
        metOfficeService,
        mettelsatService,
        mfService,
        mgmService,
        msdZwService,
        namemService,
        ncdrService,
        nlscService,
        nwsService,
        pagasaService,
        recosanteService,
        smaScService,
        smaSuService,
        smgService,
        smhiService,
        ssmsService
    )

    // Broadcast sources
    private val broadcastSourceList = persistentListOf(
        gadgetbridgeService
    )

    // The order of this list is preserved in "source chooser" dialogs
    private val sourceList: ImmutableList<Source> = buildList {
        addAll(locationSourceList)
        addAll(locationSearchSourceList)
        addAll(reverseGeocodingSourceList)
        addAll(worldwideWeatherSourceList)
        if (BreezyWeather.instance.debugMode) {
            add(debugService)
        }
        addAll(
            nationalWeatherSourceList
                .sortedWith { ws1, ws2 ->
                    // Sort by name because there are now a lot of sources
                    Collator.getInstance(context.currentLocale).compare(ws1.name, ws2.name)
                }
        )
        addAll(broadcastSourceList)
    }.toImmutableList()

    fun getSource(id: String): Source? = sourceList.firstOrNull { it.id == id }
    fun getHttpSources(): ImmutableList<HttpSource> = sourceList
        .filterIsInstance<HttpSource>()
        .toImmutableList()

    // Location
    fun getLocationSources(): ImmutableList<LocationSource> = sourceList
        .filterIsInstance<LocationSource>()
        .toImmutableList()

    fun getFeatureSources(): ImmutableList<FeatureSource> = sourceList
        .filterIsInstance<FeatureSource>()
        .toImmutableList()

    fun getWeatherSources(): ImmutableList<WeatherSource> = sourceList
        .filterIsInstance<WeatherSource>()
        .toImmutableList()

    // Secondary weather
    fun getPollenIndexSource(id: String): PollenIndexSource? = sourceList
        .filterIsInstance<PollenIndexSource>()
        .firstOrNull { it.id == id }

    // Location search
    fun getLocationSearchSources(): ImmutableList<LocationSearchSource> = sourceList
        .filterIsInstance<LocationSearchSource>()
        .toImmutableList()

    // Reverse geocoding
    fun getReverseGeocodingSources(): ImmutableList<ReverseGeocodingSource> = sourceList
        .filterIsInstance<ReverseGeocodingSource>()
        .filter { it.supportedFeatures.containsKey(SourceFeature.REVERSE_GEOCODING) }
        .toImmutableList()

    // Broadcast
    fun getBroadcastSources(): ImmutableList<BroadcastSource> = sourceList
        .filterIsInstance<BroadcastSource>()
        .toImmutableList()

    // Configurables sources
    fun getConfigurableSources(): ImmutableList<ConfigurableSource> = sourceList
        .filterIsInstance<ConfigurableSource>()
        .toImmutableList()
}
