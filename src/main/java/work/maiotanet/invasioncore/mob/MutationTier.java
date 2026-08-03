package work.maiotanet.invasioncore.mob;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 7DTD の Feral / Radiated に相当する変異ティア。
 * GameStage が上がるほど上位ティアの出現率が上がる。
 */
public enum MutationTier {

    NORMAL("", 1.00, 1.00, 1.00, 0, NamedTextColor.WHITE, false),
    FERAL("凶暴", 1.85, 1.15, 1.50, 24, NamedTextColor.GOLD, false),
    RADIATED("汚染", 3.10, 1.22, 2.20, 60, NamedTextColor.GREEN, true);

    private final String prefix;
    private final double healthMultiplier;
    private final double speedMultiplier;
    private final double damageMultiplier;
    private final int minStage;
    private final TextColor color;
    private final boolean regenerating;

    MutationTier(String prefix, double healthMultiplier, double speedMultiplier, double damageMultiplier,
                 int minStage, TextColor color, boolean regenerating) {
        this.prefix = prefix;
        this.healthMultiplier = healthMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.minStage = minStage;
        this.color = color;
        this.regenerating = regenerating;
    }

    public String prefix() {
        return prefix;
    }

    public double healthMultiplier() {
        return healthMultiplier;
    }

    public double speedMultiplier() {
        return speedMultiplier;
    }

    public double damageMultiplier() {
        return damageMultiplier;
    }

    public TextColor color() {
        return color;
    }

    /** 汚染個体は自己再生する。放置すると押し切られる。 */
    public boolean isRegenerating() {
        return regenerating;
    }

    public double shardMultiplier() {
        return switch (this) {
            case NORMAL -> 1.0;
            case FERAL -> 2.0;
            case RADIATED -> 3.5;
        };
    }

    /**
     * GameStage に応じて変異ティアを抽選する。
     * ステージが上がるほど上位ティアが増えるが、通常個体が消えることはない。
     */
    public static MutationTier roll(int gameStage) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double radiatedChance = 0.0;
        double feralChance = 0.0;

        if (gameStage >= RADIATED.minStage) {
            radiatedChance = Math.min(0.35, (gameStage - RADIATED.minStage) * 0.004);
        }
        if (gameStage >= FERAL.minStage) {
            feralChance = Math.min(0.45, (gameStage - FERAL.minStage) * 0.008);
        }

        double roll = rng.nextDouble();
        if (roll < radiatedChance) {
            return RADIATED;
        }
        if (roll < radiatedChance + feralChance) {
            return FERAL;
        }
        return NORMAL;
    }
}
