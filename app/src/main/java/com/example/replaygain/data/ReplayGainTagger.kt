package com.example.replaygain.data

import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import java.io.File
import java.util.Locale

object ReplayGainTagger {

    private const val TAG = "ReplayGainTagger"
    private const val TRACK_GAIN_KEY = "REPLAYGAIN_TRACK_GAIN"
    private const val ALBUM_GAIN_KEY = "REPLAYGAIN_ALBUM_GAIN"

    fun hasReplayGainTags(file: File): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            when (tag) {
                is FlacTag -> tag.vorbisCommentTag.getFirst(TRACK_GAIN_KEY).isNotBlank()
                is AbstractID3v2Tag -> hasTxxxFrame(tag, TRACK_GAIN_KEY)
                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check tags for ${file.name}", e)
            false
        }
    }

    fun write(file: File, trackGainDb: Double, albumGainDb: Double) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault

        val trackGainString = String.format(Locale.US, "%.2f dB", trackGainDb)
        val albumGainString = String.format(Locale.US, "%.2f dB", albumGainDb)

        when (tag) {
            is FlacTag -> writeFlac(tag, trackGainString, albumGainString)
            is AbstractID3v2Tag -> writeId3(tag, trackGainString, albumGainString)
            else -> {
                // Fallback: try generic setField if available
                try {
                    tag.setField(FieldKey.CUSTOM1, "$TRACK_GAIN_KEY=$trackGainString")
                    tag.setField(FieldKey.CUSTOM2, "$ALBUM_GAIN_KEY=$albumGainString")
                } catch (e: Exception) {
                    throw UnsupportedOperationException("Unsupported tag type: ${tag::class.java.name}", e)
                }
            }
        }

        AudioFileIO.write(audioFile)
        Log.i(TAG, "Wrote tags to ${file.name}: track=$trackGainString album=$albumGainString")
    }

    private fun writeFlac(tag: FlacTag, trackGain: String, albumGain: String) {
        val vorbisTag = tag.vorbisCommentTag
        vorbisTag.setField(TRACK_GAIN_KEY, trackGain)
        vorbisTag.setField(ALBUM_GAIN_KEY, albumGain)
    }

    private fun writeId3(id3Tag: AbstractID3v2Tag, trackGain: String, albumGain: String) {
        setTxxxFrame(id3Tag, TRACK_GAIN_KEY, trackGain)
        setTxxxFrame(id3Tag, ALBUM_GAIN_KEY, albumGain)
    }

    private fun setTxxxFrame(id3Tag: AbstractID3v2Tag, description: String, text: String) {
        val body = FrameBodyTXXX()
        body.description = description
        body.text = text
        val frame = id3Tag.createFrame("TXXX")
        frame.body = body
        id3Tag.setField(frame)
    }

    private fun hasTxxxFrame(id3Tag: AbstractID3v2Tag, description: String): Boolean {
        @Suppress("UNCHECKED_CAST")
        val iterator = id3Tag.getFrameOfType("TXXX") as Iterator<Any>
        while (iterator.hasNext()) {
            val frame = iterator.next() as? AbstractID3v2Frame ?: continue
            val body = frame.body as? FrameBodyTXXX ?: continue
            if (body.description == description && body.text.isNotBlank()) {
                return true
            }
        }
        return false
    }
}
