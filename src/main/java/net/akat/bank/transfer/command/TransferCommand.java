package net.akat.bank.transfer.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.akat.bank.transfer.gui.TransactionMenu;

public class TransferCommand implements CommandExecutor {
    private final TransactionMenu transactionMenu;

    public TransferCommand(TransactionMenu transactionMenu) {
        this.transactionMenu = transactionMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Эту команду могут использовать только игроки.");
            return false;
        }

        Player player = (Player) sender;
        if (args.length == 1) {
            return handleOwnTransactions(player, args[0]);
        } else if (args.length == 2) {
            return handleOtherPlayerTransactions(player, args);
        } else {
            player.sendMessage("Использование: /transfer <s/r> для своих транзакций");
            return false;
        }
    }

    private boolean handleOwnTransactions(Player player, String transactionTypeArgument) {
        String transactionType = getTransactionType(transactionTypeArgument);
        if (transactionType == null) {
            player.sendMessage("Использование: /transfer <s/r> для своих транзакций");
            return false;
        }

        if (player.hasPermission("akattransfer.view.self")) {
            transactionMenu.setTransactionType(player, transactionType);
            transactionMenu.setCurrentPage(player, 0);
            transactionMenu.setViewedPlayer(player, player.getName());
            transactionMenu.openTransactionMenu(player, player.getName(), transactionType);
            return true;
        } else {
            player.sendMessage("У вас нет прав для просмотра своих транзакций.");
            return false;
        }
    }

    private boolean handleOtherPlayerTransactions(Player player, String[] args) {
        String transactionType = getTransactionType(args[1]);
        if (transactionType == null) {
            player.sendMessage("Использование: /transfer <игрок> <s/r> для транзакций другого игрока");
            return false;
        }

        String playerName = args[0];
        if (player.hasPermission("akattransfer.view.others")) {
            transactionMenu.setTransactionType(player, transactionType);
            transactionMenu.setCurrentPage(player, 0);
            transactionMenu.setViewedPlayer(player, playerName);
            transactionMenu.openTransactionMenu(player, playerName, transactionType);
            return true;
        } else {
            player.sendMessage("У вас нет прав для просмотра транзакций других игроков.");
            return false;
        }
    }

    private String getTransactionType(String argument) {
        if (argument.equalsIgnoreCase("s")) {
            return "sent";
        } else if (argument.equalsIgnoreCase("r")) {
            return "received";
        } else {
            return null;
        }
    }
}
