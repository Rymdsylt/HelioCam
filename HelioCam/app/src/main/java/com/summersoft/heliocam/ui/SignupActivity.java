package com.summersoft.heliocam.ui;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.summersoft.heliocam.R;
import com.summersoft.heliocam.utils.UserModel;

public class SignupActivity extends AppCompatActivity {

    private EditText fullname, username, contact, email, password;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        databaseRef = FirebaseDatabase.getInstance().getReference("users");  // Store all users here, no role-based separation.


        fullname = findViewById(R.id.fullname);
        username = findViewById(R.id.username);
        contact = findViewById(R.id.contact);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);

        findViewById(R.id.enterBtn).setOnClickListener(v -> signUpUser());
    }

    private void signUpUser() {
        String emailText = email.getText().toString().trim();
        String passwordText = password.getText().toString().trim();
        String fullnameText = fullname.getText().toString().trim();
        String usernameText = username.getText().toString().trim();
        String contactText = contact.getText().toString().trim();


        if (emailText.isEmpty() || passwordText.isEmpty() || fullnameText.isEmpty() || usernameText.isEmpty() || contactText.isEmpty()) {
            Toast.makeText(SignupActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }


        checkEmailExists(emailText, fullnameText, usernameText, contactText, passwordText);
    }

    private void checkEmailExists(final String email, final String fullname, final String username, final String contact, final String password) {
        String sanitizedEmail = email.replace(".", "_");


        databaseRef.child(sanitizedEmail).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (task.getResult().exists()) {
                    Toast.makeText(SignupActivity.this, "Account already exists", Toast.LENGTH_SHORT).show();
                } else {
                    createUserWithEmail(email, fullname, username, contact, password);
                }
            } else {
                Toast.makeText(SignupActivity.this, "Error checking email in database", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createUserWithEmail(String email, String fullname, String username, String contact, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification()
                                    .addOnCompleteListener(task1 -> {
                                        if (task1.isSuccessful()) {
                                            Toast.makeText(SignupActivity.this, "Verification email sent. Please check your inbox.", Toast.LENGTH_LONG).show();
                                            storeUserData(email, fullname, username, contact);  // Store user data after verification
                                        } else {
                                            Toast.makeText(SignupActivity.this, "Failed to send verification email.", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    } else {
                        Toast.makeText(SignupActivity.this, "Authentication failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void storeUserData(String email, String fullname, String username, String contact) {
        String sanitizedEmail = email.replaceAll("[.#$\\[\\]]", "_");

        UserModel user = new UserModel(fullname, username, contact, email);
        databaseRef.child(sanitizedEmail).setValue(user)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(SignupActivity.this, "User signed up successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SignupActivity.this, "Failed to store user data", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
