package com.walkmate;

import android.app.Application;

import com.walkmate.data.datasource.local.WalkMateDatabase;
import com.walkmate.data.repository.SessionRepositoryImpl;
import com.walkmate.domain.session.SessionRepository;

/**
 * Service Locator (Manual DI) cho WalkMate.
 * Tồn tại xuyên suốt vòng đời Application.
 */
public class WalkMateApplication extends Application {
    
    private SessionRepository sessionRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        // Khởi tạo Database và Repository dưới dạng Singleton cho vòng đời App
        WalkMateDatabase database = WalkMateDatabase.getInstance(this);
        sessionRepository = new SessionRepositoryImpl(database.routePointDao());
    }

    public SessionRepository getSessionRepository() {
        return sessionRepository;
    }
}
