package tech.pauly.findapet.data

import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query
import tech.pauly.findapet.data.models.ShelterListResponse
import tech.pauly.findapet.data.models.SingleShelterResponse

interface ShelterService {
    @GET("shelter.find")
    fun fetchShelters(@Query("location") location: String,
                      @Query("key") key: String,
                      @Query("count") count: Int): Single<ShelterListResponse>

    @GET("shelter.get")
    fun fetchShelter(@Query("key") key: String,
                     @Query("id") id: String): Single<SingleShelterResponse>
}