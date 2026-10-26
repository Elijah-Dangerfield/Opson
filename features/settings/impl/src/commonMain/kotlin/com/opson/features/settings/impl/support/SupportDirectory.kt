package com.opson.features.settings.impl.support

import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_find_about_ed_helpline
import opson.libraries.resources.generated.resources.settings_find_about_families
import opson.libraries.resources.generated.resources.settings_find_about_peer
import opson.libraries.resources.generated.resources.settings_find_about_provider
import opson.libraries.resources.generated.resources.settings_find_about_text_keyword
import opson.libraries.resources.generated.resources.settings_find_about_therapist
import opson.libraries.resources.generated.resources.settings_find_about_young
import opson.libraries.resources.generated.resources.settings_find_alliance
import opson.libraries.resources.generated.resources.settings_find_anad
import opson.libraries.resources.generated.resources.settings_find_beat
import opson.libraries.resources.generated.resources.settings_find_bodywhys
import opson.libraries.resources.generated.resources.settings_find_butterfly
import opson.libraries.resources.generated.resources.settings_find_country_au
import opson.libraries.resources.generated.resources.settings_find_country_ca
import opson.libraries.resources.generated.resources.settings_find_country_gb
import opson.libraries.resources.generated.resources.settings_find_country_ie
import opson.libraries.resources.generated.resources.settings_find_country_nz
import opson.libraries.resources.generated.resources.settings_find_country_us
import opson.libraries.resources.generated.resources.settings_find_crisis_text_line
import opson.libraries.resources.generated.resources.settings_find_edanz
import opson.libraries.resources.generated.resources.settings_find_kids_help_phone
import opson.libraries.resources.generated.resources.settings_find_nedic
import opson.libraries.resources.generated.resources.settings_find_outside_au
import opson.libraries.resources.generated.resources.settings_find_outside_ca
import opson.libraries.resources.generated.resources.settings_find_outside_gb
import opson.libraries.resources.generated.resources.settings_find_outside_ie
import opson.libraries.resources.generated.resources.settings_find_outside_nz
import opson.libraries.resources.generated.resources.settings_find_outside_us
import opson.libraries.resources.generated.resources.settings_find_provider
import org.jetbrains.compose.resources.StringResource

/**
 * The countries Find support has a list for. [codes] are ISO 3166 region codes as the phone
 * reports them ("GB"; "UK" too, in case a platform says so).
 */
enum class SupportCountry(val codes: Set<String>, val label: StringResource, val outside: StringResource) {
    UnitedStates(setOf("US"), Res.string.settings_find_country_us, Res.string.settings_find_outside_us),
    UnitedKingdom(setOf("GB", "UK"), Res.string.settings_find_country_gb, Res.string.settings_find_outside_gb),
    Canada(setOf("CA"), Res.string.settings_find_country_ca, Res.string.settings_find_outside_ca),
    Australia(setOf("AU"), Res.string.settings_find_country_au, Res.string.settings_find_outside_au),
    Ireland(setOf("IE"), Res.string.settings_find_country_ie, Res.string.settings_find_outside_ie),
    NewZealand(setOf("NZ"), Res.string.settings_find_country_nz, Res.string.settings_find_outside_nz),
    ;

    companion object {
        /** The country for a region code, or null when there is no list for it. */
        fun of(regionCode: String?): SupportCountry? {
            val code = regionCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { code in it.codes }
        }
    }
}

/** A number as people read it ([shown], "1-888-375-7767") and as the phone dials it ([dial]). */
data class PhoneNumber(val shown: String, val dial: String) {
    val telUrl: String get() = "tel:$dial"
    val smsUrl: String get() = "sms:$dial"
}

/** How a line is reached. One button per line: Call, Text or Open. */
sealed interface Reach {
    data class Call(val number: PhoneNumber) : Reach

    /** [keyword] is the word to send ("HOME"), when the service asks for one. */
    data class Text(val number: PhoneNumber, val keyword: String? = null) : Reach

    data class Web(val url: String) : Reach
}

/**
 * One line in the list. [about] is our short description, formatted with [aboutArgs]; the row
 * adds the number after it when there is one. [source] is where the number was taken from.
 */
data class SupportLine(
    val id: String,
    val country: SupportCountry,
    val name: StringResource,
    val about: StringResource,
    val reach: Reach,
    val source: String,
    val aboutArgs: List<String> = emptyList(),
) {
    /** The number or address the row shows, and what the "couldn't open" note repeats. */
    val shown: String
        get() = when (reach) {
            is Reach.Call -> reach.number.shown
            is Reach.Text -> reach.number.shown
            is Reach.Web -> reach.url.removePrefix("https://").removePrefix("www.")
        }

    val url: String
        get() = when (reach) {
            is Reach.Call -> reach.number.telUrl
            is Reach.Text -> reach.number.smsUrl
            is Reach.Web -> reach.url
        }
}

/** A country's 24/7 crisis line: one number to call, one to text (often the same). */
data class CrisisLine(
    val country: SupportCountry,
    val call: PhoneNumber,
    val text: PhoneNumber,
    val source: String,
    /** The word to send, when the text service asks for one ("SHOUT"). */
    val textKeyword: String? = null,
) {
    /** "Call or text 988" when one number does both, else "Call 116 123". */
    val callAndTextShareANumber: Boolean get() = call.dial == text.dial
}

/** Everything one country has: its crisis line, then its eating-disorder lines in order. */
data class CountryLines(val crisis: CrisisLine, val lines: List<SupportLine>)

/**
 * The list Find support and Support's crisis row read. It ships with the app, so it works
 * offline, and nothing about it is fetched, logged or synced.
 *
 * TODO(launch): verify every number, keyword and hours against its source before launch, and
 * recheck each quarter. Each entry names its source; none of them has been checked by a person
 * yet. Hours are not claimed for any line except the crisis lines, which are 24/7.
 */
object SupportDirectory {

    fun of(country: SupportCountry): CountryLines = when (country) {
        SupportCountry.UnitedStates -> unitedStates
        SupportCountry.UnitedKingdom -> unitedKingdom
        SupportCountry.Canada -> canada
        SupportCountry.Australia -> australia
        SupportCountry.Ireland -> ireland
        SupportCountry.NewZealand -> newZealand
    }

    // TODO(launch): verify. Source: https://988lifeline.org, https://anad.org,
    // https://www.allianceforeatingdisorders.com, https://www.crisistextline.org.
    private val unitedStates = CountryLines(
        crisis = CrisisLine(
            country = SupportCountry.UnitedStates,
            call = PhoneNumber("988", "988"),
            text = PhoneNumber("988", "988"),
            source = "https://988lifeline.org",
        ),
        lines = listOf(
            SupportLine(
                id = "us-anad",
                country = SupportCountry.UnitedStates,
                name = Res.string.settings_find_anad,
                about = Res.string.settings_find_about_peer,
                reach = Reach.Call(PhoneNumber("1-888-375-7767", "18883757767")),
                source = "https://anad.org",
            ),
            SupportLine(
                id = "us-alliance",
                country = SupportCountry.UnitedStates,
                name = Res.string.settings_find_alliance,
                about = Res.string.settings_find_about_therapist,
                reach = Reach.Call(PhoneNumber("1-866-662-1235", "18666621235")),
                source = "https://www.allianceforeatingdisorders.com",
            ),
            SupportLine(
                id = "us-crisis-text-line",
                country = SupportCountry.UnitedStates,
                name = Res.string.settings_find_crisis_text_line,
                about = Res.string.settings_find_about_text_keyword,
                aboutArgs = listOf("HOME", "741741"),
                reach = Reach.Text(PhoneNumber("741741", "741741"), keyword = "HOME"),
                source = "https://www.crisistextline.org",
            ),
            // TODO(launch): point this at the Alliance's own provider search once someone has
            // checked the page. The handoff gives only the home page; don't guess a path.
            SupportLine(
                id = "us-provider",
                country = SupportCountry.UnitedStates,
                name = Res.string.settings_find_provider,
                about = Res.string.settings_find_about_provider,
                reach = Reach.Web(PROVIDER_URL),
                source = PROVIDER_URL,
            ),
        ),
    )

    // TODO(launch): verify. Source: https://www.samaritans.org, https://giveusashout.org,
    // https://www.beateatingdisorders.org.uk.
    private val unitedKingdom = CountryLines(
        crisis = CrisisLine(
            country = SupportCountry.UnitedKingdom,
            call = PhoneNumber("116 123", "116123"),
            text = PhoneNumber("85258", "85258"),
            textKeyword = "SHOUT",
            source = "https://www.samaritans.org, https://giveusashout.org",
        ),
        lines = listOf(
            SupportLine(
                id = "gb-beat",
                country = SupportCountry.UnitedKingdom,
                name = Res.string.settings_find_beat,
                about = Res.string.settings_find_about_ed_helpline,
                reach = Reach.Call(PhoneNumber("0808 801 0677", "08088010677")),
                source = "https://www.beateatingdisorders.org.uk",
            ),
        ),
    )

    // TODO(launch): verify. Source: https://988.ca, https://nedic.ca, https://kidshelpphone.ca.
    private val canada = CountryLines(
        crisis = CrisisLine(
            country = SupportCountry.Canada,
            call = PhoneNumber("988", "988"),
            text = PhoneNumber("988", "988"),
            source = "https://988.ca",
        ),
        lines = listOf(
            SupportLine(
                id = "ca-nedic",
                country = SupportCountry.Canada,
                name = Res.string.settings_find_nedic,
                about = Res.string.settings_find_about_ed_helpline,
                reach = Reach.Call(PhoneNumber("1-866-633-4220", "18666334220")),
                source = "https://nedic.ca",
            ),
            SupportLine(
                id = "ca-kids-help-phone",
                country = SupportCountry.Canada,
                name = Res.string.settings_find_kids_help_phone,
                about = Res.string.settings_find_about_young,
                reach = Reach.Call(PhoneNumber("1-800-668-6868", "18006686868")),
                source = "https://kidshelpphone.ca",
            ),
        ),
    )

    // TODO(launch): verify. Source: https://www.lifeline.org.au, https://butterfly.org.au.
    private val australia = CountryLines(
        crisis = CrisisLine(
            country = SupportCountry.Australia,
            call = PhoneNumber("13 11 14", "131114"),
            text = PhoneNumber("0477 13 11 14", "0477131114"),
            source = "https://www.lifeline.org.au",
        ),
        lines = listOf(
            SupportLine(
                id = "au-butterfly",
                country = SupportCountry.Australia,
                name = Res.string.settings_find_butterfly,
                about = Res.string.settings_find_about_ed_helpline,
                reach = Reach.Call(PhoneNumber("1800 33 4673", "1800334673")),
                source = "https://butterfly.org.au",
            ),
        ),
    )

    // TODO(launch): verify. Source: https://www.samaritans.org/ireland, https://text50808.ie,
    // https://www.bodywhys.ie.
    private val ireland = CountryLines(
        crisis = CrisisLine(
            country = SupportCountry.Ireland,
            call = PhoneNumber("116 123", "116123"),
            text = PhoneNumber("50808", "50808"),
            textKeyword = "HELLO",
            source = "https://www.samaritans.org/ireland, https://text50808.ie",
        ),
        lines = listOf(
            SupportLine(
                id = "ie-bodywhys",
                country = SupportCountry.Ireland,
                name = Res.string.settings_find_bodywhys,
                about = Res.string.settings_find_about_ed_helpline,
                reach = Reach.Call(PhoneNumber("01 210 7906", "012107906")),
                source = "https://www.bodywhys.ie",
            ),
        ),
    )

    // TODO(launch): verify. Source: https://1737.org.nz, https://www.edanz.org.nz.
    private val newZealand = CountryLines(
        crisis = CrisisLine(
            country = SupportCountry.NewZealand,
            call = PhoneNumber("1737", "1737"),
            text = PhoneNumber("1737", "1737"),
            source = "https://1737.org.nz",
        ),
        lines = listOf(
            SupportLine(
                id = "nz-edanz",
                country = SupportCountry.NewZealand,
                name = Res.string.settings_find_edanz,
                about = Res.string.settings_find_about_families,
                reach = Reach.Call(PhoneNumber("0800 233 269", "0800233269")),
                source = "https://www.edanz.org.nz",
            ),
        ),
    )
}
