package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;
import com.summersoft.heliocam.status.LogoutUser;
import com.summersoft.heliocam.status.SessionLoader;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private FirebaseAuth mAuth;
    private Handler handler = new Handler();
    private Runnable checkLoginStatusRunnable;
    private Runnable loadSessionsRunnable; // Add a Runnable for loading sessions
    private LinearLayout sessionCardContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();

        sessionCardContainer = findViewById(R.id.session_card_container);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainpage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        View hamburgerButton = findViewById(R.id.hamburgerButton);
        registerForContextMenu(hamburgerButton);

        hamburgerButton.setOnClickListener(v -> {
            Log.d(TAG, "Hamburger button clicked!");
            v.showContextMenu();
        });

        // OnClickListener for "addSession" button
        View addSessionButton = findViewById(R.id.addSession);
        addSessionButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, AddSessionActivity.class);
            startActivity(intent);
        });

        // Runnable for checking login status
        checkLoginStatusRunnable = new Runnable() {
            @Override
            public void run() {
                LoginStatus.checkLoginStatus(HomeActivity.this);
                handler.postDelayed(this, 1500);
            }
        };

        // Runnable for loading user sessions every 1.5 seconds
        loadSessionsRunnable = new Runnable() {
            @Override
            public void run() {
                SessionLoader sessionLoader = new SessionLoader(HomeActivity.this, sessionCardContainer);
                sessionLoader.loadUserSessions(); // Load sessions
                handler.postDelayed(this, 1500); // Repeat every 1.5 seconds
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.postDelayed(checkLoginStatusRunnable, 1500); // Start login check after 1.5 seconds
        handler.postDelayed(loadSessionsRunnable, 1500); // Start loading sessions after 1.5 seconds
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(checkLoginStatusRunnable); // Stop login check
        handler.removeCallbacks(loadSessionsRunnable); // Stop session loading
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (v.getId() == R.id.hamburgerButton) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_logout:
                LogoutUser.logoutUser();  // Logout user
                LoginStatus.checkLoginStatus(this); // Check login status
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
}


