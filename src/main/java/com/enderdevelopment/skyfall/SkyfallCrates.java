package com.enderdevelopment.skyfall;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class SkyfallCrates implements ModInitializer {
    public static final String MOD_ID = "skyfall_crates";
    private static final Block CRATE = new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.LAPIS_BLOCK).strength(2.0f));
    private static final Map<String, Long> LAST_DAY = new HashMap<>();
    private static final Map<String, BlockPos> ACTIVE_CRATES = new HashMap<>();
    private static final Random RANDOM = new Random();

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "supply_crate"), CRATE);
        Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "supply_crate"), new BlockItem(CRATE, new Item.Properties()));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            if (state.is(CRATE) && level instanceof ServerLevel serverLevel) {
                int tier = (int) Math.max(1, serverLevel.getLevelData().getGameTime() / 120000L);
                dropLoot(serverLevel, pos, tier);
                ACTIVE_CRATES.remove(serverLevel.dimension().toString() + ":" + pos.asLong());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SkyfallCrates::tick);
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long day = level.getLevelData().getGameTime() / 24000L;
        String worldKey = level.dimension().toString();
        if (day > 0 && day % 5 == 0 && LAST_DAY.getOrDefault(worldKey, -1L) != day) {
            LAST_DAY.put(worldKey, day);
            spawnCrate(server, level);
        }
        ACTIVE_CRATES.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(worldKey + ":")) return false;
            BlockPos pos = entry.getValue();
            if (!level.getBlockState(pos).is(CRATE)) return true;
            if (level.getGameTime() % 8 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 8, 0.18, 0.45, 0.18, 0.015);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 2, 0.08, 0.3, 0.08, 0.01);
            }
            return false;
        });
    }

    private static void spawnCrate(MinecraftServer server, ServerLevel level) {
        int x = RANDOM.nextInt(801) - 400;
        int z = RANDOM.nextInt(801) - 400;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos).canBeReplaced()) pos = pos.above();
        level.setBlock(pos, CRATE.defaultBlockState(), Block.UPDATE_ALL);
        ACTIVE_CRATES.put(level.dimension().toString() + ":" + pos.asLong(), pos);
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Skyfall] Supply crate incoming at " + x + ", " + pos.getY() + ", " + z + "!"), false);
    }

    private static void dropLoot(ServerLevel level, BlockPos pos, int tier) {
        Item[] weapons = {Items.IRON_SWORD, Items.BOW, Items.CROSSBOW, Items.TRIDENT};
        int weaponCount = Math.min(4, 1 + tier / 2);
        for (int i = 0; i < weaponCount; i++) {
            Item item = weapons[RANDOM.nextInt(Math.min(weapons.length, 1 + tier))];
            Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(item));
        }
        Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(Items.GOLDEN_APPLE, Math.min(1 + tier, 4)));
        if (tier >= 3) Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(Items.DIAMOND, Math.min(tier - 1, 5)));
        if (tier >= 5) Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(Items.NETHERITE_SCRAP, Math.min(tier / 5, 2)));
    }
}
