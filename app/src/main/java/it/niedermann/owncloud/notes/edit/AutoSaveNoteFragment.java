/*
 * Nextcloud Notes - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Reidar Cederqvist
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package it.niedermann.owncloud.notes.edit;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.Nullable;

import it.niedermann.owncloud.notes.shared.model.ISyncCallback;

/**
 * Shared base for the simple and list note editors. Provides the debounced auto-save (a dirty flag
 * plus a {@link Handler}) and a stateless {@link TextWatcher} that marks the note dirty.
 *
 * <p>Subclasses trigger a save either by attaching {@link #dirtyWatcher} to an editable or by
 * calling {@link #markDirty()} directly when their content changes. While populating the views they
 * should set {@link #loading} so those programmatic changes don't count as user edits.
 */
public abstract class AutoSaveNoteFragment extends BaseNoteFragment {

    private static final long DELAY = 2000; // wait after typing before saving
    private static final long DELAY_AFTER_SYNC = 5000; // wait after saving before the next save

    private Handler handler;
    private boolean saveActive;
    private boolean unsavedEdit;
    /** True while loading content into the views, so programmatic changes don't mark the note dirty. */
    protected boolean loading;

    private final Runnable runAutoSave = () -> {
        if (unsavedEdit) {
            autoSave();
        }
    };

    /** A shared, stateless watcher subclasses can attach to any editable to trigger auto-save. */
    protected final TextWatcher dirtyWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // no-op
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // no-op
        }

        @Override
        public void afterTextChanged(Editable s) {
            markDirty();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(runAutoSave);
    }

    /** Marks the note dirty and (re)schedules a debounced auto-save (no-op while {@link #loading}). */
    protected void markDirty() {
        if (loading) {
            return;
        }
        unsavedEdit = true;
        if (!saveActive) {
            handler.removeCallbacks(runAutoSave);
            handler.postDelayed(runAutoSave, DELAY);
        }
    }

    @Override
    protected void saveNote(@Nullable ISyncCallback callback) {
        super.saveNote(callback);
        unsavedEdit = false;
    }

    private void autoSave() {
        saveActive = true;
        saveNote(new ISyncCallback() {
            @Override
            public void onFinish() {
                onSaved();
            }

            @Override
            public void onScheduled() {
                onSaved();
            }

            private void onSaved() {
                saveActive = false;
                handler.postDelayed(runAutoSave, DELAY_AFTER_SYNC);
            }
        });
    }
}
