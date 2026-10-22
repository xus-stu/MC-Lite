package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public final class AccountStore {
    private AccountStore() {
    }

    public record Account(String id, String name, String type, long created) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record Data(List<Account> accounts, String selectedId) {
    }

    public static Data load(Path dir) {
        Properties properties = new Properties();
        Path file = dir.resolve("accounts.properties");
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException ex) {
                Log.warn("读取账户文件失败: " + ex.getMessage());
            }
        }
        List<Account> accounts = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("account.") || !key.endsWith(".name")) {
                continue;
            }
            String id = key.substring("account.".length(), key.length() - ".name".length());
            String name = properties.getProperty(key);
            if (!Config.isValidUsername(name)) {
                continue;
            }
            accounts.add(new Account(id, name,
                    properties.getProperty("account." + id + ".type", "offline"),
                    parseLong(properties.getProperty("account." + id + ".created"))));
        }
        if (accounts.isEmpty()) {
            Account steve = create("Steve");
            accounts.add(steve);
            save(dir, new Data(accounts, steve.id()));
            return new Data(accounts, steve.id());
        }
        String selectedValue = properties.getProperty("selected", "");
        String selected = accounts.stream().map(Account::id)
                .filter(id -> id.equals(selectedValue)).findFirst()
                .orElse(accounts.get(0).id());
        return new Data(accounts, selected);
    }

    public static void save(Path dir, Data data) {
        try {
            Files.createDirectories(dir);
            Properties properties = new Properties();
            for (Account account : data.accounts()) {
                properties.setProperty("account." + account.id() + ".name", account.name());
                properties.setProperty("account." + account.id() + ".type", account.type());
                properties.setProperty("account." + account.id() + ".created",
                        Long.toString(account.created()));
            }
            properties.setProperty("selected", data.selectedId() == null ? "" : data.selectedId());
            Path temp = dir.resolve("accounts.properties.tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                properties.store(out, "MC Lite offline accounts");
            }
            Files.move(temp, dir.resolve("accounts.properties"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Log.warn("保存账户失败: " + ex.getMessage());
        }
    }

    public static Account create(String name) {
        String id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        return new Account(id, name, "offline", System.currentTimeMillis());
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
