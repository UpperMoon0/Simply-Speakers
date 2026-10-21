package com.nstut.fabric.simplyspeakers;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Fabric has no side-specific dependency field; enforce the UI requirement before client entrypoints load. */
public final class OpenUiDependencyCheck implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT) return;
        ModMetadata owner = loader.getModContainer("simplyspeakers").orElseThrow(() -> new IllegalStateException("Missing Simply Speakers container")).getMetadata();
        ModMetadata openUi = loader.getModContainer("openui_mc")
                .map(container -> container.getMetadata()).orElse(null);
        validate(loader.getEnvironmentType(), owner, openUi);
    }

    static void validate(EnvType environment, ModMetadata owner, ModMetadata openUi) {
        if (environment != EnvType.CLIENT) return;
        ModDependency requirement = owner.getDependencies().stream()
                .filter(dependency -> dependency.getModId().equals("openui_mc")
                        && dependency.getKind() == ModDependency.Kind.SUGGESTS)
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing OpenUI client dependency declaration"));
        if (openUi == null || !requirement.matches(openUi.getVersion())) {
            throw new IllegalStateException(owner.getName() + " requires OpenUI MC (openui_mc) "
                    + requirement.getVersionRequirements() + " on the client. Install a compatible OpenUI MC jar. Found: "
                    + (openUi == null ? "not installed" : openUi.getVersion().getFriendlyString()));
        }
    }
}
