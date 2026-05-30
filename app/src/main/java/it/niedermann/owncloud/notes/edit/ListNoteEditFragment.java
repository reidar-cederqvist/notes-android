/*
 * Nextcloud Notes - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Reidar Cederqvist
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package it.niedermann.owncloud.notes.edit;

import android.content.ClipData;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.CompoundButtonCompat;

import java.util.ArrayList;
import java.util.List;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.databinding.FragmentNoteListEditBinding;
import it.niedermann.owncloud.notes.persistence.entity.Note;
import it.niedermann.owncloud.notes.shared.model.ISyncCallback;
import it.niedermann.owncloud.notes.shared.util.MarkdownTaskList;

/**
 * A Google-Keep-style checklist editor for notes classified as
 * {@link it.niedermann.owncloud.notes.shared.util.NoteContentClassifier.EditorType#LIST}: an
 * optional title, a list of checkbox rows, an "add item" row, and below it the checked items.
 * Tapping a checkbox moves the item to the checked section. The content is stored as GFM task-list
 * markdown via {@link MarkdownTaskList}, so the round-trip is lossless.
 */
public class ListNoteEditFragment extends BaseNoteFragment {

    private static final long DELAY = 2000; // wait after typing before saving
    private static final long DELAY_AFTER_SYNC = 5000; // wait after saving before next save
    private static final int MENU_ID_UNCHECK_ALL = -100;
    private static final int MENU_ID_EDIT_AS_MARKDOWN = -101;

    private FragmentNoteListEditBinding binding;
    private Handler handler;
    private boolean saveActive;
    private boolean unsavedEdit;
    private boolean loading;
    private boolean initialized;

    private final Runnable runAutoSave = () -> {
        if (unsavedEdit) {
            autoSave();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNoteListEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (handler != null) {
            handler.removeCallbacks(runAutoSave);
        }
    }

    @Override
    protected void onNoteLoaded(Note note) {
        super.onNoteLoaded(note);
        if (binding == null || note == null) {
            return;
        }
        loading = true;

        if (!initialized) {
            binding.addItemButton.setOnClickListener(v -> addItem(true));
            binding.itemContainer.setOnDragListener(this::onItemDrag);
            initialized = true;
        }

        final var parsed = MarkdownTaskList.parse(note.getContent());
        binding.itemContainer.removeAllViews();
        binding.checkedContainer.removeAllViews();
        // A note saved as a list always keeps at least one item; a lone empty placeholder is not
        // rendered (the list just shows no items plus the "add item" row).
        if (!isSinglePlaceholder(parsed.items)) {
            for (final var item : parsed.items) {
                final View row = createRow(item);
                (item.checked ? binding.checkedContainer : binding.itemContainer).addView(row);
            }
        }

        loading = false;

        // A brand-new list starts with one empty item ready to type into.
        if (isNew && itemCount() == 0) {
            addItem(true);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, MENU_ID_UNCHECK_ALL, 100, R.string.list_uncheck_all);
        menu.add(Menu.NONE, MENU_ID_EDIT_AS_MARKDOWN, 101, R.string.action_edit_as_markdown);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Checklist editor: hide the markdown view-mode toggles.
        final var edit = menu.findItem(R.id.menu_edit);
        if (edit != null) {
            edit.setVisible(false);
        }
        final var preview = menu.findItem(R.id.menu_preview);
        if (preview != null) {
            preview.setVisible(false);
        }
        // "Uncheck all" only makes sense when there are checked items.
        final var uncheckAll = menu.findItem(MENU_ID_UNCHECK_ALL);
        if (uncheckAll != null) {
            uncheckAll.setVisible(binding != null && binding.checkedContainer.getChildCount() > 0);
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
        if (item.getItemId() == MENU_ID_UNCHECK_ALL) {
            uncheckAll();
            return true;
        }
        if (item.getItemId() == MENU_ID_EDIT_AS_MARKDOWN) {
            saveNote(null);
            if (listener != null) {
                listener.changeMode(NoteFragmentListener.Mode.EDIT, true);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private View createRow(@NonNull MarkdownTaskList.Item item) {
        final View row = getLayoutInflater().inflate(R.layout.item_note_list_edit, binding.itemContainer, false);
        final CheckBox checkbox = row.findViewById(R.id.checkbox);
        final ListItemEditText editText = row.findViewById(R.id.editText);
        final ImageButton deleteButton = row.findViewById(R.id.deleteButton);
        final View dragHandle = row.findViewById(R.id.dragHandle);

        // The tag carries the structural bits (indent + bullet); checked/text are read live.
        row.setTag(item);
        checkbox.setChecked(item.checked);
        // Force the checkbox color to the text color (matching the markdown preview); the theme's
        // checked tint (accent) can be dark/invisible, so override it in code.
        CompoundButtonCompat.setButtonTintList(checkbox,
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.fg_default)));
        editText.setText(item.text);
        applyCheckedStyle(editText, item.checked);

        checkbox.setOnClickListener(v -> onItemCheckedChanged(row, checkbox));
        editText.addTextChangedListener(dirtyWatcher());
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                addItemAfter(row);
                return true;
            }
            return false;
        });
        editText.setOnBackspaceWhenEmptyListener(() -> removeItemAndFocusPrevious(row));

        deleteButton.setOnClickListener(v -> {
            final ViewGroup parent = (ViewGroup) row.getParent();
            if (parent != null) {
                parent.removeView(row);
            }
            markDirty();
        });

        // Drag handle (unchecked items only) starts a drag to reorder.
        dragHandle.setVisibility(item.checked ? View.GONE : View.VISIBLE);
        dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                row.startDragAndDrop(ClipData.newPlainText("", ""), new View.DragShadowBuilder(row), row, 0);
                row.setVisibility(View.INVISIBLE);
                return true;
            }
            return false;
        });
        return row;
    }

    /** Live-reorders the unchecked items as a row is dragged by its handle. */
    private boolean onItemDrag(@NonNull View container, @NonNull DragEvent event) {
        if (!(event.getLocalState() instanceof View dragged)) {
            return false;
        }
        final LinearLayout c = binding.itemContainer;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_LOCATION -> {
                int target = 0;
                for (int i = 0; i < c.getChildCount(); i++) {
                    final View child = c.getChildAt(i);
                    if (child != dragged && event.getY() > child.getY() + child.getHeight() / 2f) {
                        target++;
                    }
                }
                if (c.indexOfChild(dragged) != target) {
                    c.removeView(dragged);
                    c.addView(dragged, Math.min(target, c.getChildCount()));
                }
                return true;
            }
            case DragEvent.ACTION_DROP -> {
                markDirty();
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED -> {
                dragged.setVisibility(View.VISIBLE);
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private void onItemCheckedChanged(@NonNull View row, @NonNull CheckBox checkbox) {
        if (loading) {
            return;
        }
        // Toggling a checkbox is a "done" action, not editing -> dismiss the keyboard and cursor.
        final View focused = binding.getRoot().findFocus();
        if (focused != null) {
            focused.clearFocus();
        }
        final var imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
        }
        applyCheckedStyle(row.findViewById(R.id.editText), checkbox.isChecked());
        row.findViewById(R.id.dragHandle).setVisibility(checkbox.isChecked() ? View.GONE : View.VISIBLE);
        final ViewGroup parent = (ViewGroup) row.getParent();
        if (parent != null) {
            parent.removeView(row);
        }
        (checkbox.isChecked() ? binding.checkedContainer : binding.itemContainer).addView(row);
        markDirty();
    }

    /** Strikes through and dims a checked item to match the markdown preview (view mode). */
    private void applyCheckedStyle(@NonNull EditText editText, boolean checked) {
        if (checked) {
            editText.setPaintFlags(editText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            editText.setAlpha(0.6f);
        } else {
            editText.setPaintFlags(editText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            editText.setAlpha(1f);
        }
    }

    /** Moves every checked item back to the unchecked section. */
    private void uncheckAll() {
        final int count = binding.checkedContainer.getChildCount();
        if (count == 0) {
            return;
        }
        final List<View> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(binding.checkedContainer.getChildAt(i));
        }
        for (final View row : rows) {
            binding.checkedContainer.removeView(row);
            final CheckBox checkbox = row.findViewById(R.id.checkbox);
            checkbox.setChecked(false);
            applyCheckedStyle(row.findViewById(R.id.editText), false);
            row.findViewById(R.id.dragHandle).setVisibility(View.VISIBLE);
            binding.itemContainer.addView(row);
        }
        markDirty();
        requireActivity().invalidateOptionsMenu();
    }

    private void addItem(boolean focus) {
        final var item = new MarkdownTaskList.Item("", '-', false, "");
        final View row = createRow(item);
        binding.itemContainer.addView(row);
        if (focus) {
            final EditText editText = row.findViewById(R.id.editText);
            editText.requestFocus();
            showKeyboard(editText);
        }
        markDirty();
    }

    private int itemCount() {
        return binding.itemContainer.getChildCount() + binding.checkedContainer.getChildCount();
    }

    private void removeItemAndFocusPrevious(@NonNull View row) {
        final ViewGroup parent = (ViewGroup) row.getParent();
        if (parent == null) {
            return;
        }
        final int index = parent.indexOfChild(row);
        parent.removeView(row);
        if (index > 0) {
            final EditText previous = parent.getChildAt(index - 1).findViewById(R.id.editText);
            previous.requestFocus();
            previous.setSelection(previous.length());
            showKeyboard(previous);
        }
        markDirty();
    }

    private void addItemAfter(@NonNull View currentRow) {
        final ViewGroup parent = (ViewGroup) currentRow.getParent();
        if (parent == null) {
            return;
        }
        final var item = new MarkdownTaskList.Item("", '-', false, "");
        final View row = createRow(item);
        parent.addView(row, parent.indexOfChild(currentRow) + 1);
        final EditText editText = row.findViewById(R.id.editText);
        editText.requestFocus();
        showKeyboard(editText);
        markDirty();
    }

    private static boolean isSinglePlaceholder(@NonNull List<MarkdownTaskList.Item> items) {
        return items.size() == 1 && !items.get(0).checked && items.get(0).text.trim().isEmpty();
    }

    @Override
    protected String getContent() {
        if (binding == null) {
            return note == null ? "" : note.getContent();
        }
        final List<MarkdownTaskList.Item> items = new ArrayList<>();
        collectItems(binding.itemContainer, items);
        collectItems(binding.checkedContainer, items);
        if (items.isEmpty()) {
            // Persist an empty placeholder item so the note stays classified as a list.
            items.add(new MarkdownTaskList.Item("", '-', false, ""));
        }
        // The checklist editor only handles pure task-item content (a leading title line would have
        // been classified ADVANCED), so there is never a title line to preserve here.
        return MarkdownTaskList.serialize(null, items);
    }

    private void collectItems(@NonNull LinearLayout container, @NonNull List<MarkdownTaskList.Item> out) {
        for (int i = 0; i < container.getChildCount(); i++) {
            final View row = container.getChildAt(i);
            if (!(row.getTag() instanceof MarkdownTaskList.Item tag)) {
                continue;
            }
            final CheckBox checkbox = row.findViewById(R.id.checkbox);
            final EditText editText = row.findViewById(R.id.editText);
            out.add(new MarkdownTaskList.Item(tag.indent, tag.bullet, checkbox.isChecked(), editText.getText().toString()));
        }
    }

    private TextWatcher dirtyWatcher() {
        return new TextWatcher() {
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
    }

    private void markDirty() {
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

    private void showKeyboard(@NonNull EditText editText) {
        editText.postDelayed(() -> {
            editText.requestFocus();
            final var imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);
    }

    @Nullable
    @Override
    protected ScrollView getScrollView() {
        return binding == null ? null : binding.scrollView;
    }

    @Override
    protected void scrollToY(int scrollY) {
        if (binding != null) {
            binding.scrollView.post(() -> binding.scrollView.setScrollY(scrollY));
        }
    }

    @Override
    public void applyBrand(int color) {
        // Nothing markdown-specific to brand in the checklist editor.
    }

    public static BaseNoteFragment newInstance(long accountId, long noteId) {
        final var fragment = new ListNoteEditFragment();
        final var args = new Bundle();
        args.putLong(PARAM_NOTE_ID, noteId);
        args.putLong(PARAM_ACCOUNT_ID, accountId);
        fragment.setArguments(args);
        return fragment;
    }

    public static BaseNoteFragment newInstanceWithNewNote(Note newNote) {
        final var fragment = new ListNoteEditFragment();
        final var args = new Bundle();
        args.putSerializable(PARAM_NEWNOTE, newNote);
        fragment.setArguments(args);
        return fragment;
    }
}
