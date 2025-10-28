package com.example.mainapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SignupActivity extends BaseActivity {

    private EditText etUsernameSignup, etEmailSignup, etPasswordSignup;
    private ImageView imgProfile;
    private Button btnUploadPic, btSignup;
    private CheckBox checkBox;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_signup);

        // ✅ Toolbar setup
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Create Account");
            // Handles back arrow click
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        // 🔥 Firebase setup
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 🎨 UI references
        etUsernameSignup = findViewById(R.id.etUsernameSignup);
        etEmailSignup = findViewById(R.id.etEmailSignup);
        etPasswordSignup = findViewById(R.id.etPasswordSignup);
        imgProfile = findViewById(R.id.imgProfile);
        btnUploadPic = findViewById(R.id.btnUploadPic);
        btSignup = findViewById(R.id.btSignup);
        checkBox = findViewById(R.id.checkBox);

        // Disable upload until billing/storage added
        if (btnUploadPic != null) {
            btnUploadPic.setEnabled(false);
            btnUploadPic.setText("Upload disabled (no billing)");
        }

        // 🪄 Signup button click
        btSignup.setOnClickListener(v -> registerUser());
    }

    // Handles toolbar back button (if pressed via system)
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 🔐 Register new user
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
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = mAuth.getCurrentUser();
                    if (firebaseUser == null) {
                        Toast.makeText(this, "Signup failed: user is null", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    firebaseUser.sendEmailVerification();

                    saveUserToFirestore(firebaseUser.getUid(), username, email, "default");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Signup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // 🧩 Save user record in Firestore
    private void saveUserToFirestore(String uid, String username, String email, String imageUrl) {
        long now = System.currentTimeMillis();

        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("username", username);
        user.put("email", email);
        user.put("profileImage", imageUrl);
        user.put("online", true);
        user.put("lastActive", now);
        user.put("typingTo", "");

        db.collection("users").document(uid)
                .set(user, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(SignupActivity.this,
                            "Account created! Verification email sent.", Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(SignupActivity.this, ChatListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(SignupActivity.this,
                                "Error saving user: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
