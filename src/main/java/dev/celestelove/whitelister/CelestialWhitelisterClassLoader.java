package dev.celestelove.whitelister;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class CelestialWhitelisterClassLoader implements PluginLoader {
    private static final Exclusion EXCLUDE_ALL = new Exclusion("*", "*", "*", "*");

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        var resolver = new MavenLibraryResolver();

        try (var stream = getClass().getResourceAsStream("/paper-libraries.txt")) {
            if (stream == null) throw new RuntimeException("Missing paper-libraries.txt in plugin JAR");
            var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (var line : content.split("\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    resolver.addDependency(new Dependency(
                            new DefaultArtifact(line), null, false, List.of(EXCLUDE_ALL)
                    ));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read paper-libraries.txt", e);
        }

        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
        ).build());

        classpathBuilder.addLibrary(resolver);
    }
}
