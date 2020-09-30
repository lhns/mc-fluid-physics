package de.lolhens.minecraft.fluidphysics.mixin;

import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkManager.class)
public interface ThreadedAnvilChunkStorageAccessor {
    @Invoker
    Iterable<ChunkHolder> callGetLoadedChunksIterable();
}
