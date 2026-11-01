package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import java.io.File;
import java.util.regex.Pattern;

public class LocalLoginFragment extends Fragment {
    public static final String TAG = "LOCAL_LOGIN_FRAGMENT";

    private final Pattern mUsernameValidationPattern;
    private EditText mUsernameEditText;

    public LocalLoginFragment(){
        super(R.layout.fragment_local_login);
        mUsernameValidationPattern = Pattern.compile("^[a-zA-Z0-9_]*$");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.login_edit_email);
        view.findViewById(R.id.login_button).setOnClickListener(v -> {
            String username = mUsernameEditText.getText().toString().trim();
            mUsernameEditText.setText(username);
            Context context = v.getContext();
            if (username.length() < 3 || username.length() > 16
                    || !mUsernameValidationPattern.matcher(username).matches()) {
                Tools.dialog(context, context.getString(R.string.local_login_bad_username_title), context.getString(R.string.local_login_bad_username_text));
                return;
            }
            if (new File(Tools.DIR_ACCOUNT_NEW, username + ".json").exists()) {
                Tools.dialog(context, context.getString(R.string.local_login_existing_account_title),
                        context.getString(R.string.local_login_existing_account_text, username));
                return;
            }
            if (requireActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).completeMojangLogin(username, "", null);
            } else {
                ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO,
                        new String[]{username, ""});
            }

            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
            if (requireActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).maybeRunStartupPrompts();
            }
        });
    }


}
