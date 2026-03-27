package com.walkmate;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
  private static final String DEFAULT_USER_ID = "4a073971-67dd-4ff8-94fd-0d95d33d27cb";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_main);

    Intent intent = new Intent(this, com.walkmate.ui.profile.ProfileActivity.class);
    intent.putExtra("USER_ID", DEFAULT_USER_ID);
    intent.putExtra("VIEWER_ID", DEFAULT_USER_ID);
    intent.putExtra("PROFILE_OWNER_ID", DEFAULT_USER_ID);
    startActivity(intent);
    finish();
  }
}
