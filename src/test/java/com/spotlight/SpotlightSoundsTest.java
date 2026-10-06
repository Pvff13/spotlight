package com.spotlight;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;

public class SpotlightSoundsTest
{
	// 44.1kHz 16-bit stereo = 176400 bytes/sec
	private static ByteArrayInputStream wav(int dataBytes, boolean extraChunk)
	{
		ByteBuffer b = ByteBuffer.allocate(64 + (extraChunk ? 14 : 0) + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes()).putInt(0).put("WAVE".getBytes());
		b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(44100).putInt(176400).putShort((short) 4).putShort((short) 16);
		if (extraChunk)
		{
			// odd-sized chunk to check the pad byte is skipped
			b.put("LIST".getBytes()).putInt(5).put(new byte[6]);
		}
		b.put("data".getBytes()).putInt(dataBytes).put(new byte[dataBytes]);
		return new ByteArrayInputStream(b.array(), 0, b.position());
	}

	@Test
	public void readsDurationFromHeader()
	{
		assertEquals(1500, SpotlightSounds.readWavDurationMs(wav(264600, false)));
		assertEquals(500, SpotlightSounds.readWavDurationMs(wav(88200, true)));
	}

	@Test
	public void fallsBackForNonWav()
	{
		assertEquals(2000, SpotlightSounds.readWavDurationMs(new ByteArrayInputStream("not a wav".getBytes())));
	}
}
