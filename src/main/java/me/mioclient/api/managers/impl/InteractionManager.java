package me.mioclient.api.managers.impl;

import me.mioclient.api.managers.Managers;
import me.mioclient.api.util.interact.BlockUtil;
import me.mioclient.api.util.math.Timer;
import me.mioclient.mod.Mod;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Game world interactions management.
 * @author t.me/asphyxia1337
 */

public class InteractionManager extends Mod {

    private final Timer attackTimer = new Timer();

    //Block placements

    public void placeBlock(BlockPos pos, boolean rotate, boolean packet, boolean attackCrystal, boolean ignoreEntities) {

        if (fullNullCheck()) return;

        if (BlockUtil.canReplace(pos)) {

            if (attackCrystal) {
                attackCrystals(pos, rotate);
            }

            Optional<ClickLocation> posCL = getClickLocation(pos, ignoreEntities, false, attackCrystal);

            if (posCL.isPresent()) {

                BlockPos currentPos = posCL.get().neighbour;
                EnumFacing currentFace = posCL.get().opposite;

                boolean shouldSneak = shouldShiftClick(currentPos);

                if (shouldSneak) {
                    mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING));
                }

                Vec3d hitVec = new Vec3d(currentPos)
                        .add(0.5, 0.5, 0.5)
                        .add(new Vec3d(currentFace.getDirectionVec()).scale(0.5));

                if (rotate) {
                    Managers.ROTATIONS.lookAtVec3dPacket(hitVec, false, true);
                }

                if (packet) {
                    Vec3d extendedVec = new Vec3d(currentPos)
                            .add(0.5, 0.5, 0.5);

                    float x = (float) (extendedVec.x - currentPos.getX());
                    float y = (float) (extendedVec.y - currentPos.getY());
                    float z = (float) (extendedVec.z - currentPos.getZ());

                    mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(currentPos, currentFace, EnumHand.MAIN_HAND, x, y, z));

                } else {
                    mc.playerController.processRightClickBlock(mc.player, mc.world, currentPos, currentFace, hitVec, EnumHand.MAIN_HAND);
                }

                mc.player.connection.sendPacket(new CPacketAnimation(EnumHand.MAIN_HAND));

                if (shouldSneak) {
                    mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING));
                }
            }
        }
    }

    public void placeBlock(BlockPos pos, boolean rotate, boolean packet, boolean attackCrystal) {
        placeBlock(pos, rotate, packet, attackCrystal, false);
    }

    //Entity interactions

    public void attackEntity(Entity entity, boolean packet, boolean swing) {

        if (packet) {
            mc.player.connection.sendPacket(new CPacketUseEntity(entity));
        } else {
            mc.playerController.attackEntity(mc.player, entity);
        }

        if (swing) {
            mc.player.swingArm(EnumHand.MAIN_HAND);
        }
    }

    public void attackCrystals(BlockPos pos, boolean rotate) {

        boolean sprint = mc.player.isSprinting();

        int ping = Managers.SERVER.getPing();

        for (EntityEnderCrystal crystal : mc.world.getEntitiesWithinAABB(EntityEnderCrystal.class, new AxisAlignedBB(pos))) {

            if (attackTimer.passedMs(ping <= 50 ? 75 : 100)) {

                if (rotate) {
                    Managers.ROTATIONS.lookAtVec3dPacket(crystal.getPositionVector(), false, true);
                }

                if (sprint) {
                    mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SPRINTING));
                }

                mc.player.connection.sendPacket(new CPacketUseEntity(crystal));
                mc.player.connection.sendPacket(new CPacketAnimation(EnumHand.MAIN_HAND));

                if (sprint) {
                    mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SPRINTING));
                }

                attackTimer.reset();
                break;
            }
        }
    }
    
    //Getters & variable methods

    public static class ClickLocation {
        public final BlockPos neighbour;
        public final EnumFacing opposite;

        public ClickLocation(BlockPos neighbour, EnumFacing opposite) {
            this.neighbour = neighbour;
            this.opposite = opposite;
        }
    }

    public Optional<ClickLocation> getClickLocation(BlockPos pos, boolean ignoreEntities, boolean noPistons, boolean onlyCrystals) {
        Block block = mc.world.getBlockState(pos).getBlock();

        if (!(block instanceof BlockAir) && !(block instanceof BlockLiquid)) {
            return Optional.empty();
        }

        if (!ignoreEntities) {
            for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(pos))) {
                if (onlyCrystals && entity instanceof EntityEnderCrystal) continue;
                if (!(entity instanceof EntityItem) && !(entity instanceof EntityXPOrb) && !(entity instanceof EntityArrow)) {
                    return Optional.empty();
                }
            }
        }

        EnumFacing side = null;

        for (EnumFacing blockSide : EnumFacing.values()) {
            BlockPos sidePos = pos.offset(blockSide);
            if (noPistons) {
                if (mc.world.getBlockState(sidePos).getBlock() == Blocks.PISTON) continue;
            }
            if (!mc.world.getBlockState(sidePos).getBlock().canCollideCheck(mc.world.getBlockState(sidePos), false)) {
                continue;
            }
            IBlockState blockState = mc.world.getBlockState(sidePos);
            if (!blockState.getMaterial().isReplaceable()) {
                side = blockSide;
                break;
            }
        }
        if (side == null) {
            return Optional.empty();
        }

        BlockPos neighbour = pos.offset(side);
        EnumFacing opposite = side.getOpposite();
        if (!mc.world.getBlockState(neighbour).getBlock().canCollideCheck(mc.world.getBlockState(neighbour), false)) {
            return Optional.empty();
        }

        return Optional.of(new ClickLocation(neighbour, opposite));
    }

    public boolean shouldShiftClick(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();

        TileEntity tileEntity = null;

        for (TileEntity entity : mc.world.loadedTileEntityList) {
            if (!entity.getPos().equals(pos)) continue;
            tileEntity = entity;
            break;
        }
        return tileEntity != null || block instanceof BlockBed || block instanceof BlockContainer || block instanceof BlockDoor || block instanceof BlockTrapDoor || block instanceof BlockFenceGate || block instanceof BlockButton || block instanceof BlockAnvil || block instanceof BlockWorkbench || block instanceof BlockCake || block instanceof BlockRedstoneDiode;
    }
}
