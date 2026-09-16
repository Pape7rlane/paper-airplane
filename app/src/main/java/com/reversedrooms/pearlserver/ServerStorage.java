package com.reversedrooms.pearlserver;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Shared paths for the native servers and SRTools downloads. */
final class ServerStorage {
    static final String SERVER_FOLDER = "Pearl SR";
    static final String TOOLS_FOLDER = "Strools";

    private ServerStorage() { }

    static boolean hasAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    static File serverDirectory() {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), SERVER_FOLDER);
    }

    static String downloadFolder(String fileName) {
        if ("freesr-data.json".equalsIgnoreCase(fileName)
                || "config.json".equalsIgnoreCase(fileName)) {
            return SERVER_FOLDER + "/" + TOOLS_FOLDER;
        }
        return SERVER_FOLDER;
    }

    static File downloadDirectory(String fileName) throws IOException {
        File directory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), downloadFolder(fileName));
        ensureDirectory(directory);
        return directory;
    }

    static File prepare(Context context) throws IOException {
        if (!hasAccess(context)) {
            throw new IOException("请先允许存储访问权限");
        }
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            throw new IOException("共享存储当前不可写");
        }
        File directory = serverDirectory();
        ensureDirectory(directory);
        ensureDirectory(new File(directory, TOOLS_FOLDER));

        // Keep the previous installation's editable data when moving to Downloads.
        File legacy = new File(context.getFilesDir(), "4.5.54");
        for (String name : new String[]{"freesr-data.json", "hotfix.json"}) {
            File target = new File(directory, name);
            File source = new File(legacy, name);
            if (!target.exists() && source.isFile()) {
                try (InputStream in = new FileInputStream(source)) {
                    copyMissing(in, target);
                }
            }
        }
        copyAssets(context, "server", directory);
        return directory;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("无法创建目录: " + directory);
        }
    }

    private static void copyAssets(Context context, String assetPath, File target)
            throws IOException {
        String[] children = context.getAssets().list(assetPath);
        if (children != null && children.length > 0) {
            ensureDirectory(target);
            for (String child : children) {
                copyAssets(context, assetPath + "/" + child, new File(target, child));
            }
        } else if (!target.exists()) {
            try (InputStream in = context.getAssets().open(assetPath)) {
                copyMissing(in, target);
            }
        } else if (!target.isFile()) {
            throw new IOException("文件路径被目录占用: " + target);
        }
    }

    // Only publish a complete seed file; never overwrite an existing synced file.
    private static void copyMissing(InputStream in, File target) throws IOException {
        File temporary = File.createTempFile(".pearl-", ".part", target.getParentFile());
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }
            if (!target.exists()) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }
}
