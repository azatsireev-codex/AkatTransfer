package net.akat.bank.transfer;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.akat.bank.transfer.command.PayCommandExecutor;
import net.akat.bank.transfer.command.TransferCommand;
import net.akat.bank.transfer.event.PlayerTransactionListener;
import net.akat.bank.transfer.event.TransactionMenuListener;
import net.akat.bank.transfer.gui.TransactionMenu;
import net.akat.bank.transfer.manager.TransactionManager;
import net.milkbowl.vault.economy.Economy;

public class Main extends JavaPlugin {

    private static Main instance;

    private TransactionManager transactionManager;
    private TransactionMenu transactionMenu;
    private PayCommandExecutor payCommandExecutor;

    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("Vault не найден! Отключение плагина.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        transactionManager = new TransactionManager(this);
        transactionMenu = new TransactionMenu(this, transactionManager);
        payCommandExecutor = new PayCommandExecutor(this, transactionManager, economy);

        transactionManager.cleanupOldTransactions();

        getCommand("transfer").setExecutor(new TransferCommand(transactionMenu));
        getCommand("pay").setExecutor(payCommandExecutor);

        getServer().getPluginManager().registerEvents(new TransactionMenuListener(transactionMenu), this);
        getServer().getPluginManager().registerEvents(new PlayerTransactionListener(transactionManager, payCommandExecutor), this);
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    @Override
    public void onDisable() {
        transactionManager.close();
    }

    public static Main getInstance() {
        return instance;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public TransactionMenu getTransactionMenu() {
        return transactionMenu;
    }

    public Economy getEconomy() {
        return economy;
    }

    public PayCommandExecutor getPayCommandExecutor() {
        return payCommandExecutor;
    }
}
