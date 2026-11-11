package id.cyayo.profiles.api.event;

import id.cyayo.profiles.model.Profile;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ProfileLoadEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Profile activeProfile;

    public ProfileLoadEvent(Player player, Profile activeProfile) {
        this.player = player;
        this.activeProfile = activeProfile;
    }

    public Player getPlayer() {
        return player;
    }

    public Profile getActiveProfile() {
        return activeProfile;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

