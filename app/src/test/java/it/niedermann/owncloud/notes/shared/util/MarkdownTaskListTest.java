/*
 * Nextcloud Notes - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Reidar Cederqvist
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package it.niedermann.owncloud.notes.shared.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class MarkdownTaskListTest {

    @Test
    public void parsesPlainList() {
        final var parsed = MarkdownTaskList.parse("- [ ] Milk\n- [x] Bread");
        assertNull(parsed.title);
        assertEquals(2, parsed.items.size());
        assertFalse(parsed.items.get(0).checked);
        assertEquals("Milk", parsed.items.get(0).text);
        assertTrue(parsed.items.get(1).checked);
        assertEquals("Bread", parsed.items.get(1).text);
    }

    @Test
    public void parsesTitledList() {
        final var parsed = MarkdownTaskList.parse("Groceries\n- [ ] Milk\n- [x] Bread");
        assertEquals("Groceries", parsed.title);
        assertEquals(2, parsed.items.size());
    }

    @Test
    public void preservesMarkdownTitleVerbatim() {
        final var parsed = MarkdownTaskList.parse("# Groceries\n- [ ] Milk");
        assertEquals("# Groceries", parsed.title);
        assertEquals(1, parsed.items.size());
    }

    @Test
    public void preservesIndentAndBullet() {
        final var parsed = MarkdownTaskList.parse("- [ ] Parent\n    * [x] Child");
        assertEquals("", parsed.items.get(0).indent);
        assertEquals('-', parsed.items.get(0).bullet);
        assertEquals("    ", parsed.items.get(1).indent);
        assertEquals('*', parsed.items.get(1).bullet);
    }

    @Test
    public void serializeProducesCanonicalMarkdown() {
        final var items = Arrays.asList(
                new MarkdownTaskList.Item("", '-', false, "Milk"),
                new MarkdownTaskList.Item("", '-', true, "Bread"));
        assertEquals("Groceries\n- [ ] Milk\n- [x] Bread", MarkdownTaskList.serialize("Groceries", items));
    }

    @Test
    public void serializeWithoutTitle() {
        final var items = Arrays.asList(
                new MarkdownTaskList.Item("", '-', false, "A"),
                new MarkdownTaskList.Item("", '-', false, "B"));
        assertEquals("- [ ] A\n- [ ] B", MarkdownTaskList.serialize(null, items));
        assertEquals("- [ ] A\n- [ ] B", MarkdownTaskList.serialize("   ", items));
    }

    @Test
    public void emptyItemRoundTrips() {
        final var parsed = MarkdownTaskList.parse("- [ ] ");
        assertEquals(1, parsed.items.size());
        assertEquals("", parsed.items.get(0).text);
        assertEquals("- [ ] ", MarkdownTaskList.serialize(parsed.title, parsed.items));
    }

    @Test
    public void roundTripIsIdempotent() {
        for (final String input : new String[]{
                "- [ ] Milk\n- [x] Bread",
                "Groceries\n- [ ] Milk\n- [x] Bread",
                "# Groceries\n- [ ] Milk\n    - [x] Child",
                "- [ ] ",
        }) {
            final String once = serializeReparse(input);
            final String twice = serializeReparse(once);
            assertEquals("round-trip must be stable for: " + input, once, twice);
        }
    }

    private static String serializeReparse(String input) {
        final var p = MarkdownTaskList.parse(input);
        return MarkdownTaskList.serialize(p.title, p.items);
    }
}
