package me.proton.jobforandroid.fitliroutegps

import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import me.proton.jobforandroid.fitliroutegps.fragments.MainFragment
import me.proton.jobforandroid.fitliroutegps.fragments.SettingsFragment
import me.proton.jobforandroid.fitliroutegps.fragments.TracksFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityRecreationTest {

    @Test fun firstLaunchOpensHome() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(currentFragment(activity) is MainFragment)
    }

    @Test fun recreationKeepsSettingsAndBottomSelection() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<BottomNavigationView>(R.id.bNan).selectedItemId = R.id.setting_menu
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(currentFragment(activity) is SettingsFragment)

        val recreated = controller.recreate().get()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(currentFragment(recreated) is SettingsFragment)
        assertEquals(R.id.setting_menu,
            recreated.findViewById<BottomNavigationView>(R.id.bNan).selectedItemId)
    }

    @Test fun recreationKeepsTracksAndNavigationStillWorks() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.get().findViewById<BottomNavigationView>(R.id.bNan).selectedItemId = R.id.trucks_menu
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val recreated = controller.recreate().get()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(currentFragment(recreated) is TracksFragment)
        assertEquals(R.id.trucks_menu,
            recreated.findViewById<BottomNavigationView>(R.id.bNan).selectedItemId)

        recreated.findViewById<BottomNavigationView>(R.id.bNan).selectedItemId = R.id.setting_menu
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(currentFragment(recreated) is SettingsFragment)
    }

    private fun currentFragment(activity: MainActivity): Fragment? =
        activity.supportFragmentManager.findFragmentById(R.id.placeHolder)
}
