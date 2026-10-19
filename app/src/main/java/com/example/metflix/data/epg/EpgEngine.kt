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
     * Guarantees realistic, channel-specific, clock-aligned schedule.
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
     * Generates a realistic 24-hour schedule for today for the given channel based on name & category.
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

        val programs = ArrayList<EpgProgram>(slots.size)
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

    /**
     * Replaces channel template schedules with real parsed XMLTV schedules when available.
     */
    fun applyRealEpg(
        channels: List<LiveChannel>,
        epgMap: Map<String, List<EpgProgram>>,
        nowMs: Long = System.currentTimeMillis()
    ): List<LiveChannel> {
        if (epgMap.isEmpty()) return channels
        return channels.map { channel ->
            val programs = channel.epgId?.let { epgMap[it] } ?: epgMap[channel.id] ?: epgMap[channel.name]
            if (!programs.isNullOrEmpty()) {
                val liveProg = programs.firstOrNull { it.isLive(nowMs) } ?: programs.firstOrNull()
                channel.copy(
                    schedule = programs,
                    currentProgram = liveProg?.title ?: channel.currentProgram,
                    programDescription = liveProg?.description ?: channel.programDescription
                )
            } else {
                channel
            }
        }
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
        val lowerName = channelName.lowercase(Locale.ROOT)
        val lowerCat = category.lowercase(Locale.ROOT)

        // Specific Hindi Channel Signatures
        if (lowerName.contains("star plus")) {
            return listOf(
                ShowSlotTemplate(0, 360, "Star Plus Night Block", "Late night repeats of iconic dramas and romantic series."),
                ShowSlotTemplate(360, 480, "Bhakti Sangam", "Morning prayers, devotions and spiritual stories."),
                ShowSlotTemplate(480, 660, "Saath Nibhaana Saathiya (Rewind)", "Gopi Bahu navigates love, family responsibilities and household drama."),
                ShowSlotTemplate(660, 780, "Advocate Anjali Awasthi", "A courageous young lawyer fights for justice against powerful adversaries."),
                ShowSlotTemplate(780, 900, "Udne Ki Aasha", "Sachin and Sayali struggle through hardship to build a loving home."),
                ShowSlotTemplate(900, 1020, "Ghum Hai Kisikey Pyaar Meiin", "Emotional twists and heartfelt commitments test family loyalties."),
                ShowSlotTemplate(1020, 1140, "Jhanak", "A resilient mountain girl fights for dignity and her true identity."),
                ShowSlotTemplate(1140, 1260, "Anupamaa", "Anupamaa inspires with her unyielding courage, grace, and independence."),
                ShowSlotTemplate(1260, 1380, "Yeh Rishta Kya Kehlata Hai", "Fourth-generation romance and family drama unfold with high emotions."),
                ShowSlotTemplate(1380, 1440, "Star Mahasangam Highlights", "Special recap and backstage moments with beloved cast members.")
            )
        }

        if (lowerName.contains("sab") || lowerName.contains("sony sab")) {
            return listOf(
                ShowSlotTemplate(0, 360, "Taarak Mehta Non-Stop Laughs", "Classic Gokuldham society comedy adventures through the night."),
                ShowSlotTemplate(360, 540, "Morning Smile Hour", "Feel-good family comedy and laughter therapy."),
                ShowSlotTemplate(540, 720, "Pushpa Impossible", "Pushpa overcomes societal hurdles with grit, clever solutions and humor."),
                ShowSlotTemplate(720, 900, "Wagle Ki Duniya", "Relatable everyday triumphs and challenges of the common man."),
                ShowSlotTemplate(900, 1080, "Badall Pe Paon Hai", "A determined girl dreams of stock market success to uplift her family."),
                ShowSlotTemplate(1080, 1260, "Taarak Mehta Ka Ooltah Chashmah", "Jethalal gets into hilarious trouble while Bapuji and Tapu Sena step in."),
                ShowSlotTemplate(1260, 1380, "Tenali Rama", "Witty stories of wisdom, royal politics and comical solutions."),
                ShowSlotTemplate(1380, 1440, "Late Night Comedy Vault", "Gag reel, bloopers and funniest moments from Sab TV shows.")
            )
        }

        if (lowerName.contains("sony") && !lowerName.contains("sports") && !lowerName.contains("ten") && !lowerName.contains("max")) {
            return listOf(
                ShowSlotTemplate(0, 360, "CID Night Investigation", "ACP Pradyuman, Daya and Abhijeet crack midnight homicide mysteries."),
                ShowSlotTemplate(360, 540, "Crime Patrol Dial 100", "True crime stories alerting citizens against fraud and deceit."),
                ShowSlotTemplate(540, 720, "Indian Idol Replay", "Powerhouse vocal performances and emotional contestant journeys."),
                ShowSlotTemplate(720, 900, "Katha Ankahee", "A dramatic tale of love, redemption and complicated relationships."),
                ShowSlotTemplate(900, 1080, "Shrimad Ramayan", "The timeless epic saga of devotion, duty and righteous victory."),
                ShowSlotTemplate(1080, 1260, "The Kapil Sharma Show", "Bollywood celebrity guests join Kapil, Krushna and Kiku for nonstop laughs."),
                ShowSlotTemplate(1260, 1380, "CID: Khatarnak Sazish", "Daya breaks doors as the team neutralizes criminal syndicates."),
                ShowSlotTemplate(1380, 1440, "Crime Patrol Satark", "Real cases analyzed to create safety awareness across society.")
            )
        }

        if (lowerName.contains("zee tv") || (lowerName.contains("zee") && !lowerName.contains("cinema") && !lowerName.contains("news"))) {
            return listOf(
                ShowSlotTemplate(0, 360, "Zee Night Serials", "Catch up on twists and dramatic turning points."),
                ShowSlotTemplate(360, 540, "Morning Bhakti & Yoga", "Spiritual shlokas, healthy living tips and morning peace."),
                ShowSlotTemplate(540, 720, "Pavitra Rishta Classics", "Archana and Manav's legendary love story from the beginning."),
                ShowSlotTemplate(720, 900, "Rabb Se Hai Dua", "Dua stands firm for her marriage and self-respect in dramatic clashes."),
                ShowSlotTemplate(900, 1080, "Kumkum Bhagya", "Destiny intertwines lovers across generations against wicked scheming."),
                ShowSlotTemplate(1080, 1200, "Kundali Bhagya", "Preeta and Karan face dramatic courtroom revelations and emotional reunions."),
                ShowSlotTemplate(1200, 1320, "Bhagya Lakshmi", "Lakshmi selflessly protects her family despite constant betrayal."),
                ShowSlotTemplate(1320, 1440, "Sa Re Ga Ma Pa Mega Auditions", "Contestants sing classic melodies to impress master judges.")
            )
        }

        if (lowerName.contains("colors") && !lowerName.contains("cineplex")) {
            return listOf(
                ShowSlotTemplate(0, 360, "Bigg Boss Uncut Night", "24/7 drama, kitchen arguments and secret alliances inside the house."),
                ShowSlotTemplate(360, 540, "Morning Chants & Blessings", "Spiritual discourses and devotional blessings."),
                ShowSlotTemplate(540, 720, "Balika Vadhu (Rewind)", "Anandhi's inspiring journey of education and empowerment."),
                ShowSlotTemplate(720, 900, "Mangal Lakshmi", "Two devoted sisters face life's tests while shielding each other."),
                ShowSlotTemplate(900, 1080, "Parineetii", "Complicated fate tests the unbreakable bond between two lifelong friends."),
                ShowSlotTemplate(1080, 1260, "Bigg Boss Prime Live", "Salman Khan conducts weekend reality checks and intense eliminations."),
                ShowSlotTemplate(1260, 1380, "Shiv Shakti - Tap Tyaag Tandav", "The celestial romance and cosmic triumph of Lord Shiva and Goddess Parvati."),
                ShowSlotTemplate(1380, 1440, "Khatron Ke Khiladi Highlights", "Daredevil celebrity stunts, dangerous encounters and eliminations.")
            )
        }

        // News Channels
        if (lowerCat == "news" || lowerName.contains("news") || lowerName.contains("aaj tak") || lowerName.contains("ndtv") || lowerName.contains("abp") || lowerName.contains("cnn") || lowerName.contains("bbc")) {
            return listOf(
                ShowSlotTemplate(0, 360, "Global Overnight News", "Round-the-clock international headlines and breaking overnight updates."),
                ShowSlotTemplate(360, 480, "Morning Express & Weather", "First news of the day, weather alerts and newspaper headlines."),
                ShowSlotTemplate(480, 570, "Morning Debate Live", "Key issues debated with top journalists, editors and political analysts."),
                ShowSlotTemplate(570, 660, "Market Radar & Business Pulse", "Financial updates, stock movements and consumer trends."),
                ShowSlotTemplate(660, 750, "The Midday Bulletin", "Comprehensive midday news coverage and breaking developments."),
                ShowSlotTemplate(750, 840, "Special Investigation Desk", "Deep dive investigative reporting on issues impacting public interest."),
                ShowSlotTemplate(840, 930, "Speed 100 Non-Stop News", "100 breaking stories in rapid succession from across the nation."),
                ShowSlotTemplate(930, 1050, "Evening 360: The Big Picture", "Global and domestic perspectives on the day's major headlines."),
                ShowSlotTemplate(1050, 1200, "Prime Time with Editor-in-Chief", "In-depth investigative analysis of the headlines shaping the nation."),
                ShowSlotTemplate(1200, 1320, "The Big Debate Live", "High-stakes debate with panel experts on central controversies."),
                ShowSlotTemplate(1320, 1440, "Late Night News Wrap", "Summary of the complete day's happenings and tomorrow's outlook.")
            )
        }

        // Sports Channels
        if (lowerCat == "sports" || lowerName.contains("sports") || lowerName.contains("cricket") || lowerName.contains("football") || lowerName.contains("ten")) {
            return listOf(
                ShowSlotTemplate(0, 360, "Championship Rewind & Classics", "Legendary clashes, iconic moments and golden match archives."),
                ShowSlotTemplate(360, 480, "Morning Sports Review", "Match highlights, post-game commentary and tournament points table."),
                ShowSlotTemplate(480, 570, "Top 10 Plays & Finishes", "The most breathtaking goals, catches, knockouts and photo finishes."),
                ShowSlotTemplate(570, 690, "Match Preview & Pitch Analysis", "Tactical analysis, predicted lineups and expert weather/pitch reports."),
                ShowSlotTemplate(690, 930, "Live Tournament - Session Action", "Live commentary and championship play with expert analysis."),
                ShowSlotTemplate(930, 1050, "Post-Match Analysis Desk", "Player interviews, turning points and man-of-the-match breakdown."),
                ShowSlotTemplate(1050, 1290, "Prime Live Championship", "Live sports spectacle featuring intense rivalry and high stakes."),
                ShowSlotTemplate(1290, 1380, "Press Conference & Team Wrap", "Managers and players address critical referee calls and strategies."),
                ShowSlotTemplate(1380, 1440, "Sports Icons Documentary", "Inspiring athletic journeys, training regimes and legendary careers.")
            )
        }

        // Movies Channels
        if (lowerCat == "movies" || lowerName.contains("cinema") || lowerName.contains("movie") || lowerName.contains("gold") || lowerName.contains("max") || lowerName.contains("hbo")) {
            return listOf(
                ShowSlotTemplate(0, 360, "Midnight Action Thriller", "Suspenseful late night cinema featuring heart-pounding chases."),
                ShowSlotTemplate(360, 540, "Morning Family Classic", "Heartwarming cinema and timeless stories for the whole family."),
                ShowSlotTemplate(540, 720, "South Action Blockbuster (Hindi Dub)", "High-octane mass action spectacle with unbelievable stunts."),
                ShowSlotTemplate(720, 930, "Afternoon Superhit Cinema", "Star-studded Bollywood blockbuster loaded with romance and music."),
                ShowSlotTemplate(930, 1140, "Comedy Dhamaka Cinema", "Laugh-out-loud comedy sensation featuring comic legends."),
                ShowSlotTemplate(1140, 1380, "Prime Mega Movie Premiere", "Television premiere of the year's biggest theatrical box office hit."),
                ShowSlotTemplate(1380, 1440, "Late Night Suspense Cinema", "Gripping psychological thriller keeping viewers on edge.")
            )
        }

        // Kids Channels
        if (lowerCat == "kids" || lowerName.contains("cartoon") || lowerName.contains("pogo") || lowerName.contains("nick") || lowerName.contains("disney")) {
            return listOf(
                ShowSlotTemplate(0, 360, "Lullaby Cartoon Adventures", "Gentle animated stories and pleasant bedtime tales."),
                ShowSlotTemplate(360, 480, "Morning Toon Fun Club", "Energetic morning cartoons packed with music and laughter."),
                ShowSlotTemplate(480, 630, "Chhota Bheem & Friends", "Bheem defends Dholakpur from crafty thieves and wicked sorcerers."),
                ShowSlotTemplate(630, 780, "Oggy and the Cockroaches", "Hilarious slapstick chases as Dee Dee, Marky and Joey prank Oggy."),
                ShowSlotTemplate(780, 930, "Motu Patlu Furfuri Nagar", "Samosa-fueled antics and comical villain captures by Motu and Patlu."),
                ShowSlotTemplate(930, 1080, "Doraemon Gadget Magic", "Nobita borrows secret future gadgets with unexpected consequences."),
                ShowSlotTemplate(1080, 1230, "Shinchan Comedy Hour", "Shinchan's cheeky jokes, dance moves and family mischief."),
                ShowSlotTemplate(1230, 1380, "Ben 10 Alien Battles", "Ben utilizes the Omnitrix to defeat alien invaders and save Earth."),
                ShowSlotTemplate(1380, 1440, "Classic Cartoons Vault", "Timeless animated favorites loved by kids across generations.")
            )
        }

        // Music Channels
        if (lowerCat == "music" || lowerName.contains("music") || lowerName.contains("9xm") || lowerName.contains("mtv") || lowerName.contains("beats")) {
            return listOf(
                ShowSlotTemplate(0, 360, "Midnight Acoustic & Chill", "Relaxing ambient melodies and late-night acoustic sets."),
                ShowSlotTemplate(360, 480, "Morning Devotion & Melody", "Peaceful morning chants, acoustic sitar and meditative tunes."),
                ShowSlotTemplate(480, 660, "Top 20 Chartbusters Countdown", "The biggest trending hits taking over radio airwaves and streaming."),
                ShowSlotTemplate(660, 840, "Romantic Duets & Golden Voices", "Heartfelt love songs and unforgettable romantic duets."),
                ShowSlotTemplate(840, 1020, "90s Nostalgia Rewind", "Evergreen golden era melodies from beloved music directors."),
                ShowSlotTemplate(1020, 1200, "Trending Party Anthems", "High-energy dance bangers and viral social audio sensations."),
                ShowSlotTemplate(1200, 1380, "Club Night Non-Stop DJ Mix", "Electrifying club remixes and high-octane bass drops."),
                ShowSlotTemplate(1380, 1440, "Late Night Slow Jams", "Moody, chilled-out vibes to end the night in style.")
            )
        }

        // General / Entertainment Fallback with Channel Name Interpolation
        return listOf(
            ShowSlotTemplate(0, 360, "$channelName: Overnight Broadcast", "Continuous nighttime programming, rebroadcasts and series highlights."),
            ShowSlotTemplate(360, 480, "$channelName: Morning Awakening", "Inspiring stories, wellness tips and positive morning thoughts."),
            ShowSlotTemplate(480, 660, "$channelName: Family Special", "Beloved family programming and heartwarming character stories."),
            ShowSlotTemplate(660, 840, "$channelName: Afternoon Drama", "Emotional twists and dramatic turning points in everyday relationships."),
            ShowSlotTemplate(840, 1020, "$channelName: Special Feature", "Signature programming exploring unique culture, talent and entertainment."),
            ShowSlotTemplate(1020, 1200, "$channelName: Prime Time Showcase", "The channel's flagship evening presentation featuring top talent."),
            ShowSlotTemplate(1200, 1350, "$channelName: Star Mega Serial", "High-stakes drama with gripping cliffhangers and revelations."),
            ShowSlotTemplate(1350, 1440, "$channelName: Night Cap", "Celebrity conversations, entertaining comedy and musical performances.")
        )
    }

    private data class ShowSlotTemplate(
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
        val title: String,
        val description: String
    )
}
