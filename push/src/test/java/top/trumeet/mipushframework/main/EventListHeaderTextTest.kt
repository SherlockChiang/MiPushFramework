package top.trumeet.mipushframework.main

import org.junit.Assert.assertEquals
import org.junit.Test
import top.trumeet.mipushframework.main.subpage.eventHeaderText

class EventListHeaderTextTest {
    @Test
    fun longChannelIsPreservedForUiEllipsis() {
        val channel = "这是一个非常长的通知频道标题".repeat(20)

        assertEquals(channel, eventHeaderText(emptySet(), channel))
    }

    @Test
    fun configurationAndChannelShareOneBoundedHeader() {
        val options = linkedSetOf("first-option", "second-option")
        val channel = "notification-channel"

        assertEquals("[first-option, second-option] notification-channel", eventHeaderText(options, channel))
    }

    @Test
    fun emptyChannelDoesNotAddTrailingWhitespace() {
        assertEquals("[disable]", eventHeaderText(setOf("disable"), ""))
    }
}
