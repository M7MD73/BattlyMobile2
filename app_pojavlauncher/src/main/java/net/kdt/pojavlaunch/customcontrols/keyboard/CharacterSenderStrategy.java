package net.kdt.pojavlaunch.customcontrols.keyboard;

/** Simple interface for sending chars through whatever bridge will be necessary */
public interface CharacterSenderStrategy {
    /** Called when there is a character to delete, may be called multiple times in a row */
    void sendBackspace();

    /** Called when we want to send enter specifically */
    void sendEnter();

    /** Called when there is a character to send, may be called multiple times in a row */
    void sendChar(char character);

    /** Called when an IME commits a complete piece of text. */
    default void sendText(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            sendChar(text.charAt(i));
        }
    }

}
