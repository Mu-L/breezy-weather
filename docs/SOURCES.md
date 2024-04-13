# Weather sources

This is a user-end guide to weather sources available in Breezy Weather. If you are a developer looking to add a new source in Breezy Weather, have a look at [contribute](../CONTRIBUTE.md). Unless otherwise mentioned, **the information below is valid assuming you’re using version 5.2.0 or later of Breezy Weather**.

By default, when you add a location manually, Breezy Weather will auto-suggest your national weather source if we have support for it, and combine it with other secondary weather sources for missing features.

When we don’t have support for your national weather source, we suggest **Open-Meteo** which is the only free and open source weather source on this list, and probably also the most privacy-friendly. It is nearly as complete as **AccuWeather**, and usually more accurate for many countries, however still lacks a few features (station observations, alerts, reverse geocoding), which is why AccuWeather is also suggested for alerts and normals for some countries.

For United States of America, the [Forecast Advisor website](https://www.forecastadvisor.com/) has temperature and precipitation 1-3 days accuracy comparison by city of the following sources: AccuWeather, NWS, Open-Meteo and Pirate Weather.

Below, you can find details about the support and implementation status for features on each weather source.


## Status

| Worldwide sources¹ | Open-Meteo | AccuWeather | MET Norway | Pirate Weather | HERE     | Météo-France | DMI  |
|--------------------|------------|-------------|------------|----------------|----------|--------------|------|
| **API key**        | None       | Optional    | None       | Required       | Required | Optional     | None |

| National sources | China² | NWS  | GeoSphere Austria  | Bright Sky | ECCC   | IMS                           | SMHI   | MET Éireann |
|------------------|--------|------|--------------------|------------|--------|-------------------------------|--------|-------------|
| **API key**      | None   | None | None               | None       | None   | None                          | None   | None        |
| **Countries**    | China  | USA  | Austria and nearby | Germany    | Canada | Israel, West Bank, Gaza Strip | Sweden | Ireland     |

* ¹ Some features may not be available everywhere.
* ² Aggregated data from Beijing Meteorological Service, ColorfulClouds (Caiyun) and CNEMC


## Main features supported by each source

Sources with mandatory API key to use are at the bottom of this page.

| Worldwide sources             | Open-Meteo | AccuWeather | MET Norway | Météo-France | DMI |
|-------------------------------|------------|-------------|------------|--------------|-----|
| **Daily (days)**              | 15         | 15          | ~10        | 14           | 10  |
| **Hourly (days)**             | 16         | 10          | ~10        | 15           | 10  |
| **Weather**                   | ✅          | ✅           | ✅          | ✅            | ✅   |
| **Temperature**               | ✅          | ✅           | ✅          | ✅            | ✅   |
| **Precipitation**             | ✅          | ✅ (RSI)     | ✅          | ✅ (RS)       | ✅   |
| **Precipitation probability** | ✅          | ✅ (TRSI)    | ✅ (T)      | ✅ (RSI)      | ❌   |
| **Wind**                      | ✅          | ✅           | ✅          | ✅            | ✅   |
| **UV**                        | ✅          | ✅           | ✅          | ✅            | ❌   |
| **Sun & Moon & Moon phase**   | ✅          | ✅           | ✅          | ✅            | ✅   |

| National sources              | China | NWS    | GeoSphere Austria | Bright Sky | ECCC | IMS           | SMHI | MET Éireann |
|-------------------------------|-------|--------|-------------------|------------|------|---------------|------|-------------|
| **Daily (days)**              | 15    | 7      | 2.5               | 10         | 6    | 6             | 15   | 7           |
| **Hourly (days)**             | 1     | 7      | 2.5               | 10         | 1    | 6             | 15   | 7           |
| **Weather**                   | ✅     | ✅      | ✅                 | ✅          | ✅    | *In progress* | ✅    | ✅           |
| **Temperature**               | ✅     | ✅      | ✅                 | ✅          | ✅    | ✅             | ✅    | ✅           |
| **Precipitation**             | ❌     | ✅ (SI) | ✅                 | ✅          | ❌    | ❌             | ✅    | ✅           |
| **Precipitation probability** | Daily | ✅ (T)  | ❌                 | ✅          | ✅    | ✅             | T    | ❌           |
| **Wind**                      | ✅     | ✅      | ✅                 | ✅          | ✅    | ✅             | ✅    | ✅           |
| **UV**                        | ❌     | ❌      | ❌                 | ❌          | ❌    | ✅             | ❌    | ❌           |
| **Sun & Moon & Moon phase**   | ✅     | ✅      | ✅                 | ✅          | ✅    | ✅             | ✅    | ✅           |

Note that no forecast above 7 days is reliable, so you should not decide based on the highest number of days available.


## Features that can be added from other sources

The following features, if not available from your selected source, can be added from another source.

| Worldwide sources            | Open-Meteo | AccuWeather   | MET Norway    | Météo-France | DMI     |
|------------------------------|------------|---------------|---------------|--------------|---------|
| **Air quality**              | ✅          | ✅             | Norway        | ❌            | ❌       |
| **Pollen**                   | ✅          | North America | ❌             | ❌            | ❌       |
| **Precipitation nowcasting** | ✅          | ✅             | Nordic area   | France       | ❌       |
| **Alerts**                   | ❌          | ✅             | *In progress* | France       | Denmark |
| **Normals**                  | Average    | ✅             | Average       | ✅            | Average |

| National sources             | China   | NWS           | GeoSphere Austria  | Bright Sky | ECCC   | IMS     | SMHI    | MET Éireann |
|------------------------------|---------|---------------|--------------------|------------|--------|---------|---------|-------------|
| **Air quality**              | Current | ❌             | Europe and nearby  | ❌          | ❌      | ❌       | ❌       | ❌           |
| **Pollen**                   | ❌       | ❌             | ❌                  | ❌          | ❌      | ❌       | ❌       | ❌           |
| **Precipitation nowcasting** | ✅       | ❌             | Austria and nearby | ❌          | ❌      | ❌       | ❌       | ❌           |
| **Alerts**                   | China   | United States | Austria            | Germany    | Canada | ✅       | ❌       | Ireland     |
| **Normals**                  | Average | Average       | *In progress*      | Average    | ✅      | Average | Average | Average     |


Legend:

| Letter | Meaning      |
|--------|--------------|
| R      | Rain         |
| T      | Thunderstorm |
| S      | Snow         |
| I      | Ice          |


## Other weather data

| Worldwide sources          | Open-Meteo | AccuWeather | MET Norway | Météo-France | DMI |
|----------------------------|------------|-------------|------------|--------------|-----|
| **Humidity**               | ✅          | ✅           | ✅          | ✅            | ✅   |
| **Dew point**              | ✅          | ✅           | ✅          | ✅            | ✅   |
| **Pressure**               | ✅          | Current     | ✅          | ✅            | ✅   |
| **Cloud cover**            | ✅          | ✅           | ✅          | ✅            | ❌   |
| **Visibility**             | ✅          | ✅           | ❌          | ❌            | ✅   |
| **Ceiling**                | ❌          | ✅           | ❌          | ❌            | ❌   |
| **Precipitation duration** | ❌          | ✅ (RSI)     | ❌          | ❌            | ❌   |
| **Sunshine duration**      | ✅          | ✅           | ❌          | ❌            | ❌   |

| National sources           | China   | NWS | GeoSphere Austria | Bright Sky | ECCC    | IMS | SMHI | MET Éireann |
|----------------------------|---------|-----|-------------------|------------|---------|-----|------|-------------|
| **Humidity**               | Current | ✅   | ✅                 | ✅          | Current | ✅   | ✅    | ✅           |
| **Dew point**              | Current | ✅   | ✅                 | ✅          | Current | ✅   | ✅    | ✅           |
| **Pressure**               | ❌       | ✅   | ✅                 | ✅          | Current | ❌   | ✅    | ✅           |
| **Cloud cover**            | ❌       | ✅   | ✅                 | ✅          | ❌       | ❌   | ❌    | ❌           |
| **Visibility**             | Current | ✅   | ❌                 | ✅          | Current | ❌   | ✅    | ❌           |
| **Ceiling**                | ❌       | ❌   | ❌                 | ❌          | ❌       | ❌   | ❌    | ❌           |
| **Precipitation duration** | ❌       | ❌   | ❌                 | ❌          | ❌       | ❌   | ❌    | ❌           |
| **Sunshine duration**      | ❌       | ❌   | ❌                 | ✅          | ✅       | ❌   | ❌    | ❌           |

¹ Median from daily forecast


## Location

| Worldwide sources     | Open-Meteo | AccuWeather | MET Norway | Météo-France | DMI     |
|-----------------------|------------|-------------|------------|--------------|---------|
| **Search**            | ✅          | ✅           | Default    | Default      | Default |
| **Reverse geocoding** | ❌²         | ✅           | ❌²         | ✅²           | ✅       |

| National sources      | China | NWS     | GeoSphere Austria | Bright Sky | ECCC    | IMS     | SMHI    | MET Éireann |
|-----------------------|-------|---------|-------------------|------------|---------|---------|---------|-------------|
| **Search**            | ✅³    | Default | Default           | Default    | Default | Default | Default | Default     |
| **Reverse geocoding** | ✅³    | ✅       | ❌²                | ❌²         | ✅²      | ✅⁴      | ❌²      | ✅⁵          |

* ¹ Default means it will use the configured location search source in settings. By default, it is Open-Meteo.
* ² TimeZone is assumed to be the same as device
* ³ TimeZone is assumed to be Asia/Shanghai
* ⁴ TimeZone is assumed to be Asia/Jerusalem
* ⁵ TimeZone is assumed to be Europe/Dublin


# Additional sources with mandatory API key

## Main features

| Worldwide sources             | Pirate Weather | HERE |
|-------------------------------|----------------|------|
| **Daily (days)**              | 8              | 6    |
| **Hourly (days)**             | 2              | 6    |
| **Weather**                   | ✅              | ✅    |
| **Temperature**               | ✅              | ✅    |
| **Precipitation**             | ✅ (RS)         | ✅    |
| **Precipitation probability** | ✅              | ✅    |
| **Wind**                      | ✅              | ✅    |
| **UV**                        | ✅              | ✅    |
| **Sun & Moon & Moon phase**   | ✅              | ✅    |

## Features that can be added from other sources

| Worldwide sources            | Pirate Weather | HERE     |
|------------------------------|----------------|----------|
| **Air quality**              | ❌              | ❌        |
| **Pollen**                   | ❌              | ❌        |
| **Precipitation nowcasting** | ✅              | ❌        |
| **Alerts**                   | ✅              | ❌        |
| **Normals**                  | Average¹       | Average¹ |

¹ Median from daily forecast

## Other weather data

| Worldwide sources          | Pirate Weather | HERE |
|----------------------------|----------------|------|
| **Humidity**               | ✅              | ✅    |
| **Dew point**              | ✅              | ✅    |
| **Pressure**               | ✅              | ✅    |
| **Cloud cover**            | ✅              | ❌    |
| **Visibility**             | Current        | ✅    |
| **Ceiling**                | ❌              | ❌    |
| **Precipitation duration** | ❌              | ❌    |
| **Sunshine duration**      | ❌              | ❌    |
| **Search**                 | Default        | ✅    |
| **Reverse geocoding**      | ❌¹             | ✅    |

* ¹ TimeZone is assumed to be the same as device


# Combinable sources

| Worldwide sources            | Open-Meteo | AccuWeather | MET Norway    | Pirate Weather | Météo-France | DMI           |
|------------------------------|------------|-------------|---------------|----------------|--------------|---------------|
| **Air quality**              | ✅          | ✅           | Norway        | ❌              | ❌            | ❌             |
| **Pollen**                   | ✅²         | ✅           | ❌             | ❌              | ❌            | ❌             |
| **Precipitation nowcasting** | ✅³         | ✅           | Nordic area   | ✅              | France       | ❌             |
| **Alerts**                   | ❌          | ✅           | *In progress* | ✅              | France       | *In progress* |
| **Normals**                  | ❌          | ✅           | ❌             | ❌              | ✅⁴           | ❌             |

| National sources             | China | NWS           | GeoSphere Austria  | WMO Severe Weather | Bright Sky | ECCC   | IMS                           | MET Éireann | ATMO AuRA     |
|------------------------------|-------|---------------|--------------------|--------------------|------------|--------|-------------------------------|-------------|---------------|
| **Air quality**              | China | ❌             | Europe and nearby  | ❌                  | ❌          | ❌      | ❌                             | ❌           | France (AuRA) |
| **Pollen**                   | ❌     | ❌             | ❌                  | ❌                  | ❌          | ❌      | ❌                             | ❌           | ❌             |
| **Precipitation nowcasting** | China | ❌             | Austria and nearby | ❌                  | ❌          | ❌      | ❌                             | ❌           | ❌             |
| **Alerts**                   | China | United States | Austria            | ✅                  | Germany    | Canada | Israel, West Bank, Gaza Strip | Ireland     | ❌             |
| **Normals**                  | ❌     | ❌             | *In progress*      | ❌                  | ❌          | Canada | ❌                             | ❌           | ❌             |

* ¹ Only supports NWS alerts, but has many duplicate issues, so not worth implementing
* ² Not restricted but currently only works in Europe
* ³ Works best in Europe at the moment
