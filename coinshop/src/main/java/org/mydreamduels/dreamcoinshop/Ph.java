package org.mydreamduels.dreamcoinshop;

import java.util.LinkedHashMap;
import java.util.Map;

/** Placeholders for one message: plain values, MiniMessage snippets and style aliases. */
final class Ph {

    /** &lt;name&gt; -> text, inserted as-is. */
    final Map<String, String> values = new LinkedHashMap<>();
    /** &lt;name&gt; -> MiniMessage, parsed (e.g. &lt;block_icon&gt; -> "&lt;icon:hopper&gt;"). */
    final Map<String, String> snippets = new LinkedHashMap<>();
    /** &lt;tag&gt;..&lt;/tag&gt; -> styled like a palette entry (e.g. &lt;lvl&gt; -> "bad"). */
    final Map<String, String> styles = new LinkedHashMap<>();

    static Ph of(Object... keyValues) {
        Ph ph = new Ph();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            ph.values.put(String.valueOf(keyValues[i]), String.valueOf(keyValues[i + 1]));
        }
        return ph;
    }

    Ph set(String key, Object value) {
        values.put(key, String.valueOf(value));
        return this;
    }

    Ph snippet(String key, String miniMessage) {
        snippets.put(key, miniMessage);
        return this;
    }

    Ph style(String tag, String paletteEntry) {
        styles.put(tag, paletteEntry);
        return this;
    }

    Ph merge(Ph other) {
        if (other != null) {
            values.putAll(other.values);
            snippets.putAll(other.snippets);
            styles.putAll(other.styles);
        }
        return this;
    }
}
