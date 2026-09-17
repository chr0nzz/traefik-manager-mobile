package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.BitmapSampling
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapSamplingTest {

    @Test
    fun `a widget icon is decoded no smaller than the tile it fills`() {
        assertEquals(1, BitmapSampling.sampleSize(96, 96, 132))
        assertEquals(1, BitmapSampling.sampleSize(192, 192, 132))
        assertEquals(2, BitmapSampling.sampleSize(512, 512, 132))
        assertEquals(4, BitmapSampling.sampleSize(1024, 512, 132))
    }

    @Test
    fun `nothing is sampled away when the size is unknown`() {
        assertEquals(1, BitmapSampling.sampleSize(0, 0, 132))
        assertEquals(1, BitmapSampling.sampleSize(512, 512, 0))
        assertEquals(1, BitmapSampling.sampleSize(-1, -1, 132))
    }
}
