package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.util.SpringBlockFeature;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.feature.SpringFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(SpringFeature.class)
public class SpringFeatureMixin {
    @Inject(at = @At("RETURN"), method = "place", cancellable = true)
    public void place(IWorld worldIn,
                      ChunkGenerator<? extends GenerationSettings> generator,
                      Random random,
                      BlockPos blockPos,
                      LiquidsConfig springFeatureConfig,
                      CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValue()) {
            SpringBlockFeature.generate(worldIn, blockPos, springFeatureConfig);
        }
    }
}
