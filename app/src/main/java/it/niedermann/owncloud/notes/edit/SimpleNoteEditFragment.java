/*
 * Nextcloud Notes - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Reidar Cederqvist
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package it.niedermann.owncloud.notes.edit;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.databinding.FragmentNoteSimpleEditBinding;
import it.niedermann.owncloud.notes.persistence.entity.Note;

/**
 * A deliberately minimal, plain-text note editor for non-technical users: just a single text area,
 * no markdown rendering, no formatting toolbar, no preview toggle. The title is the first line
 * (Nextcloud Notes convention) and is edited via the toolbar like in the other editors.
 *
 * <p>Used for notes classified as {@link it.niedermann.owncloud.notes.shared.util.NoteContentClassifier.EditorType#SIMPLE}.
 * Reuses the load/save/autosave lifecycle of {@link BaseNoteFragment}; the content is plain text,
 * which is valid markdown, so the round-trip is lossless.
 */
public class SimpleNoteEditFragment extends AutoSaveNoteFragment {

    private static final int MENU_ID_EDIT_AS_MARKDOWN = -101;

    private FragmentNoteSimpleEditBinding binding;
    private boolean keyboardShown = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNoteSimpleEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.editContent.addTextChangedListener(dirtyWatcher);
        if (keyboardShown) {
            openSoftKeyboard();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        binding.editContent.removeTextChangedListener(dirtyWatcher);
    }

    @Override
    protected void onNoteLoaded(Note note) {
        super.onNoteLoaded(note);
        if (binding == null || note == null) {
            return;
        }
        binding.editContent.setText(note.getContent());
        binding.editContent.setEnabled(true);
        if (TextUtils.isEmpty(note.getContent())) {
            openSoftKeyboard();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, MENU_ID_EDIT_AS_MARKDOWN, 100, R.string.action_edit_as_markdown);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Plain-text simple editor: hide the markdown view-mode toggles.
        final var edit = menu.findItem(R.id.menu_edit);
        if (edit != null) {
            edit.setVisible(false);
        }
        final var preview = menu.findItem(R.id.menu_preview);
        if (preview != null) {
            preview.setVisible(false);
        }
        // "Edit as markdown" needs the note's id from the launch intent, which a brand-new note
        // doesn't have yet -> only offer it for already-saved notes.
        final var editAsMarkdown = menu.findItem(MENU_ID_EDIT_AS_MARKDOWN);
        if (editAsMarkdown != null) {
            editAsMarkdown.setVisible(!isNew);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == MENU_ID_EDIT_AS_MARKDOWN) {
            saveNote(null);
            if (listener != null) {
                listener.changeMode(NoteFragmentListener.Mode.EDIT, true);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    protected ScrollView getScrollView() {
        return binding == null ? null : binding.scrollView;
    }

    @Override
    protected void scrollToY(int scrollY) {
        if (binding == null) {
            return;
        }
        final var scrollView = binding.scrollView;
        scrollView.post(() -> scrollView.setScrollY(scrollY));
    }

    @Override
    protected String getContent() {
        if (binding == null) {
            return note == null ? "" : note.getContent();
        }
        final var editable = binding.editContent.getText();
        return editable == null ? "" : editable.toString();
    }

    private void openSoftKeyboard() {
        binding.editContent.postDelayed(() -> {
            binding.editContent.requestFocus();
            final var imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.editContent, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);
    }

    @Override
    public void applyBrand(int color) {
        // Nothing markdown-specific to brand in the plain-text editor.
    }

    public static BaseNoteFragment newInstance(long accountId, long noteId) {
        final var fragment = new SimpleNoteEditFragment();
        final var args = new Bundle();
        args.putLong(PARAM_NOTE_ID, noteId);
        args.putLong(PARAM_ACCOUNT_ID, accountId);
        fragment.setArguments(args);
        return fragment;
    }

    public static BaseNoteFragment newInstanceWithNewNote(Note newNote) {
        final var fragment = new SimpleNoteEditFragment();
        final var args = new Bundle();
        args.putSerializable(PARAM_NEWNOTE, newNote);
        fragment.setArguments(args);
        return fragment;
    }
}
