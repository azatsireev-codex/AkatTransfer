package net.akat.bank.transfer.gui;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.format.DateTimeFormatter;

import net.akat.bank.transfer.Main;
import net.akat.bank.transfer.Transaction;
import net.akat.bank.transfer.manager.TransactionManager;

public class TransactionMenu {
    private static final int MAX_LORE_LINES = 256;
    private static final int MENU_FOOTER_LINES = 2;

    @SuppressWarnings("unused")
    private final Main plugin;
    private final Logger logger;
    private final TransactionManager transactionManager;
    private final Map<Player, Integer> playerPages = new HashMap<>();
    private final Map<Player, String> playerTransactionTypes = new HashMap<>();
    private final Map<Player, Double> filterAmount = new HashMap<>(); // Хранение фильтров для игроков
    private final Map<UUID, String> viewedPlayerMap = new HashMap<>();

    public TransactionMenu(Main plugin, TransactionManager transactionManager) {
        this.logger = plugin.getLogger();
        this.plugin = plugin;
        this.transactionManager = transactionManager;
    }

    // Метод для установки фильтра суммы для игрока
    public void setFilterAmount(Player player, double amount) {
        filterAmount.put(player, amount);
    }

    // Метод для получения фильтра суммы для игрока
    public double getFilterAmount(Player player) {
        // Если фильтр не установлен для игрока, возвращаем 0 (или другое дефолтное значение)
        return filterAmount.getOrDefault(player, 0.0);
    }

    public void openTransactionMenu(Player player, String playerName, String transactionType) {
        setTransactionType(player, transactionType);
        setCurrentPage(player, 0);
        List<Transaction> transactions = getTransactionsForPlayer(playerName, transactionType);
        showPage(player, transactions, 0, transactionType);
    }

    @SuppressWarnings("deprecation")
    public void showPage(Player player, List<Transaction> transactions, int page, String transactionType) {
        Inventory menu = Bukkit.createInventory(null, 54, "Транзакции игрока - " + (transactionType.equals("sent") ? "Отправленные" : "Полученные"));

        // 1. Собираем уникальные транзакции по сумме (сохраняем только первую встреченную)
        Map<Double, Transaction> uniqueByAmount = new LinkedHashMap<>();
        for (Transaction tx : transactions) {
            uniqueByAmount.putIfAbsent(tx.getAmount(), tx);
        }

        List<Transaction> uniqueTransactions = new ArrayList<>(uniqueByAmount.values());

        // 2. Постраничный диапазон
        int startIndex = page * 45;
        int endIndex = Math.min(startIndex + 45, uniqueTransactions.size());
        List<Transaction> pageList = uniqueTransactions.subList(startIndex, endIndex);

        int menuIndex = 0;
        for (Transaction tx : pageList) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();

            // Название предмета
            String displayName = transactionType.equals("sent")
                    ? applyHexColor("#47C3F7") + "Все переводы с суммой -> " + applyHexColor("#6CCFF9") + tx.getAmount()
                    : applyHexColor("#0BB83D") + "Переводы от других с суммой -> " + applyHexColor("#68C57D") + tx.getAmount();
            meta.setDisplayName(displayName);

            // Лор с деталями всех транзакций этой суммы
            List<String> allLoreLines = transactionManager.findTransactionsWithSameAmount(transactions, tx.getAmount()).stream()
                    .map(t -> applyHexColor("#AAAAAA") + "Дата: " + applyHexColor("#F8D06E") + t.getTimestamp().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
                            + " | " + applyHexColor("#E3B140") + (transactionType.equals("sent") ? "-" : "+") + t.getAmount())
                    .collect(Collectors.toList());

            boolean hasHiddenLines = allLoreLines.size() > (MAX_LORE_LINES - MENU_FOOTER_LINES);
            int reservedLines = MENU_FOOTER_LINES + (hasHiddenLines ? 1 : 0);
            int maxDetailsLines = Math.max(0, MAX_LORE_LINES - reservedLines);

            List<String> lore = new ArrayList<>(allLoreLines.subList(0, Math.min(allLoreLines.size(), maxDetailsLines)));
            int hiddenLines = allLoreLines.size() - lore.size();
            if (hiddenLines > 0) {
                lore.add(applyHexColor("#FFAA00") + "... ещё " + hiddenLines + " записей");
            }

            lore.add("");
            lore.add(applyHexColor("#FFAA00") + "Нажмите, чтобы посмотреть подробнее");

            meta.setLore(lore);
            item.setItemMeta(meta);
            menu.setItem(menuIndex++, item);
        }

        // 3. Кнопка «предыдущая страница»
        if (page > 0) {
            ItemStack previousPage = new ItemStack(Material.ARROW);
            ItemMeta previousMeta = previousPage.getItemMeta();
            previousMeta.setDisplayName(applyHexColor("#FFAA00") + "Предыдущая страница");
            previousPage.setItemMeta(previousMeta);
            menu.setItem(45, previousPage);
        }

        // 4. Кнопка «следующая страница»
        if ((page + 1) * 45 < uniqueTransactions.size()) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(applyHexColor("#FFAA00") + "Следующая страница");
            nextPage.setItemMeta(nextMeta);
            menu.setItem(53, nextPage);
        }

        player.openInventory(menu);
    }

    @SuppressWarnings("deprecation")
    public void openGroupedTransactionDetailsMenu(Player player, List<Transaction> transactions, String transactionType, int page, double filterAmount) {
        logger.info("Игрок " + player.getName() + " открыл детали транзакций. Тип: " + transactionType + ", страница: " + page + ", сумма фильтра: " + filterAmount);

        // Фильтрация транзакций по указанной сумме
        List<Transaction> filteredTransactions = transactions.stream()
                .filter(tx -> tx.getAmount() == filterAmount) // Фильтруем по сумме
                .collect(Collectors.toList());

        Inventory detailsMenu = Bukkit.createInventory(null, 54, "Детали транзакций");

        int startIndex = page * 45;
        int endIndex = Math.min(startIndex + 45, filteredTransactions.size());

        int index = 0;
        int itemsAdded = 0; // Считаем, сколько предметов добавлено в меню

        for (int i = startIndex; i < endIndex; i++) {
            Transaction tx = filteredTransactions.get(i);

            // Проверка на выход за пределы
            if (index >= 54) break;

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();

            String transactionPrefix;
            String transactionColor;

            if (transactionType.equals("sent")) {
                transactionPrefix = "Вы перевели"; // Для отправленных транзакций
                transactionColor = "#47C3F7"; // Цвет для отправленных
            } else {
                transactionPrefix = "Перевод от"; // Для полученных транзакций
                transactionColor = "#0BB83D"; // Цвет для полученных
            }

            meta.setDisplayName(applyHexColor(transactionColor)
                    + transactionPrefix + applyHexColor("#BEE5F4") + " " + tx.getOtherPlayer());

            List<String> lore = new ArrayList<>();
            lore.add(applyHexColor("#AAAAAA") + "Сумма: " + applyHexColor("#E3B140") + tx.getAmount());
            lore.add(applyHexColor("#AAAAAA") + "Дата: " + applyHexColor("#F8D06E") + tx.getTimestamp().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
            lore.add(applyHexColor("#AAAAAA") + "Сообщение: " + applyHexColor("#FFFFFF") + (tx.getMessage().isEmpty() ? "Нет сообщения" : tx.getMessage()));
            lore.add(applyHexColor("#FFAA00") + (tx.isRead() ? "Прочитано" : "Не прочитано"));
            lore.add("");
            lore.add(applyHexColor("#FFAA00") + "Транзакция считается прочитанной, если:");
            lore.add(applyHexColor("#AAAAAA") + "- Получатель увидел перевод в чате при заходе на сервер");
            lore.add(applyHexColor("#AAAAAA") + "- Получатель был онлайн во время перевода");

            meta.setLore(lore);
            item.setItemMeta(meta);

            detailsMenu.setItem(index, item);
            index++;
            itemsAdded++;
        }

        // Кнопка для перехода на предыдущую страницу
        if (page > 0) {
            ItemStack previousPage = new ItemStack(Material.ARROW);
            ItemMeta previousMeta = previousPage.getItemMeta();
            previousMeta.setDisplayName(applyHexColor("#FFAA00") + "Предыдущая страница");
            previousPage.setItemMeta(previousMeta);
            detailsMenu.setItem(45, previousPage);
        }

        // Кнопка для перехода на следующую страницу
        if (itemsAdded == 45 && (page + 1) * 45 < filteredTransactions.size()) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(applyHexColor("#FFAA00") + "Следующая страница");
            nextPage.setItemMeta(nextMeta);
            detailsMenu.setItem(53, nextPage);
        }

        player.openInventory(detailsMenu);
    }

    public List<Transaction> getTransactionsForPlayer(String playerName, String transactionType) {
        return transactionManager.getTransactionsForPlayer(playerName, transactionType);
    }

    public int getMaxPage(String playerName, String transactionType) {
        List<Transaction> transactions = getTransactionsForPlayer(playerName, transactionType);
        return (int) Math.ceil(transactions.size() / 45.0) - 1;
    }

    // Методы для работы с текущей страницей и типом транзакций
    public int getCurrentPage(Player player) {
        return playerPages.getOrDefault(player, 0);
    }

    public void setCurrentPage(Player player, int page) {
        playerPages.put(player, page);
    }

    public String getTransactionType(Player player) {
        return playerTransactionTypes.get(player);
    }

    public void setTransactionType(Player player, String transactionType) {
        playerTransactionTypes.put(player, transactionType);
    }

    public void removePlayerData(Player player) {
        playerPages.remove(player);
        playerTransactionTypes.remove(player);
    }

    public void setViewedPlayer(Player viewer, String targetPlayerName) {
        viewedPlayerMap.put(viewer.getUniqueId(), targetPlayerName);
    }

    public String getViewedPlayer(Player viewer) {
        return viewedPlayerMap.getOrDefault(viewer.getUniqueId(), viewer.getName());
    }

    public void removeViewedPlayer(Player viewer) {
        viewedPlayerMap.remove(viewer.getUniqueId());
    }

    // Метод для преобразования HEX-кода цвета в формат Minecraft
    private String applyHexColor(String hex) {
        StringBuilder colorCode = new StringBuilder("§x");
        for (char c : hex.substring(1).toCharArray()) {
            colorCode.append("§").append(c);
        }
        return colorCode.toString();
    }
}
