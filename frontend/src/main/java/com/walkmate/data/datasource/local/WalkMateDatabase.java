package com.walkmate.data.datasource.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;

@Database(entities = {RoutePointEntity.class}, version = 1, exportSchema = false)
public abstract class WalkMateDatabase extends RoomDatabase {
    
    public abstract RoutePointDao routePointDao();
    
    private static volatile WalkMateDatabase INSTANCE;
    
    public static WalkMateDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (WalkMateDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            WalkMateDatabase.class, "walkmate_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
