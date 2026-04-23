package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.util.SpringBlockFeature;
import net.minecraft.world.gen.feature.SpringFeature;
import net.minecraft.world.gen.feature.SpringFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpringFeature.class)
public class SpringFeatureMixin {
    @Inject(at = @At("RETURN"), method = "generate", cancellable = true)
    public void generate(FeatureContext<SpringFeatureConfig> featureContext,
                         CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValue()) {
            SpringBlockFeature.generate(featureContext.getWorld(), featureContext.getOrigin(), featureContext.getConfig());
        }
    }
}
