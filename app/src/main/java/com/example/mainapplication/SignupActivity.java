package com.example.mainapplication;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private EditText etUsernameSignup, etEmailSignup, etPasswordSignup;
    private ImageView imgProfile;
    private Button btnUploadPic, btSignup;
    private CheckBox checkBox;
    private Uri imageUri;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private StorageReference storageRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.google.firebase.FirebaseApp.initializeApp(this);

        setContentView(R.layout.activity_signup);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etUsernameSignup = findViewById(R.id.etUsernameSignup);
        etEmailSignup = findViewById(R.id.etEmailSignup);
        etPasswordSignup = findViewById(R.id.etPasswordSignup);
        imgProfile = findViewById(R.id.imgProfile);
        btnUploadPic = findViewById(R.id.btnUploadPic);
        btSignup = findViewById(R.id.btSignup);
        checkBox = findViewById(R.id.checkBox);

        btnUploadPic.setEnabled(false);
        btnUploadPic.setText("Upload disabled(no billing)");

        btSignup.setOnClickListener(view -> registerUser());
    }
    private void registerUser() {
        String username = etUsernameSignup.getText().toString().trim();
        String email = etEmailSignup.getText().toString().trim();
        String password = etPasswordSignup.getText().toString().trim();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkBox.isChecked()) {
            Toast.makeText(this, "You must agree to the Terms & Conditions", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = mAuth.getCurrentUser();

                    if (firebaseUser != null) {
                        firebaseUser.sendEmailVerification();

                            saveUserToFirestore(firebaseUser.getUid(), username, email, "default");
                        }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(SignupActivity.this, "Signup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
    private void saveUserToFirestore(String uid, String username, String email, String imageUrl) {
        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("username", username);
        user.put("email", email);
        user.put("profileImage", imageUrl);

        db.collection("users").document(uid).set(user)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(SignupActivity.this, "Signup successful! Please verify your email.", Toast.LENGTH_LONG).show();
                    finish(); // back to login after signup
                })
                .addOnFailureListener(e ->
                        Toast.makeText(SignupActivity.this, "Error saving user: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
