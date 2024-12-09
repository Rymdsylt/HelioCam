package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.R;

public class AccountSettingsFragment extends Fragment {

    private EditText etFullname, etUsername, etContact;
    private Button btnSave, btnChangePassword;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseRef;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_account_settings, container, false);

        // Initialize Firebase Auth and Database
        mAuth = FirebaseAuth.getInstance();
        databaseRef = FirebaseDatabase.getInstance().getReference("users");

        // Get the logged-in user
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "No user is logged in.", Toast.LENGTH_SHORT).show();
            return view;
        }

        String sanitizedEmail = currentUser.getEmail().replace(".", "_");

        // Initialize UI components
        etFullname = view.findViewById(R.id.EtFirstName);
        etUsername = view.findViewById(R.id.EtUsername);
        etContact = view.findViewById(R.id.EtContactValue);
        btnSave = view.findViewById(R.id.btnSave);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);

        // Populate fields on load
        databaseRef.child(sanitizedEmail).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                etFullname.setText(task.getResult().child("fullname").getValue(String.class));
                etUsername.setText(task.getResult().child("username").getValue(String.class));
                etContact.setText(task.getResult().child("contact").getValue(String.class));
            } else {
                Toast.makeText(getContext(), "Failed to fetch account details.", Toast.LENGTH_SHORT).show();
            }
        });

        // Save updated data to Firebase
        btnSave.setOnClickListener(v -> {
            String fullname = etFullname.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String contact = etContact.getText().toString().trim();

            if (TextUtils.isEmpty(fullname) || TextUtils.isEmpty(username) || TextUtils.isEmpty(contact)) {
                Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            databaseRef.child(sanitizedEmail).child("fullname").setValue(fullname);
            databaseRef.child(sanitizedEmail).child("username").setValue(username);
            databaseRef.child(sanitizedEmail).child("contact").setValue(contact)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(getContext(), "Account details updated successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "Failed to update account details", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Send password reset email with cooldown
        btnChangePassword.setOnClickListener(v -> {
            if (currentUser.getEmail() != null) {
                mAuth.sendPasswordResetEmail(currentUser.getEmail())
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(getContext(), "Password reset email sent", Toast.LENGTH_SHORT).show();
                                startCooldown();
                            } else {
                                Toast.makeText(getContext(), "Failed to send reset email", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toast.makeText(getContext(), "Email not found", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    // Cooldown logic
    private void startCooldown() {
        btnChangePassword.setEnabled(false);
        new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                btnChangePassword.setText("Retry in " + millisUntilFinished / 1000 + "s");
            }

            @Override
            public void onFinish() {
                btnChangePassword.setEnabled(true);
                btnChangePassword.setText("Change Password");
            }
        }.start();
    }
}


