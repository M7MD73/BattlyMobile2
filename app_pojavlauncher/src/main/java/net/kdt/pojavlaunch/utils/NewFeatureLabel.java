package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

/** A wrapping title whose localized release badge participates in text measurement. */
public final class NewFeatureLabel extends AppCompatTextView {
    public NewFeatureLabel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        NewFeatureBadge.apply(this);
    }
}
