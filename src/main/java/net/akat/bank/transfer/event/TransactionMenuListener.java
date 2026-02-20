package net.akat.bank.transfer.event;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import net.akat.bank.transfer.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.akat.bank.transfer.Transaction;
import net.akat.bank.transfer.gui.TransactionMenu;
import net.md_5.bungee.api.ChatColor;

public class TransactionMenuListener implements Listener {
    private final TransactionMenu transactionMenu;

    public TransactionMenuListener(TransactionMenu transactionMenu) {
        this.transactionMenu = transactionMenu;
    }

    public void openTransactionMenu(Player player, String playerName, String transactionType) {
        transactionMenu.setTransactionType(player, transactionType);
        transactionMenu.setCurrentPage(player, 0);
        transactionMenu.openTransactionMenu(player, playerName, transactionType);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        // Только если закрывается транзакционное меню
        if (title.startsWith("Транзакции игрока -") || title.startsWith("Детали транзакций")) {
            RegionScheduler scheduler = player.getServer().getRegionScheduler();

            // Планируем задачу в регион игрока через 1 тик
            scheduler.runDelayed(Main.getInstance(), player.getLocation(), test -> {
                // Проверяем, не открыл ли игрок другое меню
                String newTitle = player.getOpenInventory().getTitle();
                if (!(newTitle.startsWith("Транзакции игрока -") || newTitle.startsWith("Детали транзакций"))) {
                    removePlayerData(player);
                    System.out.println("Списки отчищены");
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String transactionType = transactionMenu.getTransactionType(player);

        // Обработка меню с деталями транзакций
        if (isDetailsTransactionMenu(event)) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            int currentPage = transactionMenu.getCurrentPage(player);

            // Обработка перехода на предыдущую страницу (для меню с деталями)
            if (isPreviousPageButton(clickedItem)) {
                openPreviousPage(player, transactionType, currentPage, true);
            }
            // Обработка перехода на следующую страницу (для меню с деталями)
            else if (isNextPageButton(clickedItem)) {
                openNextPage(player, transactionType, currentPage, true);
            }
            return; // Прерываем выполнение дальше, если это меню с деталями
        }

        // Если это не меню деталей транзакций, проверяем основное меню
        if (isTransactionMenu(event)) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            int currentPage = transactionMenu.getCurrentPage(player);

            // Переход на предыдущую страницу (для основного меню)
            if (isPreviousPageButton(clickedItem)) {
                openPreviousPage(player, transactionType, currentPage, false);
            }
            // Переход на следующую страницу (для основного меню)
            else if (isNextPageButton(clickedItem)) {
                openNextPage(player, transactionType, currentPage, false);
            }
            // Обработка клика по предмету с группой транзакций
            else if (isTransactionItem(clickedItem)) {
                double amount = extractAmountFromDisplayName(clickedItem);
                transactionMenu.setFilterAmount(player, amount);

                // Получаем транзакции для этого игрока и типа
                String viewedPlayer = transactionMenu.getViewedPlayer(player);
                List<Transaction> transactions = transactionMenu.getTransactionsForPlayer(viewedPlayer, transactionType)
                        .stream()
                        .filter(tx -> tx.getAmount() == amount) // Фильтруем по сумме
                        .collect(Collectors.toList());

                // Открываем меню с деталями для отфильтрованных транзакций, передавая сумму фильтра
                transactionMenu.openGroupedTransactionDetailsMenu(player, transactions, transactionType, 0, amount);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public boolean isTransactionMenu(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        return title.startsWith("Транзакции игрока");
    }

    public boolean isDetailsTransactionMenu(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        return title.startsWith("Детали транзакций");
    }

    private boolean isTransactionItem(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName();
    }

    @SuppressWarnings("deprecation")
    private double extractAmountFromDisplayName(ItemStack item) {
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        try {
            // Ищем сумму после стрелки "->"
            String regex = "->\\s*([-+]?[0-9]*\\.?[0-9]+)"; // Ищем цифры после "->"
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(displayName);

            // Если найдено число после стрелки
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1)); // Извлекаем число
            }
        } catch (NumberFormatException e) {
            return 0; // Если произошла ошибка, возвращаем 0
        }
        return 0; // Если сумма не найдена, возвращаем 0
    }

    @SuppressWarnings("deprecation")
    private boolean isPreviousPageButton(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String displayName = ChatColor.stripColor(meta.getDisplayName());
        return displayName.equalsIgnoreCase("Предыдущая страница");
    }

    @SuppressWarnings("deprecation")
    private boolean isNextPageButton(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String displayName = ChatColor.stripColor(meta.getDisplayName());
        return displayName.equalsIgnoreCase("Следующая страница");
    }

    private void openPreviousPage(Player player, String transactionType, int currentPage, boolean isDetails) {
        String viewedPlayer = transactionMenu.getViewedPlayer(player);
        if (isDetails) {
            if (currentPage > 0) {
                transactionMenu.openGroupedTransactionDetailsMenu(player,
                        transactionMenu.getTransactionsForPlayer(viewedPlayer, transactionType),
                        transactionType,
                        currentPage - 1,
                        transactionMenu.getFilterAmount(player)
                );
                transactionMenu.setCurrentPage(player, currentPage - 1);
            }
        } else {
            if (currentPage > 0) {
                transactionMenu.showPage(player,
                        transactionMenu.getTransactionsForPlayer(viewedPlayer, transactionType),
                        currentPage - 1,
                        transactionType
                );
                transactionMenu.setCurrentPage(player, currentPage - 1);
            }
        }
    }

    private void openNextPage(Player player, String transactionType, int currentPage, boolean isDetails) {
        String viewedPlayer = transactionMenu.getViewedPlayer(player);
        int maxPage = transactionMenu.getMaxPage(viewedPlayer, transactionType);

        if (isDetails) {
            if (currentPage < maxPage) {
                transactionMenu.openGroupedTransactionDetailsMenu(player,
                        transactionMenu.getTransactionsForPlayer(viewedPlayer, transactionType),
                        transactionType,
                        currentPage + 1,
                        transactionMenu.getFilterAmount(player)
                );
                transactionMenu.setCurrentPage(player, currentPage + 1);
            }
        } else {
            if (currentPage < maxPage) {
                transactionMenu.showPage(player,
                        transactionMenu.getTransactionsForPlayer(viewedPlayer, transactionType),
                        currentPage + 1,
                        transactionType
                );
                transactionMenu.setCurrentPage(player, currentPage + 1);
            }
        }
    }

    public void removePlayerData(Player player) {
        transactionMenu.removePlayerData(player);
        transactionMenu.removeViewedPlayer(player);
    }
}
