package com.personal.storelocation;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {LocationEntity.class}, version = 3)
public abstract class AppDatabase extends RoomDatabase {
    public abstract LocationDao locationDao();
}
