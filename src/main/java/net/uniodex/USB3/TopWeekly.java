package net.uniodex.USB3;

import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.ASkyBlockAPI;
import com.wasteofplastic.askyblock.Island;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class TopWeekly implements Listener, CommandExecutor {

    private Main plugin;
    public Map<String, Long> islands = new HashMap<>();
    public Map<String, Long> islandsMadeMostProgress = new HashMap<>();
    private Map<String, Long> topTenList = new HashMap<>();
    private boolean calculatingWeekly = false;
    private boolean calculatingDifference = false;
    private Inventory gui;

    private Set<String> bannedTopweekly = new HashSet<>();

    private Map<String, Long> getWinners() {
        Map<String, Long> topTenListFinal = new HashMap<>();

        for (String islandString : topTenList.keySet()) {
            String[] locs = islandString.split(";");
            Location loc = new Location(ASkyBlockAPI.getInstance().getIslandWorld(), Integer.parseInt(locs[0]), 150, Integer.parseInt(locs[1]));
            Island island = ASkyBlockAPI.getInstance().getIslandAt(loc);
            if (!bannedTopweekly.contains(Bukkit.getOfflinePlayer(island.getOwner()).getName())) {
                topTenListFinal.put(Bukkit.getOfflinePlayer(island.getOwner()).getName(), getWeeklyLevel(islandString));
            }
        }

        topTenListFinal = topTenListFinal.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(3)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        return topTenListFinal;
    }

    private void announceandaward() {
        // use command topweekly worst100
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "topweekly worst100");

        String winner1 = "";
        String winner2 = "";
        String winner3 = "";

        Long winnerlevel1 = 0L;
        Long winnerlevel2 = 0L;
        Long winnerlevel3 = 0L;

        int credit1 = plugin.getConfig().getInt("topweeklyPrizes.1");
        int credit2 = plugin.getConfig().getInt("topweeklyPrizes.2");
        int credit3 = plugin.getConfig().getInt("topweeklyPrizes.3");

        // award each winner with credits taken from config.
        int i = 1;
        for (String winner : getWinners().keySet()) {
            if (i == 1) {
                award(winner, credit1);
                winner1 = winner;
                winnerlevel1 = getWinners().get(winner);
            } else if (i == 2) {
                award(winner, credit2);
                winner2 = winner;
                winnerlevel2 = getWinners().get(winner);
            } else if (i == 3) {
                award(winner, credit3);
                winner3 = winner;
                winnerlevel3 = getWinners().get(winner);
            }
            i++;
        }

        // backup topweekly.yml
        Bukkit.getScheduler().runTask(plugin, () -> {
            File data = new File(plugin.getDataFolder(), "topWeekly.yml");
            File dataClone = new File(plugin.getDataFolder(), "topWeekly-backup-" + System.currentTimeMillis() + ".yml");
            try {
                FileUtils.copyFile(data, dataClone);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // use command topweekly update
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "topweekly update");

        // sendtodiscord command announce
        String finalWinner = winner1;
        Long finalWinnerlevel = winnerlevel1;
        String finalWinner1 = winner2;
        Long finalWinnerlevel1 = winnerlevel2;
        Long finalWinnerlevel2 = winnerlevel3;
        String finalWinner2 = winner3;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String ann = "AnnounceTopweekly " + finalWinner + " " + finalWinnerlevel + " " + credit1 + " " + finalWinner1 + " " + finalWinnerlevel1 + " " + credit2 + " " + finalWinner2 + " " + finalWinnerlevel2 + " " + credit3;
            new DiscordConnection(plugin.getConfig().getString("discord.host"), plugin.getConfig().getInt("discord.port"), ann);
        });
    }

    private void award(String name, int credit) {
        plugin.sqlManager.updateSQL("INSERT INTO `genel`.`kredi` (id, isim, kredi) VALUES (NULL, '" + name + "', '" + credit + "') ON DUPLICATE KEY UPDATE kredi=kredi + " + credit + ";");
    }


    public TopWeekly(Main plugin) {
        this.plugin = plugin;
        loadData();

        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            public void run() {
                calculateDifference(true);
            }
        }, 20L, 12000L);

        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getCommand("topweekly").setExecutor(this);
    }

    private void loadData() {
        for (String island : plugin.getConfigData("topweekly").getKeys(false)) {
            islands.put(island, plugin.getConfigData("topweekly").getLong(island));
        }
    }

    public Long getWeeklyLevel(String islandString) {
        if (islands.get(islandString) != null) {
            return plugin.blockStacking.getUnioLevel(islandString) - islands.get(islandString);
        }
        return plugin.blockStacking.getUnioLevel(islandString);
    }

    private void weeklyCalculation(boolean async) {
        Runnable task = () -> {
            if (calculatingWeekly) return;
            calculatingWeekly = true;
            Bukkit.getLogger().log(Level.INFO, "[USB3] Started weekly calculation.");
            islands = new HashMap<>();
            Map<String, Long> minusIslands = new HashMap<>();
            for (String island : islandsMadeMostProgress.keySet()) {
                if (islandsMadeMostProgress.get(island) < 0) {
                    minusIslands.put(island, islandsMadeMostProgress.get(island));
                }
            }
            for (Island island : ASkyBlockAPI.getInstance().getOwnedIslands().values()) {
                String islandString = island.getCenter().getBlockX() + ";" + island.getCenter().getBlockZ();
                if (plugin.blockStacking.getUnioLevel(islandString) < 100) continue;
                if (minusIslands.containsKey(islandString)) {
                    islands.put(islandString, (plugin.blockStacking.getUnioLevel(islandString) - minusIslands.get(islandString)));
                } else {
                    islands.put(islandString, plugin.blockStacking.getUnioLevel(islandString));
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String key : plugin.getConfigData("topweekly").getKeys(false)) {
                    plugin.getConfigData("topweekly").set(key, null);
                }
                for (String island : islands.keySet()) {
                    plugin.getConfigData("topweekly").set(island, islands.get(island));
                }
                plugin.saveConfigData("topweekly");
                plugin.getConfig().set("lastCalculation", System.currentTimeMillis());
                plugin.saveConfig();
                Bukkit.getLogger().log(Level.INFO, "[USB3] Weekly top island calculation is done.");
                calculatingWeekly = false;
            });
        };

        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private Map<String, Long> getWorst100() {
        Map<String, Long> worst100 = islandsMadeMostProgress.entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).limit(100)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        return worst100;
    }

    private void calculateDifference(boolean async) {
        Runnable task = () -> {
            if (calculatingDifference) return;
            calculatingDifference = true;
            Bukkit.getLogger().log(Level.INFO, "[USB3] Started calculating island improvements.");
            islandsMadeMostProgress.clear();
            for (Island island : ASkyBlockAPI.getInstance().getOwnedIslands().values()) {
                String islandString = island.getCenter().getBlockX() + ";" + island.getCenter().getBlockZ();
                if (plugin.blockStacking.getUnioLevel(islandString) < 100) continue;
                if (islands.get(islandString) != null) {
                    islandsMadeMostProgress.put(islandString, (plugin.blockStacking.getUnioLevel(islandString) - islands.get(islandString)));
                } else {
                    islandsMadeMostProgress.put(islandString, plugin.blockStacking.getUnioLevel(islandString));
                }
            }
            topTenList = islandsMadeMostProgress.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(10)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
            calculatingDifference = false;
            Bukkit.getLogger().log(Level.INFO, "[USB3] Island improvement calculations are done.");
        };

        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof ConsoleCommandSender) {
            if (args.length == 1 && args[0].equalsIgnoreCase("update")) {
                this.calculateDifference(false);
                this.weeklyCalculation(false);
                this.calculateDifference(false);
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("updategui")) {
                this.calculateDifference(true);
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("worst100")) {
                for (String island : getWorst100().keySet()) {
                    sender.sendMessage("Island: " + island + " Level: " + getWorst100().get(island));
                }
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("checkandaward")) {
                if (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                    announceandaward();
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&b&lTOPWEEKLY &2-> &bHaftalık ödüller kazananların hesabına eklendi ve haftalık sıralaması sıfırlanarak yarış yeniden başladı! Bol şans!"));
                }
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("announceandaward")) {
                announceandaward();
                return true;
            }
        }

        if (sender.hasPermission("usb.topweekly.chat") && args.length == 1 && args[0].equalsIgnoreCase("chat")) {
            Map<String, Long> topTenListFinal = new HashMap<>();

            for (String islandString : topTenList.keySet()) {
                String[] locs = islandString.split(";");
                Location loc = new Location(ASkyBlockAPI.getInstance().getIslandWorld(), Integer.parseInt(locs[0]), 150, Integer.parseInt(locs[1]));
                Island island = ASkyBlockAPI.getInstance().getIslandAt(loc);
                topTenListFinal.put(Bukkit.getOfflinePlayer(island.getOwner()).getName(), getWeeklyLevel(islandString));
            }

            topTenListFinal = topTenListFinal.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(10)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
            Iterator<Entry<String, Long>> it = topTenListFinal.entrySet().iterator();
            int i = 1;
            while (it.hasNext()) {
                Map.Entry<String, Long> m = it.next();
                String playerName = m.getKey();
                // Remove from TopTen if the player is online and has the permission
                sender.sendMessage("Island Player: " + playerName + " Level: " + m.getValue());
                if (i++ == 10) break;
            }
            return true;
        }


        if (sender.hasPermission("usb.topweekly.ban") && args.length >= 1 && args[0].equalsIgnoreCase("ban")) {
            if (args.length == 1) {
                sender.sendMessage("Banned: " + bannedTopweekly.toString());
            } else if (args.length == 2) {
                if (bannedTopweekly.contains(args[1])) {
                    bannedTopweekly.remove(args[1]);
                    sender.sendMessage(args[1] + " isimli oyuncunun topweekly banı kaldırıldı.");
                } else {
                    bannedTopweekly.add(args[1]);
                    sender.sendMessage(args[1] + " isimli oyuncu topweekly'den banlandı.");
                }
            }
            return true;
        }

        if (sender.hasPermission("usb.topweekly.changeprize") && args.length == 3 && args[0].equalsIgnoreCase("changeprize")) {
            plugin.getConfig().set("topweeklyPrizes." + args[1], Integer.parseInt(args[2]));
            plugin.saveConfig();
            sender.sendMessage("Değişiklik yapıldı.");
            return true;
        }


        if (!(sender instanceof Player)) {
            sender.sendMessage("Komut oyunculara özeldir.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("level")) {
            Island island = ASkyBlockAPI.getInstance().getIslandOwnedBy(((Player) sender).getUniqueId());
            if (island == null) {
                sender.sendMessage(Main.hataprefix + "Bir adanız yok!");
                return true;
            }
            String islandString = island.getCenter().getBlockX() + ";" + island.getCenter().getBlockZ();
            sender.sendMessage(Main.bilgiprefix + "Bu hafta kastığınız level: " + getWeeklyLevel(islandString));
            return true;
        }

        if (calculatingDifference) {
            sender.sendMessage(ChatColor.RED + "Haftalık sıralama hesaplanıyor. Lütfen bekleyin ve bir süre sonra tekrar komutu kullanın.");
            return true;
        }
        topTenShow((Player) sender);
        return true;
    }

    @SuppressWarnings("deprecation")
    private boolean topTenShow(final Player player) {
        // New GUI display (shown by default)
        if (topTenList.isEmpty()) {
            calculateDifference(true);
            player.sendMessage(Main.hataprefix + "Haftalık sıralamada yeterli oyuncu olmadığı için menü açılamadı. Birkaç dakika sonra yeniden deneyiniz.");
            return false;
        }

        Map<String, Long> topTenListFinal = new HashMap<>();

        for (String islandString : topTenList.keySet()) {
            String[] locs = islandString.split(";");
            Location loc = new Location(ASkyBlockAPI.getInstance().getIslandWorld(), Integer.parseInt(locs[0]), 150, Integer.parseInt(locs[1]));
            Island island = ASkyBlockAPI.getInstance().getIslandAt(loc);
            topTenListFinal.put(Bukkit.getOfflinePlayer(island.getOwner()).getName(), getWeeklyLevel(islandString));
        }

        topTenListFinal = topTenListFinal.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        // Create the top ten GUI if it does not exist
        if (gui == null) {
            // Must be a multiple of 9
            int GUISIZE = 27;
            gui = Bukkit.createInventory(null, GUISIZE, "Haftanın En Çok Gelişenleri");
        }
        // Reset
        gui.clear();
        ItemStack info1 = new ItemStack(Material.BOOK, 1);
        ItemMeta meta1 = info1.getItemMeta();
        meta1.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Sanal Blok");
        List<String> lore1 = new ArrayList<>();
        lore1.add(ChatColor.GRAY + " ");
        lore1.add(ChatColor.GRAY + "Haftalık gelişim listesinde sadece");
        lore1.add(ChatColor.GRAY + "sanal bloklar sayılmaktadır.");
        lore1.add(ChatColor.GRAY + "En çok gelişen oyuncular");
        lore1.add(ChatColor.GRAY + "her hafta ödül kazanırlar.");
        lore1.add(ChatColor.GRAY + "Detaylı bilgiyi UnioCraft Skyblock");
        lore1.add(ChatColor.GRAY + "forumlarında bulabilirsiniz.");
        lore1.add(ChatColor.GRAY + " ");
        meta1.setLore(lore1);
        info1.setItemMeta(meta1);

        ItemStack info2 = new ItemStack(Material.BOOK, 1);
        ItemMeta meta2 = info2.getItemMeta();
        meta2.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Bilgi");
        List<String> lore2 = new ArrayList<>();
        lore2.add(ChatColor.GRAY + " ");
        lore2.add(ChatColor.GRAY + "Bu liste 10 dakikada");
        lore2.add(ChatColor.GRAY + "bir yenilenmektedir!");
        lore2.add(ChatColor.GRAY + " ");
        meta2.setLore(lore2);
        info2.setItemMeta(meta2);

        gui.setItem(0, info1);
        gui.setItem(8, info2);
        int i = 1;
        Iterator<Entry<String, Long>> it = topTenListFinal.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> m = it.next();
            String playerName = m.getKey();
            // Remove from TopTen if the player is online and has the permission
            gui.setItem(SLOTS[i - 1], getTrophy(i, m.getValue(), Bukkit.getOfflinePlayer(playerName).getUniqueId()));
            if (i++ == 10) break;
        }

        player.openInventory(gui);

        return true;
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory(); // The inventory that was clicked in
        if (inventory.getName() == null) {
            return;
        }
        // The player that clicked the item
        Player player = (Player) event.getWhoClicked();
        if (!inventory.getTitle().equals("Haftanın En Çok Gelişenleri")) {
            return;
        }
        event.setCancelled(true);
        player.updateInventory();
        if (event.getCurrentItem() != null && !event.getCurrentItem().getType().equals(Material.AIR) && event.getRawSlot() < 26) {
            event.getCurrentItem().setType(Material.AIR);
            player.closeInventory();
            String playerName = getPlayer(event.getRawSlot());
            Bukkit.dispatchCommand(player, "is warp " + playerName);
        }
        if (event.getSlotType().equals(SlotType.OUTSIDE)) {
            player.closeInventory();
            return;
        }
        if (event.getClick().equals(ClickType.SHIFT_RIGHT)) {
            player.closeInventory();
            return;
        }
    }

    private final int[] SLOTS = new int[]{4, 12, 14, 19, 20, 21, 22, 23, 24, 25};

    private String getPlayer(int slot) {
        String result = "";
        // Find the rank that was clicked based on the slot position
        int i = 0;
        while (i < SLOTS.length && slot != SLOTS[i]) {
            i++;
        }
        // Was the rank found?
        if (i < SLOTS.length && slot == SLOTS[i]) {
            // Iterate through the topTenList keys for the number of ranks (i)
            Iterator<String> it = islandsMadeMostProgress.keySet().iterator();
            while (i > 0 && it.hasNext()) {
                it.next();
                i--;
            }
            // Request the name
            if (it.hasNext()) {
                result = it.next();
            }
        }
        // Return the result
        return result;
    }

    private Map<String, ItemStack> topTenHeads = new HashMap<>();

    private ItemStack getTrophy(int rank, Long long1, UUID player) {
        String playerName = ASkyBlock.getPlugin().getPlayers().getName(player);
        ItemStack trophy = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        if (playerName == null) return null;
        ItemMeta meta = trophy.getItemMeta();
        if (topTenHeads.containsKey(playerName)) {
            trophy = topTenHeads.get(playerName);
            meta = trophy.getItemMeta();
        } else {
            SkullMeta skullMeta = (SkullMeta) meta;
            skullMeta.setOwner(playerName);
        }
        if (!Bukkit.getServer().getVersion().contains("1.7") && !Bukkit.getServer().getVersion().contains("1.8")) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        Island island = ASkyBlockAPI.getInstance().getIslandOwnedBy(player);

        meta.setDisplayName((ASkyBlock.getPlugin().myLocale().topTenGuiHeading.replace("[name]", ASkyBlock.getPlugin().getGrid().getIslandName(player))).replace("[rank]", String.valueOf(rank)));
        //meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "<!> " + ChatColor.YELLOW + "Island: " + ChatColor.GOLD + ChatColor.UNDERLINE + plugin.getGrid().getIslandName(player) + ChatColor.GRAY + " (#" + rank + ")");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Bu Hafta Kasılan Ada Seviyesi");
        lore.add("" + ChatColor.AQUA + long1);
        lore.add(ChatColor.YELLOW + ASkyBlock.getPlugin().myLocale(player).levelislandLevel);
        lore.add("" + ChatColor.AQUA + ASkyBlockAPI.getInstance().getLongIslandLevel(player));
        lore.add("");
        lore.add(2, "");
        lore.add(ChatColor.YELLOW + "Ada Üyeleri");
        List<UUID> pMembers = new ArrayList<>();
        pMembers.addAll(island.getMembersWithoutCoops());
        // Need to make this a vertical list, because some teams are very large and it'll go off the screen otherwise
        List<String> memberList = new ArrayList<>();
        for (UUID members : pMembers) {
            memberList.add(ChatColor.AQUA + ASkyBlock.getPlugin().getPlayers().getName(members));
        }
        // Remove duplicates
        memberList.stream().distinct().collect(Collectors.toList());
        lore.addAll(memberList);

        meta.setLore(lore);
        trophy.setItemMeta(meta);
        topTenHeads.put(playerName, trophy);
        return trophy;
    }
}
