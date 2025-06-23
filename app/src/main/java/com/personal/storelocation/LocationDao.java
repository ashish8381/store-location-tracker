package com.personal.storelocation;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LocationDao {
    @Insert
    void insert(LocationEntity location);

    @Query("SELECT * FROM location_table ORDER BY timestamp ASC")
    List<LocationEntity> getAllLocations();

    @Query("SELECT * FROM location_table WHERE timestamp BETWEEN :start AND :end")
    List<LocationEntity> getLocationsBetween(long start, long end);

    @Update
    void update(LocationEntity location);

    @Delete
    void delete(LocationEntity location);


}
