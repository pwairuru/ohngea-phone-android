/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class CodecDefaultsTest {
    @Test
    fun `default config orders opus first then speex`() {
        val text = File("src/main/assets/linphonerc_default").readText()
        val sections = Regex("""\[audio_codec_(\d+)\](.*?)(?=\n\[|\Z)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).sortedBy { it.groupValues[1].toInt() }.toList()

        fun entry(body: String, key: String) =
            Regex("""^$key=(.*)$""", RegexOption.MULTILINE).find(body)!!.groupValues[1].trim()
        val mimes = sections.map { entry(it.groupValues[2], "mime").lowercase() }
        assertEquals("opus", mimes[0])
        val opusEnd = mimes.indexOfLast { it == "opus" }
        val speex = mimes.filter { it.contains("speex") }
        assert(speex.isNotEmpty()) { "expected speex entries after opus" }
        assert(mimes.indexOf(speex.first()) > opusEnd) { "speex must come after opus, got $mimes" }
        assertEquals(listOf("48000", "32000", "16000", "8000"), sections.take(4).map { entry(it.groupValues[2], "rate") })
        assertEquals(listOf("1", "1", "1", "1"), sections.take(4).map { entry(it.groupValues[2], "enabled") })
    }
}
