package com.personal.storelocation;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "location_table")
public class LocationEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public double latitude;
    public double longitude;
    public long timestamp;

    @ColumnInfo(name = "is_checkpoint")
    public boolean isCheckpoint;

    @Nullable
    @ColumnInfo(name = "note")
    public String note;

}

