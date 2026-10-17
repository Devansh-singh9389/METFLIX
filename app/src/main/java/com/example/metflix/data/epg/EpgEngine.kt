package com.example.metflix.data.epg

import com.example.metflix.data.entities.EpgProgram
import com.example.metflix.data.entities.LiveChannel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgEngine @Inject constructor() {

    /**
     * Attaches or updates the EPG schedule on a channel.
     * If the channel doesn't have an active EPG schedule, builds a realistic 24/7 calendar schedule.
     */
    fun enrichChannelWithSchedule(channel: LiveChannel, nowMs: Long = System.currentTimeMillis()): LiveChannel {
        if (channel.schedule.isNotEmpty() && channel.schedule.any { it.isLive(nowMs) }) {
            val liveProg = channel.getCurrentEpgProgram(nowMs)
            return channel.copy(
                currentProgram = liveProg.title,
                programDescription = liveProg.description
            )
        }

        val generatedSchedule = generateScheduleForChannel(channel, nowMs)
        val currentProg = generatedSchedule.firstOrNull { it.isLive(nowMs) }
            ?: generatedSchedule.firstOrNull()

        return channel.copy(
            schedule = generatedSchedule,
            currentProgram = currentProg?.title ?: channel.currentProgram,
            programDescription = currentProg?.description ?: channel.programDescription
        )
    }

    /**
     * Generates a realistic 24-hour schedule for today for the given channel based on category & name.
     */
    fun generateScheduleForChannel(channel: LiveChannel, nowMs: Long = System.currentTimeMillis()): List<EpgProgram> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDayMs = calendar.timeInMillis
        val slots = getShowTemplates(channel.category, channel.name)

        val programs = mutableListOf<EpgProgram>()
        for (i in slots.indices) {
            val template = slots[i]
            val progStartMs = startOfDayMs + (template.startMinuteOfDay * 60 * 1000L)
            val progEndMs = startOfDayMs + (template.endMinuteOfDay * 60 * 1000L)

            programs.add(
                EpgProgram(
                    id = "${channel.id}_slot_$i",
                    title = template.title,
                    description = template.description,
                    category = channel.category,
                    startEpochMs = progStartMs,
                    endEpochMs = progEndMs
                )
            )
        }

        return programs
    }

    /**
     * Parses an XMLTV XML or GZ stream and maps programmes to channels by tvg-id.
     */
    fun parseXmltv(inputStream: InputStream, isGzip: Boolean = false): Map<String, List<EpgProgram>> {
        val stream = if (isGzip) GZIPInputStream(inputStream) else inputStream
        val reader = BufferedReader(InputStreamReader(stream))
        val result = mutableMapOf<String, MutableList<EpgProgram>>()

        val sdf = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        var currentChannelId: String? = null
        var currentStartMs: Long = 0L
        var currentEndMs: Long = 0L
        var currentTitle: String = ""
        var currentDesc: String = ""
        var currentCat: String = ""

        try {
            reader.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("<programme", ignoreCase = true)) {
                        currentChannelId = Regex("""channel="([^"]*)"""").find(trimmed)?.groupValues?.get(1)
                        val startStr = Regex("""start="([^"]*)"""").find(trimmed)?.groupValues?.get(1)
                        val stopStr = Regex("""stop="([^"]*)"""").find(trimmed)?.groupValues?.get(1)

                        currentStartMs = parseXmltvDate(startStr, sdf)
                        currentEndMs = parseXmltvDate(stopStr, sdf)
                        currentTitle = ""
                        currentDesc = ""
                        currentCat = ""
                    } else if (trimmed.startsWith("<title", ignoreCase = true)) {
                        currentTitle = extractTagValue(trimmed)
                    } else if (trimmed.startsWith("<desc", ignoreCase = true)) {
                        currentDesc = extractTagValue(trimmed)
                    } else if (trimmed.startsWith("<category", ignoreCase = true)) {
                        currentCat = extractTagValue(trimmed)
                    } else if (trimmed.startsWith("</programme>", ignoreCase = true)) {
                        if (!currentChannelId.isNullOrBlank() && currentTitle.isNotBlank() && currentStartMs > 0 && currentEndMs > currentStartMs) {
                            val prog = EpgProgram(
                                id = "${currentChannelId}_${currentStartMs}",
                                title = currentTitle,
                                description = currentDesc,
                                category = currentCat,
                                startEpochMs = currentStartMs,
                                endEpochMs = currentEndMs
                            )
                            result.getOrPut(currentChannelId!!) { mutableListOf() }.add(prog)
                        }
                        currentChannelId = null
                    }
                }
            }
        } catch (_: Exception) {}

        return result
    }

    private fun extractTagValue(xmlLine: String): String {
        val start = xmlLine.indexOf('>')
        val end = xmlLine.lastIndexOf('<')
        return if (start != -1 && end != -1 && end > start) {
            xmlLine.substring(start + 1, end).trim()
        } else ""
    }

    private fun parseXmltvDate(dateStr: String?, sdf: SimpleDateFormat): Long {
        if (dateStr == null) return 0L
        return try {
            val normalized = if (dateStr.length >= 14 && !dateStr.contains(" ")) {
                val base = dateStr.substring(0, 14)
                val zone = if (dateStr.length > 14) dateStr.substring(14).trim() else "+0000"
                "$base $zone"
            } else dateStr
            sdf.parse(normalized)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun getShowTemplates(category: String, channelName: String): List<ShowSlotTemplate> {
        return when (category.lowercase(Locale.ROOT)) {
            "news" -> listOf(
                ShowSlotTemplate(0, 360, "Overnight World News", "Round-the-clock global headlines and overnight updates."),
                ShowSlotTemplate(360, 480, "Morning Express & Headlines", "The day's top headlines, weather and morning briefing."),
                ShowSlotTemplate(480, 570, "Morning Debate Live", "Key issues debated with top journalists and analysts."),
                ShowSlotTemplate(570, 660, "Market Radar & Business Pulse", "Financial updates, stock movements and consumer trends."),
                ShowSlotTemplate(660, 750, "The Midday Bulletin", "Comprehensive midday news coverage and breaking developments."),
                ShowSlotTemplate(750, 840, "Special Investigation", "Deep dive investigative reporting on issues impacting society."),
                ShowSlotTemplate(840, 930, "Speed 100 Non-Stop News", "100 breaking stories in rapid succession from across the nation."),
                ShowSlotTemplate(930, 1020, "Evening 360", "Global and domestic perspectives on the day's major events."),
                ShowSlotTemplate(1020, 1140, "Prime Time with Editor", "In-depth analysis of the headlines shaping the world."),
                ShowSlotTemplate(1140, 1260, "The Big Debate", "High-stakes debate with panel experts on central controversies."),
                ShowSlotTemplate(1260, 1350, "Late Night News Wrap", "Summary of the complete day's happenings and tomorrow's outlook."),
                ShowSlotTemplate(1350, 1440, "World View Tonight", "International perspectives, diplomacy and cultural stories.")
            )

            "sports" -> listOf(
                ShowSlotTemplate(0, 360, "Sports Classics Rewind", "Historic moments and legendary clashes from sports history."),
                ShowSlotTemplate(360, 480, "Morning Sports Review", "Match highlights, post-game commentary and standings."),
                ShowSlotTemplate(480, 570, "Top 10 Moments & Plays", "The most breathtaking goals, catches and finishes."),
                ShowSlotTemplate(570, 690, "Match Preview & Pitch Report", "Tactical analysis, lineup updates and expert predictions."),
                ShowSlotTemplate(690, 870, "Live Tournament - Match Session", "Live commentary and action from championship competition."),
                ShowSlotTemplate(870, 960, "Post-Match Analysis Desk", "Expert breakdown of strategy, key turning points and MVP performances."),
                ShowSlotTemplate(960, 1080, "World Football & Racing Weekly", "Roundup of international leagues, races and transfer news."),
                ShowSlotTemplate(1080, 1260, "Prime Live Championship", "Live sports spectacle featuring intense rivalry and championship stakes."),
                ShowSlotTemplate(1260, 1350, "Post-Game Wrap & Press Con", "Player interviews, manager reactions and tournament points table."),
                ShowSlotTemplate(1350, 1440, "Sports Stories & Documentaries", "Incredible athletic stories and behind-the-scenes profiles.")
            )

            "movies" -> listOf(
                ShowSlotTemplate(0, 360, "Midnight Classic Movie", "Late night cinema masterpiece featuring timeless storytelling."),
                ShowSlotTemplate(360, 540, "Morning Family Blockbuster", "Heartwarming family cinema to start the morning right."),
                ShowSlotTemplate(540, 720, "Action Matinee Cinema", "High-octane action thriller filled with stunts and explosive chases."),
                ShowSlotTemplate(720, 930, "Afternoon Superhit Cinema", "Top grossing cinema sensation featuring all-star cast."),
                ShowSlotTemplate(930, 1140, "Comedy Cinema Bonanza", "Laugh-out-loud blockbuster comedy for the whole family."),
                ShowSlotTemplate(1140, 1350, "Prime Time Mega Premiere", "Mega spectacle world television premiere of this year's biggest hit."),
                ShowSlotTemplate(1350, 1440, "Midnight Thriller & Mystery", "Gripping edge-of-your-seat thriller that keeps you guessing.")
            )

            "kids" -> listOf(
                ShowSlotTemplate(0, 360, "Toon Time Bedtime Tales", "Gentle animated stories and lullaby adventures."),
                ShowSlotTemplate(360, 480, "Morning Toon Fun", "Energetic morning cartoons with fun characters and laughter."),
                ShowSlotTemplate(480, 600, "Superhero Action Academy", "Heroes protect their city from evil villains in epic battles."),
                ShowSlotTemplate(600, 750, "Magical Forest Adventures", "Enchanting fairy tales and whimsical cartoon comedy."),
                ShowSlotTemplate(750, 900, "Afternoon Toon Movie Special", "Feature-length animated blockbuster packed with fun and songs."),
                ShowSlotTemplate(900, 1050, "School's Out Cartoon Express", "Non-stop comedy gags and mischievous slapstick."),
                ShowSlotTemplate(1050, 1200, "Prime Time Super Kids", "Brand new episodes of your favourite animated characters."),
                ShowSlotTemplate(1200, 1320, "Bedtime Adventures", "Cozy night cartoons to wind down with pleasant stories."),
                ShowSlotTemplate(1320, 1440, "Classic Cartoons Vault", "Timeless retro cartoons loved by generations.")
            )

            "music" -> listOf(
                ShowSlotTemplate(0, 360, "Midnight Acoustic & Chill", "Relaxing ambient melodies and late-night acoustic sets."),
                ShowSlotTemplate(360, 480, "Morning Melodies", "Inspiring spiritual tracks and gentle morning beats."),
                ShowSlotTemplate(480, 660, "Top 50 Non-Stop Hits", "The chart-toppers and trending tracks taking over the airwaves."),
                ShowSlotTemplate(660, 840, "Romantic Duets & Love Beats", "Heartfelt anthems and unforgettable romantic melodies."),
                ShowSlotTemplate(840, 1020, "Retro Golden Era Classics", "Unforgettable vintage songs from the legendary masters."),
                ShowSlotTemplate(1020, 1200, "Trending Viral Anthems", "The fastest-rising tracks and viral social audio sensations."),
                ShowSlotTemplate(1200, 1380, "Club Night & DJ Party Mix", "High-energy dance anthems and electrifying remix sets."),
                ShowSlotTemplate(1380, 1440, "Late Night Slow Jams", "Moody, chilled-out vibes to end the night.")
            )

            else -> listOf(
                ShowSlotTemplate(0, 360, "Night Playout & Serials", "Continuous night broadcast featuring popular series and repeats."),
                ShowSlotTemplate(360, 480, "Morning Yoga & Wellness", "Rejuvenating wellness routines and inspiring life stories."),
                ShowSlotTemplate(480, 600, "Comedy Tadka Hour", "Hilarious sketches and beloved comedy sitcom favorites."),
                ShowSlotTemplate(600, 750, "Daily Family Soap: Rishte", "Dramatic twists and heartfelt moments in everyday family life."),
                ShowSlotTemplate(750, 900, "Afternoon Drama: Zindagi", "Emotional storytelling and gripping relationships."),
                ShowSlotTemplate(900, 1050, "Crime Files & Investigation", "True stories of bravery, mystery solving and justice served."),
                ShowSlotTemplate(1050, 1200, "Reality Talent Hunt", "Competitors showcase their phenomenal singing and dancing talent."),
                ShowSlotTemplate(1200, 1350, "Prime Time Mega Serial", "The nation's most-watched dramatic showdown with surprising revelations."),
                ShowSlotTemplate(1350, 1440, "The Late Night Show", "Celebrity interviews, witty banter and musical guests.")
            )
        }
    }

    private data class ShowSlotTemplate(
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
        val title: String,
        val description: String
    )
}
