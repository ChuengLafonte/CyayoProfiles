package id.cyayo.profiles.api.event;

import id.cyayo.profiles.model.Profile;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ProfileDeleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int slot;
    private final Profile profile;

    public ProfileDeleteEvent(Player player, int slot, Profile profile) {
        this.player = player;
        this.slot = slot;
        this.profile = profile;
    }

    public Player getPlayer() {
        return player;
    }

    public int getSlot() {
        return slot;
    }

    public Profile getProfile() {
        return profile;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

