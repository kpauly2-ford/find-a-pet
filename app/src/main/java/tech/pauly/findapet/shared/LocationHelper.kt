package tech.pauly.findapet.shared

import android.annotation.SuppressLint

import android.content.Context
import android.location.*
import android.os.Build
import android.os.Bundle
import android.support.annotation.VisibleForTesting
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import tech.pauly.findapet.BuildConfig
import tech.pauly.findapet.data.ObservableHelper
import tech.pauly.findapet.data.models.Contact
import tech.pauly.findapet.dependencyinjection.ForApplication
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private const val METERS_TO_MILES = 0.000621371192

@Singleton
open class LocationHelper @Inject
constructor(private val observableHelper: ObservableHelper,
            private val locationWrapper: LocationWrapper,
            private val localeWrapper: LocaleWrapper) {

    private val locationSubject = BehaviorSubject.create<Address>()

    open fun fetchCurrentLocation(): Observable<Address> {
        if (locationWrapper.isEmulator) {
            val address = Address(localeWrapper.getLocale()).apply {
                latitude = 42.465513
                longitude = -83.434321
                postalCode = "48335"
            }
            return Observable.just(address)
        }

        if (locationSubject.value == null) {
            locationWrapper.fetchNewLocation(Criteria.ACCURACY_FINE, ::updateForNewLocation)
        }
        return locationSubject
    }

    open fun getCurrentDistanceToContactInfo(contactInfo: Contact?): Observable<Int> {
        return locationSubject.map { address -> calculateDistanceBetween(address, contactInfo) }
                .compose(observableHelper.applyObservableSchedulers())
    }

    open fun updateForNewLocation(location: Location) {
        locationSubject.onNext(locationWrapper.getAddressFromLocation(location))
    }

    private fun calculateDistanceBetween(userAddress: Address, contactInfo: Contact?): Int? {
        val distance = -1
        if (contactInfo == null) {
            return distance
        }
        return try {
            val contactAddressName: String? = parseContactInfo(contactInfo)
            val petAddress = locationWrapper.getAddressFromName(contactAddressName)
            locationWrapper.locationBetweenAddresses(userAddress, petAddress)
        } catch (e: IOException) {
            e.printStackTrace()
            distance
        }
    }

    @VisibleForTesting
    fun parseContactInfo(contactInfo: Contact): String? {
        var contactAddressName: String? = ""
        if (contactInfo.city != null && contactInfo.state != null) {
            contactAddressName = if (contactInfo.address1 != null) {
                "${contactInfo.address1} ${contactInfo.city}, ${contactInfo.state}"
            } else {
                "${contactInfo.city}, ${contactInfo.state}"
            }
        } else if (contactInfo.zip != null) {
            contactAddressName = contactInfo.zip
        }
        return contactAddressName
    }
}

open class LocationWrapper @Inject
constructor(@ForApplication private val context: Context,
            private val localeWrapper: LocaleWrapper) {
    
    open val isEmulator: Boolean
        get() = BuildConfig.DEBUG
                && (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)

    @SuppressLint("MissingPermission")
    open fun fetchNewLocation(accuracy: Int, locationChangedListener: (location: Location) -> Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val criteria = Criteria().also { it.accuracy = accuracy }
        locationManager.requestSingleUpdate(criteria, object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationChangedListener(location)
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }, null)
    }

    open fun getAddressFromLocation(location: Location): Address {
        return Geocoder(context, localeWrapper.getLocale()).getFromLocation(location.latitude, location.longitude, 1)[0]
    }

    open fun getAddressFromName(contactAddressName: String?): Address {
        return Geocoder(context, localeWrapper.getLocale()).getFromLocationName(contactAddressName, 1)[0]
    }

    open fun locationBetweenAddresses(address1: Address, address2: Address): Int {
        val location1 = Location("user").apply { latitude = address1.latitude; longitude = address1.longitude }
        val location2 = Location("pet").apply { latitude = address2.latitude; longitude = address2.longitude }
        return (location1.distanceTo(location2) * METERS_TO_MILES).toInt()
    }
}