package com.spotlight;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;

public class SpotlightSoundsTest
{
	// 44.1kHz 16-bit stereo = 176400 bytes/sec
	private static File writeWav(int dataBytes, boolean extraChunk) throws IOException
	{
		File f = File.createTempFile("spotlight", ".wav");
		f.deleteOnExit();
		ByteBuffer b = ByteBuffer.allocate(64 + (extraChunk ? 14 : 0) + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes()).putInt(0).put("WAVE".getBytes());
		b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(44100).putInt(176400).putShort((short) 4).putShort((short) 16);
		if (extraChunk)
		{
			// odd-sized chunk to check the pad byte is skipped
			b.put("LIST".getBytes()).putInt(5).put(new byte[6]);
		}
		b.put("data".getBytes()).putInt(dataBytes).put(new byte[dataBytes]);
		try (FileOutputStream out = new FileOutputStream(f))
		{
			out.write(b.array(), 0, b.position());
		}
		return f;
	}

	@Test
	public void readsDurationFromHeader() throws IOException
	{
		assertEquals(1500, SpotlightSounds.readWavDurationMs(writeWav(264600, false)));
		assertEquals(500, SpotlightSounds.readWavDurationMs(writeWav(88200, true)));
	}

	@Test
	public void fallsBackForNonWav() throws IOException
	{
		File f = File.createTempFile("spotlight", ".wav");
		f.deleteOnExit();
		try (FileOutputStream out = new FileOutputStream(f))
		{
			out.write("not a wav".getBytes());
		}
		assertEquals(2000, SpotlightSounds.readWavDurationMs(f));
	}
}
