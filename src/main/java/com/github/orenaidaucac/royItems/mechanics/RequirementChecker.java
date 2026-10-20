package com.github.orenaidaucac.royItems.mechanics;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import org.bukkit.entity.Player;

/**
 * Validates usage conditions including permissions, levels, MMOCore, AuraSkills, worlds, and biomes.
 */
public class RequirementChecker {

    private final IntegrationManager integrationManager;

    public RequirementChecker(IntegrationManager integrationManager) {
        this.integrationManager = integrationManager;
    }

    public boolean canUse(Player player, ItemDefinition def) {
        if (player == null || def == null) return false;
        if (player.isOp()) return true;

        // 1. Permission check
        if (def.getPermission() != null && !def.getPermission().isEmpty()) {
            if (!player.hasPermission(def.getPermission())) return false;
        }

        // 2. XP Level check
        if (def.getLevelRequired() > 0 && player.getLevel() < def.getLevelRequired()) {
            return false;
        }

        // 3. World check
        if (def.getWorldRequired() != null && !def.getWorldRequired().isEmpty()) {
            String currentWorld = player.getWorld().getName();
            boolean match = false;
            for (String w : def.getWorldRequired()) {
                if (w.equalsIgnoreCase(currentWorld)) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;
        }

        // 4. Biome check
        if (def.getBiomeRequired() != null && !def.getBiomeRequired().isEmpty()) {
            String currentBiome = player.getLocation().getBlock().getBiome().getKey().getKey();
            boolean match = false;
            for (String b : def.getBiomeRequired()) {
                if (b.equalsIgnoreCase(currentBiome)) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;
        }

        // 5. MMOCore check
        if (def.getMmocoreClass() != null && !def.getMmocoreClass().isEmpty()) {
            if (integrationManager.hasMMOCore()) {
                if (!integrationManager.getMMOCoreHook().meetsRequirement(player, def.getMmocoreClass(), def.getMmocoreLevel())) {
                    return false;
                }
            }
        }

        // 6. AuraSkills check
        if (def.getAuraSkill() != null && !def.getAuraSkill().isEmpty()) {
            if (integrationManager.hasAuraSkills()) {
                if (!integrationManager.getAuraSkillsHook().meetsRequirement(player, def.getAuraSkill(), def.getAuraSkillLevel())) {
                    return false;
                }
            }
        }

        return true;
    }

    public void sendRequirementFailureMessage(Player player, ItemDefinition def) {
        player.sendMessage(TextUtility.formatMessage("&cYou do not meet the requirements to wield &f" +
                (def.getName() != null && !def.getName().isEmpty() ? def.getName() : def.getId()) + "&c!"));
    }
}
