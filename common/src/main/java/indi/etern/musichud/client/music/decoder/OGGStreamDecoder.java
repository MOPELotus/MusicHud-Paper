package indi.etern.musichud.client.music.decoder;

import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class OGGStreamDecoder implements AudioDecoder {
    private final ByteBuffer encodedAudioData;
    private final STBVorbisInfo info;
    private final long decoderHandle;
    private final int channels;
    private final int sampleRate;
    private final int format;

    public OGGStreamDecoder(BufferedInputStream inputStream) throws IOException {
        byte[] inputBytes = inputStream.readAllBytes();
        if (inputBytes.length == 0) {
            throw new IOException("Empty ogg stream");
        }

        encodedAudioData = MemoryUtil.memAlloc(inputBytes.length);
        encodedAudioData.put(inputBytes).flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errorBuffer = stack.mallocInt(1);
            decoderHandle = STBVorbis.stb_vorbis_open_memory(encodedAudioData, errorBuffer, null);
            if (decoderHandle == MemoryUtil.NULL) {
                MemoryUtil.memFree(encodedAudioData);
                throw new IOException("Failed to open ogg/vorbis stream, error code: " + errorBuffer.get(0));
            }
        }

        info = STBVorbisInfo.malloc();
        STBVorbis.stb_vorbis_get_info(decoderHandle, info);
        channels = info.channels();
        if (channels != 1 && channels != 2) {
            close();
            throw new IOException("Only mono/stereo ogg audio is supported");
        }

        sampleRate = info.sample_rate();
        format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
    }

    @Override
    public byte[] readChunk(long maxSize) {
        int maxSamples = (int) Math.max(channels * 1024L, Math.min(Integer.MAX_VALUE, maxSize / 2L));
        maxSamples -= maxSamples % channels;
        if (maxSamples <= 0) {
            maxSamples = channels * 1024;
        }

        ShortBuffer pcmBuffer = MemoryUtil.memAllocShort(maxSamples);
        try {
            int samplesPerChannel = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoderHandle, channels, pcmBuffer);
            if (samplesPerChannel <= 0) {
                return null;
            }
            int totalSamples = samplesPerChannel * channels;
            byte[] result = new byte[totalSamples * 2];
            ByteBuffer byteBuffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < totalSamples; i++) {
                byteBuffer.putShort(pcmBuffer.get(i));
            }
            return result;
        } finally {
            MemoryUtil.memFree(pcmBuffer);
        }
    }

    @Override
    public int getFormat() {
        return format;
    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    @Override
    public void close() {
        if (decoderHandle != MemoryUtil.NULL) {
            STBVorbis.stb_vorbis_close(decoderHandle);
        }
        if (info != null) {
            info.free();
        }
        if (encodedAudioData != null) {
            MemoryUtil.memFree(encodedAudioData);
        }
    }
}
