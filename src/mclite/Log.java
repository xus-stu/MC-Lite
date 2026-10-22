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

    private Log() {
    }

    public static synchronized void init(Path logFile) {
        try {
            Files.createDirectories(logFile.getParent());
            file = logFile;
            writer = new PrintWriter(Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
            Runtime.getRuntime().addShutdownHook(new Thread(Log::close, "mclite-log-close"));
            info("MC Lite 启动");
        } catch (IOException ex) {
            System.err.println("无法创建日志文件: " + ex.getMessage());
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
