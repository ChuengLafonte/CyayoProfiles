package id.cyayo.profiles.api.event;

import id.cyayo.profiles.model.Profile;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ProfileCreateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Profile profile;

    public ProfileCreateEvent(Player player, Profile profile) {
        this.player = player;
        this.profile = profile;
    }

    public Player getPlayer() {
        return player;
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

