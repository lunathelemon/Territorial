package io.github.lunathelemon.territorial.block.entity;

import io.github.lunathelemon.territorial.Territorial;
import io.github.lunathelemon.territorial.api.component.BoundBlockEntity;
import io.github.lunathelemon.territorial.api.component.BoundBlockEntityParams;
import io.github.lunathelemon.territorial.api.component.IPeekingEyeComponent;
import io.github.lunathelemon.territorial.block.TerritorialBlocks;
import io.github.lunathelemon.territorial.component.TerritorialComponents;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EnderEyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlinthOfPeekingBlockEntity extends BlockEntity implements BoundBlockEntity {

    private static final int[] reachMultipliers = { 1, 3, 8, 16, 27 };
    private static final Set<Item> acceptedPodiumItems = Set.of(Items.ENDER_EYE);

    private int level;
    private Item podiumItem;
    private final HashSet<UUID> boundPlayerUuids;

    public PlinthOfPeekingBlockEntity(BlockPos pos, BlockState state) {
        super(TerritorialBlockEntities.PLINTH_OF_PEEKING_BLOCK_ENTITY_TYPE, pos, state);
        boundPlayerUuids = new HashSet<>();
        podiumItem = Items.ENDER_EYE;
    }

    public static void tick(World world, BlockPos pos, BlockState state, PlinthOfPeekingBlockEntity be) {
        if(world.getTime() % 80 == 0) {
            int newLevel = be.updateLevel(world, pos);

            // Level has changed
            if(newLevel != be.level) {
                world.setBlockState(pos, state.with(Properties.ENABLED, newLevel > 0));
                be.getBoundPlayerComponents().forEach(peekingComponent -> peekingComponent.rebind(be));
            }
            be.level = newLevel;
        }
    }

    private int updateLevel(World world, BlockPos pos) {
        int level = 0;
        final int bottomY = world.getBottomY();
        int y, xPos, zPos;

        // Decrement the y value to check the blocks below
        for(int yInc = 1; yInc <= 4; level = yInc++) {
            y = pos.getY() - yInc;
            // If we reach the bottom of the world exist early
            if(y < bottomY)
                break;
            else {
                xPos = pos.getX();
                zPos = pos.getZ();
                // Check each slice of blocks below in the same way a beacon would
                for(int x = xPos - yInc; x <= xPos + yInc; ++x) {
                    for(int z = zPos - yInc; z <= zPos + yInc; ++z) {
                        // Return the current level if a new block cannot be found
                        if(!world.getBlockState(new BlockPos(x, y, z)).getBlock()
                                .equals(TerritorialBlocks.OMNISCIENT_OBSIDIAN)) {
                             return level;
                        }
                    }
                }
            }
        }
        return level;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("level", level);
        nbt.putString("podiumItem", Registries.ITEM.getId(podiumItem).toString());

        var uuidList = new NbtList();
        for(var boundUuid : boundPlayerUuids)
            uuidList.add(NbtHelper.fromUuid(boundUuid));
        nbt.put("boundPlayers", uuidList);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if(nbt.contains("level"))
            level = nbt.getInt("level");
        if(nbt.contains("podiumItem"))
            this.podiumItem = Registries.ITEM.get(new Identifier(nbt.getString("podiumItem")));
        if(nbt.contains("boundPlayers")) {
            NbtList boundPlayersNbtList = nbt.getList("boundPlayers", NbtElement.INT_ARRAY_TYPE);
            for(var boundUuidIntArray : boundPlayersNbtList)
                boundPlayerUuids.add(NbtHelper.toUuid(boundUuidIntArray));
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    public Item getPodiumEye() { return podiumItem; }
    public void setPodiumEye(Item podiumItem) { this.podiumItem = podiumItem; }
    public int getLevel() { return level; }

    @Override
    public void addBoundEntity(Entity boundEntity) {
        if(boundEntity instanceof PlayerEntity playerEntity)
            boundPlayerUuids.add(playerEntity.getUuid());
    }

    @Override
    public void removeBoundEntity(Entity boundEntity) {
        if(boundEntity instanceof PlayerEntity playerEntity)
            boundPlayerUuids.remove(playerEntity.getUuid());
    }

    @Override
    public void onBlockDestroyed() {
        getBoundPlayerComponents().forEach(IPeekingEyeComponent::stopPeeking);
    }

    @Override
    public BoundBlockEntityParams getParams() {
        if(world != null)
            return new BoundBlockEntityParams(world.getDimensionKey(), pos,
                    Territorial.getConfig().getPlinthOfPeekingMinReach() * reachMultipliers[level]);
        else return null;
    }

    private List<IPeekingEyeComponent> getBoundPlayerComponents() {
        List<IPeekingEyeComponent> components = new ArrayList<>();

        if(world != null) {
            for(var boundPlayerUuid : boundPlayerUuids) {
                var boundPlayer = world.getPlayerByUuid(boundPlayerUuid);
                if(boundPlayer != null)
                    components.add(boundPlayer.getComponent(TerritorialComponents.PEEKING_EYE));
            }
        }
        return components;
    }
}
