package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.util.SpringBlockFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Yarn: generate -> Mojmap: place
// Yarn: SpringFeatureConfig -> Mojmap: SpringConfiguration
// Yarn: FeatureContext -> Mojmap: FeaturePlaceContext
@Mixin(SpringFeature.class)
public class SpringFeatureMixin {
    @Inject(at = @At("RETURN"), method = "place", cancellable = true)
    public void fluidphysics$place(FeaturePlaceContext<SpringConfiguration> ctx,
                                   CallbackInfoReturnable<Boolean> info) {
        if (Boolean.TRUE.equals(info.getReturnValue())) {
            SpringBlockFeature.generate(ctx.level(), ctx.origin(), ctx.config());
        }
    }
}
