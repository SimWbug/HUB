package fr.cleangang.hub.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FileUtil {
    private FileUtil() {}

    /** Copie récursive ; skip reçoit le chemin relatif (avec des "/"). */
    public static void copyDir(Path src, Path dst, Predicate<String> skip) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = src.relativize(file).toString().replace(File.separatorChar, '/');
                if (!skip.test(rel)) {
                    Files.copy(file, dst.resolve(src.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    public static void zipDir(Path src, File zip, Predicate<String> skip) throws IOException {
        Files.createDirectories(zip.toPath().getParent());
        try (OutputStream out = Files.newOutputStream(zip.toPath());
             ZipOutputStream zos = new ZipOutputStream(out);
             Stream<Path> walk = Files.walk(src)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String rel = src.relativize(p).toString().replace(File.separatorChar, '/');
                if (skip.test(rel)) continue;
                zos.putNextEntry(new ZipEntry(rel));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
    }
}
