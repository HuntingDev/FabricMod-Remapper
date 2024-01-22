package dev.hunter.deobf;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class YarnDownloading {
    public static Path path;
    public static Path resolve(String mappingsVersion) throws IOException {
        Path currentDirectory = Paths.get("");
        Path mappingsTemp;

        String name = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);

        if (name.contains("win")) {
            mappingsTemp = currentDirectory.resolve("mappings.gz");
        } else {
            mappingsTemp = currentDirectory.resolve("mappings" + mappingsVersion);
        }

        try (InputStream inputStream = getMappingsFromMaven(mappingsVersion)) {
            Files.copy(inputStream, mappingsTemp, StandardCopyOption.REPLACE_EXISTING);
        }

        return mappingsTemp;
    }

    public static Path resolveTiny2(String mappingsVersion) throws IOException {
        Path currentDirectory = Paths.get("");
        Path mappingsTemp;
        String fileMappings = "mappings.tiny";

        String name = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);

        if (name.contains("win")) {
            mappingsTemp = currentDirectory.resolve("mappings-v2.zip");
        } else {
            mappingsTemp = currentDirectory.resolve("mappings-v2" + mappingsVersion);
        }

        try (InputStream inputStream = getTiny2Mappings(mappingsVersion)) {
            Files.copy(inputStream, mappingsTemp, StandardCopyOption.REPLACE_EXISTING);
        }

        path = mappingsTemp;

        return extractFileFromZip(mappingsTemp, fileMappings, currentDirectory);
    }

    private static Path extractFileFromZip(Path zipPath, String targetFileName, Path outputDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            Path outputPath;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".tiny")) {
                    try (InputStream inputStream = zipFile.getInputStream(entry)) {
                        outputPath = outputDir.resolve(targetFileName);
                        Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return outputPath;
                }
            }
        }

        throw new IOException( "File " + targetFileName + " cannot be found in " + zipPath);
    }

    private static InputStream getTiny2Mappings(String mappingsVersion) throws IOException {
        URL url = new URL(String.format("https://maven.fabricmc.net/net/fabricmc/yarn/%s/yarn-%s-v2.jar", mappingsVersion, mappingsVersion));
        URLConnection connection = url.openConnection();
        return connection.getInputStream();
    }
    private static InputStream getMappingsFromMaven(String mappingsVersion) throws IOException {
        URL url = new URL(String.format("https://maven.fabricmc.net/net/fabricmc/yarn/%s/yarn-%s-tiny.gz", mappingsVersion, mappingsVersion));
        URLConnection connection = url.openConnection();
        return connection.getInputStream();
    }



    
}
