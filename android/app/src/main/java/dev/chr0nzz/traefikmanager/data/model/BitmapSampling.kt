package dev.chr0nzz.traefikmanager.data.model

object BitmapSampling {
    fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        val longest = maxOf(width, height)
        if (longest <= 0 || targetPx <= 0) return 1
        var sample = 1
        while (longest / (sample * 2) >= targetPx) {
            sample *= 2
        }
        return sample
    }
}
