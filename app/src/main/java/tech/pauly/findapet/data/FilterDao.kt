package tech.pauly.findapet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

import io.reactivex.Single
import tech.pauly.findapet.data.models.Filter

@Dao
interface FilterDao {

    @Query("SELECT * FROM filter WHERE id=:id")
    fun findById(id: Long?): Single<Filter>

    @Insert
    fun insert(filter: Filter): Long
}
