package com.skyblockminer.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.skyblockminer.game.Server;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mozilla.javascript.Context;

class ScriptsTest {
    @Test
    void headerNamesTheScript() {
        Scripts.Info info = Scripts.parse("farm.js", """
            // @name   Pumpkin farm
            // Some notes that are not tags.
            // @description Walks the lanes.
            // @server Hypixel

            // @name Not part of the header
            log('hi');
            """);
        assertEquals("Pumpkin farm", info.name());
        assertEquals("Walks the lanes.", info.description());
        assertSame(Server.Kind.HYPIXEL, info.server());
    }

    @Test
    void missingHeaderFallsBackToTheFileName() {
        Scripts.Info info = Scripts.parse("auto_fish.js", "sleep(10);\n// @name ignored");
        assertEquals("auto_fish", info.name());
        assertEquals("", info.description());
        assertNull(info.server());
        assertNull(Scripts.parse("x.js", "// @server any").server());
        assertSame(Server.Kind.WYNNCRAFT, Scripts.parse("x.js", "// @server wynn").server());
    }

    @Test
    void firstReloadCreatesTheFolderWithAnExample(@TempDir Path root) throws IOException {
        Scripts scripts = new Scripts(root.resolve("scripts"));
        List<Scripts.Info> found = scripts.reload();
        assertEquals(1, found.size());
        assertEquals("Example", found.get(0).name());
        assertEquals(Scripts.EXAMPLE, scripts.read(found.get(0)));

        Files.writeString(root.resolve("scripts/b.js"), "// @name B\n");
        Files.writeString(root.resolve("scripts/notes.txt"), "not a script");
        Files.delete(root.resolve("scripts/example.js"));
        scripts.reload();
        assertEquals(List.of("b.js"), scripts.list().stream().map(Scripts.Info::file).toList());
        assertSame(scripts.list().get(0), scripts.find("B"));
        assertNull(scripts.find("example"));
    }

    @Test
    void bundledExamplesCompile() throws IOException {
        String doc;
        try (var in = Scripts.class.getResourceAsStream("/skyblockminer/scripts/API.md")) {
            doc = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String docExample = doc.substring(doc.lastIndexOf("```js") + 5, doc.lastIndexOf("```"));
        Context cx = Context.enter();
        try {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.compileString(Scripts.EXAMPLE, "example.js", 1, null);
            cx.compileString(docExample, "API.md", 1, null);
        } finally {
            Context.exit();
        }
    }
}
