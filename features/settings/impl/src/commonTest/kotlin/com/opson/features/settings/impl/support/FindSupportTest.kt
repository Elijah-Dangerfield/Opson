package com.opson.features.settings.impl.support

import com.opson.features.settings.impl.FakeLinks
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Find support: the country from the phone's region, changing it, what each tap opens, and what
 * a failed open says. The list itself is data, checked by hand against each source (see
 * [SupportDirectory]); these tests check its shape (every country has a crisis line and at least
 * one line, every entry has a source), not that the numbers are right.
 */
class FindSupportTest : CoroutineTest() {

    private fun viewModel(region: String?, links: FakeLinks = FakeLinks()) = FindSupportViewModel(DeviceRegion { region }, links)

    // --- country ---

    @Test
    fun regionCodesMapToCountries() {
        assertEquals(SupportCountry.UnitedStates, SupportCountry.of("US"))
        assertEquals(SupportCountry.UnitedKingdom, SupportCountry.of("GB"))
        assertEquals(SupportCountry.UnitedKingdom, SupportCountry.of("uk"))
        assertEquals(SupportCountry.Canada, SupportCountry.of(" ca "))
        assertEquals(SupportCountry.Australia, SupportCountry.of("AU"))
        assertEquals(SupportCountry.Ireland, SupportCountry.of("IE"))
        assertEquals(SupportCountry.NewZealand, SupportCountry.of("NZ"))
        assertNull(SupportCountry.of("FR"))
        assertNull(SupportCountry.of(""))
        assertNull(SupportCountry.of(null))
    }

    @Test
    fun thePageStartsFromThePhonesRegion() = runUnitTest {
        val vm = viewModel("AU")

        assertEquals(SupportCountry.Australia, vm.state.country)
        assertEquals(SupportDirectory.of(SupportCountry.Australia).crisis, vm.state.crisis)
        assertEquals(SupportDirectory.of(SupportCountry.Australia).lines, vm.state.lines)
    }

    @Test
    fun elsewhereHasOneLineFromEachOtherCountry() {
        val state = FindSupportStateBuilder.build(SupportCountry.UnitedStates)

        assertEquals(SupportCountry.entries - SupportCountry.UnitedStates, state.elsewhere.map { it.country })
        state.elsewhere.forEach { assertEquals(SupportDirectory.of(it.country).lines.first(), it) }
    }

    @Test
    fun aRegionWithNoListAsksForACountryAndListsEveryOne() = runUnitTest {
        val vm = viewModel("FR")

        assertNull(vm.state.country)
        assertNull(vm.state.crisis)
        assertTrue(vm.state.lines.isEmpty())
        assertEquals(SupportCountry.entries, vm.state.elsewhere.map { it.country })
    }

    @Test
    fun theCountryCanBeChanged_forThisVisitOnly() = runUnitTest {
        val vm = viewModel("US")

        vm.takeAction(FindSupportAction.CountryTapped)
        assertTrue(vm.state.pickingCountry)

        vm.takeAction(FindSupportAction.CountryPicked(SupportCountry.Ireland))
        assertEquals(FindSupportStateBuilder.build(SupportCountry.Ireland), vm.state)
        assertEquals(false, vm.state.pickingCountry)

        // A new visit starts from the region again: nothing was stored.
        assertEquals(SupportCountry.UnitedStates, viewModel("US").state.country)
    }

    @Test
    fun thePickerCloses() = runUnitTest {
        val vm = viewModel("US")

        vm.takeAction(FindSupportAction.CountryTapped)
        vm.takeAction(FindSupportAction.CountryPickerDismissed)

        assertEquals(false, vm.state.pickingCountry)
        assertEquals(SupportCountry.UnitedStates, vm.state.country)
    }

    // --- taps ---

    @Test
    fun theCrisisButtonsDialAndText() = runUnitTest {
        val links = FakeLinks()
        val vm = viewModel("GB", links)

        vm.takeAction(FindSupportAction.CrisisCallTapped)
        vm.takeAction(FindSupportAction.CrisisTextTapped)

        assertEquals(listOf("tel:116123", "sms:85258"), links.opened)
    }

    @Test
    fun eachLineOpensTheDialerMessagesOrTheBrowser() = runUnitTest {
        val links = FakeLinks()
        val vm = viewModel("US", links)
        val lines = vm.state.lines

        lines.forEach { vm.takeAction(FindSupportAction.LineTapped(it)) }

        assertEquals(
            listOf("tel:18883757767", "tel:18666621235", "sms:741741", PROVIDER_URL),
            links.opened,
        )
    }

    @Test
    fun aFailedOpenSaysTheNumber_andTheNextOpenClearsIt() = runUnitTest {
        val links = FakeLinks(opens = false)
        val vm = viewModel("NZ", links)

        vm.takeAction(FindSupportAction.CrisisCallTapped)
        assertEquals(CouldNotOpen("1737", isLink = false), vm.state.couldNotOpen)

        vm.takeAction(FindSupportAction.LineTapped(vm.state.lines.first()))
        assertEquals(CouldNotOpen("0800 233 269", isLink = false), vm.state.couldNotOpen)

        links.opens = true
        vm.takeAction(FindSupportAction.CrisisCallTapped)
        assertNull(vm.state.couldNotOpen)
    }

    @Test
    fun aFailedWebOpenSaysTheAddress() = runUnitTest {
        val vm = viewModel("US", FakeLinks(opens = false))
        val provider = vm.state.lines.single { it.reach is Reach.Web }

        vm.takeAction(FindSupportAction.LineTapped(provider))

        assertEquals(CouldNotOpen("findedhelp.com", isLink = true), vm.state.couldNotOpen)
    }

    @Test
    fun findAHelplineOpensForEveryOtherCountry_andSaysItsAddressWhenItCant() = runUnitTest {
        val links = FakeLinks()
        val vm = viewModel("FR", links)

        vm.takeAction(FindSupportAction.DirectoryTapped)
        assertEquals(listOf(FIND_A_HELPLINE_URL), links.opened)
        assertNull(vm.state.couldNotOpen)

        links.opens = false
        vm.takeAction(FindSupportAction.DirectoryTapped)
        assertEquals(CouldNotOpen("findahelpline.com", isLink = true), vm.state.couldNotOpen)
    }

    @Test
    fun backCloses_andNothingElseIsAnEvent() = runUnitTest {
        val vm = viewModel("US")
        val events = eventsOf(vm)

        vm.takeAction(FindSupportAction.CrisisCallTapped)
        vm.takeAction(FindSupportAction.CountryTapped)
        vm.takeAction(FindSupportAction.BackTapped)

        assertEquals(listOf<FindSupportEvent>(FindSupportEvent.Close), events)
    }

    // --- the list's shape ---

    @Test
    fun everyCountryHasACrisisLineAndAtLeastOneLine_eachWithASource() {
        SupportCountry.entries.forEach { country ->
            val lines = SupportDirectory.of(country)
            assertEquals(country, lines.crisis.country)
            assertTrue(lines.crisis.source.startsWith("https://"), "$country crisis source")
            assertTrue(lines.lines.isNotEmpty(), "$country has lines")
            lines.lines.forEach { line ->
                assertEquals(country, line.country, line.id)
                assertTrue(line.source.startsWith("https://"), "${line.id} source")
            }
        }
    }

    @Test
    fun dialStringsAreDigitsOnly() {
        val numbers = SupportCountry.entries.flatMap { country ->
            val lines = SupportDirectory.of(country)
            listOf(lines.crisis.call, lines.crisis.text) + lines.lines.mapNotNull {
                when (val reach = it.reach) {
                    is Reach.Call -> reach.number
                    is Reach.Text -> reach.number
                    is Reach.Web -> null
                }
            }
        }
        numbers.forEach { assertTrue(it.dial.all(Char::isDigit), "${it.shown} dials ${it.dial}") }
        assertNotNull(numbers.firstOrNull())
    }

    @Test
    fun ids_areUnique() {
        val ids = SupportCountry.entries.flatMap { SupportDirectory.of(it).lines.map(SupportLine::id) }
        assertEquals(ids.size, ids.toSet().size)
    }
}
