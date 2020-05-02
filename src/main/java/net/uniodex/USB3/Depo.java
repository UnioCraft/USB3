package net.uniodex.USB3;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Depo implements CommandExecutor {

    private Main plugin;
    private Map<String, Integer> sungerDeposu = new HashMap<>();
    private Map<String, Integer> endFrameDeposu = new HashMap<>();

    public Depo(Main plugin) {
        this.plugin = plugin;
        loadData();

        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> saveData(), 20L, 6000L);
    }

    public void addSungerToDepo(String player, Integer amount) {
        if (sungerDeposu.containsKey(player)) {
            sungerDeposu.put(player, sungerDeposu.get(player) + amount);
        } else {
            sungerDeposu.put(player, amount);
        }
    }

    public void addFrameToDepo(String player, Integer amount) {
        if (endFrameDeposu.containsKey(player)) {
            endFrameDeposu.put(player, endFrameDeposu.get(player) + amount);
        } else {
            endFrameDeposu.put(player, amount);
        }
    }

    public boolean removeSungerFromDepo(String player, Integer amount) {
        if (sungerDeposu.containsKey(player)) {
            int currentShard = sungerDeposu.get(player);
            if (currentShard >= amount) {
                sungerDeposu.put(player, sungerDeposu.get(player) - amount);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean removeFrameFromDepo(String player, Integer amount) {
        if (endFrameDeposu.containsKey(player)) {
            int currentShard = endFrameDeposu.get(player);
            if (currentShard >= amount) {
                endFrameDeposu.put(player, endFrameDeposu.get(player) - amount);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void loadData() {
        for (String player : plugin.getConfigData("depo").getKeys(false)) {
            String amounts = plugin.getConfigData("depo").getString(player);
            String[] amountsArray = amounts.split(":");
            int sunger = Integer.valueOf(amountsArray[0]);
            int frame = Integer.valueOf(amountsArray[1]);

            sungerDeposu.put(player, sunger);
            endFrameDeposu.put(player, frame);
        }
    }

    public void saveData() {
        for (String player : plugin.getConfigData("depo").getKeys(false)) {
            plugin.getConfigData("depo").set(player, null);
        }

        List<String> players = new ArrayList<>();
        Map<String, String> playersAndTheirStuff = new HashMap<>();


        for (String player : sungerDeposu.keySet()) {
            players.add(player);
        }

        for (String player : endFrameDeposu.keySet()) {
            if (!players.contains(player)) {
                players.add(player);
            }
        }

        for (String player : players) {
            String amount = "";
            if (sungerDeposu.containsKey(player)) {
                amount = "" + sungerDeposu.get(player);
            } else {
                amount = "0";
            }

            if (endFrameDeposu.containsKey(player)) {
                amount += ":" + endFrameDeposu.get(player);
            } else {
                amount += ":0";
            }
            playersAndTheirStuff.put(player, amount);
        }

        for (String player : playersAndTheirStuff.keySet()) {
            plugin.getConfigData("depo").set(player, playersAndTheirStuff.get(player));
        }

        plugin.saveConfigData("depo");
    }

    public Integer getSunger(String player) {
        if (sungerDeposu.containsKey(player)) {
            return sungerDeposu.get(player);
        }
        return 0;
    }

    public Integer getFrame(String player) {
        if (endFrameDeposu.containsKey(player)) {
            return endFrameDeposu.get(player);
        }
        return 0;
    }

    public void onDisable() {
        this.saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // blokdepo koy sunger/portal
        // blokdepo al sunger/portal <miktar>

        if (!(sender instanceof Player) || !sender.hasPermission("usb.blokdepo")) {
            sender.sendMessage(Main.hataprefix + "Bu komutu kullanmak için izniniz yok.");
            return false;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("miktar")) {
                sender.sendMessage(Main.bilgiprefix + "Deponuzdaki;");
                sender.sendMessage(Main.bilgiprefix + "Sünger Sayısı: " + ChatColor.AQUA + getSunger(player.getName()));
                sender.sendMessage(Main.bilgiprefix + "Portal Çerçevesi Sayısı: " + ChatColor.AQUA + getFrame(player.getName()));
                return false;
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("koy")) {
                if (args[1].equalsIgnoreCase("sunger")) {
                    int spongeAmount = 0;
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType().equals(Material.SPONGE)) {
                            spongeAmount += item.getAmount();
                            player.getInventory().remove(item);
                        }
                    }
                    if (spongeAmount == 0) {
                        sender.sendMessage(Main.hataprefix + "Envanterinizde hiç sünger yok! Depodaki mevcut sünger sayısı: " + ChatColor.AQUA + getSunger(sender.getName()));
                        return false;
                    }
                    addSungerToDepo(sender.getName(), spongeAmount);
                    sender.sendMessage(Main.bilgiprefix + "Envanterinizdeki süngerler başarıyla depoya koyuldu. Depodaki mevcut sünger sayısı: " + ChatColor.AQUA + getSunger(sender.getName()));
                    return false;
                } else if (args[1].equalsIgnoreCase("portal")) {
                    int portalAmount = 0;
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType().equals(Material.ENDER_PORTAL_FRAME)) {
                            portalAmount += item.getAmount();
                            player.getInventory().remove(item);
                        }
                    }
                    if (portalAmount == 0) {
                        sender.sendMessage(Main.hataprefix + "Envanterinizde hiç portal çerçevesi yok! Depodaki mevcut portal çerçevesi sayısı: " + ChatColor.AQUA + getFrame(sender.getName()));
                        return false;
                    }
                    addFrameToDepo(sender.getName(), portalAmount);
                    sender.sendMessage(Main.bilgiprefix + "Envanterinizdeki portal çerçeveleri başarıyla depoya koyuldu. Depodaki mevcut portal çerçevesi sayısı: " + ChatColor.AQUA + getFrame(sender.getName()));
                    return false;
                }
            }
        } else if (args.length == 3) {
            Integer miktar = 0;
            try {
                miktar = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Main.hataprefix + "İşlem başarısız. Lütfen geçerli bir miktar giriniz.");
                return false;
            }
            if (miktar < 1) {
                sender.sendMessage(Main.hataprefix + "İşlem başarısız. Lütfen geçerli bir miktar giriniz.");
                return false;
            }

            if (args[0].equalsIgnoreCase("al")) {
                if (args[1].equalsIgnoreCase("sunger")) {
                    if (getSunger(sender.getName()) < miktar) {
                        sender.sendMessage(Main.hataprefix + "Yeterince süngeriniz olmadığı için işlem başarısız!");
                        return false;
                    }
                    HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(Material.SPONGE, miktar));
                    int remaining = 0;
                    for (ItemStack item : leftovers.values()) {
                        if (item.getType().equals(Material.SPONGE)) {
                            remaining += item.getAmount();
                        }
                    }
                    removeSungerFromDepo(player.getName(), miktar - remaining);
                    sender.sendMessage(Main.bilgiprefix + "Depodan " + (miktar - remaining) + " adet sünger çektiniz. Kalan sünger sayısı: " + ChatColor.AQUA + getSunger(sender.getName()));
                    return false;
                } else if (args[1].equalsIgnoreCase("portal")) {
                    if (getFrame(sender.getName()) < miktar) {
                        sender.sendMessage(Main.hataprefix + "Yeterince portal çerçeveniz olmadığı için işlem başarısız!");
                        return false;
                    }
                    HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(Material.ENDER_PORTAL_FRAME, miktar));
                    int remaining = 0;
                    for (ItemStack item : leftovers.values()) {
                        if (item.getType().equals(Material.ENDER_PORTAL_FRAME)) {
                            remaining += item.getAmount();
                        }
                    }
                    removeFrameFromDepo(player.getName(), miktar - remaining);
                    sender.sendMessage(Main.bilgiprefix + "Depodan " + (miktar - remaining) + " adet portal çerçevesi çektiniz. Kalan portal çerçevesi sayısı: " + ChatColor.AQUA + getFrame(sender.getName()));
                    return false;
                }
            }

        }
        sender.sendMessage(ChatColor.GOLD + "================ BLOK DEPOSU ================");
        sender.sendMessage(ChatColor.GREEN + "/blokdepo koy sunger/portal:");
        sender.sendMessage(ChatColor.DARK_RED + "➸" + ChatColor.GRAY + " Envanterinizdeki tüm sünger ya da portal çerçevelerini depoya koyar.");
        sender.sendMessage(ChatColor.GREEN + "/blokdepo al sunger/portal <miktar>:");
        sender.sendMessage(ChatColor.DARK_RED + "➸" + ChatColor.GRAY + " Depodan belirttiğiniz miktarda sünger ya da portal çerçevesi verir.");
        sender.sendMessage(ChatColor.GREEN + "/blokdepo miktar:");
        sender.sendMessage(ChatColor.DARK_RED + "➸" + ChatColor.GRAY + " Depodaki blok miktarlarınızı gösterir.");
        return false;
    }
}
