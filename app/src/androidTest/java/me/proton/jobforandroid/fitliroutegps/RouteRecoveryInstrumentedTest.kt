package me.proton.jobforandroid.fitliroutegps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.proton.jobforandroid.fitliroutegps.db.MainDB
import me.proton.jobforandroid.fitliroutegps.location.DraftPoint
import me.proton.jobforandroid.fitliroutegps.location.LocationService
import me.proton.jobforandroid.fitliroutegps.location.TrackDraft
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftStore
import me.proton.jobforandroid.fitliroutegps.viewmodel.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ThreadLocalRandom

@RunWith(AndroidJUnit4::class)
class RouteRecoveryInstrumentedTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store: TrackDraftStore get() = TrackDraftStore(context)
    private val dao get() = MainDB.getDatabase(context).getDao()
    private var ownedId: Int? = null
    private var startedService = false

    @Before fun protectExistingData() {
        assumeTrue("Existing unfinished route must be left untouched", store.read() == null)
        assumeTrue("Existing recording must be left untouched", !LocationService.isRunning)
    }

    @After fun removeOnlyTestData() {
        if (startedService) context.stopService(Intent(context, LocationService::class.java))
        val id = ownedId ?: return
        val draft = runCatching { store.read() }.getOrNull()
        if (draft?.id == id) store.delete()
        runBlocking {
            val item = dao.getTrackById(id)
            if (item?.geoPoints?.startsWith("52.123456,62.123456/") == true) dao.deleteTrack(item)
        }
    }

    private fun seedDraft(): TrackDraft {
        val now = System.currentTimeMillis()
        val id = ThreadLocalRandom.current().nextInt(100_000_000, Int.MAX_VALUE)
        assumeTrue("Test ID collides with an existing route", runBlocking { dao.getTrackById(id) == null })
        val draft = TrackDraft(id, true, now - 20_000L, 100f, 1.2f,
            DraftPoint(52.123457, 62.123457),
            listOf(DraftPoint(52.123456, 62.123456), DraftPoint(52.123457, 62.123457)),
            2L, now)
        store.write(draft)
        ownedId = id
        return draft
    }

    private fun await(message: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 12_000L
        while (System.currentTimeMillis() < until) {
            if (condition()) return
            Thread.sleep(150)
        }
        throw AssertionError(message)
    }

    private fun clickTextWhenReady(text: String) {
        await("Missing UI action: $text") {
            try { onView(withText(text)).perform(click()); true }
            catch (_: Exception) { false }
            catch (_: AssertionError) { false }
        }
    }

    private fun launchApp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "am start -n ${context.packageName}/.MainActivity -f 0x10008000"
        ).close()
    }

    @Test fun recoveredDraftCanBeDeletedInUi() {
        seedDraft()
        launchApp()
        clickTextWhenReady(context.resources.getStringArray(R.array.draft_actions)[2])
        await("Draft was not deleted") { store.read() == null }
    }

    @Test fun recoveredDraftSavesOnceInRoomAndClearsFile() {
        val draft = seedDraft()
        launchApp()
        clickTextWhenReady(context.resources.getStringArray(R.array.draft_actions)[1])
        onView(withId(R.id.btnSave_dialog)).perform(click())
        await("Route was not saved and draft cleared") {
            runBlocking { dao.getTrackById(draft.id) != null } && store.read() == null
        }
        val item = runBlocking { dao.getTrackById(draft.id) }
        assertNotNull(item)
        assertTrue(runBlocking { MainViewModel(MainDB.getDatabase(context)).saveRecoveredTrack(item!!) })
        assertEquals(item, runBlocking { dao.getTrackById(draft.id) })
    }

    @Test fun recoveredDraftContinuesAndStopCheckpointsBeforeDeletion() {
        val draft = seedDraft()
        assumeTrue("Fine location permission required", ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        assumeTrue("GPS provider required", manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        launchApp()
        clickTextWhenReady(context.resources.getStringArray(R.array.draft_actions)[0])
        startedService = true
        await("Service did not resume") { LocationService.isRunning }
        val resumed = store.read()
        assertEquals(draft.id, resumed?.id)
        assertTrue(resumed?.recording == true)
        onView(withId(R.id.fStartStop)).perform(click())
        onView(withId(R.id.fStartStop)).perform(click())
        await("STOP did not checkpoint") { store.read()?.recording == false }
        assertFalse(LocationService.isRunning)
        onView(withId(R.id.btnCancel_dialog)).perform(click())
        await("Canceled draft was not deleted") { store.read() == null }
    }

    @Test fun newStartAndRepeatedStopLeaveOneCheckpoint() {
        assumeTrue("Fine location permission required", ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        assumeTrue("GPS provider required", manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        launchApp()
        onView(withId(R.id.fStartStop)).perform(click())
        startedService = true
        await("START did not create a running draft") {
            LocationService.isRunning && store.read()?.recording == true
        }
        ownedId = store.read()!!.id
        onView(withId(R.id.fStartStop)).perform(click())
        onView(withId(R.id.fStartStop)).perform(click())
        await("STOP did not persist the checkpoint") { store.read()?.recording == false }
        val stopped = store.read()!!
        assertEquals(ownedId, stopped.id)
        if (stopped.points.isEmpty()) {
            clickTextWhenReady(context.getString(R.string.draft_delete))
        } else {
            onView(withId(R.id.btnCancel_dialog)).perform(click())
        }
        await("Test draft was not removed after explicit cancel") { store.read() == null }
    }
}
