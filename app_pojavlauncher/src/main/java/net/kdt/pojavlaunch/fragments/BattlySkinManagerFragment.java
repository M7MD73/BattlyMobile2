package net.kdt.pojavlaunch.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.BattlyCapeApi;
import net.kdt.pojavlaunch.utils.BattlySkinApi;
import net.kdt.pojavlaunch.utils.BattlySkinPreviewRenderer;
import net.kdt.pojavlaunch.utils.OfflineSkinManager;
import net.kdt.pojavlaunch.value.MinecraftAccount;

public class BattlySkinManagerFragment extends Fragment {
    public static final String TAG = "BattlySkinManagerFragment";

    private ActivityResultLauncher<String[]> mSkinPicker;
    private ActivityResultLauncher<String[]> mCapePicker;
    private LinearLayout mRoot;
    private LinearLayout mSkinListContainer;
    private LinearLayout mCapeListContainer;
    private View mCapePanel;
    private WebView mSkin3dPreview;
    private TextView mCurrentStatus;
    private TextView mCapeStatus;
    private ProgressBar mProgressBar;
    private Button mUploadButton;
    private Button mCapeUploadButton;
    private String mCurrentSkinDataUri;
    private String mCurrentCapeDataUri;
    private boolean mCapeUploadAllowed;
    private boolean mCapeServiceAvailable;
    private int mRequestId;

    public BattlySkinManagerFragment() {
        super();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSkinPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onSkinPicked);
        mCapePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onCapePicked);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(dp(24), dp(12), dp(24), dp(24));
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);

        mRoot = new LinearLayout(requireContext());
        mRoot.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mRoot, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        buildHeader();
        buildCurrentSkinPanel();
        buildSkinListPanel();
        buildCapePanel();
        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshLibrary();
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundResource(R.drawable.bg_battly_launcher_play);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.logo);
        icon.setBackgroundResource(R.drawable.bg_battly_profile_icon);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        header.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout textBlock = new LinearLayout(requireContext());
        textBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        textParams.setMargins(dp(14), 0, 0, 0);
        header.addView(textBlock, textParams);

        TextView title = titleText(R.string.battly_appearance_title, 22);
        textBlock.addView(title);

        TextView subtitle = bodyText(R.string.battly_appearance_subtitle);
        subtitle.setPadding(0, dp(4), 0, 0);
        textBlock.addView(subtitle);

        mRoot.addView(header);
    }

    private void buildCurrentSkinPanel() {
        LinearLayout card = panel();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(6), dp(12), dp(6));

        LinearLayout previews = new LinearLayout(requireContext());
        previews.setGravity(Gravity.CENTER);
        previews.setOrientation(LinearLayout.HORIZONTAL);
        previews.setBackgroundColor(0x00000000);
        previews.setPadding(0, 0, 0, 0);
        card.addView(previews, new LinearLayout.LayoutParams(dp(300), dp(202)));

        mSkin3dPreview = createSkin3dPreview();
        previews.addView(mSkin3dPreview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout textBlock = new LinearLayout(requireContext());
        textBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        textParams.setMargins(dp(16), 0, 0, 0);
        card.addView(textBlock, textParams);

        textBlock.addView(titleText(R.string.battly_skins_current, 18));
        mCurrentStatus = bodyText(R.string.battly_skins_loading);
        mCurrentStatus.setPadding(0, dp(6), 0, 0);
        textBlock.addView(mCurrentStatus);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button slimButton = secondaryButton(R.string.battly_skins_slim);
        LinearLayout.LayoutParams slimParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        actions.addView(slimButton, slimParams);
        slimButton.setOnClickListener(v -> {
            Context appContext = requireContext().getApplicationContext();
            runMutation(() -> BattlySkinApi.toggleSlim(appContext), R.string.battly_skins_slim_done);
        });

        mProgressBar = new ProgressBar(requireContext());
        mProgressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        progressParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(mProgressBar, progressParams);

        Button refreshButton = secondaryButton(R.string.battly_skins_refresh);
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        refreshParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(refreshButton, refreshParams);
        refreshButton.setOnClickListener(v -> refreshLibrary());

        mUploadButton = primaryButton(R.string.battly_skins_upload);
        LinearLayout.LayoutParams uploadParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        uploadParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(mUploadButton, uploadParams);
        mUploadButton.setOnClickListener(v -> mSkinPicker.launch(new String[]{"image/png"}));

        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.setMargins(0, dp(12), 0, 0);
        textBlock.addView(actions, actionsParams);

        addWithTopMargin(card, dp(10));
    }

    private void buildSkinListPanel() {
        LinearLayout card = panel();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(titleText(R.string.battly_skins_library, 18));

        TextView subtitle = bodyText(R.string.battly_skins_upload_desc);
        subtitle.setPadding(0, dp(4), 0, 0);
        card.addView(subtitle);

        mSkinListContainer = new LinearLayout(requireContext());
        mSkinListContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        listParams.setMargins(0, dp(12), 0, 0);
        card.addView(mSkinListContainer, listParams);

        addWithTopMargin(card, dp(12));
    }

    private void buildCapePanel() {
        LinearLayout card = panel();
        mCapePanel = card;
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout heading = new LinearLayout(requireContext());
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textBlock = new LinearLayout(requireContext());
        textBlock.setOrientation(LinearLayout.VERTICAL);
        heading.addView(textBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView capeTitle = titleText(R.string.battly_capes_title, 18);
        net.kdt.pojavlaunch.utils.NewFeatureBadge.apply(capeTitle);
        textBlock.addView(capeTitle);
        mCapeStatus = bodyText(R.string.battly_capes_loading);
        mCapeStatus.setPadding(0, dp(4), 0, 0);
        textBlock.addView(mCapeStatus);

        Button clearButton = secondaryButton(R.string.battly_capes_disable);
        heading.addView(clearButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));
        clearButton.setOnClickListener(v -> {
            Context appContext = requireContext().getApplicationContext();
            runMutation(() -> BattlyCapeApi.clearCape(appContext), R.string.battly_capes_disabled);
        });

        mCapeUploadButton = primaryButton(R.string.battly_capes_upload);
        LinearLayout.LayoutParams uploadParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        uploadParams.setMargins(dp(8), 0, 0, 0);
        heading.addView(mCapeUploadButton, uploadParams);
        mCapeUploadButton.setOnClickListener(v -> {
            if (!mCapeUploadAllowed) {
                toast(getString(R.string.battly_capes_plus_required));
                return;
            }
            mCapePicker.launch(new String[]{"image/png"});
        });
        card.addView(heading);

        TextView help = bodyText(R.string.battly_capes_description);
        help.setPadding(0, dp(10), 0, 0);
        card.addView(help);

        mCapeListContainer = new LinearLayout(requireContext());
        mCapeListContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        listParams.setMargins(0, dp(12), 0, 0);
        card.addView(mCapeListContainer, listParams);

        addWithTopMargin(card, dp(12));
    }

    private void onSkinPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        setLoading(true);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                MinecraftAccount currentAccount = PojavProfile.getCurrentProfileContent(appContext, null);
                String offlineUsername = currentAccount == null ? "Steve" : currentAccount.username;
                byte[] skinBytes = BattlySkinApi.readSkinBytesForOffline(appContext, uri);
                OfflineSkinManager.saveSkin(appContext, offlineUsername, skinBytes);
                if (currentAccount == null || currentAccount.isLocal()) {
                    Tools.runOnUiThread(() -> {
                        if (!isAdded()) return;
                        toast(getString(R.string.battly_skins_offline_saved));
                        loadSkin3dPreview("data:image/png;base64," + android.util.Base64.encodeToString(skinBytes, android.util.Base64.NO_WRAP));
                        mCurrentStatus.setText(R.string.battly_skins_offline_active);
                        setLoading(false);
                    });
                    return;
                }
                BattlySkinApi.UploadResult result = BattlySkinApi.uploadSkin(appContext, uri);
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    toast(getString(R.string.battly_skins_uploaded_offline, result.skinId));
                    refreshLibrary();
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    showError(e);
                });
            }
        });
    }

    private void onCapePicked(Uri uri) {
        if (uri == null) return;
        Context appContext = requireContext().getApplicationContext();
        setLoading(true);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlyCapeApi.UploadResult result = BattlyCapeApi.uploadCape(appContext, uri);
                BattlyCapeApi.setCape(appContext, result.capeId);
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    toast(getString(R.string.battly_capes_uploaded));
                    refreshLibrary();
                });
            } catch (Exception exception) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) showError(exception);
                });
            }
        });
    }

    private void refreshLibrary() {
        if (!isAdded()) {
            return;
        }
        int requestId = ++mRequestId;
        setLoading(true);
        Context appContext = requireContext().getApplicationContext();
        MinecraftAccount currentAccount = PojavProfile.getCurrentProfileContent(appContext, null);
        if (currentAccount != null && currentAccount.isLocal()) {
            bindLocalOfflineSkin(currentAccount);
            return;
        }
        if (mCapePanel != null) mCapePanel.setVisibility(View.VISIBLE);
        mCurrentStatus.setText(R.string.battly_skins_loading);
        mCapeStatus.setText(R.string.battly_capes_loading);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlySkinApi.SkinLibrary library = BattlySkinApi.loadLibrary(appContext);
                CapeLoadResult capeResult = loadCapeLibrarySafely(appContext);
                String currentSkin = safeLoadCurrentSkin(library.username);
                String currentCape = capeResult.library == null ? null : safeLoadCurrentCape(capeResult.library);
                Tools.runOnUiThread(() -> {
                    if (!isAdded() || requestId != mRequestId) return;
                    bindLibrary(library, currentSkin, capeResult, currentCape);
                    setLoading(false);
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    if (!isAdded() || requestId != mRequestId) return;
                    setLoading(false);
                    showError(e);
                    mCurrentStatus.setText(R.string.battly_skins_auth_required);
                    mCapeStatus.setText(R.string.battly_skins_auth_required);
                    mSkinListContainer.removeAllViews();
                    mCapeListContainer.removeAllViews();
                    addEmptyState();
                });
            }
        });
    }

    private void bindLocalOfflineSkin(MinecraftAccount account) {
        setLoading(false);
        if (mCapePanel != null) mCapePanel.setVisibility(View.GONE);
        mSkinListContainer.removeAllViews();
        java.io.File skinFile = OfflineSkinManager.getSkinFile(account.username);
        if (skinFile.isFile()) {
            String dataUri = readLocalSkinDataUri(skinFile);
            loadSkin3dPreview(dataUri);
            mCurrentStatus.setText(R.string.battly_skins_offline_active);
        } else {
            loadSkin3dPlaceholder();
            mCurrentStatus.setText(R.string.battly_skins_offline_empty);
        }
        TextView info = bodyText(R.string.battly_skins_offline_help);
        info.setGravity(Gravity.CENTER);
        info.setBackgroundResource(R.drawable.bg_battly_form_section);
        info.setPadding(dp(16), dp(22), dp(16), dp(22));
        mSkinListContainer.addView(info);
    }

    private String readLocalSkinDataUri(java.io.File skinFile) {
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(skinFile);
             java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
            return "data:image/png;base64," + android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeLoadCurrentSkin(String username) {
        try {
            return BattlySkinApi.downloadCurrentSkinDataUri(username);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeLoadCurrentCape(BattlyCapeApi.CapeLibrary library) {
        if (!Tools.isValidString(library.establishedCapeId)) return null;
        for (BattlyCapeApi.CapeEntry entry : library.capes) {
            if (entry.selected) {
                try {
                    return BattlyCapeApi.downloadCapeDataUri(entry.url);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private CapeLoadResult loadCapeLibrarySafely(Context context) {
        try {
            return new CapeLoadResult(BattlyCapeApi.loadLibrary(context), null);
        } catch (Exception exception) {
            String message = Tools.isValidString(exception.getMessage())
                    ? exception.getMessage() : null;
            return new CapeLoadResult(null, message);
        }
    }

    private void bindLibrary(BattlySkinApi.SkinLibrary library, String currentSkin,
                             CapeLoadResult capeResult, String currentCape) {
        mCurrentSkinDataUri = currentSkin;
        mCurrentCapeDataUri = currentCape;
        if (currentSkin != null) {
            loadSkin3dPreview(currentSkin, currentCape);
        } else {
            loadSkin3dPlaceholder();
        }
        String current = Tools.isValidString(library.establishedSkinId)
                ? getString(R.string.battly_skins_selected_model,
                        getString(library.slim ? R.string.battly_skins_model_slim : R.string.battly_skins_model_classic))
                : getString(R.string.battly_skins_no_selected);
        mCurrentStatus.setText(current);
        mSkinListContainer.removeAllViews();
        if (capeResult.library != null) {
            bindCapeLibrary(capeResult.library);
        } else {
            bindCapeError(capeResult.error);
        }
        if (library.skins.isEmpty()) {
            addEmptyState();
            return;
        }

        GridLayout grid = new GridLayout(requireContext());
        grid.setColumnCount(4);
        grid.setUseDefaultMargins(true);
        for (BattlySkinApi.SkinEntry entry : library.skins) {
            grid.addView(createSkinCard(library.username, entry));
        }
        mSkinListContainer.addView(grid);
    }

    private void bindCapeError(String error) {
        mCapeServiceAvailable = false;
        mCapeUploadAllowed = false;
        mCapeUploadButton.setText(R.string.battly_capes_upload);
        mCapeUploadButton.setEnabled(false);
        mCapeStatus.setText(R.string.battly_capes_unavailable);
        mCapeListContainer.removeAllViews();
        TextView message = bodyText(R.string.battly_capes_unavailable);
        if (Tools.isValidString(error) && !error.contains("HTTP 404")) {
            message.setText(error);
        }
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(16), dp(22), dp(16), dp(22));
        message.setBackgroundResource(R.drawable.bg_battly_form_section);
        mCapeListContainer.addView(message);
    }

    private void bindCapeLibrary(BattlyCapeApi.CapeLibrary library) {
        mCapeServiceAvailable = true;
        mCapeUploadAllowed = library.premium;
        mCapeUploadButton.setText(library.premium
                ? R.string.battly_capes_upload : R.string.battly_capes_upload_plus);
        mCapeStatus.setText(Tools.isValidString(library.establishedCapeId)
                ? R.string.battly_capes_active : R.string.battly_capes_none);
        mCapeListContainer.removeAllViews();
        if (library.capes.isEmpty()) {
            TextView empty = bodyText(R.string.battly_capes_empty);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(22), dp(16), dp(22));
            empty.setBackgroundResource(R.drawable.bg_battly_form_section);
            mCapeListContainer.addView(empty);
            return;
        }

        GridLayout grid = new GridLayout(requireContext());
        grid.setColumnCount(4);
        grid.setUseDefaultMargins(true);
        for (BattlyCapeApi.CapeEntry entry : library.capes) {
            grid.addView(createCapeCard(entry));
        }
        mCapeListContainer.addView(grid);
    }

    private View createCapeCard(BattlyCapeApi.CapeEntry entry) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_battly_form_section);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> previewCape(entry));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(10));
        card.setLayoutParams(params);

        ImageView preview = new ImageView(requireContext());
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(false);
        preview.setPadding(dp(18), dp(8), dp(18), dp(8));
        card.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(112)));
        PojavApplication.sExecutorService.execute(() -> {
            try {
                Bitmap bitmap = BattlyCapeApi.downloadCapeBitmap(entry.url);
                Tools.runOnUiThread(() -> {
                    if (isAdded()) preview.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) preview.setImageResource(R.drawable.logo);
                });
            }
        });

        TextView name = new TextView(requireContext());
        name.setText(Tools.isValidString(entry.name) ? entry.name : readableCapeName(entry.id));
        name.setTextSize(13);
        name.setTextColor(entry.selected ? 0xFF8DEEDC : 0xFFFFFFFF);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        card.addView(name);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(8), 0, 0);
        Button apply = secondaryButton(R.string.battly_skins_apply);
        apply.setEnabled(!entry.selected);
        actions.addView(apply, new LinearLayout.LayoutParams(0, dp(42), 1));
        apply.setOnClickListener(v -> {
            Context appContext = requireContext().getApplicationContext();
            runMutation(() -> BattlyCapeApi.setCape(appContext, entry.id), R.string.battly_capes_applied);
        });
        if (entry.deletable) {
            ImageButton delete = new ImageButton(requireContext());
            delete.setImageResource(android.R.drawable.ic_menu_delete);
            delete.setBackgroundResource(R.drawable.bg_battly_profile_icon);
            delete.setColorFilter(0xFFFFFFFF);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(42), dp(42));
            deleteParams.setMargins(dp(8), 0, 0, 0);
            actions.addView(delete, deleteParams);
            delete.setOnClickListener(v -> {
                Context appContext = requireContext().getApplicationContext();
                runMutation(() -> BattlyCapeApi.deleteCape(appContext, entry.id), R.string.battly_capes_deleted);
            });
        }
        card.addView(actions);
        return card;
    }

    private String readableCapeName(String id) {
        if (!Tools.isValidString(id)) return getString(R.string.battly_capes_custom);
        if (id.endsWith("_custom")) return getString(R.string.battly_capes_custom);
        String normalized = id.replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isEmpty()) return getString(R.string.battly_capes_official);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private View createSkinCard(String username, BattlySkinApi.SkinEntry entry) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_battly_form_section);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> previewLibrarySkin(username, entry));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(10));
        card.setLayoutParams(params);

        ImageView preview = new ImageView(requireContext());
        preview.setBackgroundColor(0x00000000);
        preview.setAdjustViewBounds(false);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setImageBitmap(BattlySkinPreviewRenderer.renderCardPreview(null));
        card.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(188)));

        PojavApplication.sExecutorService.execute(() -> {
            try {
                Bitmap bitmap = BattlySkinApi.downloadSkinBitmap(username, entry.id);
                Bitmap rendered = BattlySkinPreviewRenderer.renderCardPreview(bitmap);
                Tools.runOnUiThread(() -> {
                    if (isAdded()) preview.setImageBitmap(rendered);
                });
            } catch (Exception ignored) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) preview.setImageBitmap(BattlySkinPreviewRenderer.renderCardPreview(null));
                });
            }
        });

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);

        Button apply = secondaryButton(R.string.battly_skins_apply);
        apply.setEnabled(!entry.selected);
        actions.addView(apply, new LinearLayout.LayoutParams(0, dp(42), 1));
        apply.setOnClickListener(v -> {
            Context appContext = requireContext().getApplicationContext();
            runMutation(() -> {
                BattlySkinApi.setSkin(appContext, entry.id);
                OfflineSkinManager.saveSkin(appContext, username, BattlySkinApi.downloadSkinBytes(username, entry.id));
            }, R.string.battly_skins_applied_offline);
        });

        ImageButton delete = new ImageButton(requireContext());
        delete.setImageResource(android.R.drawable.ic_menu_delete);
        delete.setBackgroundResource(R.drawable.bg_battly_profile_icon);
        delete.setColorFilter(0xFFFFFFFF);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        deleteParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(delete, deleteParams);
        delete.setOnClickListener(v -> {
            Context appContext = requireContext().getApplicationContext();
            runMutation(() -> BattlySkinApi.deleteSkin(appContext, entry.id), R.string.battly_skins_deleted);
        });

        card.addView(actions);
        return card;
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private WebView createSkin3dPreview() {
        WebView webView = new WebView(requireContext());
        webView.setBackgroundColor(0x00000000);
        webView.setWebViewClient(new WebViewClient());
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(
                    event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE);
            return false;
        });
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        loadSkin3dPlaceholder(webView);
        return webView;
    }

    private void loadSkin3dPlaceholder() {
        loadSkin3dPlaceholder(mSkin3dPreview);
    }

    private void loadSkin3dPlaceholder(WebView webView) {
        if (webView == null) {
            return;
        }
        String html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<style>html,body{margin:0;width:100%;height:100%;background:transparent;color:#c7d4df;font:500 13px sans-serif;}" +
                "body{display:flex;align-items:center;justify-content:center;text-align:center;padding:14px;box-sizing:border-box;}</style></head>" +
                "<body>" + escapeHtml(getString(R.string.battly_skins_3d_loading)) + "</body></html>";
        webView.loadDataWithBaseURL("https://api.battlylauncher.com/", html, "text/html", "UTF-8", null);
    }

    private void loadSkin3dPreview(String skinDataUri) {
        loadSkin3dPreview(skinDataUri, mCurrentCapeDataUri);
    }

    private void loadSkin3dPreview(String skinDataUri, String capeDataUri) {
        loadSkin3dPreview(mSkin3dPreview, skinDataUri, capeDataUri, false);
    }

    private void loadSkin3dPreview(WebView targetWebView, String skinDataUri, String capeDataUri, boolean compact) {
        if (targetWebView == null) {
            return;
        }
        if (!Tools.isValidString(skinDataUri)) {
            loadSkin3dPlaceholder(targetWebView);
            return;
        }
        String skin = escapeJs(skinDataUri);
        String cape = escapeJs(capeDataUri);
        String loading = escapeJs(getString(R.string.battly_skins_3d_loading));
        String error = escapeJs(getString(R.string.battly_skins_3d_error));
        String hint = "";
        String camera = compact ? "viewer.camera.position.set(16,15,40);" : "viewer.camera.position.set(18,15,46);";
        String animation = compact ? "viewer.animation=new skinview3d.IdleAnimation();" : "viewer.animation=new skinview3d.WalkingAnimation();viewer.animation.speed=.55;";
        String html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">" +
                "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent;touch-action:none;}" +
                "canvas{width:100%;height:100%;display:block;background:transparent;transform:translateY(-7%);}" +
                "#status{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#c7d4df;font:600 12px sans-serif;text-align:center;padding:16px;box-sizing:border-box;}" +
                "#hint{display:none;}" +
                "</style><script src=\"file:///android_asset/skinview3d/skinview3d.bundle.js\"></script></head><body>" +
                "<canvas id=\"skin\"></canvas><div id=\"status\">" + loading + "</div><div id=\"hint\">" + hint + "</div>" +
                "<script>(function(){const skin='" + skin + "';const cape='" + cape + "';const status=document.getElementById('status');" +
                "function fail(){status.textContent='" + error + "';status.style.display='flex';}" +
                "function resize(viewer){const w=Math.max(240,document.body.clientWidth||320);const h=Math.max(240,document.body.clientHeight||320);viewer.setSize(w,h);}" +
                "function boot(){try{if(!window.skinview3d){fail();return;}const canvas=document.getElementById('skin');" +
                "const viewer=new skinview3d.SkinViewer({canvas:canvas,width:320,height:320});" +
                camera + "viewer.camera.lookAt(0,2,0);viewer.controls.target.set(0,2,0);viewer.controls.enableRotate=true;viewer.controls.enableZoom=true;viewer.controls.enablePan=false;viewer.controls.update();" +
                animation + "resize(viewer);window.addEventListener('resize',()=>resize(viewer));" +
                "const loads=[Promise.resolve(viewer.loadSkin(skin))];if(cape){loads.push(Promise.resolve(viewer.loadCape(cape)));}" +
                "Promise.all(loads).then(()=>{status.style.display='none';}).catch(fail);" +
                "}catch(e){fail();}}" +
                "if(document.readyState==='complete'){boot();}else{window.addEventListener('load',boot);}" +
                "})();</script></body></html>";
        targetWebView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    private void previewLibrarySkin(String username, BattlySkinApi.SkinEntry entry) {
        setLoading(true);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                String dataUri = BattlySkinApi.downloadSkinDataUri(username, entry.id);
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    mCurrentSkinDataUri = dataUri;
                    loadSkin3dPreview(dataUri, mCurrentCapeDataUri);
                    mCurrentStatus.setText(getString(R.string.battly_skins_previewing_clean));
                    setLoading(false);
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) showError(e);
                });
            }
        });
    }

    private void previewCape(BattlyCapeApi.CapeEntry entry) {
        if (!Tools.isValidString(mCurrentSkinDataUri)) return;
        setLoading(true);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                String capeDataUri = BattlyCapeApi.downloadCapeDataUri(entry.url);
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    loadSkin3dPreview(mCurrentSkinDataUri, capeDataUri);
                    mCapeStatus.setText(R.string.battly_capes_previewing);
                    setLoading(false);
                });
            } catch (Exception exception) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) showError(exception);
                });
            }
        });
    }

    private void runMutation(Mutation mutation, int successTextRes) {
        setLoading(true);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                mutation.run();
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    toast(getString(successTextRes));
                    refreshLibrary();
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) showError(e);
                });
            }
        });
    }

    private void addEmptyState() {
        TextView empty = bodyText(R.string.battly_skins_empty);
        empty.setGravity(Gravity.CENTER);
        empty.setBackgroundResource(R.drawable.bg_battly_form_section);
        empty.setPadding(dp(16), dp(22), dp(16), dp(22));
        mSkinListContainer.addView(empty);
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setBackgroundResource(R.drawable.bg_battly_form_panel);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        return panel;
    }

    private void addWithTopMargin(View view, int marginTop) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, marginTop, 0, 0);
        mRoot.addView(view, params);
    }

    private TextView titleText(int textRes, int sp) {
        TextView textView = new TextView(requireContext());
        textView.setText(textRes);
        textView.setTextColor(0xFFFFFFFF);
        textView.setTextSize(sp);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        return textView;
    }

    private TextView bodyText(int textRes) {
        TextView textView = new TextView(requireContext());
        textView.setText(textRes);
        textView.setTextColor(0xFFC7D4DF);
        textView.setTextSize(13);
        return textView;
    }

    private Button primaryButton(int textRes) {
        Button button = new Button(requireContext());
        button.setText(textRes);
        button.setAllCaps(false);
        button.setTextColor(0xFF073A34);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundResource(R.drawable.bg_battly_button_primary);
        return button;
    }

    private Button secondaryButton(int textRes) {
        Button button = new Button(requireContext());
        button.setText(textRes);
        button.setAllCaps(false);
        button.setTextColor(0xFF8DEEDC);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundResource(R.drawable.bg_battly_button_secondary);
        return button;
    }

    private void setLoading(boolean loading) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (mUploadButton != null) {
            mUploadButton.setEnabled(!loading);
        }
        if (mCapeUploadButton != null) {
            mCapeUploadButton.setEnabled(!loading && mCapeServiceAvailable);
        }
    }

    private void showError(Exception e) {
        setLoading(false);
        toast(getString(R.string.battly_skins_error, e.getMessage()));
    }

    private void toast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface Mutation {
        void run() throws Exception;
    }

    private static final class CapeLoadResult {
        final BattlyCapeApi.CapeLibrary library;
        final String error;

        CapeLoadResult(BattlyCapeApi.CapeLibrary library, String error) {
            this.library = library;
            this.error = error;
        }
    }
}
