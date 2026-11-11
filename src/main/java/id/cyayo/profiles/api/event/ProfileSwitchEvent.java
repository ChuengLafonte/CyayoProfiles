package id.cyayo.profiles.api.event;

import id.cyayo.profiles.model.Profile;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ProfileSwitchEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final Profile oldProfile;
    private final Profile newProfile;

    public ProfileSwitchEvent(Player player, Profile oldProfile, Profile newProfile) {
        this.player = player;
        this.oldProfile = oldProfile;
        this.newProfile = newProfile;
    }

    public Player getPlayer() {
        return player;
    }

    public Profile getOldProfile() {
        return oldProfile;
    }

    public Profile getNewProfile() {
        return newProfile;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

