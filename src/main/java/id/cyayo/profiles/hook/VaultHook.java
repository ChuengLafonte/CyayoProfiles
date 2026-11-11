package id.cyayo.profiles.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public class VaultHook {

    private Object economyProvider = null;
    private Method hasMethod = null;
    private Method withdrawMethod = null;
    private Method depositMethod = null;
    private Method getBalanceMethod = null;
    private boolean initialized = false;

    public VaultHook() {
        setupEconomy();
    }

    public void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(econClass);
            if (rsp != null) {
                this.economyProvider = rsp.getProvider();
                if (this.economyProvider != null) {
                    this.hasMethod = econClass.getMethod("has", OfflinePlayer.class, double.class);
                    this.withdrawMethod = econClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                    this.depositMethod = econClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                    this.getBalanceMethod = econClass.getMethod("getBalance", OfflinePlayer.class);
                    this.initialized = true;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean isEnabled() {
        if (!initialized) {
            setupEconomy();
        }
        return initialized && economyProvider != null;
    }

    public boolean has(Player player, double amount) {
        if (!isEnabled() || player == null || amount <= 0) return true;
        try {
            Object res = hasMethod.invoke(economyProvider, player, amount);
            return res instanceof Boolean b && b;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (!isEnabled() || player == null || amount <= 0) return true;
        try {
            Object resp = withdrawMethod.invoke(economyProvider, player, amount);
            if (resp != null) {
                Method successMethod = resp.getClass().getMethod("transactionSuccess");
                Object ok = successMethod.invoke(resp);
                return ok instanceof Boolean b && b;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public boolean deposit(Player player, double amount) {
        if (!isEnabled() || player == null || amount <= 0) return true;
        try {
            Object resp = depositMethod.invoke(economyProvider, player, amount);
            if (resp != null) {
                Method successMethod = resp.getClass().getMethod("transactionSuccess");
                Object ok = successMethod.invoke(resp);
                return ok instanceof Boolean b && b;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public double getBalance(Player player) {
        if (!isEnabled() || player == null) return 0.0;
        try {
            Object res = getBalanceMethod.invoke(economyProvider, player);
            if (res instanceof Number num) {
                return num.doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return 0.0;
    }

    public boolean setBalance(Player player, double targetBalance) {
        if (!isEnabled() || player == null) return false;
        double target = Math.max(0.0, targetBalance);
        double current = getBalance(player);
        double diff = target - current;
        if (Math.abs(diff) < 1e-6) {
            return true;
        }
        if (diff > 0) {
            return deposit(player, diff);
        } else {
            return withdraw(player, -diff);
        }
    }
}

