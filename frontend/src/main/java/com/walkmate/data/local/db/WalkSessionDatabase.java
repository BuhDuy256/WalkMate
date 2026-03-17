package com.walkmate.data.local.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.walkmate.data.local.dao.SessionLocalDao;
import com.walkmate.data.local.dao.SessionPointLocalDao;
import com.walkmate.data.local.entity.SessionLocalEntity;
import com.walkmate.data.local.entity.SessionPointLocalEntity;

@Database(entities = { SessionLocalEntity.class, SessionPointLocalEntity.class }, version = 1, exportSchema = false)
public abstract class WalkSessionDatabase extends RoomDatabase {

    private static volatile WalkSessionDatabase INSTANCE;

    public static WalkSessionDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (WalkSessionDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            WalkSessionDatabase.class,
                            "walk_session.db")
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract SessionLocalDao sessionLocalDao();

    public abstract SessionPointLocalDao sessionPointLocalDao();
}
