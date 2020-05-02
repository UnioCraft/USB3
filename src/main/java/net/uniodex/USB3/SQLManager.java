package net.uniodex.USB3;

import com.wasteofplastic.askyblock.Island;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SQLManager {

    private ConnectionPoolManager pool;

    public SQLManager(Main plugin) {
        pool = new ConnectionPoolManager(plugin, "USBPool");
    }

    public boolean updateSQL(String QUERY) {
        try (Connection connection = pool.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(QUERY);
            int count = statement.executeUpdate();
            return count > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean topTenEntryExist(int i) {
        try (Connection connection = pool.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(String.format("SELECT id FROM `topTen` WHERE `id` = %d;", i));
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void updateTopTen(Map<Island, Long> topten) {
        int i = 0;
        for (Island island : topten.keySet()) {
            if (island == null) continue;
            String islandLeader = Bukkit.getOfflinePlayer(island.getOwner()).getName();
            List<String> islandMembers = new ArrayList<>();
            Long islandLevel = topten.get(island);

            List<UUID> islandMembersUUID = new ArrayList<>(island.getMembersWithoutCoops());

            while (islandMembersUUID.contains(island.getOwner())) {
                islandMembersUUID.remove(island.getOwner());
            }

            for (UUID uuid : islandMembersUUID) {
                if (Bukkit.getOfflinePlayer(uuid) != null) {
                    islandMembers.add(Bukkit.getOfflinePlayer(uuid).getName());
                }
            }
            i++;
            if (topTenEntryExist(i)) {
                updateSQL("UPDATE `topTen` SET `islandLeader` = '" + islandLeader + "', `islandMembers` = '" + String.join(", ", islandMembers) + "', `islandLevel` = '" + islandLevel + "' WHERE `topTen`.`id` = " + i + ";");
            } else {
                updateSQL("INSERT INTO `topTen` (`id`, `islandLeader`, `islandMembers`, `islandLevel`) VALUES (" + i + ", '" + islandLeader + "', '" + String.join(", ", islandMembers) + "', '" + islandLevel + "');");
            }
        }
    }

    public String[] getSkin(String playerName) {
        String texture = "";
        String signature = "";
        String QUERY = String.format("SELECT * FROM `genel`.`skin` WHERE `player` = '%s';", playerName);
        try (Connection connection = pool.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(QUERY);
            ResultSet res = statement.executeQuery();
            if (res.next()) {
                if (res.getString("texture") != null) {
                    texture = res.getString("texture");
                }
                if (res.getString("signature") != null) {
                    signature = res.getString("signature");
                }
            } else {
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return new String[]{texture, signature};
    }

    public void onDisable() {
        pool.closePool();
    }

}