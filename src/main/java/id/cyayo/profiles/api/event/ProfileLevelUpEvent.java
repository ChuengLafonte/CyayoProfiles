package id.cyayo.profiles.api.event;

import id.cyayo.profiles.model.Profile;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ProfileLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Profile profile;
    private final int oldLevel;
    private final int newLevel;

    public ProfileLevelUpEvent(Player player, Profile profile, int oldLevel, int newLevel) {
        this.player = player;
        this.profile = profile;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() {
        return player;
    }

    public Profile getProfile() {
        return profile;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

