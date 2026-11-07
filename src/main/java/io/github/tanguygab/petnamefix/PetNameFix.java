package io.github.tanguygab.petnamefix;

import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetNameFix extends JavaPlugin implements Listener {

    private PipelineInjector pipeline;

    @Override
    public void onEnable() {
        pipeline = new PipelineInjector();
        getServer().getOnlinePlayers().forEach(pipeline::inject);
        getServer().getPluginManager().registerEvents(this,this);
    }

    @Override
    public void onDisable() {
        if (pipeline != null) pipeline.unload();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        pipeline.inject(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Tameable pet)) return;
        if (pet.isTamed() && e.getPlayer().getUniqueId().equals(pet.getOwnerUniqueId())
                && e.getHand() == EquipmentSlot.OFF_HAND)
            e.setCancelled(true);
    }

}
