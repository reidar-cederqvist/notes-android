/*
 * Nextcloud Notes - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Reidar Cederqvist
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package it.niedermann.owncloud.notes.shared.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and serializes a checklist note made of GFM task items ({@code - [ ] ...} / {@code - [x] ...}),
 * optionally preceded by a single plain title line (the Nextcloud Notes title convention). Used by
 * the list editor.
 *
 * <p>The round-trip is idempotent: {@code serialize(parse(s))} is stable under re-parsing. Each
 * item's leading indentation and bullet character are preserved (so nested items survive); the
 * checkbox marker and a single separating space are canonicalized. The title line is preserved
 * verbatim (Option A), so a markdown title such as {@code "# Groceries"} round-trips unchanged.
 */
public final class MarkdownTaskList {

    /** A single checklist item. {@code indent} + {@code bullet} are structural; {@code checked}/{@code text} are content. */
    public static final class Item {
        public final String indent;
        public final char bullet;
        public boolean checked;
        public String text;

        public Item(@NonNull String indent, char bullet, boolean checked, @NonNull String text) {
            this.indent = indent;
            this.bullet = bullet;
            this.checked = checked;
            this.text = text;
        }
    }

    public static final class Parsed {
        @Nullable
        public final String title;
        @NonNull
        public final List<Item> items;

        Parsed(@Nullable String title, @NonNull List<Item> items) {
            this.title = title;
            this.items = items;
        }
    }

    private static final Pattern TASK_ITEM =
            Pattern.compile("^(\\s*)([-*+])\\s+\\[([ xX])]\\s?(.*)$");

    private MarkdownTaskList() {
        throw new UnsupportedOperationException("Do not instantiate this util class.");
    }

    @NonNull
    public static Parsed parse(@NonNull String content) {
        final List<Item> items = new ArrayList<>();
        String title = null;
        for (final String line : content.split("\n", -1)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            final Matcher m = TASK_ITEM.matcher(line);
            if (m.matches()) {
                final String indent = m.group(1);
                final char bullet = m.group(2).charAt(0);
                final boolean checked = !" ".equals(m.group(3));
                final String text = m.group(4);
                items.add(new Item(indent, bullet, checked, text));
            } else if (title == null && items.isEmpty()) {
                // The first non-empty, non-task line is the title, kept verbatim.
                title = line;
            }
            // Any other non-task line is ignored (should not occur for a note classified as LIST).
        }
        return new Parsed(title, items);
    }

    /** @return true if the list is a single empty, unchecked placeholder item (a "blank" list). */
    public static boolean isSingleEmptyPlaceholder(@NonNull List<Item> items) {
        return items.size() == 1 && !items.get(0).checked && items.get(0).text.trim().isEmpty();
    }

    @NonNull
    public static String serialize(@Nullable String title, @NonNull List<Item> items) {
        final StringBuilder sb = new StringBuilder();
        final boolean hasTitle = title != null && !title.trim().isEmpty();
        if (hasTitle) {
            sb.append(title);
            if (!items.isEmpty()) {
                sb.append('\n');
            }
        }
        for (int i = 0; i < items.size(); i++) {
            final Item item = items.get(i);
            sb.append(item.indent)
                    .append(item.bullet)
                    .append(" [")
                    .append(item.checked ? 'x' : ' ')
                    .append("] ")
                    .append(item.text);
            if (i < items.size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
