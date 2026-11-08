package dev.mclite.island.mc26;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 轻量房间声学采样：不用 Sound Physics 的射线追踪，直接摸玩家周围 11x7x11 的方块，
 * 按材质估一个吸声系数和包裹度，喂给 JavaSound 的混响。
 */
final class RoomAcoustics26 {

    record Result(float enclosure, float absorption) {
    }

    private RoomAcoustics26() {
    }

    static Result measure(ClientLevel level, Player player) {
        BlockPos center = player.blockPosition();
        int solid = 0;
        int cells = 0;
        float sum = 0f;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    cells++;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    solid++;
                    sum += absorption(state);
                }
            }
        }
        float enclosure = cells == 0 ? 0f : Math.min(1f, solid / (float) cells * 1.35f);
        float absorption = solid == 0 ? 0.5f : sum / solid;
        return new Result(enclosure, Math.max(0.05f, Math.min(0.95f, absorption)));
    }

    private static float absorption(BlockState state) {
        String name = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
        if (name.contains("wool") || name.contains("carpet") || name.contains("leaves")
                || name.contains("moss") || name.contains("grass") || name.contains("hay")
                || name.contains("cloth") || name.contains("bed")) {
            return 0.88f;
        }
        if (name.contains("planks") || name.contains("log") || name.contains("wood")
                || name.contains("bamboo") || name.contains("bookshelf")) {
            return 0.58f;
        }
        if (name.contains("glass") || name.contains("stone") || name.contains("deepslate")
                || name.contains("ore") || name.contains("metal") || name.contains("iron")
                || name.contains("copper") || name.contains("tile") || name.contains("brick")) {
            return 0.22f;
        }
        if (name.contains("water") || name.contains("ice") || name.contains("snow")) {
            return 0.35f;
        }
        return 0.48f;
    }
}
