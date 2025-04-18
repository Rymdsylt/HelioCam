package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.summersoft.heliocam.R;

public class AccountSettingsFragment extends Fragment {

    private TextInputEditText etFullName, etUsername, etContact, etEmail;
    private MaterialButton btnSave, btnChangePassword, btnTwoFactorAuth, btnLogout;
    private ShapeableImageView ivProfileAvatar;
    private FloatingActionButton fabChangePhoto;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseRef;
    private StorageReference storageRef;
    private Uri selectedImageUri;

    // Activity result launcher for image selection
    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    ivProfileAvatar.setImageURI(uri);
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_account_settings, container, false);

        // Initialize Firebase services
        mAuth = FirebaseAuth.getInstance();
        databaseRef = FirebaseDatabase.getInstance().getReference("users");
        storageRef = FirebaseStorage.getInstance().getReference("profile_images");

        // Get the logged-in user
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "No user is logged in.", Toast.LENGTH_SHORT).show();
            return view;
        }

        String sanitizedEmail = currentUser.getEmail().replace(".", "_");

        // Initialize UI components
        etFullName = view.findViewById(R.id.etFullName);
        etUsername = view.findViewById(R.id.etUsername);
        etContact = view.findViewById(R.id.etContact);
        etEmail = view.findViewById(R.id.etEmail);
        ivProfileAvatar = view.findViewById(R.id.ivProfileAvatar);
        fabChangePhoto = view.findViewById(R.id.fabChangePhoto);
        btnSave = view.findViewById(R.id.btnSave);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnTwoFactorAuth = view.findViewById(R.id.btnTwoFactorAuth);
        btnLogout = view.findViewById(R.id.btnLogout);

        // Set email field to current user's email and disable editing
        etEmail.setText(currentUser.getEmail());
        etEmail.setEnabled(false);

        // Load profile image if exists
        StorageReference profileImageRef = storageRef.child(sanitizedEmail + ".jpg");
        profileImageRef.getDownloadUrl().addOnSuccessListener(uri -> {
            if (getContext() != null) {
                Glide.with(getContext())
                        .load(uri)
                        .placeholder(R.drawable.default_avatar)
                        .into(ivProfileAvatar);
            }
        }).addOnFailureListener(e -> {
            // Use default avatar if no custom image exists
            ivProfileAvatar.setImageResource(R.drawable.default_avatar);
        });

        // Populate fields on load
        databaseRef.child(sanitizedEmail).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                etFullName.setText(task.getResult().child("fullname").getValue(String.class));
                etUsername.setText(task.getResult().child("username").getValue(String.class));
                etContact.setText(task.getResult().child("contact").getValue(String.class));

                // Check if 2FA is enabled and update button text
                Boolean isTwoFactorEnabled = task.getResult().child("twoFactorEnabled").getValue(Boolean.class);
                if (isTwoFactorEnabled != null && isTwoFactorEnabled) {
                    btnTwoFactorAuth.setText("Disable Two-Factor Authentication");
                }
            } else {
                Toast.makeText(getContext(), "Failed to fetch account details.", Toast.LENGTH_SHORT).show();
            }
        });

        // Change profile photo
        fabChangePhoto.setOnClickListener(v -> mGetContent.launch("image/*"));

        // Save updated data to Firebase
        btnSave.setOnClickListener(v -> {
            String fullname = etFullName.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String contact = etContact.getText().toString().trim();

            if (TextUtils.isEmpty(fullname) || TextUtils.isEmpty(username) || TextUtils.isEmpty(contact)) {
                Toast.makeText(getContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show progress indicator
            btnSave.setEnabled(false);
            btnSave.setText("Saving...");

            // Update profile data
            databaseRef.child(sanitizedEmail).child("fullname").setValue(fullname);
            databaseRef.child(sanitizedEmail).child("username").setValue(username);
            databaseRef.child(sanitizedEmail).child("contact").setValue(contact);

            // Upload profile image if selected
            if (selectedImageUri != null) {
                uploadProfileImage(sanitizedEmail);
            } else {
                // If no new image, just update profile data
                btnSave.setEnabled(true);
                btnSave.setText("Save Changes");
                Toast.makeText(getContext(), "Account details updated successfully", Toast.LENGTH_SHORT).show();
            }
        });

        // Send password reset email with cooldown
        btnChangePassword.setOnClickListener(v -> {
            if (currentUser.getEmail() != null) {
                btnChangePassword.setEnabled(false);
                btnChangePassword.setText("Sending...");

                mAuth.sendPasswordResetEmail(currentUser.getEmail())
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(getContext(), "Password reset email sent", Toast.LENGTH_SHORT).show();
                                startCooldown();
                            } else {
                                Toast.makeText(getContext(), "Failed to send reset email", Toast.LENGTH_SHORT).show();
                                btnChangePassword.setEnabled(true);
                                btnChangePassword.setText("Change Password");
                            }
                        });
            } else {
                Toast.makeText(getContext(), "Email not found", Toast.LENGTH_SHORT).show();
            }
        });

        // Two-factor authentication toggle
        btnTwoFactorAuth.setOnClickListener(v -> {
            databaseRef.child(sanitizedEmail).child("twoFactorEnabled").get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Boolean isEnabled = task.getResult().getValue(Boolean.class);
                    boolean newValue = !(isEnabled != null && isEnabled);

                    databaseRef.child(sanitizedEmail).child("twoFactorEnabled").setValue(newValue)
                            .addOnSuccessListener(unused -> {
                                btnTwoFactorAuth.setText(newValue ?
                                        "Disable Two-Factor Authentication" :
                                        "Enable Two-Factor Authentication");

                                Toast.makeText(getContext(),
                                        "Two-factor authentication " + (newValue ? "enabled" : "disabled"),
                                        Toast.LENGTH_SHORT).show();
                            });
                }
            });
        });

        // Logout functionality
        /* btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            // Navigate to login activity or main activity
            // Replace MainActivity.class with your login activity
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

         */

        return view;
    }

    // Upload profile image to Firebase Storage
    private void uploadProfileImage(String userId) {
        if (selectedImageUri != null) {
            StorageReference fileRef = storageRef.child(userId + ".jpg");
            fileRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            // Update profile image URL in database
                            databaseRef.child(userId).child("profileImageUrl").setValue(uri.toString())
                                    .addOnCompleteListener(task -> {
                                        btnSave.setEnabled(true);
                                        btnSave.setText("Save Changes");
                                        Toast.makeText(getContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
                                    });
                        });
                    })
                    .addOnFailureListener(e -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("Save Changes");
                        Toast.makeText(getContext(), "Failed to upload image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // Cooldown logic for password reset
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