package ch.hflet.BuildMode;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.*;

import java.time.Instant;
import java.util.*;

public class EventListener implements Listener {
    private BuildMode plugin;
    private PlayerRegistry registry;
    private List<Player> rateLimitedPlayers = new ArrayList<Player>();
    private Server server;
    private boolean isSetup = false;
    private Timer timer;

    static float deductionModifier = 0.00035F;

    private float deductInstantBreak;
    private float deductFlying;
    private float deductMovingFlyingEnabled;

    EventListener(BuildMode plugin) {
        this.plugin = plugin;
        server = Bukkit.getServer();
        registry = plugin.getRegistry();
        timer = new Timer();
    }

    void setup() {
        FileConfiguration config = plugin.getConfig();

        deductInstantBreak = (float) config.getDouble("deductions.instant_breaking") * deductionModifier;
        deductFlying = (float) config.getDouble("deductions.flying") * deductionModifier;
        deductMovingFlyingEnabled = (float) config.getDouble("deductions.moving_while_flight_enabled") * deductionModifier;

        plugin.getLogger().info("Deduction for Instabreak: " + deductInstantBreak);
        plugin.getLogger().info("Deduction for flying: " + deductFlying);
        plugin.getLogger().info("Deduction for moving in flight: " + deductMovingFlyingEnabled);

        isSetup = true;
    }

    void reset() {
        rateLimitedPlayers.clear();
        rateLimitedPlayers = null;
        timer.cancel();
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();

        if(!registry.hasPlayer(player))
            return;

        if(event.getBlock().getType() == Material.BEDROCK) {
            player.sendMessage(ChatColor.YELLOW + "no");
            event.setCancelled(true);
            return;
        }

        try {
            plugin.deductExp(deductInstantBreak, player);
        } catch(Error err) {
            player.sendMessage(ChatColor.RED + "You don't have enough XP to insta-break this!");
            event.setInstaBreak(false);
            return;
        }

        Location location = event.getBlock().getLocation();

//        server.dispatchCommand(player, "setblock " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ() + " air destroy");

        while(!rateLimitedPlayers.contains(player)) {
            rateLimitedPlayers.add(player);

            timer.schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            rateLimitedPlayers.remove(player);
                        }
                    },
                    100
            );

            event.setInstaBreak(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();



//        if(player.getFallDistance()) player.teleport(plugin.getSafeLocation(player));
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if(registry.hasPlayer(player))
            registry.removePlayer(player);
    }

    @EventHandler
    public void onExpGain(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();

        if(!registry.hasPlayer(player)) return;

        if(!player.getAllowFlight() && event.getAmount() >= deductFlying) {
            player.setAllowFlight(true);
            player.sendMessage(ChatColor.GREEN + "You can now fly!");
        }
    }

    @EventHandler
    public void onMoving(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if(!registry.hasPlayer(player)) return;

        if(
                event.getFrom().getBlockX() == event.getTo().getBlockX()
                        && event.getFrom().getBlockY() == event.getTo().getBlockY()
                        && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        if(player.getAllowFlight() && !player.isFlying()) {
            try {
                plugin.deductExp(deductMovingFlyingEnabled, player);
            } catch(Error err) {
                player.sendMessage(ChatColor.RED + "You don't have enough XP to keep flight enabled!");

                player.setAllowFlight(false);
            }
        }

        if(player.isFlying())
            try {
                plugin.deductExp(deductFlying, player);
            } catch(Error err) {
                player.sendMessage(ChatColor.RED + "You don't have enough XP to cover this action!");

                if(plugin.getSafeLocation(player) != null)
                    player.teleport(plugin.getSafeLocation(player));

                player.setFlying(false);
            }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();

        if(event.getNewGameMode() == GameMode.CREATIVE && registry.hasPlayer(player)) {
            registry.removePlayer(player);
        }
    }
}
