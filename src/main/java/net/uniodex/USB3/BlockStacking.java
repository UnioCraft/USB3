package net.uniodex.USB3;

import com.boydti.fawe.FaweAPI;
import com.boydti.fawe.util.EditSessionBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.wasteofplastic.askyblock.ASkyBlockAPI;
import com.wasteofplastic.askyblock.Island;
import com.wasteofplastic.askyblock.events.IslandDeleteEvent;
import com.wasteofplastic.askyblock.events.IslandResetEvent;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

public class BlockStacking implements Listener, CommandExecutor {

    private List<String> disabled = new ArrayList<>(); // Player
    private List<String> disabledChat = new ArrayList<>(); // Player
    private Map<String, List<Location>> cooldowns = new HashMap<>(); // Player, Location
    private Map<String, Integer> spongeAmounts = new HashMap<>(); // Island Location, Amount
    private Map<String, Integer> portalAmounts = new HashMap<>(); // Island Location, Amount
    private List<String> convertCooldowns = new ArrayList<>(); // Player
    private List<String> convertedCooldowns = new ArrayList<>(); // Player

    private Main plugin;

    private WorldEditPlugin wpl;

    public BlockStacking(Main plugin) {
        this.plugin = plugin;
        wpl = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getCommand("sanalblok").setExecutor(this);
        loadData();

        Bukkit.getScheduler().runTaskTimer(plugin, () -> saveData(), 20L, 12000L);
    }

    private void loadData() {
        if (plugin.getConfigData("level").getConfigurationSection("sponge") != null) {
            for (String island : plugin.getConfigData("level").getConfigurationSection("sponge").getKeys(false)) {
                spongeAmounts.put(island, plugin.getConfigData("level").getConfigurationSection("sponge").getInt(island));
            }
        }

        if (plugin.getConfigData("level").getConfigurationSection("portal") != null) {
            for (String island : plugin.getConfigData("level").getConfigurationSection("portal").getKeys(false)) {
                portalAmounts.put(island, plugin.getConfigData("level").getConfigurationSection("portal").getInt(island));
            }
        }

        if (plugin.getConfigData("level").getStringList("disabled") != null) {
            disabled = plugin.getConfigData("level").getStringList("disabled");
        }

        if (plugin.getConfigData("level").getStringList("disabledchat") != null) {
            disabledChat = plugin.getConfigData("level").getStringList("disabledchat");
        }
    }

    public void saveData() {
        for (String key : plugin.getConfigData("level").getKeys(false)) {
            plugin.getConfigData("level").set(key, null);
        }

        for (String location : spongeAmounts.keySet()) {
            plugin.getConfigData("level").set("sponge." + location, spongeAmounts.get(location));
        }

        for (String location : portalAmounts.keySet()) {
            plugin.getConfigData("level").set("portal." + location, portalAmounts.get(location));
        }

        plugin.getConfigData("level").set("disabled", disabled);
        plugin.getConfigData("level").set("disabledChat", disabledChat);

        plugin.saveConfigData("level");
    }

    public Long getUnioLevel(String island) {
        long level = 0;

        if (spongeAmounts.containsKey(island)) {
            level += spongeAmounts.get(island) * 50;
        }

        if (portalAmounts.containsKey(island)) {
            level += portalAmounts.get(island) * 500;
        }

        return level;
    }

    public Integer getSpongeAmount(String island) {
        if (spongeAmounts.containsKey(island)) {
            return spongeAmounts.get(island);
        } else {
            return 0;
        }
    }

    public Integer getPortalAmount(String island) {
        if (portalAmounts.containsKey(island)) {
            return portalAmounts.get(island);
        } else {
            return 0;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            if (args.length < 1 || args.length > 2) {
                sender.sendMessage(ChatColor.GREEN + "Sanal Blok Komutları:");
                sender.sendMessage(ChatColor.GOLD + "- /sanalblok ackapa > " + ChatColor.WHITE + "Sanal blok sistemini açar/kapatır.");
                sender.sendMessage(ChatColor.GOLD + "- /sanalblok mesajackapa > " + ChatColor.WHITE + "Blok koyduğunuzda mesaj göstermeyi açar/kapatır.");
                sender.sendMessage(ChatColor.GOLD + "- /sanalblok miktar > " + ChatColor.WHITE + "Adanızdaki sanal blok miktarını gösterir.");
                sender.sendMessage(ChatColor.GOLD + "- /sanalblok donustur > " + ChatColor.WHITE + "Adanızdaki sünger ve portal çerçevelerini sanal bloğa dönüştürür.");
                return true;
            }
            Player player = (Player) sender;
            if (args.length == 2 && args[0].equalsIgnoreCase("sil")) {
                if (!player.isOp()) {
                    return true;
                }

                String islandString = args[1];

                spongeAmounts.remove(islandString);
                portalAmounts.remove(islandString);
                sender.sendMessage(Main.bilgiprefix + "Ada hafızadan silindi.");
            }
            // /sanalblok ver <adaString> sunger 100
            if (args.length == 4 && args[0].equalsIgnoreCase("ver")) {
                if (!player.isOp()) {
                    return true;
                }

                String islandString = args[1];
                String blockToGive = args[2];
                Integer amount = Integer.valueOf(args[3]);

                if (blockToGive.equalsIgnoreCase("sunger")) {
                    if (spongeAmounts.containsKey(islandString)) {
                        spongeAmounts.put(islandString, spongeAmounts.get(islandString) + amount);
                    } else {
                        spongeAmounts.put(islandString, amount);
                    }
                }

                if (blockToGive.equalsIgnoreCase("portal")) {
                    if (portalAmounts.containsKey(islandString)) {
                        portalAmounts.put(islandString, portalAmounts.get(islandString) + amount);
                    } else {
                        portalAmounts.put(islandString, amount);
                    }
                }

                sender.sendMessage(Main.bilgiprefix + "Adaya " + amount + " adet " + blockToGive + " eklendi.");
                sender.sendMessage(Main.bilgiprefix + "Adanın güncel sünger miktarı: " + spongeAmounts.get(islandString));
                sender.sendMessage(Main.bilgiprefix + "Adanın güncel portal miktarı: " + portalAmounts.get(islandString));
            }
            // /sanalblok al <adaString> sunger 100
            if (args.length == 4 && args[0].equalsIgnoreCase("al")) {
                if (!player.isOp()) {
                    return true;
                }

                String islandString = args[1];
                String blockToTake = args[2];
                Integer amount = Integer.valueOf(args[3]);

                if (blockToTake.equalsIgnoreCase("sunger")) {
                    if (spongeAmounts.containsKey(islandString)) {
                        spongeAmounts.put(islandString, spongeAmounts.get(islandString) - amount);
                    } else {
                        spongeAmounts.put(islandString, amount * -1);
                    }
                }

                if (blockToTake.equalsIgnoreCase("portal")) {
                    if (portalAmounts.containsKey(islandString)) {
                        portalAmounts.put(islandString, portalAmounts.get(islandString) - amount);
                    } else {
                        portalAmounts.put(islandString, amount * -1);
                    }
                }

                sender.sendMessage(Main.bilgiprefix + "Adadan " + amount + " adet " + blockToTake + " alındı.");
                sender.sendMessage(Main.bilgiprefix + "Adanın güncel sünger miktarı: " + spongeAmounts.get(islandString));
                sender.sendMessage(Main.bilgiprefix + "Adanın güncel portal miktarı: " + portalAmounts.get(islandString));
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("ackapa") || args[0].equalsIgnoreCase("ac") || args[0].equalsIgnoreCase("kapa")) {
                if (disabled.contains(sender.getName())) {
                    disabled.remove(sender.getName());
                    sender.sendMessage(Main.bilgiprefix + "Sanal blok koyma etkinleştirildi.");
                } else {
                    disabled.add(sender.getName());
                    sender.sendMessage(Main.bilgiprefix + "Sanal blok koyma devre dışı bırakıldı.");
                }
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("mesajackapa") || args[0].equalsIgnoreCase("mesajac") || args[0].equalsIgnoreCase("mesajkapa")) {
                if (disabledChat.contains(sender.getName())) {
                    disabledChat.remove(sender.getName());
                    sender.sendMessage(Main.bilgiprefix + "Blok koyma mesajları etkinleştirildi.");
                } else {
                    disabledChat.add(sender.getName());
                    sender.sendMessage(Main.bilgiprefix + "Blok koyma mesajları devre dışı bırakıldı.");
                }
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("miktar")) {
                Island island = ASkyBlockAPI.getInstance().getIslandOwnedBy(player.getUniqueId());
                if (island == null) {
                    sender.sendMessage(Main.hataprefix + "Adanızdaki sanal blok sayılarını görebilmek için bir adanız olmalıdır.");
                    return true;
                }
                String islandString = getLocation(island);
                player.sendMessage(ChatColor.GREEN + "Adanızda sanal blok olarak bulunan;\n"
                        + ChatColor.GOLD + "-> Portal Çerçevesi: " + ChatColor.AQUA + portalAmounts.get(islandString)
                        + ChatColor.GOLD + "\n-> Sünger: " + ChatColor.AQUA + spongeAmounts.get(islandString));
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("donustur")) {
                Island island = ASkyBlockAPI.getInstance().getIslandOwnedBy(player.getUniqueId());
                if (island == null || !island.getOwner().equals(player.getUniqueId())) {
                    sender.sendMessage(Main.hataprefix + "Adanızdaki blokları sanal bloğa dönüştürmek için ada lideri olmalısınız.");
                    return true;
                }
                if (convertedCooldowns.contains(player.getName())) {
                    sender.sendMessage(Main.hataprefix + "Adanızdaki blokları 10 dakikada bir sanal bloğa dönüştürebilirsiniz.");
                    return true;
                }
                String islandString = getLocation(island);
                if (plugin.topWeekly.getWeeklyLevel(islandString) >= 0) {
                    sender.sendMessage(Main.hataprefix + "Sanal blok dönüştürebilmek için haftalık ada seviyeniz 0'ın altında olmalıdır!");
                    return true;
                }

                if (convertCooldowns.contains(player.getName())) {
                    int max1 = island.getMinX() + island.getIslandDistance() - 1;
                    int max2 = island.getMinZ() + island.getIslandDistance() - 1;

                    Vector pos1 = new Vector(island.getMinX(), 0, island.getMinZ());
                    Vector pos2 = new Vector(max1, 255, max2);

                    BukkitPlayer wePlayer = wpl.wrapPlayer(player);

                    List<Vector> blocks = new ArrayList<>();
                    blocks.add(pos1);
                    blocks.add(pos2);
                    Set<BaseBlock> baseBlocks = null;
                    try {
                        baseBlocks = wpl.getWorldEdit().getBlocks(wePlayer, "sponge");
                    } catch (WorldEditException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }

                    CuboidRegion region = new CuboidRegion(pos1, pos2);
                    EditSession editSession = new EditSessionBuilder(FaweAPI.getWorld(player.getWorld().getName())).fastmode(true).build();
                    int spongeAmount = 0;
                    try {
                        spongeAmount = editSession.replaceBlocks(region, baseBlocks, new BaseBlock(0));
                    } catch (MaxChangedBlocksException e2) {
                        // TODO Auto-generated catch block
                        e2.printStackTrace();
                    }
                    try {
                        baseBlocks = wpl.getWorldEdit().getBlocks(wePlayer, "ender_portal_frame", true, true);
                    } catch (WorldEditException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    int portalAmount = 0;
                    try {
                        portalAmount = editSession.replaceBlocks(region, baseBlocks, new BaseBlock(0));
                    } catch (MaxChangedBlocksException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    editSession.flushQueue();

                    if (spongeAmounts.containsKey(islandString)) {
                        spongeAmounts.put(islandString, spongeAmounts.get(islandString) + spongeAmount);
                    } else {
                        spongeAmounts.put(islandString, spongeAmount);
                    }

                    if (portalAmounts.containsKey(islandString)) {
                        portalAmounts.put(islandString, portalAmounts.get(islandString) + portalAmount);
                    } else {
                        portalAmounts.put(islandString, portalAmount);
                    }

					/*Long level = 0L;
					level = plugin.blockStacking.getUnioLevel(islandString);

					for (String islanda : plugin.topWeekly.islandsMadeMostProgress.keySet()) {
						if (plugin.topWeekly.islandsMadeMostProgress.get(islanda) < 0) {
							level = plugin.topWeekly.islandsMadeMostProgress.get(islanda);
						}
					}

					plugin.topWeekly.islands.put(islandString, level);
					plugin.getConfigData("topweekly").set(islandString, plugin.topWeekly.islands.get(islandString));
					plugin.saveConfigData("topweekly");

					if (plugin.topWeekly.islands.get(islandString) != null) {
						plugin.topWeekly.islandsMadeMostProgress.put(islandString, (plugin.blockStacking.getUnioLevel(islandString) - plugin.topWeekly.islands.get(islandString)));
					}else {
						plugin.topWeekly.islandsMadeMostProgress.put(islandString, plugin.blockStacking.getUnioLevel(islandString));
					}*/

                    if (!player.isOp()) {
                        convertedCooldowns.add(sender.getName());

                        String name = sender.getName();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            convertedCooldowns.remove(name);
                        }, 12000L);
                    }

                    sender.sendMessage(Main.bilgiprefix + "Adanızdaki tüm sünger ve portal çerçeveleri blokları sanal bloğa dönüştürüldü.");
                    sender.sendMessage(Main.bilgiprefix + "Sünger: " + spongeAmount);
                    sender.sendMessage(Main.bilgiprefix + "Portal Çerçevesi: " + portalAmount);
                } else {
                    sender.sendMessage(Main.dikkatprefix + "Blokları dönüştürmek istediğinizden eminseniz tekrar \"/sanalblok donustur\" komutunu kullanınız.");
                    sender.sendMessage(Main.dikkatprefix + "DİKKAT: Bu işlem geri dönüştürülemez. Sanal bloğa dönüştürülen blokları fiziksel blok olarak geri alamazsınız.");
                    convertCooldowns.add(sender.getName());
                    String name = sender.getName();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        convertCooldowns.remove(name);
                    }, 200L);
                }
            }
        }
        return true;
    }

    public String getLocation(Island island) {
        String islandString = island.getCenter().getBlockX() + ";" + island.getCenter().getBlockZ();
        return islandString;
    }

    @EventHandler
    public void onRestart(IslandResetEvent event) {
        String islandString = event.getLocation().getBlockX() + ";" + event.getLocation().getBlockZ();
        Long level = plugin.topWeekly.getWeeklyLevel(islandString);
        if (level < 0) {
            Bukkit.getLogger().log(Level.INFO, event.getPlayer().getName() + ", " + islandString + " konumundaki adasını leveli " + level + " iken sıfırladı!");
            logInfo(event.getPlayer().getName() + ", " + islandString + " konumundaki adasını leveli " + level + " iken sıfırladı!");
        }
        spongeAmounts.remove(islandString);
        portalAmounts.remove(islandString);
    }

    @EventHandler
    public void onDelete(IslandDeleteEvent event) {
        String islandString = event.getLocation().getBlockX() + ";" + event.getLocation().getBlockZ();
        Long level = plugin.topWeekly.getWeeklyLevel(islandString);
        if (level < 0) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(event.getPlayerUUID());
            Bukkit.getLogger().log(Level.INFO, offlinePlayer.getName() + ", " + islandString + " konumundaki adasını leveli " + level + " iken sildi!");
            logInfo(offlinePlayer.getName() + ", " + islandString + " konumundaki adasını leveli " + level + " iken sildi!");
        }
        spongeAmounts.remove(islandString);
        portalAmounts.remove(islandString);
    }

    public void logInfo(String message) {
        String messageToLog = "[" + getTimeAsHours() + "] " + message;

        try {
            File file = new File(plugin.getDataFolder().getAbsolutePath() + "/logs/" + getTimeAsYearMonthDay() + ".info.log");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(messageToLog);
            bw.newLine();
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getTimeAsHours() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public static String getTimeAsYearMonthDay() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!event.getBlock().getType().equals(Material.SPONGE) && !event.getBlock().getType().equals(Material.ENDER_PORTAL_FRAME))
            return;
        if (!ASkyBlockAPI.getInstance().locationIsOnIsland(player, event.getBlock().getLocation())) return;
        if (disabled.contains(player.getName())) return;

        event.setCancelled(true);

        if (cooldowns.containsKey(player.getName()) && cooldowns.get(player.getName()).contains(event.getBlock().getLocation())) {
            player.sendMessage(Main.hataprefix + "Aynı yere 10 saniyede bir blok koyabilirsiniz.");
            return;
        }

        Island island = ASkyBlockAPI.getInstance().getIslandAt(event.getBlock().getLocation());
        String islandString = getLocation(island);

        if (event.getBlock().getType().equals(Material.SPONGE)) {
            if (player.getItemInHand().getAmount() > 1) {
                player.getItemInHand().setAmount(player.getItemInHand().getAmount() - 1);
            } else {
                player.getInventory().remove(player.getItemInHand());
            }
            if (spongeAmounts.containsKey(islandString)) {
                spongeAmounts.put(islandString, spongeAmounts.get(islandString) + 1);
            } else {
                spongeAmounts.put(islandString, 1);
            }
        } else if (event.getBlock().getType().equals(Material.ENDER_PORTAL_FRAME)) {
            if (player.getItemInHand().getAmount() > 1) {
                player.getItemInHand().setAmount(player.getItemInHand().getAmount() - 1);
            } else {
                player.getInventory().remove(player.getItemInHand());
            }
            if (portalAmounts.containsKey(islandString)) {
                portalAmounts.put(islandString, portalAmounts.get(islandString) + 1);
            } else {
                portalAmounts.put(islandString, 1);
            }
        }

        Integer portalAmount = portalAmounts.get(islandString) == null ? 0 : portalAmounts.get(islandString);
        Integer spongeAmount = spongeAmounts.get(islandString) == null ? 0 : spongeAmounts.get(islandString);
        if (!disabledChat.contains(player.getName())) {
            player.sendMessage(ChatColor.GREEN + "Adanızda sanal blok olarak bulunan;\n"
                    + ChatColor.GOLD + "-> Portal Çerçevesi: " + ChatColor.AQUA + portalAmount
                    + ChatColor.GOLD + "\n-> Sünger: " + ChatColor.AQUA + spongeAmount);
            player.sendMessage(ChatColor.GREEN + "Mesajları kapatmak için " + ChatColor.GOLD + "/sanalblok mesajackapa " + ChatColor.GREEN + "komutunu kullanın.");
        }

        if (cooldowns.containsKey(player.getName())) {
            List<Location> locs = cooldowns.get(player.getName());
            locs.add(event.getBlock().getLocation());
            cooldowns.put(player.getName(), locs);
        } else {
            List<Location> locs = new ArrayList<>();
            locs.add(event.getBlock().getLocation());
            cooldowns.put(player.getName(), locs);
        }

        String name = player.getName();
        Location loc = event.getBlock().getLocation();

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (cooldowns.containsKey(name)) {
                if (cooldowns.get(name).contains(loc)) {
                    cooldowns.get(name).remove(loc);
                }
            }
        }, 200L);
    }

}
