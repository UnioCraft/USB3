package net.uniodex.USB3;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class Damagers {
    // AntiLootSteal'den entegre edilmiş class
    protected Long lasthit;

    private HashMap<UUID, Integer> damagers = new HashMap<UUID, Integer>();

    public Damagers() {
    }

    public HashMap<UUID, Integer> getDamagers() {
        return damagers;
    }

    public void addDamage(Player player, int damage) {
        if (damagers.containsKey(player.getUniqueId()))
            damagers.put(player.getUniqueId(), damagers.get(player.getUniqueId()) + damage);
        else
            damagers.put(player.getUniqueId(), damage);
        lasthit = System.currentTimeMillis();
    }

    public UUID getTopDamager() {
        UUID player = (UUID) damagers.keySet().toArray()[0];

        for (UUID damager : damagers.keySet()) {
            if (damagers.get(damager) > damagers.get(player))
                player = damager;
        }
        return player;
    }

    public boolean elapsed() {
        if (System.currentTimeMillis() - lasthit >= (30 * 1000))
            return true;
        return false;
    }
}
