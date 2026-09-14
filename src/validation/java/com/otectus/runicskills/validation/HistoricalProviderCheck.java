package com.otectus.runicskills.validation;

/** Isolated provider-bytecode comparison; never presented as an old-release gameplay boot. */
final class HistoricalProviderCheck {
    private HistoricalProviderCheck() {}
    static void run(net.minecraft.server.MinecraftServer server) {
        String artifact = System.getProperty("runicskills.historicalProviders");
        if (artifact == null) return;
        java.nio.file.Path jar = java.nio.file.Path.of(artifact);
        var root = new com.google.gson.JsonObject();
        root.addProperty("mode", "historical 2.1.2 provider bytecode on 2.2.0 production registry/config; not a baseline gameplay pass");
        root.addProperty("artifact_sha256", com.otectus.runicskills.integration.common.IntegrationRuntime.sha256(jar));
        var exact = java.util.Set.of("HandlerSkill", "SpartanIntegration", "IceAndFireIntegration", "LocksIntegration",
                "SamuraiDynastyIntegration", "MoreVanillaIntegration", "JewelcraftIntegration", "IronsSpellbooksIntegration");
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, HistoricalProviderCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                String simple = name.substring(name.lastIndexOf('.')+1).split("\\$")[0];
                if (name.startsWith("com.otectus.runicskills.integration.lock.") ||
                        (name.startsWith("com.otectus.runicskills.") && exact.contains(simple))) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> found = findLoadedClass(name);
                        if (found == null) found = findClass(name);
                        if (resolve) resolveClass(found);
                        return found;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }) {
            var cls = loader.loadClass("com.otectus.runicskills.handler.HandlerSkill");
            @SuppressWarnings("unchecked")
            var rules = (java.util.Map<String, java.util.List<com.otectus.runicskills.common.model.Skills>>) cls.getMethod("getSkill").invoke(null);
            var items = new com.google.gson.JsonObject();
            rules.forEach((id, list) -> {
                var row = new com.google.gson.JsonObject(); var vector = new com.google.gson.JsonObject();
                list.forEach(value -> vector.addProperty(value.getKey(), value.getSkillLvl()));
                row.add("requirements", vector);
                row.addProperty("registered", net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(new net.minecraft.resources.ResourceLocation(id)));
                if (!list.isEmpty()) row.addProperty("source", list.get(0).getSource()); items.add(id, row);
            });
            root.add("items", items); root.addProperty("passed", true);
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError e) {
            root.addProperty("passed", false); root.addProperty("failure", e.toString());
            com.otectus.runicskills.RunicSkills.getLOGGER().error("Historical provider comparison failed", e);
        }
        try {
            java.nio.file.Files.writeString(server.getServerDirectory().toPath().resolve("debug/runicskills-historical-providers.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
