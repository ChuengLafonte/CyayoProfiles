package id.cyayo.profiles.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GamemodeData {

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final String symbol;
    private final double classXpBonus;
    private final double gatheringXpBonus;
    private final double raceXpBonus;
    private final double gatheringLuckBonus;
    private final double gatheringSpeedBonus;
    private final double gatheringDoubleBonus;
    private final Map<String, Double> mythicStats;

    public GamemodeData(String id, boolean enabled, String displayName, String symbol,
                        double classXpBonus, double gatheringXpBonus, double raceXpBonus,
                        double gatheringLuckBonus, double gatheringSpeedBonus, double gatheringDoubleBonus,
                        Map<String, Double> mythicStats) {
        this.id = id.toLowerCase();
        this.enabled = enabled;
        this.displayName = displayName != null ? displayName : id;
        this.symbol = symbol != null ? symbol : "";
        this.classXpBonus = classXpBonus;
        this.gatheringXpBonus = gatheringXpBonus;
        this.raceXpBonus = raceXpBonus;
        this.gatheringLuckBonus = gatheringLuckBonus;
        this.gatheringSpeedBonus = gatheringSpeedBonus;
        this.gatheringDoubleBonus = gatheringDoubleBonus;
        this.mythicStats = mythicStats != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(mythicStats))
                : Collections.emptyMap();
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getClassXpBonus() {
        return classXpBonus;
    }

    public double getGatheringXpBonus() {
        return gatheringXpBonus;
    }

    public double getRaceXpBonus() {
        return raceXpBonus;
    }

    public double getGatheringLuckBonus() {
        return gatheringLuckBonus;
    }

    public double getGatheringSpeedBonus() {
        return gatheringSpeedBonus;
    }

    public double getGatheringDoubleBonus() {
        return gatheringDoubleBonus;
    }

    public Map<String, Double> getMythicStats() {
        return mythicStats;
    }
}

