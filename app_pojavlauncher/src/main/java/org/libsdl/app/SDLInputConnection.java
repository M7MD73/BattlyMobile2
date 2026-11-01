package org.libsdl.app;

import android.content.*;
import android.os.Build;
import android.text.Editable;
import android.view.*;
import android.view.inputmethod.BaseInputConnection;
import android.widget.EditText;

class SDLInputConnection extends BaseInputConnection
{
    protected EditText mEditText;
    protected String mCommittedText = "";

    SDLInputConnection(View targetView, boolean fullEditor) {
        super(targetView, fullEditor);
        mEditText = new EditText(targetView.getContext());
    }

    @Override
    public Editable getEditable() {
        return mEditText.getEditableText();
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) deleteSurroundingTextInCodePoints(1, 0);
            return true;
        }
        /*
         * This used to handle the keycodes from soft keyboard (and IME-translated input from hardkeyboard)
         * However, as of Ice Cream Sandwich and later, almost all soft keyboard doesn't generate key presses
         * and so we need to generate them ourselves in commitText.  To avoid duplicates on the handful of keys
         * that still do, we empty this out.
         */

        /*
         * Return DOES still generate a key event, however.  So rather than using it as the 'click a button' key
         * as we do with physical keyboards, let's just use it to hide the keyboard.
         */

        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            // Some IMEs emit only ACTION_DOWN, while others emit both actions
            // and performEditorAction. submitReturn() deduplicates those forms.
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    || event.getAction() == KeyEvent.ACTION_UP) {
                submitAndCloseKeyboard();
            }
            return true;
        }

        return super.sendKeyEvent(event);
    }

    @Override
    public boolean performEditorAction(int actionCode) {
        if (actionCode == android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                || actionCode == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                || actionCode == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                || actionCode == android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED) {
            return submitAndCloseKeyboard();
        }
        return super.performEditorAction(actionCode);
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (!super.commitText(text, newCursorPosition)) {
            return false;
        }
        updateText();
        return true;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (!super.setComposingText(text, newCursorPosition)) {
            return false;
        }
        updateText();
        return true;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        // Workaround to capture backspace key. Ref: http://stackoverflow.com/questions>/14560344/android-backspace-in-webview-baseinputconnection
        // and https://bugzilla.libsdl.org/show_bug.cgi?id=2265
        if (beforeLength > 0 && afterLength == 0) {
            if (!super.deleteSurroundingText(beforeLength, afterLength)) return false;
            mCommittedText = getEditable().toString();
            while (beforeLength-- > 0) {
                emitScancode('\b');
            }
            return true;
       }

        if (!super.deleteSurroundingText(beforeLength, afterLength)) {
            return false;
        }
        updateText();
        return true;
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        if (beforeLength > 0 && afterLength == 0) {
            if (!super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)) return false;
            mCommittedText = getEditable().toString();
            for (int i = 0; i < beforeLength; i++) emitScancode('\b');
            return true;
        }
        boolean changed = super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
        if (changed) updateText();
        return changed;
    }

    protected void emitScancode(char character) { nativeGenerateScancodeForUnichar(character); }
    protected void emitText(String text) { nativeCommitText(text, 0); }

    protected void updateText() {
        final Editable content = getEditable();
        if (content == null) {
            return;
        }

        String text = content.toString();
        int compareLength = Math.min(text.length(), mCommittedText.length());
        int matchLength, offset;

        /* Backspace over characters that are no longer in the string */
        for (matchLength = 0; matchLength < compareLength; ) {
            int codePoint = mCommittedText.codePointAt(matchLength);
            if (codePoint != text.codePointAt(matchLength)) {
                break;
            }
            matchLength += Character.charCount(codePoint);
        }
        /* FIXME: This doesn't handle graphemes, like '🌬️' */
        for (offset = matchLength; offset < mCommittedText.length(); ) {
            int codePoint = mCommittedText.codePointAt(offset);
            emitScancode('\b');
            offset += Character.charCount(codePoint);
        }

        if (matchLength < text.length()) {
            String pendingText = text.subSequence(matchLength, text.length()).toString();
            if (!SDLActivity.dispatchingKeyEvent()) {
                for (offset = 0; offset < pendingText.length(); ) {
                    int codePoint = pendingText.codePointAt(offset);
                    if (codePoint == '\n') {
                        submitAndCloseKeyboard();
                        return;
                    }
                    /* Higher code points don't generate simulated scancodes */
                    if (codePoint > 0 && codePoint < 128) {
                        emitScancode((char)codePoint);
                    }
                    offset += Character.charCount(codePoint);
                }
            }
            emitText(pendingText);
        }
        mCommittedText = text;
    }

    private boolean submitAndCloseKeyboard() {
        SDLActivity.submitSoftReturnKey();
        Editable content = getEditable();
        if (content != null) content.clear();
        mCommittedText = "";
        return true;
    }

    public static native void nativeCommitText(String text, int newCursorPosition);

    public static native void nativeGenerateScancodeForUnichar(char c);
}

