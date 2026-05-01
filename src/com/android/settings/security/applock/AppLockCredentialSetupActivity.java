package com.android.settings.security.applock;

import android.app.Activity;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.InputType;
import android.text.TextUtils;
import java.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.android.internal.app.AppLockUtils;
import com.android.internal.widget.LockPatternView;
import com.android.settings.R;

import lineageos.providers.LineageSettings;

import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

public class AppLockCredentialSetupActivity extends Activity {

    public static final String EXTRA_TYPE = "extra_type";

    private ViewFlipper mFlipper;
    private EditText mPasswordInput;
    private LockPatternView mPatternInput;
    private TextView mSetupTitle;
    private Button mSaveButton;
    private Button mCancelButton;

    private int mSelectedType = 0; // 1=PIN, 2=Password, 3=Pattern
    private String mPatternString = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_lock_credential_setup);

        mSelectedType = getIntent().getIntExtra(EXTRA_TYPE, 1);

        mFlipper = findViewById(R.id.input_flipper);
        mPasswordInput = findViewById(R.id.password_input);
        mPatternInput = findViewById(R.id.pattern_input);
        mSetupTitle = findViewById(R.id.setup_title);
        mSaveButton = findViewById(R.id.btn_save);
        mCancelButton = findViewById(R.id.btn_cancel);

        if (mSelectedType == 3) {
            mSetupTitle.setText("Set pattern");
            mFlipper.setDisplayedChild(1);
        } else {
            if (mSelectedType == 1) {
                mSetupTitle.setText("Set PIN");
                mPasswordInput.setHint("Enter PIN");
                mPasswordInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            } else {
                mSetupTitle.setText("Set password");
                mPasswordInput.setHint("Enter password");
                mPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            mFlipper.setDisplayedChild(0);
            mPasswordInput.requestFocus();
        }

        mPatternInput.setOnPatternListener(new LockPatternView.OnPatternListener() {
            @Override
            public void onPatternDetected(List<LockPatternView.Cell> pattern, byte patternSize) {
                if (pattern.size() < 4) {
                    mPatternInput.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                    Toast.makeText(AppLockCredentialSetupActivity.this, "Connect at least 4 dots", Toast.LENGTH_SHORT).show();
                    mPatternString = null;
                    return;
                }
                mPatternString = lockPatternToString(pattern);
            }
        });

        mSaveButton.setOnClickListener(v -> saveCredential());
        mCancelButton.setOnClickListener(v -> finish());
    }

    private void saveCredential() {
        String credentialToSave = null;

        if (mSelectedType == 3) { // Pattern
            if (mPatternString == null) {
                Toast.makeText(this, "Please draw a pattern", Toast.LENGTH_SHORT).show();
                return;
            }
            credentialToSave = mPatternString;
        } else { // PIN or Password
            credentialToSave = mPasswordInput.getText().toString();
            if (TextUtils.isEmpty(credentialToSave)) {
                Toast.makeText(this, "Please enter a credential", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mSelectedType == 1 && credentialToSave.length() < 4) {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        try {
            String salt = UUID.randomUUID().toString();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            byte[] hash = md.digest(credentialToSave.getBytes());
            String encodedHash = Base64.getEncoder().encodeToString(hash);
            
            LineageSettings.Secure.putIntForUser(getContentResolver(),
                    AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_CUSTOM_TYPE,
                    mSelectedType, UserHandle.myUserId());
            LineageSettings.Secure.putStringForUser(getContentResolver(),
                    AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_CUSTOM_SALT,
                    salt, UserHandle.myUserId());
            LineageSettings.Secure.putStringForUser(getContentResolver(),
                    AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_CUSTOM_PASSWORD,
                    encodedHash, UserHandle.myUserId());
                    
            Toast.makeText(this, "App Lock credential saved", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error saving credential", Toast.LENGTH_SHORT).show();
        }
    }

    private String lockPatternToString(List<LockPatternView.Cell> pattern) {
        if (pattern == null) {
            return "";
        }
        final int patternSize = pattern.size();
        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            LockPatternView.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * 3 + cell.getColumn() + '1');
        }
        return new String(res);
    }
}
