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

package org.breezyweather.common.source

// location.
data class LocationPositionWrapper(
    val latitude: Double,
    val longitude: Double,
    val timeZone: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val admin1: String? = null,
    val admin1Code: String? = null,
    val admin2: String? = null,
    val admin2Code: String? = null,
    val admin3: String? = null,
    val admin3Code: String? = null,
    val admin4: String? = null,
    val admin4Code: String? = null,
    val city: String? = null,
    val district: String? = null,
)
