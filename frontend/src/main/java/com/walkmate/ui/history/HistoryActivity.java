package com.walkmate.ui.history;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvSessions;
    private SessionAdapter sessionAdapter;
    private List<Session> sessionList;
    private View cvBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Init views
        rvSessions = findViewById(R.id.rvSessions);
        cvBack = findViewById(R.id.cvBack);

        // Setup back button
        if (cvBack != null) {
            cvBack.setOnClickListener(v -> finish());
        }

        // Setup RecyclerView
        if (rvSessions != null) {
            rvSessions.setLayoutManager(new LinearLayoutManager(this));
            rvSessions.setNestedScrollingEnabled(false);

            // Generate mock data
            sessionList = new ArrayList<>();
            sessionList.add(new Session("04", "TUE", R.mipmap.ic_launcher, "Sophia K.", "Completed", "City Park", "Park Stroll", "32 min", "1.2 km", "3,150 steps", 5, null));
            sessionList.add(new Session("03", "MON", R.mipmap.ic_launcher, "James L.", "Completed", "Riverside Trail", "Morning Run", "48 min", "2.4 km", "3,700 steps", 4, null));
            sessionList.add(new Session("02", "SUN", R.mipmap.ic_launcher, "Mia T.", "No-show", "Downtown Loop", "City Walk", "0 min", "0 km", "0 steps", 0, "🚨 Partner didn't show up - Contact support"));
            sessionList.add(new Session("01", "SAT", R.mipmap.ic_launcher, "Aiko N.", "Completed", "Botanical Gardens", "Nature Walk", "40 min", "1.8 km", "2,400 steps", 5, null));
            sessionList.add(new Session("28", "FRI", R.mipmap.ic_launcher, "Carlos M.", "Cancelled", "Beach Walk", "Beach", "0 min", "0 km", "0 steps", 0, "✖ Walk was cancelled before it started"));
            sessionList.add(new Session("27", "THU", R.mipmap.ic_launcher, "Sophia K.", "Completed", "Old Town", "Heritage Walk", "62 min", "3.1 km", "4,100 steps", 4, null));

            // Set adapter
            sessionAdapter = new SessionAdapter(sessionList);
            rvSessions.setAdapter(sessionAdapter);
        }
    }
}