package dev.mclite.island.core;

import java.nio.file.Path;

/** 在线曲目解析：返回可直接交给 JavaSoundEngine 的本地 WAV。 */
public interface RemoteAudioResolver {
    Path resolve(Track track) throws Exception;
}
