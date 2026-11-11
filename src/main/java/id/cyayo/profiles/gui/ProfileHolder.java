package id.cyayo.profiles.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ProfileHolder implements InventoryHolder {

    private final String menuType;
    private final int targetSlot;
    private Inventory inventory;

    public ProfileHolder(String menuType, int targetSlot) {
        this.menuType = menuType;
        this.targetSlot = targetSlot;
    }

    public String getMenuType() {
        return menuType;
    }

    public int getTargetSlot() {
        return targetSlot;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

