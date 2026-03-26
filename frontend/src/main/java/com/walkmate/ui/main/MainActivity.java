package com.walkmate.ui.main;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.walkmate.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Assuming activity_main layout exists. If not, this might be a blank stub for
        // now.
        setContentView(R.layout.activity_main);
    }
}
