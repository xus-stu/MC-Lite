package mclite;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Log {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static PrintWriter writer;
    private static Path file;
    private static int linesSinceCheck;

    private Log() {
    }

    private static final long MAX_LOG_BYTES = 1_000_000L;

    public static synchronized void init(Path logFile) {
        try {
            Files.createDirectories(logFile.getParent());
            rotateIfLarge(logFile);
            file = logFile;
            writer = newWriter(logFile);
            Runtime.getRuntime().addShutdownHook(new Thread(Log::close, "mclite-log-close"));
            info("MC Lite 启动");
        } catch (IOException ex) {
            System.err.println("无法创建日志文件: " + ex.getMessage());
        }
    }

    /** Keeps the launcher log bounded: one old file, rotated once it passes 1 MB. */
    private static void rotateIfLarge(Path logFile) {
        try {
            if (!Files.isRegularFile(logFile) || Files.size(logFile) <= MAX_LOG_BYTES) {
                return;
            }
            Path old = logFile.resolveSibling(logFile.getFileName() + ".1");
            Files.move(logFile, old, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            System.err.println("无法轮转日志文件: " + ex.getMessage());
        }
    }

    private static PrintWriter newWriter(Path logFile) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
    }

    /** Long-running sessions also rotate: checked every 200 lines instead of only at startup. */
    private static void maybeRotate() {
        if (writer == null || file == null || ++linesSinceCheck < 200) {
            return;
        }
        linesSinceCheck = 0;
        try {
            if (Files.size(file) <= MAX_LOG_BYTES) {
                return;
            }
            writer.close();
            writer = null;
            Path old = file.resolveSibling(file.getFileName() + ".1");
            try {
                Files.move(file, old, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.err.println("无法轮转日志文件: " + ex.getMessage());
            }
            writer = newWriter(file);
        } catch (IOException ex) {
            System.err.println("检查日志大小失败: " + ex.getMessage());
            if (writer == null) {
                try {
                    writer = newWriter(file);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static Path file() {
        return file;
    }

    public static synchronized void info(String message) {
        write("INFO", message, null);
    }

    public static synchronized void warn(String message) {
        write("WARN", message, null);
    }

    public static synchronized void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    private static void write(String level, String message, Throwable error) {
        String line = TIME.format(LocalDateTime.now()) + " [" + level + "] " + message;
        System.err.println(line);
        if (writer != null) {
            writer.println(line);
            if (error != null) {
                error.printStackTrace(writer);
            }
            maybeRotate();
        } else if (error != null) {
            error.printStackTrace(System.err);
        }
    }

    public static synchronized void close() {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }
}
