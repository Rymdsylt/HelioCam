package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.fragment.app.Fragment;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;
import com.summersoft.heliocam.status.LogoutUser;
import com.summersoft.heliocam.status.SessionLoader;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment"; // Change to reflect fragment's name
    private FirebaseAuth mAuth;
    private Handler handler = new Handler();
    private Runnable checkLoginStatusRunnable;
    private Runnable loadSessionsRunnable;
    private LinearLayout sessionCardContainer;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        mAuth = FirebaseAuth.getInstance();

        // Initialize UI components
        sessionCardContainer = rootView.findViewById(R.id.session_card_container);

        // Set padding for window insets
        ViewCompat.setOnApplyWindowInsetsListener(rootView.findViewById(R.id.mainpage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Register context menu for the hamburger button
        View hamburgerButton = rootView.findViewById(R.id.hamburgerButton);
        registerForContextMenu(hamburgerButton);

        hamburgerButton.setOnClickListener(v -> {
            Log.d(TAG, "Hamburger button clicked!");
            v.showContextMenu();
        });

        // OnClickListener for "addSession" button
        View addSessionButton = rootView.findViewById(R.id.addSession);
        addSessionButton.setOnClickListener(v -> {
            // Start AddSessionActivity
            Intent intent = new Intent(getActivity(), AddSessionActivity.class);
            startActivity(intent);
        });

        // Runnable for checking login status
        checkLoginStatusRunnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null && isAdded()) {
                    LoginStatus.checkLoginStatus(getActivity());
                }
                handler.postDelayed(this, 1500); // Repeat every 1.5 seconds
            }
        };

        // Runnable for loading user sessions every 1.5 seconds
        loadSessionsRunnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null && isAdded()) {
                    // Only proceed if fragment is added and activity is available
                    if (getActivity() instanceof HomeActivity) {
                        HomeActivity homeActivity = (HomeActivity) getActivity();
                        SessionLoader sessionLoader = new SessionLoader(homeActivity, sessionCardContainer);
                        sessionLoader.loadUserSessions();  // Load sessions
                    }
                }
                handler.postDelayed(this, 1500); // Repeat every 1.5 seconds
            }
        };

        return rootView;

    }

    @Override
    public void onStart() {
        super.onStart();
        // Start login check after 1.5 seconds
        handler.postDelayed(checkLoginStatusRunnable, 1500);
        // Start loading sessions after 1.5 seconds
        handler.postDelayed(loadSessionsRunnable, 1500);
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(checkLoginStatusRunnable); // Stop login check
        handler.removeCallbacks(loadSessionsRunnable); // Stop session loading
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (v.getId() == R.id.hamburgerButton) {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_logout:
                LogoutUser.logoutUser();  // Logout user
                LoginStatus.checkLoginStatus(getActivity()); // Check login status
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
}
