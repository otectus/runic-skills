package com.otectus.runicskills.registry;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the "no missing perk icon" invariant from the 1.5.x icon overhaul.
 *
 * <p>Every perk icon is an original Runic Skills texture living under
 * <code>assets/runicskills/textures/skill/&lt;skill&gt;/&lt;perk_id&gt;.png</code>. This test keeps
 * that true in three ways:</p>
 * <ol>
 *   <li>Every texture path built via <code>HandlerResources.create(...)</code> (and every literal
 *       <code>textures/skill/...</code> reference anywhere in main sources) resolves to a real PNG,
 *       so no perk can render the purple/black missing-texture square.</li>
 *   <li>No <code>*_PERK</code> constant points at a foreign mod namespace — borrowed third-party
 *       item sprites were replaced in 1.5.x and must not creep back in.</li>
 *   <li>Every <code>PERKS.register("name", ...)</code> id has a matching
 *       <code>textures/skill/&lt;skill&gt;/name.png</code> on disk.</li>
 * </ol>
 *
 * <p>Source-scanning (not class-loading) so it stays Forge-free, like
 * {@code PerkEffectCoverageTest}.</p>
 */
class PerkTextureResolutionTest {

    private static final Pattern CREATE_PATH = Pattern.compile("create\\(\"(textures/[\\w/]+\\.png)\"\\)");
    private static final Pattern LITERAL_SKILL_PATH = Pattern.compile("(textures/skill/[\\w/]+\\.png)");
    private static final Pattern FOREIGN_PERK_CONST = Pattern.compile(
            "ResourceLocation\\s+\\w+_PERK\\s*=\\s*new\\s+ResourceLocation\\(\"(?!" + "runicskills" + ")");
    private static final Pattern PERK_REGISTRATION = Pattern.compile("PERKS\\.register\\(\"(\\w+)\"");

    private static File root() {
        return new File(System.getProperty("user.dir"));
    }

    private static File mainJavaRoot() {
        return new File(root(), "src/main/java/com/otectus/runicskills");
    }

    private static File assetsRoot() {
        return new File(root(), "src/main/resources/assets/runicskills");
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void collectJava(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) collectJava(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }

    /** All texture paths referenced from main sources, whether via create() or literal ids. */
    private static Set<String> referencedTexturePaths() throws IOException {
        List<File> files = new ArrayList<>();
        collectJava(mainJavaRoot(), files);
        Set<String> paths = new TreeSet<>();
        for (File f : files) {
            String src = read(f);
            Matcher m = CREATE_PATH.matcher(src);
            while (m.find()) paths.add(m.group(1));
            m = LITERAL_SKILL_PATH.matcher(src);
            while (m.find()) paths.add(m.group(1));
        }
        return paths;
    }

    @Test
    void everyReferencedTextureResolvesToAFile() throws IOException {
        Set<String> paths = referencedTexturePaths();
        assertTrue(paths.size() > 400, "texture path parse looks wrong, only found " + paths.size());
        List<String> missing = new ArrayList<>();
        for (String p : paths) {
            if (!new File(assetsRoot(), p).isFile()) missing.add(p);
        }
        assertTrue(missing.isEmpty(),
                "Texture paths referenced in code with no PNG under assets/runicskills "
                        + "(these render as the missing-texture square in-game): " + missing);
    }

    @Test
    void noPerkConstantUsesAForeignNamespace() throws IOException {
        String src = read(new File(mainJavaRoot(), "handler/HandlerResources.java"));
        Matcher m = FOREIGN_PERK_CONST.matcher(src);
        List<String> offenders = new ArrayList<>();
        while (m.find()) offenders.add(m.group());
        assertTrue(offenders.isEmpty(),
                "Perk icon constants must use original runicskills textures, not foreign mod sprites: "
                        + offenders);
    }

    @Test
    void everyRegisteredPerkIdHasAnIconNamedAfterIt() throws IOException {
        String src = read(new File(mainJavaRoot(), "registry/RegistryPerks.java"));
        Set<String> ids = new TreeSet<>();
        Matcher m = PERK_REGISTRATION.matcher(src);
        while (m.find()) ids.add(m.group(1));
        assertTrue(ids.size() > 400, "perk registration parse looks wrong, only found " + ids.size());

        File skillDir = new File(assetsRoot(), "textures/skill");
        File[] skillFolders = skillDir.listFiles(File::isDirectory);
        assertTrue(skillFolders != null && skillFolders.length > 0, "no skill texture folders found");

        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            boolean found = false;
            for (File folder : skillFolders) {
                if (new File(folder, id + ".png").isFile()) {
                    found = true;
                    break;
                }
            }
            if (!found) missing.add(id);
        }
        assertTrue(missing.isEmpty(),
                "Registered perks with no textures/skill/<skill>/<id>.png icon: " + missing);
    }
}
