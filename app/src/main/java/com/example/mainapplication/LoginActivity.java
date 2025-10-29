package com.example.mainapplication;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends BaseActivity {

    private EditText etEmail, etPassword;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // --- Google Sign-In setup ---
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // --- UI elements ---
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnSignup = findViewById(R.id.btnSignupLogin);
        Button btnPassreset = findViewById(R.id.btnPassreset);
        Button btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        // --- Button listeners ---
        btnLogin.setOnClickListener(v -> loginUser());
        btnSignup.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, SignupActivity.class)));
        btnPassreset.setOnClickListener(v -> Toast.makeText(this, "Forgot password feature coming soon!", Toast.LENGTH_SHORT).show());
        btnGoogleLogin.setOnClickListener(v -> signInWithGoogle());
    }

    // --- Normal email/password login ---
    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            createOrUpdateUserDocument(user);
                            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, ChatListActivity.class));
                            finish();
                        }
                    } else {
                        Toast.makeText(this, "Login failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // --- Google sign-in flow ---
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            createOrUpdateUserDocument(user);
                            Toast.makeText(this, "Welcome " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, ChatListActivity.class));
                            finish();
                        }
                    } else {
                        Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void createOrUpdateUserDocument(FirebaseUser user) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", user.getUid());
        userMap.put("email", user.getEmail());


        String displayName = user.getDisplayName();
        String fallbackName = user.getEmail() != null ? user.getEmail().split("@")[0] : "User";
        userMap.put("username", (displayName != null && !displayName.trim().isEmpty()) ? displayName : fallbackName);

        userMap.put("online", true);
        userMap.put("lastActive", System.currentTimeMillis());
        userMap.put("lastMessage", "");
        userMap.put("lastMessageTime", 0L);
        userMap.put("typingTo", "");

        // Merge instead of overwrite existing data
        db.collection("users").document(user.getUid())
                .set(userMap, SetOptions.merge());
    }
}
