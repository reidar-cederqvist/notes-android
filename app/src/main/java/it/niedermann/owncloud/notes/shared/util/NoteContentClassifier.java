/*
 * Nextcloud Notes - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Reidar Cederqvist
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package it.niedermann.owncloud.notes.shared.util;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Classifies a note's markdown content into one of three editor types, so the app can route to the
 * matching editor (simple / list / advanced) and render the matching grid card.
 *
 * <p>Design contract (see {@code SIMPLE_NOTES_FORK_PLAN.md}):
 * <ul>
 *   <li>The type is <em>inferred from content only</em> &mdash; never stored, never synced.</li>
 *   <li>Classification is <strong>conservative</strong>: the simple and list editors may only claim
 *       content they can represent 100% losslessly. Anything ambiguous or mixed falls to
 *       {@link EditorType#ADVANCED}. A misclassification can therefore only ever go <em>toward</em>
 *       advanced, never a lossy downgrade.</li>
 *   <li>Title convention follows Nextcloud Notes: the first non-empty line is the title (no leading
 *       {@code #} required); the remaining non-empty lines are the body.</li>
 * </ul>
 *
 * <p>This class is intentionally free of Android dependencies so it can be unit-tested on the plain
 * JVM and reused both by the editor router and the grid-card binder.
 */
public final class NoteContentClassifier {

    public enum EditorType {
        /** Title + plain text only. */
        SIMPLE,
        /** Title (optional) + checkbox rows only. */
        LIST,
        /** Anything richer &mdash; rendered/edited with the full markdown editor. */
        ADVANCED
    }

    /**
     * A GFM task-list item: optional indent, a {@code -}/{@code *}/{@code +} bullet, then a
     * {@code [ ]} / {@code [x]} / {@code [X]} checkbox, then optional text.
     */
    private static final Pattern TASK_ITEM =
            Pattern.compile("^\\s*[-*+]\\s+\\[[ xX]]\\s?.*$");

    /**
     * Markers that mean "this needs the advanced (markdown) editor". If a note that is not a clean
     * list contains any of these, it is not a plain simple note. Kept deliberately broad so that
     * {@link EditorType#SIMPLE} only claims genuinely plain text.
     */
    private static final Pattern ADVANCED_MARKER = Pattern.compile(
            "(?m)(" +
            "^\\s*#{1,6}\\s" +                          // ATX heading
            "|^\\s*>\\s?" +                             // blockquote
            "|^\\s*```" +                               // fenced code (backticks)
            "|^\\s*~~~" +                               // fenced code (tildes)
            "|^\\s*[-*+]\\s+" +                         // bullet list (non-task bullets)
            "|^\\s*\\d+[.)]\\s+" +                      // ordered list
            "|^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$" +      // horizontal rule
            "|\\|" +                                    // table pipe
            "|!\\[" +                                   // image
            "|\\[[^]]*]\\([^)]*\\)" +                   // link: [text](url)
            "|\\*\\*" +                                 // bold **
            "|__" +                                     // bold/italic __
            "|~~" +                                     // strikethrough
            "|`" +                                      // inline code
            ")"
    );

    private NoteContentClassifier() {
        throw new UnsupportedOperationException("Do not instantiate this util class.");
    }

    /**
     * @param content the full markdown content of the note (including its title line)
     * @return the inferred {@link EditorType}
     */
    @NonNull
    public static EditorType classify(@NonNull String content) {
        final List<String> lines = nonEmptyLines(content);

        // Empty / whitespace-only note (e.g. a brand-new note) -> simple.
        if (lines.isEmpty()) {
            return EditorType.SIMPLE;
        }

        // LIST iff either:
        //   (a) every non-empty line is a task item (a list without a separate title line), or
        //   (b) the first line is a non-task title and every remaining non-empty line is a task item.
        // The title line in case (b) may contain markdown (e.g. "# Groceries"). This stays lossless
        // because the list editor's contract is to round-trip the title line VERBATIM -- it must not
        // reformat or strip it (Option A).
        if (allTaskItems(lines, 0)) {
            return EditorType.LIST;
        }
        if (!isTaskItem(lines.get(0)) && lines.size() > 1 && allTaskItems(lines, 1)) {
            return EditorType.LIST;
        }

        // SIMPLE iff plain text only: no task items anywhere and no advanced markdown markers.
        if (!containsAnyTaskItem(lines) && !ADVANCED_MARKER.matcher(content).find()) {
            return EditorType.SIMPLE;
        }

        return EditorType.ADVANCED;
    }

    @NonNull
    private static List<String> nonEmptyLines(@NonNull String content) {
        final String[] raw = content.split("\n", -1);
        final List<String> lines = new ArrayList<>();
        for (final String line : raw) {
            if (line.trim().length() > 0) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static boolean isTaskItem(@NonNull String line) {
        return TASK_ITEM.matcher(line).matches();
    }

    /** @return {@code true} iff there is at least one line from {@code fromIndex} and all are task items. */
    private static boolean allTaskItems(@NonNull List<String> lines, int fromIndex) {
        if (fromIndex >= lines.size()) {
            return false;
        }
        for (int i = fromIndex; i < lines.size(); i++) {
            if (!isTaskItem(lines.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsAnyTaskItem(@NonNull List<String> lines) {
        for (final String line : lines) {
            if (isTaskItem(line)) {
                return true;
            }
        }
        return false;
    }
}
