package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import de.lolhens.minecraft.fluidphysics.util.FluidSourceFinder;
import net.minecraft.block.*;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Option;

import java.util.HashSet;
import java.util.Set;

@Mixin(PistonBlock.class)
public abstract class PistonBlockMixin {
    @Inject(at = @At("HEAD"), method = "isMovable", cancellable = true)
    private static void isMovable(BlockState state,
                                  World world,
                                  BlockPos pos,
                                  Direction motionDir,
                                  boolean canBreak,
                                  Direction pistonDir,
                                  CallbackInfoReturnable<Boolean> info) {
        BlockPos prevBlockPos = pos.offset(motionDir.getOpposite());

        FluidState prevBlockFluidState = world.getFluidState(prevBlockPos);
        if (!prevBlockFluidState.isEmpty() &&
                FluidPhysicsMod.config().enabledFor(prevBlockFluidState.getFluid()) &&
                prevBlockFluidState.isStill() &&
                state.getFluidState().isEmpty()) {
            info.setReturnValue(false);
        }
    }

    @Inject(at = @At("HEAD"), method = "move", cancellable = true)
    private void move(World world,
                      BlockPos pos,
                      Direction dir,
                      boolean retract,
                      CallbackInfoReturnable<Boolean> info) {
        BlockPos blockPos = pos.offset(dir);
        if (!retract && world.getBlockState(blockPos).isOf(Blocks.PISTON_HEAD)) {
            world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), 20);
        }

        PistonHandler pistonHandler = new PistonHandler(world, pos, dir, retract);
        if (!pistonHandler.calculatePush()) {
            info.setReturnValue(false);
        } else {
            Direction oppositeDir = dir.getOpposite();

            Set<BlockPos> blockPosSet = new HashSet<>();
            blockPosSet.add(blockPos);
            for (BlockPos movedBlockPos : pistonHandler.getMovedBlocks()) {
                blockPosSet.add(movedBlockPos);
                blockPosSet.add(movedBlockPos.offset(dir));
            }

            for (BlockPos currentBlockPos : blockPosSet) {
                BlockState blockState = world.getBlockState(currentBlockPos);
                FluidState fluidState = blockState.getFluidState();

                if (!fluidState.isEmpty() &&
                        FluidPhysicsMod.config().enabledFor(fluidState.getFluid()) &&
                        fluidState.getFluid() instanceof FlowableFluid && !fluidState.isStill()) {
                    FlowableFluid fluid = (FlowableFluid) fluidState.getFluid();

                    Option<BlockPos> sourcePos = FluidSourceFinder.findSource(
                            world,
                            currentBlockPos,
                            fluidState.getFluid(),
                            oppositeDir,
                            FluidSourceFinder.setOf(blockPosSet),
                            true,
                            true
                    );

                    if (sourcePos.isDefined()) {
                        FluidState still = fluid.getStill(false);
                        int newSourceLevel = still.getLevel() - 1;
                        FluidState newSourceFluidState = fluid.getFlowing(newSourceLevel, false);

                        BlockState sourceState = world.getBlockState(sourcePos.get());

                        // Drain source block
                        if (sourceState.getBlock() instanceof FluidDrainable && !(sourceState.getBlock() instanceof FluidBlock)) {
                            ((FluidDrainable) sourceState.getBlock()).tryDrainFluid(world, sourcePos.get(), sourceState);
                        } else {
                            if (!sourceState.isAir()) {
                                ((FlowableFluidAccessor) fluid).callBeforeBreakingBlock(world, sourcePos.get(), sourceState);
                            }

                            world.setBlockState(sourcePos.get(), newSourceFluidState.getBlockState(), 3);
                        }

                        // Flow source block to new position
                        if (fluidState.getBlockState().getBlock() instanceof FluidFillable) {
                            ((FluidFillable) blockState.getBlock()).tryFillWithFluid(world, currentBlockPos, blockState, still);
                        } else {
                            if (!blockState.isAir()) {
                                ((FlowableFluidAccessor) fluid).callBeforeBreakingBlock(world, currentBlockPos, blockState);
                            }

                            world.setBlockState(currentBlockPos, still.getBlockState(), 3);
                        }
                    }
                }
            }
        }
    }
}
