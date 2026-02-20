package net.akat.bank.transfer.manager;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import org.bukkit.Bukkit;

import net.akat.bank.transfer.Main;
import net.akat.bank.transfer.Transaction;

public class TransactionManager {
    private final Main plugin;
    private Connection connection;

    public TransactionManager(Main plugin) {
        this.plugin = plugin;
        connect();
        createTableIfNotExists();
    }

    private void connect() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, "transactions.db");
            String dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(dbUrl);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player TEXT, " +
                "otherPlayer TEXT, " +
                "amount REAL, " +
                "timestamp TEXT, " +
                "type TEXT, " +
                "message TEXT, " +
                "read INTEGER DEFAULT 0" +
                ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addTransaction(String sender, String receiver, double amount, LocalDateTime timestamp, String message, boolean read) {
        saveTransaction(sender, receiver, amount, timestamp, "sent", message, read);
        saveTransaction(receiver, sender, amount, timestamp, "received", message, read);
    }

    private void saveTransaction(String player, String otherPlayer, double amount, LocalDateTime timestamp, String type, String message, boolean read) {
        String sql = "INSERT INTO transactions (player, otherPlayer, amount, timestamp, type, message, read) VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, player);
            stmt.setString(2, otherPlayer);
            stmt.setDouble(3, amount);
            stmt.setString(4, timestamp.atZone(ZoneId.of("Europe/Moscow")).toString());
            stmt.setString(5, type);
            stmt.setString(6, message != null ? message : "");
            stmt.setInt(7, read ? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateReadStatus(String player, String otherPlayer, double amount, boolean read, String transactionType) {
        // Создаем асинхронную задачу
        AsyncScheduler asyncScheduler = plugin.getServer().getAsyncScheduler();
        asyncScheduler.runNow(plugin, task -> {
            String sql = "UPDATE transactions SET read = ? WHERE player = ? AND otherPlayer = ? AND amount = ? AND type = ?;";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, read ? 1 : 0);
                stmt.setString(2, player);
                stmt.setString(3, otherPlayer);
                stmt.setDouble(4, amount);
                stmt.setString(5, transactionType);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public List<Transaction> getUnreadTransactionsForPlayer(String playerName) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE player = ? AND read = false AND (type = 'received' OR type = 'sent') ORDER BY timestamp DESC;";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String otherPlayer = rs.getString("otherPlayer");
                double amount = rs.getDouble("amount");
                String rawTimestamp = rs.getString("timestamp");
                ZonedDateTime zonedTimestamp;
                try {
                    zonedTimestamp = ZonedDateTime.parse(rawTimestamp);
                } catch (DateTimeParseException e) {
                    LocalDateTime localTimestamp = LocalDateTime.parse(rawTimestamp);
                    zonedTimestamp = localTimestamp.atZone(ZoneId.of("Europe/Moscow"));
                }
                LocalDateTime timestamp = zonedTimestamp.toLocalDateTime();
                String type = rs.getString("type");
                String message = rs.getString("message");
                boolean read = rs.getBoolean("read");

                // Добавляем транзакцию в список
                transactions.add(new Transaction(playerName, otherPlayer, amount, timestamp, type, message, read));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return transactions;
    }

    public List<Transaction> getTransactionsForPlayer(String playerName, String transactionType) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE player = ? AND type = ? ORDER BY timestamp DESC;";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            stmt.setString(2, transactionType);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String otherPlayer = rs.getString("otherPlayer");
                double amount = rs.getDouble("amount");
                String rawTimestamp = rs.getString("timestamp");
                ZonedDateTime zonedTimestamp;
                try {
                    zonedTimestamp = ZonedDateTime.parse(rawTimestamp);
                } catch (DateTimeParseException e) {
                    // Старый формат без временной зоны — интерпретируем как Moscow time
                    LocalDateTime localTimestamp = LocalDateTime.parse(rawTimestamp);
                    zonedTimestamp = localTimestamp.atZone(ZoneId.of("Europe/Moscow"));
                }
                LocalDateTime timestamp = zonedTimestamp.toLocalDateTime();
                String type = rs.getString("type");
                String message = rs.getString("message");
                boolean read = rs.getInt("read") == 1;
                transactions.add(new Transaction(playerName, otherPlayer, amount, timestamp, type, message, read));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return transactions;
    }

    public void cleanupOldTransactions() {
        String sql = "DELETE FROM transactions WHERE timestamp <= ?";

        LocalDateTime oneMonthAgo = LocalDateTime.now(ZoneId.of("Europe/Moscow")).minusMonths(1);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, oneMonthAgo.toString());
            int deletedRows = stmt.executeUpdate();
            plugin.getLogger().info("Удалено " + deletedRows + " старых транзакций из базы данных.");
        } catch (SQLException e) {
            e.printStackTrace();
            plugin.getLogger().severe("Ошибка при очистке старых транзакций.");
        }
    }

    public List<Transaction> findTransactionsWithSameAmount(List<Transaction> transactions, double amount) {
        List<Transaction> sameAmountTransactions = new ArrayList<>();
        for (Transaction tx : transactions) {
            if (tx.getAmount() == amount) {
                sameAmountTransactions.add(tx);
            }
        }
        return sameAmountTransactions;
    }
}
