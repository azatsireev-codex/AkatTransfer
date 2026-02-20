package net.akat.bank.transfer.command;

import java.time.LocalDateTime;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.akat.bank.transfer.Main;
import net.akat.bank.transfer.manager.TransactionManager;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;

public class PayCommandExecutor implements CommandExecutor {

    private final Main plugin;
    private final TransactionManager transactionManager;
    private final Economy economy;
    private final Map<UUID, Map<UUID, Long>> lastTransferTimestamps = new HashMap<>();

    public PayCommandExecutor(Main plugin, TransactionManager transactionManager, Economy economy) {
        this.plugin = plugin;
        this.transactionManager = transactionManager;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Эту команду может использовать только игрок.");
            return true;
        }

        if (!player.hasPermission("akattransfer.pay")) {
            player.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды.");
            return true;
        }

        if (args.length < 2) {
            sendUsageMessage(player);
            return true;
        }

        double amount = parseAmount(player, args[1]);
        if (amount <= 0) return true;

        OfflinePlayer target = getTargetPlayer(player, args[0]);
        if (target == null) return true;

        long cooldownLeft = getCooldownLeft(player, target);
        if (cooldownLeft > 0) {
            double secondsLeft = cooldownLeft / 1000.0;
            player.sendMessage(ChatColor.RED + String.format("Вы не можете отправлять переводы этому игроку чаще, чем раз в 5 секунд. Подождите %.1f секунд.", secondsLeft));
            return true;
        }

        String message = parseMessage(args);
        if (!hasSufficientFunds(player, amount)) return true;

        executePayment(player, target, amount, message);
        return true;
    }

    private long getCooldownLeft(Player sender, OfflinePlayer target) {
        long now = System.currentTimeMillis();

        Map<UUID, Long> targetMap = lastTransferTimestamps.get(sender.getUniqueId());
        if (targetMap == null) return 0;

        Long lastTime = targetMap.get(target.getUniqueId());
        if (lastTime == null) return 0;

        long diff = now - lastTime;
        long cooldown = 5_000; // 10 секунд в мс

        if (diff < cooldown) {
            return cooldown - diff; // сколько осталось
        }

        return 0; // кулдауна нет
    }

    private void sendUsageMessage(Player player) {
        player.sendMessage(ChatColor.RED + "Использование: /pay <игрок> <сумма> [сообщение]");
        plugin.getLogger().warning("Неправильное количество аргументов.");
    }

    private double parseAmount(Player player, String amountString) {
        double amount;
        try {
            amount = Double.parseDouble(amountString);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Введите корректную сумму.");
            plugin.getLogger().warning("Ошибка парсинга суммы.");
            return -1;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Сумма должна быть больше 0.");
            plugin.getLogger().warning("Ошибка: сумма меньше или равна 0.");
            return -1;
        }

        if (amount < 0.01) {
            player.sendMessage(ChatColor.RED + "Минимальная сумма перевода — 0.01.");
            plugin.getLogger().warning("Ошибка: сумма меньше 0.01.");
            return -1;
        }

        return amount;
    }

    private OfflinePlayer getTargetPlayer(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || !target.hasPlayedBefore()) {
            player.sendMessage(ChatColor.RED + "Игрок не найден.");
            plugin.getLogger().warning("Игрок не найден. Имя игрока: " + targetName);
            return null;
        }
        return target;
    }

    private String parseMessage(String[] args) {
        if (args.length > 2) {
            return String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }
        return "";
    }

    private boolean hasSufficientFunds(Player player, double amount) {
        if (!economy.has(player, amount)) {
            player.sendMessage(ChatColor.RED + "Недостаточно средств.");
            plugin.getLogger().warning("Недостаточно средств у игрока " + player.getName());
            return false;
        }
        return true;
    }

    private void executePayment(Player player, OfflinePlayer target, double amount, String message) {
        economy.withdrawPlayer(player, amount);
        economy.depositPlayer(target, amount);

        lastTransferTimestamps
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(target.getUniqueId(), System.currentTimeMillis());

        // Используем Московское время
        LocalDateTime timestamp = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        boolean isRead = target.isOnline();
        transactionManager.addTransaction(player.getName(), target.getName(), amount, timestamp, message, isRead);

        player.sendMessage(ChatColor.GREEN + "Вы отправили " + amount + " " + economy.currencyNamePlural() + " игроку " + target.getName() + ".");

        if (target.isOnline()) {
            sendOnlineTargetMessage((Player) target, player, amount, message);
        } else {
            player.sendMessage(ChatColor.YELLOW + "Игрок " + target.getName() + " не в сети, но перевод выполнен.");
        }
    }

    private void sendOnlineTargetMessage(Player targetPlayer, Player sender, double amount, String message) {
        targetPlayer.sendMessage(ChatColor.GREEN + "Вы получили " + amount + " " + economy.currencyNamePlural() + " от игрока " + sender.getName() + ".");
        if (!message.isEmpty()) {
            targetPlayer.sendMessage(ChatColor.GRAY + "Сообщение: " + ChatColor.ITALIC + message);
        }

        transactionManager.updateReadStatus(targetPlayer.getName(), sender.getName(), amount, true, "received");
        transactionManager.updateReadStatus(sender.getName(), targetPlayer.getName(), amount, true, "sent");
    }

    public void clearCooldownFor(UUID playerId) {
        lastTransferTimestamps.remove(playerId);
    }
}
