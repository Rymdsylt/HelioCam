package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;

public class ProfileFragment extends Fragment {

    private TextView emailView, fullnameView, usernameView, contactView, topUserView;
    private FirebaseAuth mAuth;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize FirebaseAuth
        mAuth = FirebaseAuth.getInstance();

        // Initialize TextViews
        emailView = view.findViewById(R.id.email);
        fullnameView = view.findViewById(R.id.fullname);
        usernameView = view.findViewById(R.id.username);
        topUserView = view.findViewById(R.id.username_top);
        contactView = view.findViewById(R.id.contact);

        // Get the current user's email
        String userEmail = mAuth.getCurrentUser().getEmail();

        // Fetch data from Firebase using the logged-in user's email
        FirebaseDatabase.getInstance().getReference("users")
                .child(userEmail.replace(".", "_")) // Firebase key format (email as key)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        // Data retrieved successfully
                        if (dataSnapshot.exists()) {
                            String fullname = dataSnapshot.child("fullname").getValue(String.class);
                            String username = dataSnapshot.child("username").getValue(String.class);
                            String contact = dataSnapshot.child("contact").getValue(String.class);
                            String email = dataSnapshot.child("email").getValue(String.class);

                            // Set data to the TextViews

                            topUserView.setText(username);

                            emailView.setText("Email: "+ email);
                            fullnameView.setText("Fullname: " + fullname);
                            usernameView.setText("Username: " +username);
                            contactView.setText("Contact: " +contact);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        // Handle errors if any
                    }
                });

        return view;
    }
}
