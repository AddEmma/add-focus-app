package com.addemma.focuslock

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Layer 2: On-Device Visual AI Scanner
 * Combines Google ML Kit Image Labeling (Bikini, Brassiere, Underwear, Lingerie, Swimsuit,
 * Cleavage, Barechested, Abdomen, Navel, Thigh, Flesh) + Face-Anchored Torso Skin Exposure
 * analysis across all skin tones (Fitzpatrick I-VI) + Quadrant Grid Tile scanning for
 * social media profile grids (Instagram, TikTok, Browsers).
 */
object NsfwVisualScanner {
    private const val TAG = "NsfwVisualScanner"

    private val isScanning = AtomicBoolean(false)

    // ML Kit Image Labeler with low confidence threshold to catch smaller grid thumbnails
    private val imageLabeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.42f)
            .build()
        ImageLabeling.getClient(options)
    }

    // ML Kit Fast Face Detector to anchor chest/torso skin exposure directly below faces
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.06f)
            .build()
        FaceDetection.getClient(options)
    }

    // Direct clothing/intimate labels that indicate bikini / lingerie / underwear / half-naked content
    private val INTIMATE_ATTIRE_LABELS = setOf(
        "swimsuit", "bikini", "brassiere", "bra", "underwear", "lingerie",
        "undergarment", "active undergarment", "briefs", "swim brief",
        "maillot", "corset", "negligee", "thong", "bathers", "swimwear"
    )

    // Body exposure labels emitted by ML Kit when torso/chest/thighs are exposed
    private val BODY_EXPOSURE_LABELS = setOf(
        "cleavage", "barechested", "navel", "abdomen", "thigh",
        "chest", "trunk", "flesh", "stomach", "waist", "hip", "muscle"
    )

    /**
     * Analyzes a captured screen Bitmap asynchronously and invokes [onResult] if NSFW
     * or sexy/half-naked visual content is detected.
     */
    fun analyzeScreenBitmap(
        rawBitmap: Bitmap,
        blockSexyContent: Boolean,
        onResult: (NsfwDetectionResult?) -> Unit
    ) {
        if (!isScanning.compareAndSet(false, true)) {
            rawBitmap.recycleSafely()
            onResult(null)
            return
        }

        try {
            // Convert HARDWARE bitmap to software ARGB_8888 scaled down for fast (<60ms) analysis
            val softwareBitmap = ensureSoftwareBitmap(rawBitmap)
            if (softwareBitmap == null) {
                isScanning.set(false)
                onResult(null)
                return
            }

            // Crop out top status/URL bar (top 10%) and bottom nav bar (bottom 8%)
            val cropTop = (softwareBitmap.height * 0.10f).toInt()
            val cropBottom = (softwareBitmap.height * 0.92f).toInt()
            val cropHeight = max(100, cropBottom - cropTop)
            val viewportBitmap = Bitmap.createBitmap(
                softwareBitmap,
                0,
                cropTop,
                softwareBitmap.width,
                min(cropHeight, softwareBitmap.height - cropTop)
            )
            if (viewportBitmap != softwareBitmap) {
                softwareBitmap.recycleSafely()
            }

            // 1. Run pixel-level multi-tone torso & grid tile skin analysis synchronously (takes ~8ms)
            val gridExposure = evaluateGridSkinExposure(viewportBitmap)

            // 2. Run ML Kit Image Labeling + Face Detection on the viewport
            val inputImage = InputImage.fromBitmap(viewportBitmap, 0)

            imageLabeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    faceDetector.process(inputImage)
                        .addOnSuccessListener { faces ->
                            try {
                                val decision = combineVisualSignals(
                                    bitmap = viewportBitmap,
                                    labels = labels.map {
                                        it.text.lowercase(Locale.ROOT) to it.confidence
                                    },
                                    faceBounds = faces.map { it.boundingBox },
                                    gridExposure = gridExposure,
                                    blockSexyContent = blockSexyContent
                                )
                                viewportBitmap.recycleSafely()
                                isScanning.set(false)
                                onResult(decision)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error combining visual signals", e)
                                viewportBitmap.recycleSafely()
                                isScanning.set(false)
                                onResult(null)
                            }
                        }
                        .addOnFailureListener {
                            val decision = combineVisualSignals(
                                bitmap = viewportBitmap,
                                labels = labels.map {
                                    it.text.lowercase(Locale.ROOT) to it.confidence
                                },
                                faceBounds = emptyList(),
                                gridExposure = gridExposure,
                                blockSexyContent = blockSexyContent
                            )
                            viewportBitmap.recycleSafely()
                            isScanning.set(false)
                            onResult(decision)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit labeling failed", e)
                    viewportBitmap.recycleSafely()
                    isScanning.set(false)
                    onResult(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Visual scan exception", e)
            isScanning.set(false)
            onResult(null)
        }
    }

    private fun combineVisualSignals(
        bitmap: Bitmap,
        labels: List<Pair<String, Float>>,
        faceBounds: List<Rect>,
        gridExposure: GridExposureStats,
        blockSexyContent: Boolean
    ): NsfwDetectionResult? {
        var matchedIntimateLabel: String? = null
        var intimateConfidence = 0f
        var matchedBodyLabel: String? = null
        var bodyConfidence = 0f

        for ((label, confidence) in labels) {
            if (INTIMATE_ATTIRE_LABELS.contains(label) && confidence > intimateConfidence) {
                matchedIntimateLabel = label
                intimateConfidence = confidence
            }
            if (BODY_EXPOSURE_LABELS.contains(label) && confidence > bodyConfidence) {
                matchedBodyLabel = label
                bodyConfidence = confidence
            }
        }

        // Measure chest/torso skin exposure directly below each detected face
        var maxTorsoSkinRatio = 0f
        for (faceRect in faceBounds) {
            val torsoRatio = computeTorsoSkinRatioBelowFace(bitmap, faceRect)
            if (torsoRatio > maxTorsoSkinRatio) {
                maxTorsoSkinRatio = torsoRatio
            }
        }

        Log.d(
            TAG,
            "Scan signals -> intimate=($matchedIntimateLabel: $intimateConfidence), " +
                "body=($matchedBodyLabel: $bodyConfidence), " +
                "torsoSkin=${"%.2f".format(maxTorsoSkinRatio)}, " +
                "maxTileSkin=${"%.2f".format(gridExposure.maxTileSkinRatio)}, " +
                "hotTiles=${gridExposure.highExposureTileCount}"
        )

        // CASE 1: Direct Intimate Attire detected by ML Kit (Bikini, Brassiere, Swimsuit, Underwear, Lingerie)
        if (matchedIntimateLabel != null) {
            val minConf = if (blockSexyContent) 0.45f else 0.72f
            if (intimateConfidence >= minConf) {
                return NsfwDetectionResult(
                    isBlocked = true,
                    layer = "VISUAL_AI_SCANNER",
                    category = if (intimateConfidence > 0.78f && gridExposure.maxTileSkinRatio > 0.45f) {
                        "EXPLICIT_PORN"
                    } else {
                        "SEXY_HALF_NAKED"
                    },
                    matchedTrigger = matchedIntimateLabel.replaceFirstChar { it.uppercase() },
                    confidence = intimateConfidence,
                    details = "Visual AI detected ${matchedIntimateLabel.uppercase()} / revealing content on screen (${(intimateConfidence * 100).toInt()}% confidence)"
                )
            }
        }

        // CASE 2: Body Exposure Label (Cleavage, Barechested, Abdomen, Navel, Thigh, Flesh) + Skin Exposure
        if (matchedBodyLabel != null) {
            val isExplicitBody = matchedBodyLabel == "cleavage" ||
                matchedBodyLabel == "barechested" ||
                matchedBodyLabel == "flesh" ||
                matchedBodyLabel == "abdomen" ||
                matchedBodyLabel == "navel" ||
                matchedBodyLabel == "thigh"

            if (blockSexyContent && isExplicitBody &&
                (maxTorsoSkinRatio >= 0.34f || gridExposure.maxTileSkinRatio >= 0.38f || bodyConfidence >= 0.62f)
            ) {
                return NsfwDetectionResult(
                    isBlocked = true,
                    layer = "VISUAL_AI_SCANNER",
                    category = "SEXY_HALF_NAKED",
                    matchedTrigger = matchedBodyLabel.replaceFirstChar { it.uppercase() },
                    confidence = max(bodyConfidence, maxTorsoSkinRatio),
                    details = "Visual AI detected half-naked / body exposure (${matchedBodyLabel.uppercase()}) on screen"
                )
            }
        }

        // CASE 3: Face-Anchored Torso Exposure (Catches bikini/bra/crop-top/cleavage selfies & grid tiles)
        if (faceBounds.isNotEmpty()) {
            val torsoThreshold = if (blockSexyContent) 0.48f else 0.68f
            if (maxTorsoSkinRatio >= torsoThreshold && gridExposure.maxTileSkinRatio >= 0.32f) {
                return NsfwDetectionResult(
                    isBlocked = true,
                    layer = "VISUAL_AI_SCANNER",
                    category = if (maxTorsoSkinRatio >= 0.68f) "EXPLICIT_PORN" else "SEXY_HALF_NAKED",
                    matchedTrigger = "Torso/Cleavage Exposure",
                    confidence = maxTorsoSkinRatio,
                    details = "Visual AI detected high torso/chest skin exposure (${(maxTorsoSkinRatio * 100).toInt()}%) below face"
                )
            }
        }

        // CASE 4: Extreme multi-tile skin exposure (Explicit nudity / porn video frame without visible face)
        val extremeSkinThreshold = if (blockSexyContent) 0.58f else 0.68f
        if (gridExposure.maxTileSkinRatio >= extremeSkinThreshold && gridExposure.highExposureTileCount >= 2) {
            return NsfwDetectionResult(
                isBlocked = true,
                layer = "VISUAL_AI_SCANNER",
                category = "EXPLICIT_PORN",
                matchedTrigger = "High Visual Skin Exposure",
                confidence = gridExposure.maxTileSkinRatio,
                details = "Visual AI detected explicit skin exposure across screen (${(gridExposure.maxTileSkinRatio * 100).toInt()}%)"
            )
        }

        return null
    }

    /**
     * Computes the ratio of skin-tone pixels in the torso/chest box directly below a detected face.
     * In normal clothed portraits, the chest/torso region below the neck is covered by fabric (<20% skin).
     * In cleavage, bikini, bra, or bare-midriff photos (like the Instagram screenshots), the chest/torso
     * region directly below the face is 45%–85% exposed skin.
     */
    private fun computeTorsoSkinRatioBelowFace(bitmap: Bitmap, faceRect: Rect): Float {
        val faceWidth = faceRect.width()
        val faceHeight = faceRect.height()
        if (faceWidth < 20 || faceHeight < 20) return 0f

        // Sample the face center to calibrate the person's exact skin tone (works across all ethnicities)
        val faceTone = sampleFaceSkinTone(bitmap, faceRect)

        // Define chest/torso region directly below the chin (from 0.25 * faceHeight below chin to 2.2 * faceHeight below chin)
        val left = max(0, faceRect.centerX() - (faceWidth * 1.15f).toInt())
        val right = min(bitmap.width - 1, faceRect.centerX() + (faceWidth * 1.15f).toInt())
        val top = min(bitmap.height - 1, faceRect.bottom + (faceHeight * 0.20f).toInt())
        val bottom = min(bitmap.height - 1, faceRect.bottom + (faceHeight * 2.10f).toInt())

        if (right - left < 15 || bottom - top < 15) return 0f

        var skinPixels = 0
        var totalSampled = 0
        val step = max(2, (right - left) / 24)

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                totalSampled++
                if (isSkinPixelInclusive(pixel, faceTone)) {
                    skinPixels++
                }
                x += step
            }
            y += step
        }

        return if (totalSampled > 0) skinPixels.toFloat() / totalSampled.toFloat() else 0f
    }

    private data class FaceToneSample(
        val meanY: Float,
        val meanCr: Float,
        val meanCb: Float,
        val valid: Boolean
    )

    private fun sampleFaceSkinTone(bitmap: Bitmap, faceRect: Rect): FaceToneSample {
        val left = max(0, faceRect.left + faceRect.width() / 4)
        val right = min(bitmap.width - 1, faceRect.right - faceRect.width() / 4)
        val top = max(0, faceRect.top + faceRect.height() / 4)
        val bottom = min(bitmap.height - 1, faceRect.bottom - faceRect.height() / 4)

        if (right <= left || bottom <= top) {
            return FaceToneSample(0f, 0f, 0f, false)
        }

        var sumY = 0f
        var sumCr = 0f
        var sumCb = 0f
        var count = 0
        val step = max(1, (right - left) / 10)

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b
                val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
                sumY += luma
                sumCr += cr
                sumCb += cb
                count++
                x += step
            }
            y += step
        }

        return if (count > 0) {
            FaceToneSample(sumY / count, sumCr / count, sumCb / count, true)
        } else {
            FaceToneSample(0f, 0f, 0f, false)
        }
    }

    private data class GridExposureStats(
        val maxTileSkinRatio: Float,
        val highExposureTileCount: Int
    )

    /**
     * Divides the viewport into a 3x3 grid (matching Instagram profile grids and feed layouts)
     * and calculates skin-tone density per tile.
     */
    private fun evaluateGridSkinExposure(bitmap: Bitmap): GridExposureStats {
        val cols = 3
        val rows = 3
        val tileW = bitmap.width / cols
        val tileH = bitmap.height / rows
        if (tileW < 20 || tileH < 20) return GridExposureStats(0f, 0)

        var maxRatio = 0f
        var hotTiles = 0

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val startX = c * tileW
                val startY = r * tileH
                val endX = min(bitmap.width, startX + tileW)
                val endY = min(bitmap.height, startY + tileH)

                var skinCount = 0
                var totalCount = 0
                val step = max(3, tileW / 20)

                var y = startY
                while (y < endY) {
                    var x = startX
                    while (x < endX) {
                        val pixel = bitmap.getPixel(x, y)
                        totalCount++
                        if (isSkinPixelInclusive(pixel, null)) {
                            skinCount++
                        }
                        x += step
                    }
                    y += step
                }

                val ratio = if (totalCount > 0) skinCount.toFloat() / totalCount.toFloat() else 0f
                if (ratio > maxRatio) {
                    maxRatio = ratio
                }
                if (ratio >= 0.38f) {
                    hotTiles++
                }
            }
        }

        return GridExposureStats(maxRatio, hotTiles)
    }

    /**
     * Inclusive human skin-tone detector in YCbCr + HSV color space supporting
     * Fitzpatrick types I through VI (light, medium, olive, brown, and deep dark skin tones),
     * plus adaptive matching when a face skin sample is available.
     */
    private fun isSkinPixelInclusive(pixel: Int, faceTone: FaceToneSample?): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // Exclude near-black UI backgrounds, near-white backgrounds, and neutral grays
        val maxRgb = max(r, max(g, b))
        val minRgb = min(r, min(g, b))
        if (maxRgb < 32 || minRgb > 242) return false
        if (maxRgb - minRgb < 12) return false // Neutral gray/black/white

        // Human skin across all tones has Red >= Green >= Blue (with minor lighting tolerance)
        if (r < g || g < b - 8 || r <= b + 10) return false

        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b
        val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b

        // If we calibrated against a detected face in the same image, check chromatic similarity
        if (faceTone != null && faceTone.valid) {
            val dCr = kotlin.math.abs(cr - faceTone.meanCr)
            val dCb = kotlin.math.abs(cb - faceTone.meanCb)
            val dY = kotlin.math.abs(y - faceTone.meanY)
            if (dCr <= 14f && dCb <= 14f && dY <= 75f && cr in 132f..178f && cb in 80f..132f) {
                return true
            }
        }

        // General multi-tone YCbCr + HSV skin envelope (Fitzpatrick I-VI)
        // Lighter to medium skin: Y > 80, Cr in [135..175], Cb in [85..127]
        // Darker brown/deep skin: Y in [35..120], Cr in [133..168], Cb in [88..130], R > G > B
        val inYCbCrSkin = (y >= 35f && cr in 134f..174f && cb in 85f..128f)
        if (!inYCbCrSkin) return false

        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]

        return (hue in 4f..38f) && (sat in 0.15f..0.78f) && (value in 0.16f..0.96f)
    }

    private fun ensureSoftwareBitmap(source: Bitmap): Bitmap? {
        return try {
            val targetWidth = 480
            val scale = targetWidth.toFloat() / max(1, source.width).toFloat()
            val targetHeight = max(1, (source.height * scale).toInt())

            val argbBitmap = if (source.config == Bitmap.Config.ARGB_8888 && !source.isMutable) {
                source
            } else {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } ?: return null

            val scaled = Bitmap.createScaledBitmap(argbBitmap, targetWidth, targetHeight, true)
            if (argbBitmap != source && argbBitmap != scaled) {
                argbBitmap.recycleSafely()
            }
            if (source != scaled) {
                source.recycleSafely()
            }
            scaled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert bitmap", e)
            null
        }
    }

    private fun Bitmap.recycleSafely() {
        try {
            if (!this.isRecycled) {
                this.recycle()
            }
        } catch (_: Exception) {
        }
    }
}
