package com.smddzcy.eventmanager;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {
    private EditText mUsernameView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    private static final Gson gson = new Gson();

    public void showToast(String message) {
        showToast(message, Toast.LENGTH_SHORT);
    }

    public void showToast(String message, int toastLength) {
        Toast.makeText(LoginActivity.this, message, toastLength).show();
    }

    public static String md5(String s) {
        final String MD5 = "MD5";
        try {
            // create MD5 hash
            MessageDigest digest = java.security.MessageDigest
                    .getInstance(MD5);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // create hex string
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                StringBuilder h = new StringBuilder(Integer.toHexString(0xFF & aMessageDigest));
                while (h.length() < 2) h.insert(0, "0");
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    private class LoginTask extends AsyncTask<Void, Void, String> {
        JSONObject msg;
        String successMsg;
        View view;
        ProgressDialog dialog;
        private Socket socket = null;
        private BufferedWriter out;
        private BufferedReader in;

        LoginTask(View view, JSONObject msg, String successMsg) {
            dialog = new ProgressDialog(LoginActivity.this);
            this.view = view;
            this.msg = msg;
            this.successMsg = successMsg;
        }

        @Override
        protected void onPreExecute() {
            dialog.setMessage("Logging in...");
            dialog.show();
        }

        @Override
        protected String doInBackground(Void... voids) {
            return SocketUtils.sendMessage(this.msg.toString());
        }

        @Override
        protected void onPostExecute(final String result) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            try {
                Models.SuccessMessage msg = gson.fromJson(result, Models.SuccessMessage.class);
                showToast(this.successMsg);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(view.getContext(), MainActivity.class);
                        intent.putExtra("username", mUsernameView.getText().toString());
                        startActivity(intent);
                    }
                });
            } catch (Exception e) {
                try {
                    Models.FailMessage msg = gson.fromJson(result, Models.FailMessage.class);
                    showToast(msg.payload);
                } catch (Exception ex) {
                    showToast("Your message couldn't be sent to the server");
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        mUsernameView = findViewById(R.id.username);
        mPasswordView = findViewById(R.id.password);

        Button loginBtn = findViewById(R.id.login_button);
        loginBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                // Reset errors.
                mUsernameView.setError(null);
                mPasswordView.setError(null);

                // Store values at the time of the login attempt.
                String username = mUsernameView.getText().toString();
                String password = mPasswordView.getText().toString();

                boolean cancel = false;
                View focusView = null;

                // Check for a non-empty password.
                if (TextUtils.isEmpty(password)) {
                    mPasswordView.setError(getString(R.string.error_field_required));
                    focusView = mPasswordView;
                    cancel = true;
                }

                // Check for a non-empty username.
                if (TextUtils.isEmpty(username)) {
                    mUsernameView.setError(getString(R.string.error_field_required));
                    focusView = mUsernameView;
                    cancel = true;
                }

                if (cancel) {
                    // There was an error; don't attempt login and focus the first
                    // form field with an error.
                    focusView.requestFocus();
                } else {
                    try {
                        new LoginTask(
                                view,
                                new JSONObject()
                                        .put("type", "login")
                                        .put("payload", mUsernameView.getText().toString() + ":::" + md5(mPasswordView.getText().toString())),
                                "Successfully logged in."
                        ).execute();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
    }
}

