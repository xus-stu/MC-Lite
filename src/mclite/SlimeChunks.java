package mclite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 史莱姆区块：按世界种子算，和版本无关的生物群系无关。
 *
 * <p>Java 版 1.13+ 的实现：{@code RandomSampler.seedSlimeChunk} 用
 * {@code seed + x²*4987142 + x*5947611 + z²*4392871 + z*389711 ^ 987234911}
 * 播种，再 {@code nextInt(10) == 0}。这里逐位复刻，long 溢出行为与游戏一致。</p>
 */
final class SlimeChunks {

    private static final long SALT = 987234911L;

    private SlimeChunks() {
    }

    record Spot(int chunkX, int chunkZ) {
        int x() {
            return chunkX * 16 + 8;
        }

        int z() {
            return chunkZ * 16 + 8;
        }
    }

    static boolean isSlime(long seed, int chunkX, int chunkZ) {
        long mixed = seed
                + (long) chunkX * chunkX * 4987142L
                + (long) chunkX * 5947611L
                + (long) chunkZ * chunkZ * 4392871L
                + (long) chunkZ * 389711L
                ^ SALT;
        return new Random(mixed).nextInt(10) == 0;
    }

    /** 以 centerChunk 为中心、radius 个区块为半径，取最近的 limit 个史莱姆区块。 */
    static List<Spot> nearest(long seed, int centerChunkX, int centerChunkZ, int radius, int limit) {
        List<Spot> spots = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (isSlime(seed, chunkX, chunkZ)) {
                    spots.add(new Spot(chunkX, chunkZ));
                }
            }
        }
        spots.sort(Comparator
                .comparingLong((Spot spot) -> (long) (spot.chunkX() - centerChunkX)
                        * (spot.chunkX() - centerChunkX)
                        + (long) (spot.chunkZ() - centerChunkZ) * (spot.chunkZ() - centerChunkZ))
                .thenComparingInt(Spot::chunkX)
                .thenComparingInt(Spot::chunkZ));
        return spots.size() > limit ? new ArrayList<>(spots.subList(0, limit)) : spots;
    }
}
