package com.example.mainapplication;

import androidx.annotation.NonNull;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SignupActivity extends BaseActivity {

    private EditText etUsernameSignup, etEmailSignup, etPasswordSignup;
    private Button btSignup, btBackToLogin;
    private CheckBox checkBox;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_signup);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Create Account");
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etUsernameSignup = findViewById(R.id.etUsernameSignup);
        etEmailSignup = findViewById(R.id.etEmailSignup);
        etPasswordSignup = findViewById(R.id.etPasswordSignup);
        btSignup = findViewById(R.id.btSignup);
        btBackToLogin = findViewById(R.id.btBackToLogin);
        checkBox = findViewById(R.id.checkBox);

        btSignup.setOnClickListener(v -> registerUser());

        Button btnGotoLogin = findViewById(R.id.btBackToLogin);
        btnGotoLogin.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = mAuth.getCurrentUser();
                    if (firebaseUser == null) {
                        Toast.makeText(this, "Signup failed: user is null", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    firebaseUser.sendEmailVerification();
                    saveUserToFirestore(firebaseUser.getUid(), username, email);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Signup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveUserToFirestore(String uid, String username, String email) {
        long now = System.currentTimeMillis();

        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("username", username);
        user.put("email", email);
        user.put("online", true);
        user.put("lastActive", now);
        user.put("lastMessage", "");
        user.put("lastMessageTime", 0L);
        user.put("typingTo", "");

        db.collection("users").document(uid)
                .set(user, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(SignupActivity.this,
                            "Account created successfully!", Toast.LENGTH_LONG).show();

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
