package net.uniodex.USB3;

import com.wasteofplastic.askyblock.ASkyBlockAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IntegratedListeners implements Listener {

    /******************************************************/
    /*********************DEĞİŞKENLER**********************/
    /******************************************************/

    // -------------------------------------------------- //
    // Integrated from AntiLootSteal
    // -------------------------------------------------- //
    protected boolean usesDamage = true;
    protected static HashMap<UUID, Damagers> damagers = new HashMap<>();

    // -------------------------------------------------- //
    // Integrated from ClearLag
    // -------------------------------------------------- //
    int range = Main.plugin.getConfig().getInt("range");
    int breedLimit = Main.plugin.getConfig().getInt("breed-limit");
    int naturalLimit = Main.plugin.getConfig().getInt("natural-limit");
    int spawnerLimit = Main.plugin.getConfig().getInt("spawner-limit");
    int spawnEggLimit = Main.plugin.getConfig().getInt("spawnegg-limit");

    // -------------------------------------------------- //
    // Integrated from EnderPearlCooldown
    // -------------------------------------------------- //
    public static Map<String, Long> _initialized;
    public long _cooldown = 3000;
    public int amount = 3;

    /******************************************************/
    /*********************FONKSİYONLAR*********************/
    /******************************************************/

    @SuppressWarnings("deprecation")
    public static void onEnableIntegratedListeners() {
        /******ENTEGRE KODLAR BAŞLANGIÇ******/
        // ClearLag hatırası.
        Bukkit.getServer().getScheduler()
                .scheduleAsyncDelayedTask(Main.plugin, new Runnable() {
                    public void run() {
                        long time = new Date().getTime() - 86400000L * 60;

                        File folder = new File(Bukkit.getServer().getWorldContainer().getAbsolutePath() + "/logs");
                        if (!folder.exists()) {
                            return;
                        }
                        File[] files = folder.listFiles();

                        @SuppressWarnings("unused")
                        int deleted = 0;
                        for (File file : files) {
                            if ((file.isFile()) && (file.getName().endsWith(".log.gz")) && (time > parseTime(file.getName().replace(".log.gz", "")).getTime())) {
                                file.delete();
                                deleted++;
                            }
                        }
                        System.out.println("Loglar temizlendi.");
                    }
                }, 1L);
        // AntiLootSteal'den entegre edilmiş kod
        new BukkitRunnable() {
            public void run() {
                for (Iterator<UUID> it = IntegratedListeners.damagers.keySet().iterator(); it.hasNext(); ) {
                    UUID player = it.next();
                    if (IntegratedListeners.damagers.get(player).elapsed())
                        it.remove();
                }
            }
        }.runTaskTimer(Main.plugin, 20, 20);

        // EnderPearlCooldown entegre edilmiş kod
        IntegratedListeners._initialized = new ConcurrentHashMap<>();
        /********ENTEGRE KODLAR BİTİŞ********/
    }

    // -------------------------------------------------- //
    // Integrated from ClearLag
    // -------------------------------------------------- //
    public static Date parseTime(String time) {
        try {
            String[] frag = time.split("-");
            if (frag.length < 2) {
                return new Date();
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.parse(frag[0] + "-" + frag[1] + "-" + frag[2]);
        } catch (Exception e) {
        }
        return new Date();
    }

    // -------------------------------------------------- //
    // Integrated from EnderPearlCooldown
    // -------------------------------------------------- //

    public String toDecimalsEPC(double x) {
        String asString = Double.toString(x);
        String fullNumber = asString.split("\\.")[0];

        return fullNumber + "." + (asString.split("\\.")[1].length() > this.amount ? asString.split("\\.")[1].substring(0, this.amount) : asString.split("\\.")[1]);
    }


    /******************************************************/
    /*********************LISTENERLAR**********************/
    /******************************************************/

    // -------------------------------------------------- //
    // Integrated from AntiLootSteal
    // -------------------------------------------------- //
    @EventHandler
    public void onPlayerDeathALS(PlayerDeathEvent e) { // Oyuncu ölünce
        // Kişi oyuncu mu kontrolü
        if (e.getEntity() instanceof Player) {
            // Skyworld kontrolü
            if (e.getEntity().getWorld().getName().equalsIgnoreCase("skyworld")) {
                // Kişi adasında mı değil mi kontrolü.
                if (!ASkyBlockAPI.getInstance().playerIsOnIsland(e.getEntity())) {
                    return;
                }
            }
        }
        if ((!(e.getEntity() instanceof Player) || !(e.getEntity().getKiller() instanceof Player)))
            return;

        Player player = e.getEntity();

        if (e.getDrops().isEmpty())
            return;

        if (damagers.containsKey(player.getUniqueId())) {
            final Player killer = Bukkit.getPlayer(damagers.get(player.getUniqueId()).getTopDamager());

            if (killer == null || killer.getUniqueId() == null) {
                return;
            }

            String killerUUID = killer.getUniqueId().toString();

            for (ItemStack is : e.getDrops()) {
                Entity entity = e.getEntity().getWorld().dropItem(e.getEntity().getLocation(), is);

                if (entity.hasMetadata("LootSteal"))
                    entity.removeMetadata("LootSteal", Main.plugin);

                entity.setMetadata("LootSteal", new FixedMetadataValue(Main.plugin, killerUUID + " " + System.currentTimeMillis()));
            }

            new BukkitRunnable() {
                public void run() {
                    killer.sendMessage(Main.dikkatprefix + "Ganimetinizin koruması kalktı. Artık herkes alabilir.");
                }
            }.runTaskLater(Main.plugin, 20 * 10);
        } else {
            final Player killer = (Player) e.getEntity().getKiller();

            for (ItemStack is : e.getDrops()) {
                Entity entity = e.getEntity().getWorld().dropItem(e.getEntity().getLocation(), is);

                if (entity.hasMetadata("LootSteal"))
                    entity.removeMetadata("LootSteal", Main.plugin);

                entity.setMetadata("LootSteal", new FixedMetadataValue(Main.plugin, killer.getUniqueId().toString() + " " + System.currentTimeMillis()));
            }

            new BukkitRunnable() {
                public void run() {
                    killer.sendMessage(Main.dikkatprefix + "Ganimetinizin koruması kalktı. Artık herkes alabilir.");
                }
            }.runTaskLater(Main.plugin, 20 * 10);
        }
        e.getDrops().clear();
    }

    @EventHandler
    public void onItemPickupALS(PlayerPickupItemEvent e) {
        if (!(e.getItem().hasMetadata("LootSteal")))
            return;

        String value = e.getItem().getMetadata("LootSteal").get(0).asString();

        String[] values = value.split(" ");

        if (e.getPlayer().getUniqueId().toString().equals(values[0]))
            return;

        if (System.currentTimeMillis() - Long.valueOf(values[1]).longValue() >= (10 * 1000))
            return;

        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamageByEntityALS(EntityDamageByEntityEvent event) {
        if (!(usesDamage))
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        if (event.getDamage() == 0)
            return;

        Player player = (Player) event.getEntity(), damager = null;

        if (event.getDamager() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getDamager();

            if (arrow.getShooter() instanceof Player)
                damager = (Player) arrow.getShooter();
            else
                return;
        } else if (event.getDamager() instanceof Snowball) {
            Snowball snowball = (Snowball) event.getDamager();

            if (snowball.getShooter() instanceof Player)
                damager = (Player) snowball.getShooter();
            else
                return;
        } else if (event.getDamager() instanceof Egg) {
            Egg egg = (Egg) event.getDamager();

            if (egg.getShooter() instanceof Player)
                damager = (Player) egg.getShooter();
            else
                return;
        } else if (event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        } else {
            return;
        }

        if (!(damagers.containsKey(player.getUniqueId())))
            damagers.put(player.getUniqueId(), new Damagers());

        damagers.get(player.getUniqueId()).addDamage(damager, (int) event.getDamage());
    }

    // -------------------------------------------------- //
    // Integrated from UnioAntiOP
    // -------------------------------------------------- //

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().getName().equals("UnioDex") && (event.getPlayer().isOp())) {
            event.getPlayer().setOp(false);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!event.getPlayer().getName().equals("UnioDex") && (event.getPlayer().isOp())) {
            event.getPlayer().setOp(false);
        }
    }

    /*@EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        if (!event.getPlayer().getName().equals("UnioDex") && (event.getPlayer().isOp())) {
            event.getPlayer().setOp(false);
        }
    }*/

    // -------------------------------------------------- //
    // Integrated from UnioDispenserFix
    // -------------------------------------------------- //

    @EventHandler
    public void onInventoryClickDispenserFix(InventoryClickEvent event) {
        // Spawner ve canavar yumurtalarını örse koymayı engellle.
        if (event.getInventory().getType() == InventoryType.DISPENSER) {
            Player p = (Player) event.getWhoClicked();
            ItemStack item = event.getCurrentItem();
            if ((item != null) && (item.getType() == Material.ITEM_FRAME)) {
                event.setCancelled(true);
                p.sendMessage("§2[§bUnioCraft§2] §cBazı açıklardan dolayı fırlatıcıya eşya çerçevesi koyulması engellenmiştir!");
            }
        }
    }

    @EventHandler
    public void onDispense(BlockDispenseEvent event) {
        if (event.getBlock().getType() == Material.ITEM_FRAME) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------- //
    // Integrated from ClearLag
    // -------------------------------------------------- //

    @EventHandler
    public void onEntitySpawnTeaLimit(CreatureSpawnEvent e) {
        if ((e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.BREEDING)) || (e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.EGG)) || (e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.DISPENSE_EGG))) {
            if (entityBreedLimit(e.getEntity(), this.breedLimit)) {
                e.setCancelled(true);
            }
        } else if ((e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.NATURAL)) || (e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.NETHER_PORTAL))) {
            if (entityBreedLimit(e.getEntity(), this.naturalLimit)) {
                e.setCancelled(true);
            }
        } else if ((e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER))) {
            if (Main.plugin.getConfig().getBoolean("spawner-limit-enabled", true)) {
                if (entityBreedLimit(e.getEntity(), this.spawnerLimit)) {
                    e.setCancelled(true);
                }
            }
        } else if ((e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER_EGG)) &&
                (entityBreedLimit(e.getEntity(), this.spawnEggLimit))) {
            e.setCancelled(true);
        }
    }

    public boolean entityBreedLimit(Entity entity, int limit) {
        List<Entity> entityList = entity.getNearbyEntities(this.range, 255.0D, this.range);
        EntityType entityType = entity.getType();
        int count = 0;
        for (int c = 0; c < entityList.size(); c++) {
            if (((Entity) entityList.get(c)).getType() == entityType) {
                count++;
            }
        }
        if (count > limit) {
            return true;
        }
        return false;
    }

    // -------------------------------------------------- //
    // Integrated from EnderPearlCooldown
    // -------------------------------------------------- //

    @EventHandler
    public void onProjectileLaunchEPC(ProjectileLaunchEvent event) {
        // EnderPearlCooldown entegre edilmiş kod
        if (event.getEntityType() != EntityType.ENDER_PEARL) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity().getShooter();
        if (_initialized.containsKey(player.getName())) {
            long start = ((Long) _initialized.get(player.getName())).longValue();
            if (System.currentTimeMillis() - start >= this._cooldown) {
                _initialized.remove(player.getName());
            } else {
                event.setCancelled(true);
                return;
            }
        }
        _initialized.put(player.getName(), Long.valueOf(System.currentTimeMillis()));
    }

    @EventHandler
    public void onPlayerInteractEPC(PlayerInteractEvent event) {
        // EnderPearlCooldown entegre edilmiş kod
        if ((event.getAction() != Action.RIGHT_CLICK_AIR) && (event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getItem() == null) {
            return;
        }
        if (event.getItem().getType() != Material.ENDER_PEARL) {
            return;
        }
        if ((!ASkyBlockAPI.getInstance().playerIsOnIsland(player))) {
            if (!event.getPlayer().hasPermission("usb.mod.bypassteleport")) {
                event.setCancelled(true);
                player.updateInventory();
                player.sendMessage(Main.hataprefix + "Sadece kendi adanızda ender incisi kullanabilirsiniz!");
                return;
            }
        }
        if (_initialized.containsKey(player.getName())) {
            long start = ((Long) _initialized.get(player.getName())).longValue();
            if (System.currentTimeMillis() - start >= this._cooldown) {
                _initialized.remove(player.getName());
            } else {
                event.setCancelled(true);
                player.updateInventory();
                player.sendMessage(Main.hataprefix + "Ender incisi kullanmak için " + toDecimalsEPC(Math.abs(((Long) _initialized.get(player.getName())).longValue() + this._cooldown - System.currentTimeMillis()) / 1000.0D) + " saniye daha beklemelisin!");
                return;
            }
        }
    }

    // -------------------------------------------------- //
    // Integrated from Spawn-TP
    // -------------------------------------------------- //

    @EventHandler
    public void OyuncuYenidenDogunca(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Location spawn = Bukkit.getWorld("world").getSpawnLocation();
        spawn.setYaw((float) 90.37866);
        spawn.setPitch((float) 0.44991094);
        p.teleport(spawn);
    }

    // -------------------------------------------------- //
    // Integrated from UnioAclik
    // -------------------------------------------------- //

    @EventHandler
    public void AclikBugFix(FoodLevelChangeEvent event) {
        // Oyuncuların çok çabuk acıkmasını engeller
        if ((event.getEntity() instanceof Player)) {
            ((Player) event.getEntity()).setSaturation(((Player) event.getEntity()).getSaturation() + 3.0F);
        }
    }

    // -------------------------------------------------- //
    // Integrated from Mob Egg Spawner Blocker
    // -------------------------------------------------- //

    @EventHandler
    public void DisablemobEggSpawnerChange(PlayerInteractEvent event) {
        if ((event.getClickedBlock() != null) && (event.getItem() != null) &&
                (event.getClickedBlock().getType() == Material.MOB_SPAWNER) && (event.getItem().getType() == Material.MONSTER_EGG)) {
            event.setCancelled(true);
        }
    }

}
