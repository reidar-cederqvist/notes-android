/*
 * Nextcloud Notes - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Reidar Cederqvist
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package it.niedermann.owncloud.notes.edit;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

/**
 * An {@link android.widget.EditText} that reports a backspace pressed while the field is empty, so
 * the checklist editor can remove the item and move focus to the previous one (Keep-style). Handles
 * both the soft-keyboard path ({@code deleteSurroundingText}) and hardware keys ({@code sendKeyEvent}).
 */
public class ListItemEditText extends AppCompatEditText {

    public interface OnBackspaceWhenEmptyListener {
        void onBackspaceWhenEmpty();
    }

    @Nullable
    private OnBackspaceWhenEmptyListener backspaceListener;

    public ListItemEditText(Context context) {
        super(context);
    }

    public ListItemEditText(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ListItemEditText(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnBackspaceWhenEmptyListener(@Nullable OnBackspaceWhenEmptyListener listener) {
        this.backspaceListener = listener;
    }

    private boolean isEmpty() {
        return getText() == null || getText().length() == 0;
    }

    private boolean handleBackspaceWhenEmpty() {
        if (isEmpty() && backspaceListener != null) {
            backspaceListener.onBackspaceWhenEmpty();
            return true;
        }
        return false;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        final InputConnection ic = super.onCreateInputConnection(outAttrs);
        if (ic == null) {
            return null;
        }
        return new InputConnectionWrapper(ic, true) {
            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (handleBackspaceWhenEmpty()) {
                    return true;
                }
                return super.deleteSurroundingText(beforeLength, afterLength);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_DEL
                        && handleBackspaceWhenEmpty()) {
                    return true;
                }
                return super.sendKeyEvent(event);
            }
        };
    }
}
