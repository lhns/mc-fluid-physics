package de.lolhens.minecraft.fluidphysics.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// 1.21.1 rename: ThreadedAnvilChunkStorage -> ChunkMap. Class name of the accessor is kept for
// source/binary stability with existing Scala code referencing ThreadedAnvilChunkStorageAccessor.
@Mixin(ChunkMap.class)
public interface ThreadedAnvilChunkStorageAccessor {
    // Yarn: callEntryIterator (method name entryIterator) -> Mojmap: getChunks (protected)
    @Invoker("getChunks")
    Iterable<ChunkHolder> callGetChunks();
}
