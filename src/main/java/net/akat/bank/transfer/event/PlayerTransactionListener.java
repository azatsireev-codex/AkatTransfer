package net.akat.bank.transfer.event;

import java.util.List;
import java.util.UUID;

import net.akat.bank.transfer.command.PayCommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import net.akat.bank.transfer.Transaction;
import net.akat.bank.transfer.manager.TransactionManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerTransactionListener implements Listener {

    private final TransactionManager transactionManager;
    private final PayCommandExecutor payCommandExecutor;

    public PlayerTransactionListener(TransactionManager transactionManager, PayCommandExecutor payCommandExecutor) {
        this.transactionManager = transactionManager;
        this.payCommandExecutor = payCommandExecutor;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        payCommandExecutor.clearCooldownFor(playerId);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Получаем все непрочитанные транзакции для игрока
        List<Transaction> unreadTransactions = transactionManager.getUnreadTransactionsForPlayer(player.getName());

        // Проверяем, если игрок является получателем транзакций
        if (!unreadTransactions.isEmpty()) {
            // Отправляем сообщения только получателю транзакции
            sendUnreadTransactions(player, unreadTransactions);

            // Обновляем статус прочтения для обеих сторон
            for (Transaction transaction : unreadTransactions) {
                if (transaction.getType().equals("received")) {
                    transactionManager.updateReadStatus(player.getName(), transaction.getOtherPlayer(), transaction.getAmount(), true, "received");
                    transactionManager.updateReadStatus(transaction.getOtherPlayer(), player.getName(), transaction.getAmount(), true, "sent");
                }
            }
        }
    }

    private void sendUnreadTransactions(Player player, List<Transaction> unreadTransactions) {
        player.sendMessage(ChatColor.GOLD + "◆ " + ChatColor.YELLOW + "Ваши непрочитанные транзакции:");

        for (Transaction transaction : unreadTransactions) {
            if (transaction.getType().equals("received")) { // Сообщения отправляются только получателю
                String formattedMessage = formatTransactionMessage(transaction);
                player.sendMessage(formattedMessage);
            }
        }
    }

    private String formatTransactionMessage(Transaction transaction) {
        String otherPlayer = transaction.getOtherPlayer() != null ? transaction.getOtherPlayer() : "Неизвестный игрок";
        String message = transaction.getMessage() != null ? transaction.getMessage() : "Без сообщения";
        double amount = transaction.getAmount();

        return String.format(
                ChatColor.GRAY + " - " + ChatColor.AQUA + "%s " +
                        ChatColor.GREEN + "отправил вам " +
                        ChatColor.AQUA + "%.2f " + ChatColor.GREEN + "нефтей" +
                        ChatColor.GRAY + ". Сообщение: " + ChatColor.ITALIC + "%s",
                otherPlayer, amount, message
        );
    }
}
