package com.example.replaygain.data

import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagField
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import java.io.File
import java.util.Locale

object ReplayGainTagger {

    private const val TAG = "ReplayGainTagger"
    private const val TRACK_GAIN_KEY = "REPLAYGAIN_TRACK_GAIN"
    private const val ALBUM_GAIN_KEY = "REPLAYGAIN_ALBUM_GAIN"

    fun hasReplayGainTags(file: File, onError: (String) -> Unit = {}): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            when (tag) {
                is FlacTag -> hasVorbisField(tag.vorbisCommentTag, TRACK_GAIN_KEY)
                is AbstractID3v2Tag -> hasTxxxFrame(tag, TRACK_GAIN_KEY)
                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check tags for ${file.name}", e)
            onError("读取标签失败：${file.name} - ${e.localizedMessage}")
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
        setVorbisField(vorbisTag, TRACK_GAIN_KEY, trackGain)
        setVorbisField(vorbisTag, ALBUM_GAIN_KEY, albumGain)
    }

    private fun setVorbisField(vorbisTag: VorbisCommentTag, key: String, value: String) {
        deleteVorbisField(vorbisTag, key)
        vorbisTag.setField(key, value)
    }

    private fun deleteVorbisField(vorbisTag: VorbisCommentTag, key: String) {
        val toRemove = mutableListOf<TagField>()
        val iterator = vorbisTag.fields
        while (iterator.hasNext()) {
            val field = iterator.next()
            if (field.id.equals(key, ignoreCase = true)) {
                toRemove.add(field)
            }
        }
        toRemove.forEach { vorbisTag.deleteField(it) }
    }

    private fun writeId3(id3Tag: AbstractID3v2Tag, trackGain: String, albumGain: String) {
        setTxxxFrame(id3Tag, TRACK_GAIN_KEY, trackGain)
        setTxxxFrame(id3Tag, ALBUM_GAIN_KEY, albumGain)
    }

    private fun setTxxxFrame(id3Tag: AbstractID3v2Tag, description: String, text: String) {
        removeTxxxFrames(id3Tag, description)
        val body = FrameBodyTXXX()
        body.description = description
        body.text = text
        val frame = id3Tag.createFrame("TXXX")
        frame.body = body
        id3Tag.setField(frame)
    }

    private fun hasVorbisField(vorbisTag: VorbisCommentTag, key: String): Boolean {
        val iterator = vorbisTag.fields
        while (iterator.hasNext()) {
            val field = iterator.next()
            if (field.id.equals(key, ignoreCase = true) && !field.toString().isNullOrBlank()) {
                return true
            }
        }
        return false
    }

    // getFields() 会把可重复帧（如 TXXX）展平成单个 frame，直接判断即可
    private fun hasTxxxFrame(id3Tag: AbstractID3v2Tag, description: String): Boolean {
        val iterator = id3Tag.getFields()
        while (iterator.hasNext()) {
            val frame = iterator.next() as? AbstractID3v2Frame ?: continue
            val body = frame.body as? FrameBodyTXXX ?: continue
            if (body.description?.equals(description, ignoreCase = true) == true &&
                !body.text.isNullOrBlank()
            ) {
                return true
            }
        }
        return false
    }

    private fun removeTxxxFrames(id3Tag: AbstractID3v2Tag, description: String) {
        val iterator = id3Tag.getFields()
        while (iterator.hasNext()) {
            val frame = iterator.next() as? AbstractID3v2Frame ?: continue
            val body = frame.body as? FrameBodyTXXX ?: continue
            if (body.description?.equals(description, ignoreCase = true) == true) {
                iterator.remove()
            }
        }
    }
}
