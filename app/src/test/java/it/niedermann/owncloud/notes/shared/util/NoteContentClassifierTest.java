/*
 * Nextcloud Notes - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Reidar Cederqvist
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package it.niedermann.owncloud.notes.shared.util;

import static it.niedermann.owncloud.notes.shared.util.NoteContentClassifier.EditorType.ADVANCED;
import static it.niedermann.owncloud.notes.shared.util.NoteContentClassifier.EditorType.LIST;
import static it.niedermann.owncloud.notes.shared.util.NoteContentClassifier.EditorType.SIMPLE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NoteContentClassifierTest {

    // ---- SIMPLE ----

    @Test
    public void emptyOrBlankIsSimple() {
        assertEquals(SIMPLE, NoteContentClassifier.classify(""));
        assertEquals(SIMPLE, NoteContentClassifier.classify("   \n  \n"));
    }

    @Test
    public void plainSingleLineIsSimple() {
        assertEquals(SIMPLE, NoteContentClassifier.classify("Buy milk and bread"));
    }

    @Test
    public void titlePlusPlainTextIsSimple() {
        assertEquals(SIMPLE, NoteContentClassifier.classify("Shopping\nmilk\nbread\neggs"));
    }

    @Test
    public void plainTextWithPunctuationStaysSimple() {
        // dashes mid-line, parentheses, percent etc. must NOT trip the advanced detector
        assertEquals(SIMPLE, NoteContentClassifier.classify("Call Anna - maybe around 5 (50% sure)"));
    }

    // ---- LIST ----

    @Test
    public void pureCheckboxListIsList() {
        assertEquals(LIST, NoteContentClassifier.classify("- [ ] Milk\n- [x] Bread"));
    }

    @Test
    public void titleLineBeforeChecklistIsAdvanced() {
        // A note that starts with a title/heading before the checkboxes is NOT a clean list; it is
        // treated as ADVANCED (the heading is editable as markdown there). A list's name lives in the
        // note title, not in the content.
        assertEquals(ADVANCED, NoteContentClassifier.classify("Groceries\n- [ ] Milk\n- [x] Bread"));
        assertEquals(ADVANCED, NoteContentClassifier.classify("# Groceries\n- [ ] Milk"));
        assertEquals(ADVANCED, NoteContentClassifier.classify("**Groceries**\n- [ ] Milk"));
    }

    @Test
    public void indentedChildItemsStillList() {
        assertEquals(LIST, NoteContentClassifier.classify("- [ ] Parent\n    - [x] Child"));
    }

    @Test
    public void starAndPlusBulletsAreList() {
        assertEquals(LIST, NoteContentClassifier.classify("* [ ] a\n+ [x] b"));
    }

    @Test
    public void itemWithInlineFormattingStillList() {
        // inline formatting *inside* a task item is fine; it is still a clean list
        assertEquals(LIST, NoteContentClassifier.classify("- [ ] Buy **milk**\n- [ ] Bread"));
    }

    @Test
    public void newlySeededEmptyListItemIsList() {
        // a freshly created list is seeded with one empty checkbox so it classifies as LIST
        assertEquals(LIST, NoteContentClassifier.classify("- [ ] "));
    }

    // ---- ADVANCED (the conservative "don't misinterpret" guarantees) ----

    @Test
    public void textPlusOneCheckboxIsAdvanced_neverLossyDowngrade() {
        // mixed text + a single checkbox must NOT be claimed by the list editor
        assertEquals(ADVANCED, NoteContentClassifier.classify("Reminder\nsome text\n- [ ] todo"));
    }

    @Test
    public void listWithTrailingTextIsAdvanced() {
        assertEquals(ADVANCED, NoteContentClassifier.classify("- [ ] a\n- [ ] b\nremember to call"));
    }

    @Test
    public void headingIsAdvanced() {
        assertEquals(ADVANCED, NoteContentClassifier.classify("# Title\nbody"));
    }

    @Test
    public void inlineFormattingInPlainNoteIsAdvanced() {
        assertEquals(ADVANCED, NoteContentClassifier.classify("This is **bold** text"));
        assertEquals(ADVANCED, NoteContentClassifier.classify("Use `git status` here"));
    }

    @Test
    public void linkIsAdvanced() {
        assertEquals(ADVANCED, NoteContentClassifier.classify("See [docs](https://example.com)"));
    }

    @Test
    public void codeFenceIsAdvanced() {
        assertEquals(ADVANCED, NoteContentClassifier.classify("```\ncode\n```"));
    }

    @Test
    public void tableIsAdvanced() {
        assertEquals(ADVANCED, NoteContentClassifier.classify("a | b\n--- | ---\n1 | 2"));
    }

    @Test
    public void plainBulletListIsAdvanced() {
        // non-task bullet list is not a checkbox list and not plain text -> advanced
        assertEquals(ADVANCED, NoteContentClassifier.classify("- milk\n- bread"));
    }
}
