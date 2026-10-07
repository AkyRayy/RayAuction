package ray.labs.rayauction.platform;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public final class RayAuctionLibraries implements PluginLoader {

    private static final List<String> COORDINATES = List.of(
            "com.zaxxer:HikariCP:6.3.0",
            "com.github.ben-manes.caffeine:caffeine:3.2.0",
            "com.h2database:h2:2.3.232",
            "org.postgresql:postgresql:42.7.7");

    @Override
    public void classloader(PluginClasspathBuilder builder) {
        addLocalLibraries(builder);
        if (reachable(MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR)) {
            MavenLibraryResolver resolver = new MavenLibraryResolver();
            resolver.addRepository(new RemoteRepository.Builder(
                        "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR)
                    .build());
            for (String coordinate : COORDINATES) {
                resolver.addDependency(new Dependency(new DefaultArtifact(coordinate), null));
            }
            builder.addLibrary(resolver);
        }
    }

    private void addLocalLibraries(PluginClasspathBuilder builder) {
        Path directory = builder.getContext().getDataDirectory().resolve("libs");
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .forEach(path -> builder.addLibrary(new JarLibrary(path)));
        } catch (IOException ignored) {
        }
    }

    private boolean reachable(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int code = connection.getResponseCode();
            return code >= 200 && code < 500;
        } catch (IOException ex) {
            return false;
        }
    }
}
