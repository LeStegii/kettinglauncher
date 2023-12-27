package org.kettingpowered.launcher.internal.utils;

import org.jetbrains.annotations.Nullable;
import org.kettingpowered.ketting.internal.KettingConstants;
import org.kettingpowered.launcher.KettingLauncher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

//Copied from https://git.magmafoundation.org/JustRed23/Magma-1-18-x/-/blob/1.18.x/magmalauncher/src/main/java/org/magmafoundation/magma/installer/NetworkUtils.java
public class NetworkUtils {

    private static final AtomicInteger threadNr = new AtomicInteger();
    private static final ExecutorService downloadSrvc  = java.util.concurrent.Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("network-utils-download-thread-"+threadNr.incrementAndGet());
        return thread;
    });


    public static URLConnection getConnection(String url) {
        URLConnection conn = null;
        try {
            conn = new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:31.0) Gecko/20100101 Firefox/31.0");

            int timeout = (int) TimeUnit.SECONDS.toMillis(20);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return conn;
    }

    public static void downloadFile(String URL, File f) throws Exception {
        downloadFile(URL, f, null, "SHA-512");
    }

    public static void downloadFile(String URL, File f, @Nullable String expectedHash, String hashFormat) throws Exception {
        URLConnection conn = getConnection(URL);
        ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());
        FileChannel fc = FileChannel.open(f.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        int fS = conn.getContentLength();
        CompletableFuture.supplyAsync(() -> {
            try {
                fc.transferFrom(rbc, 0, fS);
                fc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }, downloadSrvc).get(20, TimeUnit.SECONDS);
        rbc.close();
        String hash = Hash.getHash(f, hashFormat);
        if(expectedHash != null && !hash.equals(expectedHash.toLowerCase())) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            throw new Exception(hashFormat+" hash of downloaded file does not match expected value!");
        }
    }
    
    public static String downloadToString(String URL, @Nullable String expectedHash, @Nullable String hashFormat) throws Exception {
        if (hashFormat == null) hashFormat = "SHA-512";
        URLConnection conn = getConnection(URL);
        InputStream stream = conn.getInputStream();
        StringWriter writer = new StringWriter();
        MessageDigest digest = MessageDigest.getInstance(hashFormat);
        int fS = conn.getContentLength();
        CompletableFuture.supplyAsync(() -> {
            byte[] buffer = new byte[Math.min(fS, KettingLauncher.BufferSize)];
            try (BufferedInputStream inputStream = new BufferedInputStream(stream)) {
                int total;
                while ((total = inputStream.read(buffer)) != -1) {
                    writer.append(new String(buffer));
                    digest.update(buffer, 0, total);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }, downloadSrvc).get(20, TimeUnit.SECONDS);
        stream.close();
        
        String hash = Hash.getHash(digest);
        if(expectedHash != null && !hash.equals(expectedHash.toLowerCase())) {
            throw new Exception(hashFormat + " hash of downloaded file does not match expected value!");
        }
        return writer.toString();
    }

    public static String readFile(String URL) {
        try {
            File f = File.createTempFile(KettingConstants.NAME, ".tmp");
            downloadFile(URL, f);
            return new String(Files.readAllBytes(f.toPath()));
        } catch (FileNotFoundException fnf) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
