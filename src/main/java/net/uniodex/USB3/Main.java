package net.uniodex.USB3;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.craftbukkit.InventoryWorkaround;
import com.earth2me.essentials.utils.DateUtil;
import com.flobi.floauction.Auction;
import com.flobi.floauction.FloAuction;
import com.flobi.floauction.events.AuctionBidEvent;
import com.flobi.floauction.events.AuctionStartEvent;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.wasteofplastic.askyblock.ASkyBlockAPI;
import com.wasteofplastic.askyblock.Island;
import com.wasteofplastic.askyblock.events.IslandDeleteEvent;
import com.wasteofplastic.askyblock.events.IslandLeaveEvent;
import com.wasteofplastic.askyblock.events.IslandNewEvent;
import com.wasteofplastic.askyblock.events.IslandResetEvent;
import me.neznamy.tab.bukkit.api.TABAPI;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.apache.commons.lang.StringUtils;
import org.black_ixx.bossshop.BossShop;
import org.black_ixx.bossshop.api.BossShopAPI;
import org.black_ixx.bossshop.events.BSPlayerPurchaseEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Main extends JavaPlugin implements Listener {

    /******************************************************/
    /*********************DEĞİŞKENLER**********************/
    /******************************************************/

    public static String hataprefix = ChatColor.AQUA + "" + ChatColor.BOLD + "UNIOCRAFT " + ChatColor.DARK_GREEN + "->" + ChatColor.RED + " ";
    public static String dikkatprefix = ChatColor.AQUA + "" + ChatColor.BOLD + "UNIOCRAFT " + ChatColor.DARK_GREEN + "->" + ChatColor.GOLD + " ";
    public static String bilgiprefix = ChatColor.AQUA + "" + ChatColor.BOLD + "UNIOCRAFT " + ChatColor.DARK_GREEN + "->" + ChatColor.GREEN + " ";

    public static Economy economy = null;
    public static Permission permission = null;

    public static Plugin plugin;
    private Plugin asb;

    private HashSet<String> izinliKomutlar = new HashSet<>();
    private HashSet<String> izinliKomutlarMod = new HashSet<>();

    private FileConfiguration chatData = null;
    private File chatDataFile = null;

    private FileConfiguration skullData = null;
    private File skullDataFile = null;

    private FileConfiguration komutlar = null;
    private File komutlarFile = null;

    private FileConfiguration duyuruData = null;
    private File duyuruDataFile = null;

    private FileConfiguration depoData = null;
    private File depoDataFile = null;

    private FileConfiguration topWeeklyData = null;
    private File topWeeklyDataFile = null;

    private FileConfiguration levelData = null;
    private File levelDataFile = null;

    HashMap<Player, Integer> cooldowns = new HashMap<>();
    HashMap<Player, Integer> duyuruCooldowns = new HashMap<>();

    private HashSet<Player> needsCancel = new HashSet<>();
    private HashSet<Player> otoDuyuruActive = new HashSet<>();
    private Queue<Player> queue = new LinkedList<>();

    private BossShop bs; //BossShopPro Plugin Instance
    private FloAuction flo; //BossShopPro Plugin Instance
    public SQLManager sqlManager;
    private Depo depo;
    private Essentials essPlugin;

    private WorldGuardPlugin wgpl;
    public BlockStacking blockStacking;
    public TopWeekly topWeekly;

    @Override
    public void onEnable() {
        plugin = this; // Plugin tanıma

        // Configi kaydet
        saveDefaultConfig();

        // IntegratedListeners onEnable
        IntegratedListeners.onEnableIntegratedListeners();

        // BossShop Hook
        BSPHook();

        // Auction Hook
        AuctionHook();

        // Chat Data Config yarat
        reloadConfigData("all");

        // Vault initialize et
        setupVault();

        // Eventleri kaydet
        registerEvents(this, this);
        registerEvents(this, new IntegratedListeners());
        getCommand("blokdepo").setExecutor(depo = new Depo(this));

        // İzinli komutları gir
        for (String cmd : getConfig().getStringList("izinli-komutlar")) {
            izinliKomutlar.add(cmd);
        }
        for (String cmd : getConfig().getStringList("izinli-komutlar-mod")) {
            izinliKomutlarMod.add(cmd);
        }

        // ASkyBlock initialize et
        asb = getServer().getPluginManager().getPlugin("ASkyBlock");
        if (asb == null) {
            getLogger().severe("ASkyBlock not loaded. Disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
        } else {
            getLogger().info("Linking to ASkyblock Version " + asb.getDescription().getVersion());
        }

        if (getServer().getPluginManager().isPluginEnabled("MineResetLite") && getServer().getPluginManager().isPluginEnabled("HolographicDisplays")) {
            HologramsAPI.registerPlaceholder(this, "{madensure}", 60, new USBPlaceholderReplacer());
        }

        essPlugin = (Essentials) this.getServer().getPluginManager().getPlugin("Essentials");
        wgpl = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");

        // Her 10 saniyede bir duyuru yapma için zamanlayıcı kur
        duyuruYapZamanlayicisi();

        sqlManager = new SQLManager(this);

        if (getConfig().getBoolean("updatetopten")) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, toptenTask, 100L, 18000L);
        }

        topWeekly = new TopWeekly(this);
        blockStacking = new BlockStacking(this);
    }

    @Override
    public void onDisable() { // Devre dışı bırakıldığında
        sqlManager.onDisable();
        depo.onDisable();
        blockStacking.saveData();
        plugin = null;
    }

    private Runnable toptenTask = () -> {
        Map<UUID, Long> topTen = new HashMap<>(ASkyBlockAPI.getInstance().getLongTopTen());
        Map<Island, Long> toptenHumanReadable = new HashMap<>();

        for (UUID uuid : topTen.keySet()) {
            toptenHumanReadable.put(ASkyBlockAPI.getInstance().getIslandOwnedBy(uuid), topTen.get(uuid));
        }

        toptenHumanReadable = toptenHumanReadable.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        sqlManager.updateTopTen(toptenHumanReadable);
    };

    /******************************************************/
    /*********************FONKSİYONLAR*********************/
    /******************************************************/

    public static void registerEvents(org.bukkit.plugin.Plugin plugin, Listener... listeners) {
        for (Listener listener : listeners) {
            Bukkit.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    public void BSPHook() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BossShopPro"); //Get BossShopPro Plugin

        if (plugin == null) { //Not installed?
            System.out.print("[BSP Hook] BossShopPro was not found... you can download it here: https://www.spigotmc.org/resources/25699/");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bs = (BossShop) plugin; //Success :)

    }

    public void AuctionHook() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ObsidianAuctions"); //Get BossShopPro Plugin

        if (plugin == null) { //Not installed?
            System.out.print("[Auction Hook] Obsidian Auctions cant find.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        flo = (FloAuction) plugin; //Success :)

    }

    public BossShopAPI getBSPAPI() {
        return bs.getAPI(); //Returns BossShopPro API
    }

    private boolean setupVault() {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if ((permissionProvider != null) && (economyProvider != null)) {
            permission = permissionProvider.getProvider();
            economy = economyProvider.getProvider();
        }
        return (permission != null && economy != null);
    }

    public void reloadConfigData(String type) {
        if (type.equalsIgnoreCase("chat")) {
            if (chatDataFile == null) {
                chatDataFile = new File(plugin.getDataFolder(), "chatData.yml");
                try {
                    chatDataFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            chatData = YamlConfiguration.loadConfiguration(chatDataFile);
        } else if (type.equalsIgnoreCase("skull")) {
            if (skullDataFile == null) {
                skullDataFile = new File(plugin.getDataFolder(), "skullData.yml");
                try {
                    skullDataFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            skullData = YamlConfiguration.loadConfiguration(skullDataFile);
        } else if (type.equalsIgnoreCase("komutlar")) {
            if (komutlarFile == null) {
                komutlarFile = new File(plugin.getDataFolder(), "komutlar.yml");
                try {
                    komutlarFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            komutlar = YamlConfiguration.loadConfiguration(komutlarFile);
        } else if (type.equalsIgnoreCase("duyuru")) {
            if (duyuruDataFile == null) {
                duyuruDataFile = new File(plugin.getDataFolder(), "duyuruData.yml");
                try {
                    duyuruDataFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            duyuruData = YamlConfiguration.loadConfiguration(duyuruDataFile);
        } else if (type.equalsIgnoreCase("depo")) {
            if (depoDataFile == null) {
                depoDataFile = new File(plugin.getDataFolder(), "depoData.yml");
                try {
                    depoDataFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            depoData = YamlConfiguration.loadConfiguration(depoDataFile);
        } else if (type.equalsIgnoreCase("topweekly")) {
            if (topWeeklyData == null) {
                topWeeklyDataFile = new File(plugin.getDataFolder(), "topWeekly.yml");
                try {
                    topWeeklyDataFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            topWeeklyData = YamlConfiguration.loadConfiguration(topWeeklyDataFile);
        } else if (type.equalsIgnoreCase("level")) {
            if (levelData == null) {
                levelDataFile = new File(plugin.getDataFolder(), "levelData.yml");
                try {
                    levelDataFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            levelData = YamlConfiguration.loadConfiguration(levelDataFile);
        } else if (type.equalsIgnoreCase("all")) {
            reloadConfigData("chat");
            reloadConfigData("skull");
            reloadConfigData("komutlar");
            reloadConfigData("duyuru");
            reloadConfigData("depo");
            reloadConfigData("topweekly");
            reloadConfigData("level");
        }
    }

    public FileConfiguration getConfigData(String type) {
        if (type.equalsIgnoreCase("chat")) {
            if (chatData == null) {
                reloadConfigData("chat");
            }
            return chatData;
        } else if (type.equalsIgnoreCase("skull")) {
            if (skullData == null) {
                reloadConfigData("skull");
            }
            return skullData;
        } else if (type.equalsIgnoreCase("komutlar")) {
            if (komutlar == null) {
                reloadConfigData("komutlar");
            }
            return komutlar;
        } else if (type.equalsIgnoreCase("duyuru")) {
            if (duyuruData == null) {
                reloadConfigData("duyuru");
            }
            return duyuruData;
        } else if (type.equalsIgnoreCase("depo")) {
            if (depoData == null) {
                reloadConfigData("depo");
            }
            return depoData;
        } else if (type.equalsIgnoreCase("topweekly")) {
            if (topWeeklyData == null) {
                reloadConfigData("topweekly");
            }
            return topWeeklyData;
        } else if (type.equalsIgnoreCase("level")) {
            if (levelData == null) {
                reloadConfigData("level");
            }
            return levelData;
        } else {
            return null;
        }
    }

    public void saveConfigData(String type) {
        if (type.equalsIgnoreCase("chat")) {
            if (chatData == null || chatDataFile == null) {
                return;
            }
            try {
                getConfigData("chat").save(chatDataFile);
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Could not save config to " + chatDataFile, ex);
            }
        } else if (type.equalsIgnoreCase("skull")) {
            if (skullData == null || skullDataFile == null) {
                return;
            }
            try {
                getConfigData("skull").save(skullDataFile);
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Could not save config to " + skullDataFile, ex);
            }
        } else if (type.equalsIgnoreCase("duyuru")) {
            if (duyuruData == null || duyuruDataFile == null) {
                return;
            }
            try {
                getConfigData("duyuru").save(duyuruDataFile);
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Could not save config to " + duyuruDataFile, ex);
            }
        } else if (type.equalsIgnoreCase("depo")) {
            if (depoData == null || depoDataFile == null) {
                return;
            }
            try {
                getConfigData("depo").save(depoDataFile);
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Could not save config to " + depoDataFile, ex);
            }
        } else if (type.equalsIgnoreCase("topweekly")) {
            if (topWeeklyData == null || topWeeklyDataFile == null) {
                return;
            }
            try {
                getConfigData("topweekly").save(topWeeklyDataFile);
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Could not save config to " + topWeeklyDataFile, ex);
            }
        } else if (type.equalsIgnoreCase("level")) {
            if (levelData == null || levelDataFile == null) {
                return;
            }
            try {
                getConfigData("level").save(levelDataFile);
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Could not save config to " + levelDataFile, ex);
            }
        }
    }

    public void duyuruYapZamanlayicisi() {
        new BukkitRunnable() {
            public void run() {
                if (queue.peek() != null) {
                    duyuruYap(queue.poll());
                }
            }
        }.runTaskTimer(plugin, 20, 200);
    }

    public void silentduyuruYap(Player p) {
        if (duyuruCooldowns.containsKey(p) && ((int) (new Date().getTime() / 1000) - duyuruCooldowns.get(p) < 30)) {
            return;
        } else if (queue.contains(p)) {
            return;
        } else if (getConfigData("duyuru").getString(p.getName()) == null) {
            return;
        } else {
            queue.add(p);
        }
    }

    public void duyuruYap(Player p) {
        String message = getConfigData("duyuru").getString(p.getName());
        String color = getConfigData("chat").getString(p.getName());
        if (color == null || !p.hasPermission("usb.chat.color")) {
            color = "f";
        }
        Bukkit.broadcastMessage(getPrefix(p, true) + " §7» §" + color + message);
        int i = (int) (new Date().getTime() / 1000);
        duyuruCooldowns.put(p, i);
    }

    /******************************************************/
    /*********************EVENTLER*************************/
    /******************************************************/

    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("kurallar")) {
            if (args.length == 1) {
                Bukkit.getPlayer(sender.getName()).performCommand("rules " + args[0]);
                return true;
            } else {
                Bukkit.getPlayer(sender.getName()).performCommand("rules");
                return true;
            }
        }
        if (command.getName().equalsIgnoreCase("help")) {
            if (args.length == 1) {
                Bukkit.getPlayer(sender.getName()).performCommand("komutlar " + args[0]);
                return true;
            } else {
                Bukkit.getPlayer(sender.getName()).performCommand("komutlar");
                return true;
            }
        }
        if (command.getName().equalsIgnoreCase("envsat")) {
            Bukkit.getPlayer(sender.getName()).performCommand("sell all");
            return true;
        }
        if (command.getName().equalsIgnoreCase("elsat")) {
            int amount = Bukkit.getPlayer(sender.getName()).getItemInHand().getAmount();
            Bukkit.getPlayer(sender.getName()).performCommand("sell hand " + amount);
            return true;
        }
        if (command.getName().equalsIgnoreCase("iteminfo")) {
            Bukkit.getPlayer(sender.getName()).performCommand("itemdb");
            return true;
        }
        if (command.getName().equalsIgnoreCase("vip")) {
            String vip1 = getConfigData("komutlar").getString("vip1");
            String vip2 = getConfigData("komutlar").getString("vip2");
            String vip3 = getConfigData("komutlar").getString("vip3");

            if (args.length == 0) {
                sender.sendMessage(vip1);
                return true;
            }
            if (args.length == 1) {
                if (args[0].equals("1")) {
                    sender.sendMessage(vip1);
                    return true;
                } else if (args[0].equals("2")) {
                    sender.sendMessage(vip2);
                    return true;
                } else if (args[0].equals("3")) {
                    sender.sendMessage(vip3);
                    return true;
                }
            }
            sender.sendMessage(getConfig().getString("vipmesaj"));
            return true;
        }
        if (command.getName().equalsIgnoreCase("uvip")) {
            String uvip1 = getConfigData("komutlar").getString("uvip1");
            String uvip2 = getConfigData("komutlar").getString("uvip2");
            String uvip3 = getConfigData("komutlar").getString("uvip3");

            if (args.length == 0) {
                sender.sendMessage(uvip1);
                return true;
            }
            if (args.length == 1) {
                if (args[0].equals("1")) {
                    sender.sendMessage(uvip1);
                    return true;
                } else if (args[0].equals("2")) {
                    sender.sendMessage(uvip2);
                    return true;
                } else if (args[0].equals("3")) {
                    sender.sendMessage(uvip3);
                    return true;
                }
            }
        }
        if (command.getName().equalsIgnoreCase("komutlar")) {
            String komutlar1 = getConfigData("komutlar").getString("1");
            String komutlar2 = getConfigData("komutlar").getString("2");
            String komutlar3 = getConfigData("komutlar").getString("3");
            String komutlar4 = getConfigData("komutlar").getString("4");
            String komutlar5 = getConfigData("komutlar").getString("5");
            String komutlar6 = getConfigData("komutlar").getString("6");
            String komutlar7 = getConfigData("komutlar").getString("7");
            String komutlar8 = getConfigData("komutlar").getString("8");
            String komutlar9 = getConfigData("komutlar").getString("9");

            if (args.length == 0) {
                sender.sendMessage(komutlar1);
                return true;
            }
            if (args.length == 1) {
                if (args[0].equals("1")) {
                    sender.sendMessage(komutlar1);
                    return true;
                } else if (args[0].equals("2")) {
                    sender.sendMessage(komutlar2);
                    return true;
                } else if (args[0].equals("3")) {
                    sender.sendMessage(komutlar3);
                    return true;
                } else if (args[0].equals("4")) {
                    sender.sendMessage(komutlar4);
                    return true;
                } else if (args[0].equals("5")) {
                    sender.sendMessage(komutlar5);
                    return true;
                } else if (args[0].equals("6")) {
                    sender.sendMessage(komutlar6);
                    return true;
                } else if (args[0].equals("7")) {
                    sender.sendMessage(komutlar7);
                    return true;
                } else if (args[0].equals("8")) {
                    sender.sendMessage(komutlar8);
                    return true;
                } else if (args[0].equals("9")) {
                    sender.sendMessage(komutlar9);
                    return true;
                } else {
                    sender.sendMessage(komutlar1);
                    return true;
                }
            }
            return false;
        }
        if ((command.getName().equalsIgnoreCase("renk"))) {
            Player p = (Player) sender;
            if (cooldowns.containsKey(p) && ((int) (new Date().getTime() / 1000) - cooldowns.get(p) < 60)) {
                p.sendMessage(hataprefix + "Sohbet rengi 1 dakikada bir değiştirilebilir.");
                return true;
            }
            if (!p.hasPermission("usb.chat.color")) {
                p.sendMessage(hataprefix + "Bu komutu kullanmak için VIP ya da Overlord ve üzeri bir rütbeniz olmalıdır.");
                return false;
            }
            if (args.length != 1) {
                if (p.hasPermission("usb.chat.color.mod")) {
                    p.sendMessage("§b§lRENK-> §aKullanılabilir Renkler:\n§cKırmızı, §aYeşil, §bAçık_Mavi, §eSarı, §fBeyaz, §2Koyu_Yeşil, §5Koyu_Mor, §6Turuncu, §7Gri, §8Koyu_Gri\n§b§lRENK-> §aÖrnek Kullanım: §b/renk Açık_Mavi");
                } else {
                    p.sendMessage("§b§lRENK-> §aKullanılabilir Renkler:\n§aYeşil, §bAçık_Mavi, §eSarı, §fBeyaz, §2Koyu_Yeşil, §5Koyu_Mor, §6Turuncu, §7Gri, §8Koyu_Gri\n§b§lRENK-> §aÖrnek Kullanım: §b/renk Açık_Mavi");
                }
                return true;
            } else if (args.length == 1) {
                String renk = "f";
                if (args[0].equalsIgnoreCase("Yeşil")) {
                    renk = "a";
                } else if (args[0].equalsIgnoreCase("Açık_Mavi")) {
                    renk = "b";
                } else if (args[0].equalsIgnoreCase("Sarı")) {
                    renk = "e";
                } else if (args[0].equalsIgnoreCase("Beyaz")) {
                    renk = "f";
                } else if (args[0].equalsIgnoreCase("Koyu_Yeşil")) {
                    renk = "2";
                } else if (args[0].equalsIgnoreCase("Koyu_Mor")) {
                    renk = "5";
                } else if (args[0].equalsIgnoreCase("Turuncu")) {
                    renk = "6";
                } else if (args[0].equalsIgnoreCase("Gri")) {
                    renk = "7";
                } else if (args[0].equalsIgnoreCase("Koyu_Gri")) {
                    renk = "8";
                } else if (args[0].equalsIgnoreCase("Kırmızı") && p.hasPermission("usb.chat.color.mod")) {
                    renk = "c";
                } else {
                    if (p.hasPermission("usb.chat.color.mod")) {
                        p.sendMessage("§b§lRENK-> §aKullanılabilir Renkler:\n§cKırmızı, §aYeşil, §bAçık_Mavi, §eSarı, §fBeyaz, §2Koyu_Yeşil, §5Koyu_Mor, §6Turuncu, §7Gri, §8Koyu_Gri\n§b§lRENK-> §aÖrnek Kullanım: §b/renk Açık_Mavi");
                    } else {
                        p.sendMessage("§b§lRENK-> §aKullanılabilir Renkler:\n§aYeşil, §bAçık_Mavi, §eSarı, §fBeyaz, §2Koyu_Yeşil, §5Koyu_Mor, §6Turuncu, §7Gri, §8Koyu_Gri\n§b§lRENK-> §aÖrnek Kullanım: §b/renk Açık_Mavi");
                    }
                    return true;
                }
                getConfigData("chat").createSection(p.getName());
                getConfigData("chat").set(p.getName(), renk);
                saveConfigData("chat");
                if (!p.hasPermission("usb.chat.color.timebypass")) {
                    int i = (int) (new Date().getTime() / 1000);
                    cooldowns.put(p, i);
                }
                p.sendMessage(bilgiprefix + "Sohbet konuşma renginiz düzenlendi.");
                return true;
            }
        }
        if (command.getName().equalsIgnoreCase("kafa")) {
            Player p = Bukkit.getPlayer(sender.getName());
            if (!p.hasPermission("usb.kafa")) {
                p.sendMessage(hataprefix + "Bunun için izniniz yok.");
                return true;
            }
            if (args.length != 1) {
                p.sendMessage(hataprefix + "Kullanım: /kafa <oyuncuİsmi>");
                if (getConfigData("skull").getLong(p.getName()) > System.currentTimeMillis() && !p.hasPermission("usb.kafa.bypass")) {
                    String commandCooldownTime = DateUtil.formatDateDiff(getConfigData("skull").getLong(p.getName()));
                    p.sendMessage(hataprefix + "Bir başka kafa almak için " + commandCooldownTime + " daha beklemelisiniz.");
                }
                return true;
            }
            if (!args[0].matches("^[A-Za-z0-9_]+$")) {
                p.sendMessage(hataprefix + "Hatalı bir oyuncu adı girdiniz.");
                return true;
            }
            if (p.getInventory().firstEmpty() == -1) {
                p.sendMessage(hataprefix + "Envanterinizde yeterince yer yok!");
                return true;
            }
            if (getConfigData("skull").getLong(p.getName()) > System.currentTimeMillis() && !p.hasPermission("usb.kafa.bypass")) {
                String commandCooldownTime = DateUtil.formatDateDiff(getConfigData("skull").getLong(p.getName()));
                p.sendMessage(hataprefix + "Bir başka kafa almak için " + commandCooldownTime + " daha beklemelisiniz.");
                return true;
            }

            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                String owner = args[0];
                ItemStack itemSkull = getSkull(owner);
                if (itemSkull == null) {
                    p.sendMessage(hataprefix + owner + " isimli kişinin skini olmadığı için kafasını alamadınız.");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.hasPermission("usb.kafa.bypass")) {
                        getConfigData("skull").createSection(p.getName());
                        long i = System.currentTimeMillis() + 604800000;
                        getConfigData("skull").set(p.getName(), i);
                        saveConfigData("skull");
                    }
                    InventoryWorkaround.addItems(p.getInventory(), itemSkull);
                    p.sendMessage(bilgiprefix + owner + " isimli kişinin kafasını aldınız.");
                });
            });
        }
        if (command.getName().equalsIgnoreCase("duyuru")) {
            Player p = Bukkit.getPlayer(sender.getName());
            if (!sender.hasPermission("usb.duyuru")) {
                sender.sendMessage(hataprefix + "Bunun için izniniz yok.");
                return false;
            }
            if (args.length < 1) {
                sender.sendMessage("§e--------------- §6Duyuru Komutları: sayfa 1/1 §e---------------");
                sender.sendMessage("§6/duyuru nedir: §bDuyuru sistemi hakkında bilgi alırsınız.");
                sender.sendMessage("§6/duyuru kural: §bDuyuru kurallarını görürsünüz.");
                sender.sendMessage("§6/duyuru yap: §bAyarladığınız duyuru mesajını yayınlanma sırasına koyarsınız.");
                sender.sendMessage("§6/duyuru ayarla <mesaj>: §bYayınlamak istediğiniz duyuru mesajını ayarlarsınız. (Kısaca /da <mesaj>");
                sender.sendMessage("§6/duyuru iptal: §bSıraya koyduğunuz duyuruyu sıradan çıkarırsınız.");
                sender.sendMessage("§6/duyuru sil: §bAyarladığınız duyuru mesajını silersiniz.");
                sender.sendMessage("§6/duyuru oto <saniye>: §eVIP özel §c| §bDuyuru mesajınızı ayarladığınız sürede otomatik olarak tekrar sıraya koyar.");
                sender.sendMessage("§6/duyuru oto iptal: §eVIP özel §c| §bOtomatik duyuru mesajı yayınlamayı devre dışı bırakır.");
                return false;
            } else if (args.length == 1) {
                if (args[0].equalsIgnoreCase("nedir")) {
                    sender.sendMessage("§b§lDuyuru Bilgi:");
                    sender.sendMessage("§aAda marketi reklamı yapmak, bir eşya aradığınızı ya da sattığınızı belirtmek için duyuru sistemini kullanabilirsiniz.");
                    sender.sendMessage("§aBu sistem oyuncu duyurularının sohbet bölümünü çok kirletmemesi için duyuruları 10 saniyede bir sırayla yayınlıyor. Ayrıca 30 saniye geçmeden birinin tekrar duyuru yapması engelleniyor. Böylece sohbet hem daha temiz oluyor hem de ada marketi reklamı yapmak için 30 saniyeyi sizin yerine makine sayıyor. Böylece 30 saniye kuralına uymadığınız için ceza alma ihtimali ortadan kalkıyor.");
                } else if (args[0].equalsIgnoreCase("kural")) {
                    sender.sendMessage("§b§lDuyuru Kuralları:");
                    sender.sendMessage("§aBu sistemi sadece ada marketi reklamı, eşya satma, eşya alma, eşya takaslama gibi duyurularınız için kullanınız.");
                    sender.sendMessage("§aDuyuru sistemi dışında ada marketi reklamı duyuruları yapanlar sunucudan 7-14 gün susturma cezası alacaktır.");
                    sender.sendMessage("§aSistemi suistimal eden oyuncular sunucudan 7-14 gün arası uzaklaştırma cezası alacaktır.");
                } else if (args[0].equalsIgnoreCase("yap")) {
                    if (sender instanceof ConsoleCommandSender) {
                        sender.sendMessage("Konsol bunu kullanamaz.");
                        return false;
                    }
                    if (duyuruCooldowns.containsKey(p) && ((int) (new Date().getTime() / 1000) - duyuruCooldowns.get(p) < 30)) {
                        p.sendMessage(hataprefix + "Bir duyuru daha yapmak için en az 30 saniye beklemelisiniz.");
                        return false;
                    } else if (queue.contains(p)) {
                        Object[] queueArray = queue.toArray();
                        int index = Arrays.asList(queueArray).indexOf(p);
                        int sira = index + 1;
                        p.sendMessage(hataprefix + "Zaten duyuru yapma sıradasınız. Sıranız: " + sira);
                        return false;
                    } else if (getConfigData("duyuru").getString(p.getName()) == null) {
                        p.sendMessage(hataprefix + "Bir duyuru mesajınız yok. Önce /duyuru kural yazarak kuralları okuduktan sonra /duyuru ayarla <mesaj> komutu ile mesajınızı ayarlamalısınız.");
                        return false;
                    } else {
                        queue.add(p);
                        p.sendMessage(bilgiprefix + "Duyuru yapma sırasına girdiniz! İptal etmek için §b/duyuru iptal §akomutunu kullanabilirsiniz.");
                    }
                } else if (args[0].equalsIgnoreCase("iptal")) {
                    if (sender instanceof ConsoleCommandSender) {
                        sender.sendMessage("Konsol bunu kullanamaz.");
                        return false;
                    }
                    if (queue.contains(p)) {
                        queue.remove(p);
                        p.sendMessage(bilgiprefix + "Sıradan çıkarıldınız.");
                    } else {
                        p.sendMessage(hataprefix + "Zaten sırada değilsiniz.");
                    }
                } else if (args[0].equalsIgnoreCase("sil")) {
                    if (sender instanceof ConsoleCommandSender) {
                        sender.sendMessage("Konsol bunu kullanamaz.");
                        return false;
                    }
                    if (queue.contains(p)) {
                        queue.remove(p);
                        if (getConfigData("duyuru").get(p.getName()) != null) {
                            getConfigData("duyuru").set(p.getName(), null);
                            saveConfigData("duyuru");
                            p.sendMessage(bilgiprefix + "Sıradan çıkarıldınız ve duyuru mesajınız silindi.");
                        } else {
                            p.sendMessage(hataprefix + "Zaten bir duyuru mesajınız olmadığı için mesaj silinemedi.");
                        }
                    } else {
                        if (getConfigData("duyuru").get(p.getName()) != null) {
                            getConfigData("duyuru").set(p.getName(), null);
                            saveConfigData("duyuru");
                            p.sendMessage(bilgiprefix + "Duyuru mesajınız silindi.");
                        } else {
                            p.sendMessage(hataprefix + "Zaten bir duyuru mesajınız olmadığı için mesaj silinemedi.");
                        }
                    }
                } else {
                    sender.sendMessage("§e--------------- §6Duyuru Komutları: sayfa 1/1 §e---------------");
                    sender.sendMessage("§6/duyuru nedir: §bDuyuru sistemi hakkında bilgi alırsınız.");
                    sender.sendMessage("§6/duyuru kural: §bDuyuru kurallarını görürsünüz.");
                    sender.sendMessage("§6/duyuru yap: §bAyarladığınız duyuru mesajını yayınlanma sırasına koyarsınız.");
                    sender.sendMessage("§6/duyuru ayarla <mesaj>: §bYayınlamak istediğiniz duyuru mesajını ayarlarsınız. (Kısaca /da <mesaj>");
                    sender.sendMessage("§6/duyuru iptal: §bSıraya koyduğunuz duyuruyu sıradan çıkarırsınız.");
                    sender.sendMessage("§6/duyuru sil: §bAyarladığınız duyuru mesajını silersiniz.");
                    sender.sendMessage("§6/duyuru oto <saniye>: §eVIP özel §c| §bDuyuru mesajınızı ayarladığınız sürede otomatik olarak tekrar sıraya koyar.");
                    sender.sendMessage("§6/duyuru oto iptal: §eVIP özel §c| §bOtomatik duyuru mesajı yayınlamayı devre dışı bırakır.");
                    return false;
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("oto") && (args[1].equalsIgnoreCase("iptal"))) {
                    if (!sender.hasPermission("usb.duyuru.oto")) {
                        sender.sendMessage(hataprefix + "Bunun için izniniz yok.");
                        return false;
                    }
                    if (sender instanceof ConsoleCommandSender) {
                        sender.sendMessage("Konsol bunu kullanamaz.");
                        return false;
                    }
                    if (otoDuyuruActive.contains(p)) {
                        if (!needsCancel.contains(p)) {
                            needsCancel.add(p);
                            p.sendMessage(bilgiprefix + "Otomatik duyuru yapma iptal edildi.");
                        } else {
                            p.sendMessage(bilgiprefix + "Lütfen bu komutu tekrar kullanabilmek için bir süre bekleyiniz.");
                        }
                    } else {
                        p.sendMessage(hataprefix + "Otomatik duyuru yapma zaten aktif değil.");
                    }
                } else if (args[0].equalsIgnoreCase("oto") && (!args[1].isEmpty())) {
                    if (!sender.hasPermission("usb.duyuru.oto")) {
                        sender.sendMessage(hataprefix + "Bunun için izniniz yok.");
                        return false;
                    }
                    if (sender instanceof ConsoleCommandSender) {
                        sender.sendMessage("Konsol bunu kullanamaz.");
                        return false;
                    }
                    if (getConfigData("duyuru").getString(p.getName()) == null) {
                        p.sendMessage(hataprefix + "Bir duyuru mesajınız yok. Önce /duyuru kural yazarak kuralları okuduktan sonra /duyuru ayarla <mesaj> komutu ile mesajınızı ayarlamalısınız.");
                        return false;
                    }
                    int saniye;
                    try {
                        saniye = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        p.sendMessage(hataprefix + "§cİşlem başarısız. Lütfen geçerli bir sayı giriniz.");
                        return false;
                    }
                    if (saniye < 30) {
                        p.sendMessage(hataprefix + "En az 30 saniyede bir duyuru mesajınızı kuyruğa ekleyebilirsiniz. Lütfen 30 veya daha yüksek bir miktar giriniz.");
                        return false;
                    }
                    if (saniye > 600) {
                        p.sendMessage(hataprefix + "En fazla 600 saniyede bir duyuru mesajınızı kuyruğa ekleyebilirsiniz. Lütfen 600 veya daha düşük bir miktar giriniz.");
                        return false;
                    }
                    if (saniye <= 39) {
                        saniye = 40;
                    }
                    if (otoDuyuruActive.contains(p)) {
                        p.sendMessage(hataprefix + "Zaten otomatik duyuru yapma aktif.");
                        return false;
                    }
                    new BukkitRunnable() {
                        public void run() {
                            if (needsCancel != null) {
                                if (needsCancel.contains(p)) {
                                    this.cancel();
                                    needsCancel.remove(p);
                                    otoDuyuruActive.remove(p);
                                } else {
                                    silentduyuruYap(p);
                                }
                            }
                        }
                    }.runTaskTimer(plugin, 0L, saniye * 20);
                    otoDuyuruActive.add(p);
                    p.sendMessage(bilgiprefix + "Otomatik duyuru yapma aktif edildi. İptal etmek için §b/duyuru oto iptal §akomutunu kullanabilirsiniz.");
                }
            }
            if (args.length >= 2) {
                if (args[0].equalsIgnoreCase("ayarla")) {
                    if (sender instanceof ConsoleCommandSender) {
                        sender.sendMessage("Konsol bunu kullanamaz.");
                        return false;
                    }
                    String mesaj = args.length > 0 ? StringUtils.join(args, ' ', 1, args.length) : null;
                    getConfigData("duyuru").createSection(p.getName());
                    getConfigData("duyuru").set(p.getName(), mesaj);
                    saveConfigData("duyuru");
                    p.sendMessage(bilgiprefix + "Mesajınız ayarlanmıştır. /duyuru yap komutu ile sıraya girebilirsiniz.");
                }
            }
        }
        return false;
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        String message = event.getMessage().replaceAll("%", "%%");
        String color = getConfigData("chat").getString(p.getName());
        if (color == null || !p.hasPermission("usb.chat.color")) color = "f";
        if (p.getName().equals("UnioDex")) {
            event.setFormat("§4§l[§F§lKURUCU§4§l] §4UnioDex §c-§f> §" + color + message);
        } else {
            event.setFormat(getPrefix(p, true) + " §7-> §" + color + message);
        }
    }

    public String getPrefix(Player p, boolean withName) {
        String name = p.getName();
        String vipPrefix = "";
        String grupPrefix = "";

        if (vipPrefix.equalsIgnoreCase("")) if (permission.playerInGroup(p, "Emekli")) vipPrefix = "§8§lEMEKLİ";
        if (vipPrefix.equalsIgnoreCase("")) if (permission.playerInGroup(p, "Youtuber")) vipPrefix = "§9§lYOUTUBER";
        if (vipPrefix.equalsIgnoreCase("")) if (permission.playerInGroup(p, "UVIP")) vipPrefix = "§b§lUVIP";
        if (vipPrefix.equalsIgnoreCase("")) if (permission.playerInGroup(p, "VIP")) vipPrefix = "§e§lVIP";

        if (grupPrefix.equalsIgnoreCase("")) if (permission.playerInGroup(p, "Kurucu")) {
            grupPrefix = "§4§LKURUCU §4";
            vipPrefix = "";
        }
        if (grupPrefix.equalsIgnoreCase("")) if (permission.playerInGroup(p, "SuperModerator")) {
            grupPrefix = "§c§LS.MODERATOR §c";
            vipPrefix = "";
        }
        if (grupPrefix.equalsIgnoreCase("")) if (permission.playerInGroup(p, "Moderator")) {
            grupPrefix = "§6§LMODERATOR §6";
            vipPrefix = "";
        }
        if (grupPrefix.equalsIgnoreCase("")) if (permission.playerInGroup(p, "DenemeModerator")) {
            grupPrefix = "§3§LD.MODERATOR §3";
            vipPrefix = "";
        }

        if (grupPrefix.equalsIgnoreCase(""))
            if (permission.playerInGroup(p, "SkyGod")) grupPrefix = "§F§L[§4§lSKY§f§LGOD§f§l] §4";
        if (grupPrefix.equalsIgnoreCase(""))
            if (permission.playerInGroup(p, "Emperor")) grupPrefix = "§F§L[§4§lEMPEROR§f§l] §4";
        if (grupPrefix.equalsIgnoreCase(""))
            if (permission.playerInGroup(p, "King")) grupPrefix = "§F§L[§c§LKING§f§l] §c";
        if (grupPrefix.equalsIgnoreCase(""))
            if (permission.playerInGroup(p, "Overlord")) grupPrefix = "§F§L[§9§LOVERLORD§f§l] §6";
        if (grupPrefix.equalsIgnoreCase(""))
            if (permission.playerInGroup(p, "Lord")) grupPrefix = "§F§L[§2§LLORD§f§l] §f";

        if (essPlugin != null && grupPrefix == "§F§L[§4§lSKY§f§LGOD§f§l] §4") {
            name = essPlugin.getUser(p)._getNickname() != null ? essPlugin.getUser(p)._getNickname() : name;
        }

        if (grupPrefix.equalsIgnoreCase("")) if (!vipPrefix.equalsIgnoreCase("")) grupPrefix = " ";
        if (grupPrefix.equalsIgnoreCase("")) grupPrefix = "§7";

        if (withName) {
            return vipPrefix + grupPrefix + name;
        } else {
            return vipPrefix + grupPrefix;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Spawner ve canavar yumurtalarını örse koymayı engellle.
        if (event.getSlot() != 64537) {
            if (event.getInventory().getType() == InventoryType.ANVIL) {
                Player p = (Player) event.getWhoClicked();
                org.bukkit.inventory.ItemStack item = event.getCurrentItem();
                if (item != null) {
                    if ((item.getType() == Material.MOB_SPAWNER) || (item.getType() == Material.MONSTER_EGG) || (item.getType() == Material.MONSTER_EGGS) || (item.getType().equals(Material.MAP) && item.hasItemMeta() && item.getItemMeta().getDisplayName() != null && item.getItemMeta().getDisplayName().contains("§"))) {
                        event.setCancelled(true);
                        p.sendMessage(hataprefix + "Bu eşyayı örse koymak engellenmiştir.");
                    }
                }
            }
        }
    }

    @EventHandler
    public void AdaSilinince(IslandDeleteEvent event) {
        UUID id = event.getPlayerUUID();
        Player p = Bukkit.getPlayer(id);

        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(this, () -> {
            if (p != null) {
                if (p.hasPermission("usb.rank.lord") || p.hasPermission("usb.rank.overlord") || p.hasPermission("usb.rank.king") || p.hasPermission("usb.rank.emperor") || p.hasPermission("usb.rank.skygod")) {
                    permission.playerRemoveGroup(p, "Lord");
                    permission.playerRemoveGroup(p, "Overlord");
                    permission.playerRemoveGroup(p, "King");
                    permission.playerRemoveGroup(p, "Emperor");
                    permission.playerRemoveGroup(p, "SkyGod");
                }
            }
        }, 1L);
    }

    @EventHandler
    public void YeniAdaAcinca(IslandNewEvent event) {
        Player p = event.getPlayer();

        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(this, () -> {
            if (p != null) {
                if (p.hasPermission("usb.rank.lord") || p.hasPermission("usb.rank.overlord") || p.hasPermission("usb.rank.king") || p.hasPermission("usb.rank.emperor") || p.hasPermission("usb.rank.skygod")) {
                    permission.playerRemoveGroup(p, "Lord");
                    permission.playerRemoveGroup(p, "Overlord");
                    permission.playerRemoveGroup(p, "King");
                    permission.playerRemoveGroup(p, "Emperor");
                    permission.playerRemoveGroup(p, "SkyGod");
                }
            }
        }, 1L);
    }

    @EventHandler
    public void AdayiSifirlayinca(IslandResetEvent event) {
        Player p = event.getPlayer();

        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(this, () -> {
            if (p != null) {
                if (p.hasPermission("usb.rank.lord") || p.hasPermission("usb.rank.overlord") || p.hasPermission("usb.rank.king") || p.hasPermission("usb.rank.emperor") || p.hasPermission("usb.rank.skygod")) {
                    permission.playerRemoveGroup(p, "Lord");
                    permission.playerRemoveGroup(p, "Overlord");
                    permission.playerRemoveGroup(p, "King");
                    permission.playerRemoveGroup(p, "Emperor");
                    permission.playerRemoveGroup(p, "SkyGod");
                }
            }
        }, 1L);
    }

    @EventHandler
    public void AdadanCikinca(IslandLeaveEvent event) {
        UUID id = event.getPlayer();
        Player p = Bukkit.getPlayer(id);

        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(this, () -> {
            if (p != null) {
                if (p.hasPermission("usb.rank.lord") || p.hasPermission("usb.rank.overlord") || p.hasPermission("usb.rank.king") || p.hasPermission("usb.rank.emperor") || p.hasPermission("usb.rank.skygod")) {
                    permission.playerRemoveGroup(p, "Lord");
                    permission.playerRemoveGroup(p, "Overlord");
                    permission.playerRemoveGroup(p, "King");
                    permission.playerRemoveGroup(p, "Emperor");
                    permission.playerRemoveGroup(p, "SkyGod");
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        // Oyuncu bir araçta ise araçtan indir
        Entity vehicle = p.getVehicle();
        if (vehicle != null) {
            p.getVehicle().leaveVehicle();
        }
        // Giriş mesajını sil
        event.setJoinMessage(null);

        if (p.hasPotionEffect(PotionEffectType.INVISIBILITY))
            p.removePotionEffect(PotionEffectType.INVISIBILITY);

        if (p.isOp()) return;
        if (p.hasPermission("usb.rutbe.dmod")) return;


        /*
        // Ada seviyesini hesapla
        new BukkitRunnable() {
            public void run() {
                ASkyBlockAPI.getInstance().calculateIslandLevel(p.getUniqueId());
            }
        }.runTaskLater(this, 1);
         */

        // Rankı levele göre ayarla.
        new BukkitRunnable() {
            public void run() {
                long level = ASkyBlockAPI.getInstance().getLongIslandLevel(p.getUniqueId());
                if (isPlayerTopTenFirst(p)) {
                    if (!p.hasPermission("usb.rank.skygod")) {
                        removeEveryGroupAndPerm(p, true, false);
                        permission.playerAddGroup(p, "SkyGod");
                        p.sendMessage(bilgiprefix + "Sunucu birincisi olduğunuz için rütbeniz SkyGod seviyesine çıkarılmıştır!");
                        p.sendMessage(bilgiprefix + "Yanlış rütbede olduğunuzu düşünüyorsanız /is level yazdıktan sonra oyundan çıkıp giriniz.");
                    }
                } else if (level < 10000) {
                    if (p.hasPermission("usb.rank.lord") || p.hasPermission("usb.rank.overlord") || p.hasPermission("usb.rank.king") || p.hasPermission("usb.rank.emperor") || p.hasPermission("usb.rank.skygod")) {
                        removeEveryGroupAndPerm(p, true, true);
                        p.sendMessage(bilgiprefix + "Rütbeniz seviyeniz yetersiz olduğu için silinmiştir.");
                        p.sendMessage(bilgiprefix + "Yanlış rütbede olduğunuzu düşünüyorsanız /is level yazdıktan sonra oyundan çıkıp giriniz.");
                    }
                } else if (level >= 10000 && level < 100000) {
                    if (!p.hasPermission("usb.rank.lord")) {
                        removeEveryGroupAndPerm(p, true, false);
                        permission.playerAddGroup(p, "Lord");
                        p.sendMessage(bilgiprefix + "Adanız " + level + " seviye olduğu için rütbeniz §cLord §aolarak değiştirilmiştir.");
                        p.sendMessage(bilgiprefix + "Yanlış rütbede olduğunuzu düşünüyorsanız /is level yazdıktan sonra oyundan çıkıp giriniz.");
                    }
                } else if (level >= 100000 && level < 1000000) {
                    if (!p.hasPermission("usb.rank.overlord")) {
                        removeEveryGroupAndPerm(p, true, false);
                        permission.playerAddGroup(p, "Overlord");
                        p.sendMessage(bilgiprefix + "Adanız " + level + " seviye olduğu için rütbeniz §cOverlord §aolarak değiştirilmiştir.");
                        p.sendMessage(bilgiprefix + "Yanlış rütbede olduğunuzu düşünüyorsanız /is level yazdıktan sonra oyundan çıkıp giriniz.");
                    }
                } else if (level >= 1000000 && level < 5000000) {
                    if (!p.hasPermission("usb.rank.king")) {
                        removeEveryGroupAndPerm(p, true, false);
                        permission.playerAddGroup(p, "King");
                        p.sendMessage(bilgiprefix + "Adanız " + level + " seviye olduğu için rütbeniz §cKing §aolarak değiştirilmiştir.");
                        p.sendMessage(bilgiprefix + "Yanlış rütbede olduğunuzu düşünüyorsanız /is level yazdıktan sonra oyundan çıkıp giriniz.");
                    }
                } else if (level >= 5000000) {
                    if (!p.hasPermission("usb.rank.emperor")) {
                        removeEveryGroupAndPerm(p, true, false);
                        permission.playerAddGroup(p, "Emperor");
                        p.sendMessage(bilgiprefix + "Adanız " + level + " seviye olduğu için rütbeniz §cEmperor §aolarak değiştirilmiştir.");
                        p.sendMessage(bilgiprefix + "Yanlış rütbede olduğunuzu düşünüyorsanız /is level yazdıktan sonra oyundan çıkıp giriniz.");
                    }
                }

                updateTag(p);
            }
        }.runTaskLater(this, 60); // 3 saniye sonra
    }

    private void removeEveryGroupAndPerm(Player p, boolean removeGroups, boolean removePerms) {
        if (removeGroups) {
            if (permission.playerInGroup(p, "Lord")) {
                permission.playerRemoveGroup(p, "Lord");
            }
            if (permission.playerInGroup(p, "Overlord")) {
                permission.playerRemoveGroup(p, "Overlord");
            }
            if (permission.playerInGroup(p, "King")) {
                permission.playerRemoveGroup(p, "King");
            }
            if (permission.playerInGroup(p, "Emperor")) {
                permission.playerRemoveGroup(p, "Emperor");
            }
            if (permission.playerInGroup(p, "SkyGod")) {
                permission.playerRemoveGroup(p, "SkyGod");
            }
        }
        if (removePerms) {
            if (permission.playerHas(p, "tab.sort.UVIPLord")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.UVIPLord");
            }
            if (permission.playerHas(p, "tab.sort.VIPLord")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.VIPLord");
            }
            if (permission.playerHas(p, "tab.sort.UVIPOverlord")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.UVIPOverlord");
            }
            if (permission.playerHas(p, "tab.sort.VIPOverlord")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.VIPOverlord");
            }
            if (permission.playerHas(p, "tab.sort.UVIPKing")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.UVIPKing");
            }
            if (permission.playerHas(p, "tab.sort.VIPKing")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.VIPKing");
            }
            if (permission.playerHas(p, "tab.sort.UVIPEmperor")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.UVIPEmperor");
            }
            if (permission.playerHas(p, "tab.sort.VIPEmperor")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.VIPEmperor");
            }
            if (permission.playerHas(p, "tab.sort.UVIPSkyGod")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.UVIPSkyGod");
            }
            if (permission.playerHas(p, "tab.sort.VIPSkyGod")) {
                permission.playerRemove((String) null, p.getName(), "tab.sort.VIPSkyGod");
            }
        }
    }

    private void updateTag(Player p) {
        if (p.hasPermission("usb.updatetag.bypass")) return;
        removeEveryGroupAndPerm(p, false, true); // Remove every perm first.
        if (!p.hasPermission("usb.rank.skygod")) {
            if (essPlugin.getUser(p).getNickname() != null) {
                essPlugin.getUser(p).setNickname(null);
            }
        }

        boolean added = false;
        if (permission.playerInGroup(p, "VIP") && permission.playerInGroup(p, "Lord")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.VIPLord");
            added = true;
        }

        if (permission.playerInGroup(p, "VIP") && permission.playerInGroup(p, "Overlord")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.VIPOverlord");
            added = true;
        }

        if (permission.playerInGroup(p, "VIP") && permission.playerInGroup(p, "King")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.VIPKing");
            added = true;
        }

        if (permission.playerInGroup(p, "VIP") && permission.playerInGroup(p, "Emperor")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.VIPEmperor");
            added = true;
        }

        if (permission.playerInGroup(p, "VIP") && permission.playerInGroup(p, "SkyGod")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.VIPSkyGod");
            added = true;
        }

        if (permission.playerInGroup(p, "UVIP") && permission.playerInGroup(p, "Lord")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.UVIPLord");
            added = true;
        }

        if (permission.playerInGroup(p, "UVIP") && permission.playerInGroup(p, "Overlord")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.UVIPOverlord");
            added = true;
        }

        if (permission.playerInGroup(p, "UVIP") && permission.playerInGroup(p, "King")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.UVIPKing");
            added = true;
        }

        if (permission.playerInGroup(p, "UVIP") && permission.playerInGroup(p, "Emperor")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.UVIPEmperor");
            added = true;
        }

        if (permission.playerInGroup(p, "UVIP") && permission.playerInGroup(p, "SkyGod")) {
            permission.playerAdd((String) null, p.getName(), "tab.sort.UVIPSkyGod");
            added = true;
        }

        if (added) {
            if (!TABAPI.getOriginalTabPrefix(p).equals(getPrefix(p, false))) {
                TABAPI.setTabPrefixPermanently(p, getPrefix(p, false));
                TABAPI.setTagPrefixPermanently(p, getPrefix(p, false));
            }
        } else {
            if (TABAPI.getOriginalTabPrefix(p) != null) {
                TABAPI.setTabPrefixPermanently(p, "");
                TABAPI.setTagPrefixPermanently(p, "");
            }
        }
    }

    private int getPriority(Player p) {
        if (p.hasPermission("unio.rank.vip")) {
            if (p.hasPermission("usb.rank.lord")) {
                return 19;
            } else if (p.hasPermission("usb.rank.overlord")) {
                return 18;
            } else if (p.hasPermission("usb.rank.king")) {
                return 17;
            } else if (p.hasPermission("usb.rank.emperor")) {
                return 16;
            } else if (p.hasPermission("usb.rank.skygod")) {
                return 9;
            }
        }
        if (p.hasPermission("unio.rank.uvip")) {
            if (p.hasPermission("usb.rank.lord")) {
                return 14;
            } else if (p.hasPermission("usb.rank.overlord")) {
                return 13;
            } else if (p.hasPermission("usb.rank.king")) {
                return 12;
            } else if (p.hasPermission("usb.rank.emperor")) {
                return 11;
            } else if (p.hasPermission("usb.rank.skygod")) {
                return 8;
            }
        }
        return -1;
    }

    private boolean isPlayerTopTenFirst(Player p) {
        Map<UUID, Long> topten = new HashMap<>(ASkyBlockAPI.getInstance().getLongTopTen());
        topten = topten.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        UUID firstUUID = topten.keySet().stream().findFirst().get();
        UUID playerUUID = p.getUniqueId();
        Island island = ASkyBlockAPI.getInstance().getIslandOwnedBy(firstUUID);
        if (island == null) return false;

        return island.getMembersWithoutCoops().contains(playerUUID);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        Player p = event.getPlayer();
        if (queue.contains(event.getPlayer())) {
            queue.remove(event.getPlayer());
        }
        if (otoDuyuruActive.contains(p)) {
            if (!needsCancel.contains(p)) {
                needsCancel.add(p);
            }
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        Player p = event.getPlayer();
        if (queue.contains(event.getPlayer())) {
            queue.remove(event.getPlayer());
        }
        if (otoDuyuruActive.contains(p)) {
            if (!needsCancel.contains(p)) {
                needsCancel.add(p);
            }
        }
    }

    @EventHandler
    public void onPlayerFallUSG(PlayerMoveEvent event) {
        // USG integre (Void2Spawn için.)
        Player p = event.getPlayer();
        int toY = event.getTo().getBlockY();
        if ((toY < -5)) {
            Auction auc = FloAuction.getPlayerAuction(p);
            if (auc != null) {
                Player bidder;
                if (auc.getCurrentBid() == null) {
                    bidder = Bukkit.getPlayer("BidderBuAdamKimseDegil123");
                } else {
                    bidder = Bukkit.getPlayer(auc.getCurrentBid().getBidder());
                    if (bidder == null) {
                        bidder = Bukkit.getPlayer("BidderBuAdamKimseDegil123");
                    }
                }
                Player owner = Bukkit.getPlayer(auc.getOwner());
                if (owner == null) {
                    owner = Bukkit.getPlayer("OwnerBuAdamKimseDegil123");
                }
                if (p.equals(bidder) || (p.equals(owner))) {
                    p.performCommand("is go");
                    event.setCancelled(true);
                } else {
                    p.setFallDistance(0.0F);
                    Location spawn = Bukkit.getWorld("world").getSpawnLocation();
                    spawn.setYaw((float) 90.37866);
                    spawn.setPitch((float) 0.44991094);
                    p.teleport(spawn);
                }
            } else {
                p.setFallDistance(0.0F);
                Location spawn = Bukkit.getWorld("world").getSpawnLocation();
                spawn.setYaw((float) 90.37866);
                spawn.setPitch((float) 0.44991094);
                p.teleport(spawn);
            }
        }

    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player p = event.getPlayer();
        Auction auc = FloAuction.getPlayerAuction(p);
        if (auc == null) {
            return;
        }
        Player bidder;
        if (auc.getCurrentBid() == null) {
            bidder = Bukkit.getPlayer("BidderBuAdamKimseDegil123");
        } else {
            bidder = Bukkit.getPlayer(auc.getCurrentBid().getBidder());
            if (bidder == null) {
                bidder = Bukkit.getPlayer("BidderBuAdamKimseDegil123");
            }
        }
        Player owner = Bukkit.getPlayer(auc.getOwner());
        if (owner == null) {
            owner = Bukkit.getPlayer("OwnerBuAdamKimseDegil123");
        }
        if (p.equals(bidder) || (p.equals(owner))) {
            int toY = p.getLocation().getBlockY();
            if ((toY < -2)) {
                return;
            } else {
                p.sendMessage(hataprefix + "Açık arttırmada iken ışınlanamazsınız.");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBid(AuctionBidEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (ASkyBlockAPI.getInstance().playerIsOnIsland(event.getPlayer())) {
            return;
        } else {
            Player p = event.getPlayer();
            event.setCancelled(true);
            p.sendMessage(hataprefix + "Açık arttırmaya teklif verebilmek için adanızda olmalısınız.");
        }
    }

    @EventHandler
    public void onAuctionStart(AuctionStartEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (ASkyBlockAPI.getInstance().playerIsOnIsland(event.getPlayer())) {
            return;
        } else {
            Player p = event.getPlayer();
            event.setCancelled(true);
            p.sendMessage(hataprefix + "Açık arttırma başlatabilmek için adanızda olmalısınız.");
        }
    }

    @EventHandler
    public void EsyaDusurmeEvent(PlayerDropItemEvent event) {
        if (event.isCancelled()) {
            return;
        } else {
            ItemStack stack = event.getItemDrop().getItemStack();
            if (stack.getType().equals(Material.MOB_SPAWNER)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(hataprefix + "Eşyanın silinme riskine karşı yere spawner atmak engellenmiştir! Bir sandığa koymayı deneyin.");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("usb.command.bypass")) return;

        List<String> allowedCommands = new ArrayList<>(izinliKomutlar);

        if (player.hasPermission("usb.rutbe.dmod")) {
            allowedCommands.addAll(izinliKomutlarMod);
        }

        String command = event.getMessage().split(" ")[0].replace("/", "").toLowerCase();
        if (allowedCommands.contains(command)) {
            return;
        }

        /// USB Additions
        // /ah sell <miktar> <fiyat> dolandırıcılığı koruması.
        String[] cmd = event.getMessage().replaceFirst("/", "").split(" ");
        if (cmd.length > 3) {
            if (cmd[0].equalsIgnoreCase("ah") || cmd[0].equalsIgnoreCase("ca") || cmd[0].equalsIgnoreCase("crazyauction") || cmd[0].equalsIgnoreCase("crazyauctions") || cmd[0].equalsIgnoreCase("hdv") || cmd[0].equalsIgnoreCase("pazaryeri")) {
                if (cmd[1].equalsIgnoreCase("sell")) {
                    event.setCancelled(true);
                    player.sendMessage(hataprefix + " Çok fazla argüman kullandınız! Lütfen /ah sell <fiyat> komutunu kullanınız.");
                    return;
                }
            }
        }

        // sell hand yerine /elsat
        if (cmd.length >= 2) {
            if (player.hasPermission("usb.rank.vip") && !player.isOp()) {
                if (cmd[0].equalsIgnoreCase("sell") && !cmd[1].equalsIgnoreCase("hand")) {
                    event.setCancelled(true);
                    player.sendMessage(hataprefix + "Lütfen eşya satmak için /elsat komutunu kullanınız.");
                }
                if (cmd[0].equalsIgnoreCase("sell") && cmd[1].equalsIgnoreCase("hand") && cmd.length == 2) {
                    event.setCancelled(true);
                    player.sendMessage(hataprefix + "Lütfen eşya satmak için /elsat komutunu kullanınız.");
                }
            }
        }

        event.setCancelled(true);
        player.sendMessage("§cHatalı bir komut girdiniz! Kullanabileceğiniz komutlar için §b/komutlar §cyazınız");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void CommandInstance(PlayerCommandPreprocessEvent event) {
        String[] cmd = event.getMessage().replaceFirst("/", "").split(" ");
        Player p = event.getPlayer();

        if (cmd.length == 1) {
            if (cmd[0].equalsIgnoreCase("skull")) {
                event.setCancelled(true);
                p.performCommand("kafa");
            }
            if (cmd[0].equalsIgnoreCase("sınır")) {
                event.setCancelled(true);
                p.performCommand("isborder toggle");
            }
            if (cmd[0].equalsIgnoreCase("sandık")) {
                event.setCancelled(true);
                p.performCommand("pv");
            }
            if (cmd[0].equalsIgnoreCase("kasa")) {
                event.setCancelled(true);
                p.performCommand("crates");
            }
            if (cmd[0].equalsIgnoreCase("kasalar")) {
                event.setCancelled(true);
                p.performCommand("crates");
            }
        }
        if (cmd.length == 2) {
            if (cmd[0].equalsIgnoreCase("skull")) {
                event.setCancelled(true);
                p.performCommand("kafa " + cmd[1]);
            }
            if (cmd[0].equalsIgnoreCase("sandık")) {
                event.setCancelled(true);
                p.performCommand("pv " + cmd[1]);
            }
            if (cmd[0].equalsIgnoreCase("warp") && cmd[1].equalsIgnoreCase("nether")) {
                event.setCancelled(true);
                p.sendMessage(dikkatprefix + "Nether'a gidebilmek için bir Nether Portalı kullanmalısınız.");
            }
            if (cmd[0].equalsIgnoreCase("warp") && cmd[1].equalsIgnoreCase("market")) {
                p.sendMessage(bilgiprefix + "/market komutunu kullanarak hızlı marketi açabilirsiniz.");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("setwarp")) {
                event.setCancelled(true);
                p.sendMessage(dikkatprefix + "Warp oluşturmak istediğiniz yere bir tabela koyun ve en üst satıra [HOŞGELDİN] yazın.");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("party")) {
                event.setCancelled(true);
                p.performCommand("is team");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("topweekly")) {
                event.setCancelled(true);
                p.performCommand("topweekly");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("biome")) {
                event.setCancelled(true);
                p.performCommand("is biomes");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("home")) {
                event.setCancelled(true);
                p.performCommand("is go");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("h")) {
                event.setCancelled(true);
                p.performCommand("is go");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("w")) {
                event.setCancelled(true);
                p.performCommand("is warp");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("trust")) {
                event.setCancelled(true);
                p.performCommand("is listcoops");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("untrust")) {
                event.setCancelled(true);
                p.performCommand("is uncoop");
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("levelweekly")) {
                event.setCancelled(true);
                p.performCommand("topweekly level");
            }
        } else if (cmd.length == 3) {
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("w")) {
                event.setCancelled(true);
                p.performCommand("is warp " + cmd[2]);
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("home")) {
                event.setCancelled(true);
                p.performCommand("is go " + cmd[2]);
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("h")) {
                event.setCancelled(true);
                p.performCommand("is go " + cmd[2]);
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("trust")) {
                event.setCancelled(true);
                p.performCommand("is coop " + cmd[2]);
            }
            if (cmd[0].equalsIgnoreCase("is") && cmd[1].equalsIgnoreCase("untrust")) {
                event.setCancelled(true);
                p.performCommand("is uncoop " + cmd[2]);
            }
        }

        if (cmd.length == 2) {
            if (cmd[0].equalsIgnoreCase("auc") && cmd[1].equalsIgnoreCase("info")) {
                return;
            }
            if (cmd[0].equalsIgnoreCase("sauc") && cmd[1].equalsIgnoreCase("info")) {
                return;
            }
            if (cmd[0].equalsIgnoreCase("auction") && cmd[1].equalsIgnoreCase("info")) {
                return;
            }
            if (cmd[0].equalsIgnoreCase("sealedauction") && cmd[1].equalsIgnoreCase("info")) {
                return;
            }
        }

        if (cmd.length > 0) {
            if (cmd[0].equalsIgnoreCase("bid")) {
                if (ASkyBlockAPI.getInstance().playerIsOnIsland(event.getPlayer())) {
                    return;
                } else {
                    event.setCancelled(true);
                    p.sendMessage(hataprefix + "Açık arttırma komutlarını kullanabilmek için adanızda olmalısınız.");
                }
            }
            if (cmd[0].equalsIgnoreCase("auc")) {
                if (ASkyBlockAPI.getInstance().playerIsOnIsland(event.getPlayer())) {
                    return;
                } else {
                    event.setCancelled(true);
                    p.sendMessage(hataprefix + "Açık arttırma komutlarını kullanabilmek için adanızda olmalısınız.");
                }
            }
            if (cmd[0].equalsIgnoreCase("sauc")) {
                if (ASkyBlockAPI.getInstance().playerIsOnIsland(event.getPlayer())) {
                    return;
                } else {
                    event.setCancelled(true);
                    p.sendMessage(hataprefix + "Açık arttırma komutlarını kullanabilmek için adanızda olmalısınız.");
                }
            }
            if (cmd[0].equalsIgnoreCase("auction")) {
                if (ASkyBlockAPI.getInstance().playerIsOnIsland(event.getPlayer())) {
                    return;
                } else {
                    event.setCancelled(true);
                    p.sendMessage(hataprefix + "Açık arttırma komutlarını kullanabilmek için adanızda olmalısınız.");
                }
            }
            if (cmd[0].equalsIgnoreCase("sealedauction")) {
                if (ASkyBlockAPI.getInstance().playerIsOnIsland(event.getPlayer())) {
                    return;
                } else {
                    event.setCancelled(true);
                    p.sendMessage(hataprefix + "Açık arttırma komutlarını kullanabilmek için adanızda olmalısınız.");
                }
            }
        }
    }


    @EventHandler
    public void ucmaEngelWG(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission("usb.fly.bypass")) {
            return;
        }
        List<String> regions = getConfig().getStringList("izinsiz-regions");
        String world = event.getPlayer().getWorld().getName();
        double px = p.getLocation().getX();
        double py = p.getLocation().getY() + 1.0D;
        double pz = p.getLocation().getZ();
        if (p.isFlying()) {
            for (String region : regions) {
                String[] regionInfo = region.split(":");
                if ((regionInfo[0].equalsIgnoreCase(world)) &&
                        (wgpl.getRegionManager(Bukkit.getWorld(world)).hasRegion(regionInfo[1]))) {
                    ProtectedRegion reg = wgpl.getRegionManager(Bukkit.getWorld(world)).getRegion(regionInfo[1]);
                    if (reg.contains((int) px, (int) py, (int) pz)) {
                        for (double y = py; y >= 0.0D; y -= 1.0D) {
                            Block topBlock = p.getWorld().getBlockAt(new Location(p.getWorld(), px, y, pz));
                            Block bottomBlock = p.getWorld().getBlockAt(new Location(p.getWorld(), px, y - 1.0D, pz));
                            Block ground = p.getWorld().getBlockAt(new Location(p.getWorld(), px, y - 2.0D, pz));
                            if (((topBlock.isEmpty()) || (topBlock.isLiquid())) && ((bottomBlock.isEmpty()) || (bottomBlock.isLiquid())) &&
                                    (ground.getType().isSolid())) {
                                p.teleport(new Location(p.getWorld(), px, y, pz, p.getLocation().getYaw(), p.getLocation().getPitch()));
                                p.setFlying(false);
                                p.setAllowFlight(false);
                                p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("ucmaengelmesaj")));
                                break;
                            }
                        }
                    }
                }
            }
        }

    }

	/*@EventHandler
	public void ucmaEngel(PlayerMoveEvent event)
	{
		if (ASkyBlockAPI.getInstance().playerIsOnIsland(event.getPlayer())){
			return;
		}
		Player p = event.getPlayer();
		if (p.hasPermission("usb.fly.bypass")) {
			return;
		}
		double px = p.getLocation().getX();
		double py = p.getLocation().getY() + 1.0D;
		double pz = p.getLocation().getZ();
		if ((p.isFlying()) && (p.getWorld().getName().equalsIgnoreCase("skyworld"))) {
			for (double y = py; y >= 0.0D; y -= 1.0D)
			{
				Block topBlock = p.getWorld().getBlockAt(new Location(p.getWorld(), px, y, pz));
				Block bottomBlock = p.getWorld().getBlockAt(new Location(p.getWorld(), px, y - 1.0D, pz));
				//Block ground = p.getWorld().getBlockAt(new Location(p.getWorld(), px, y - 2.0D, pz));
				if (((topBlock.isEmpty()) || (topBlock.isLiquid())) && ((bottomBlock.isEmpty()) || (bottomBlock.isLiquid())))
				{
					p.teleport(new Location(p.getWorld(), px, y, pz, p.getLocation().getYaw(), p.getLocation().getPitch()));
					p.setFlying(false);
					p.setAllowFlight(false);
					p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("ucmaengelmesaj")));
					break;
				}
			}
		}
	}*/

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void BSPEvent(BSPlayerPurchaseEvent event) {
        if (event.getShop().getDisplayName() == null)
            return;
        if (event.getShopItem().getItem().getItemMeta() == null)
            return;
        if (event.getShopItem().getItem().getItemMeta().getDisplayName() == null)
            return;

        if (event.getShop().getDisplayName().equalsIgnoreCase("§4§lALIŞVERİŞ") && event.getShopItem().getItem().getItemMeta().getDisplayName().contains("§cHepsini Sat")) {
            if (!event.getPlayer().hasPermission("usb.rank.uvip")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(hataprefix + "Bu özellik UVIP oyunculara özeldir!");
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        Block b = event.getClickedBlock();
        Location loc = b.getLocation();
        ItemStack endFrame = new ItemStack(Material.ENDER_PORTAL_FRAME);

        if (!p.getGameMode().equals(GameMode.SURVIVAL)) {
            return;
        }

        if (event.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
            if (ASkyBlockAPI.getInstance().locationIsOnIsland(p, loc)) {
                if (b.getType().equals(Material.ENDER_PORTAL_FRAME)) {
                    if (p.getInventory().firstEmpty() != -1) {
                        event.setCancelled(true);
                        b.setType(Material.AIR);
                        p.getInventory().addItem(endFrame);
                    } else {
                        event.setCancelled(true);
                        b.setType(Material.AIR);
                        loc.setY(loc.getY() + 0.80);
                        b.getWorld().dropItemNaturally(loc, endFrame);
                    }
                }
            }
        }
    }

    public boolean isAreaArena(Location location) {
        List<String> regionIds = new ArrayList<>();
        RegionManager regionManager = wgpl.getRegionManager(location.getWorld());
        ApplicableRegionSet regionsAtLocation = regionManager.getApplicableRegions(location);

        for (ProtectedRegion region : regionsAtLocation) {
            regionIds.add(region.getId());
        }

        if (regionIds.contains("arenaatla")) {
            return false;
        }

        if (regionIds.contains("arena")) {
            return true;
        } else {
            return false;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (event.getCause().equals(TeleportCause.PLUGIN)) {
            return;
        }

        if (player.hasPermission("usb.rutbe.dmod")) return;

        if (isAreaArena(event.getTo())) {
            event.setCancelled(true);
            player.sendMessage(dikkatprefix + "Işınlanmaya çalıştığınız alan arena olduğu için ışınlanma iptal edildi.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onLeash(PlayerLeashEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getEntity();

        if (entity.getType().equals(EntityType.SNOWMAN)) {
            event.setCancelled(true);
            player.sendMessage(dikkatprefix + "Kardan adamı bağlamak sunucu tarafından engellenmiştir.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntityType().equals(EntityType.WOLF)) {
            Island from = ASkyBlockAPI.getInstance().getIslandAt(event.getFrom());
            Island to = ASkyBlockAPI.getInstance().getIslandAt(event.getTo());
            if (from != to) {
                event.setCancelled(true);
            }
        }
    }

    private ItemStack getSkull(String playerName) {
        String[] textureAndSiganture;
        textureAndSiganture = sqlManager.getSkin(playerName);
        if (textureAndSiganture == null) {
            textureAndSiganture = getTextureAndSignature(playerName);
        }
        if (textureAndSiganture == null) {
            return null;
        }
        return getSkull(playerName, textureAndSiganture[0], textureAndSiganture[1]);
    }

    private ItemStack getSkull(String playerName, String value, String signature) {
        ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        GameProfile gameProfile = new GameProfile(UUID.randomUUID(), null);
        gameProfile.getProperties().put("textures", new Property("textures", value, signature));

        SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
        try {
            Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullMeta, gameProfile);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException exception) {
            return null;
        }
        skullMeta.setDisplayName("§f" + playerName + " isimli oyuncunun kafası");

        item.setItemMeta(skullMeta);
        return item;
    }

    public String[] getTextureAndSignature(String name) {
        try {
            URL url_0 = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            InputStreamReader reader_0 = new InputStreamReader(url_0.openStream());
            String uuid = new JsonParser().parse(reader_0).getAsJsonObject().get("id").getAsString();

            URL url_1 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            InputStreamReader reader_1 = new InputStreamReader(url_1.openStream());
            JsonObject textureProperty = new JsonParser().parse(reader_1).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
            String texture = textureProperty.get("value").getAsString();
            String signature = textureProperty.get("signature").getAsString();

            return new String[]{texture, signature};
        } catch (IOException e) {
            System.err.println("Could not get skin data from session servers!");
            e.printStackTrace();
            return null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (item.getType().equals(Material.MAP) && item.hasItemMeta() && item.getItemMeta().getDisplayName() != null && item.getItemMeta().getDisplayName().contains("§")) {
                if (item.getItemMeta().getDisplayName().contains("§")) {
                    event.getInventory().setResult(null);
                    return;
                }
            }
        }
    }
}