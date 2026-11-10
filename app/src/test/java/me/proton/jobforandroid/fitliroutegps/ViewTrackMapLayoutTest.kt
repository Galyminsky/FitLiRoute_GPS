package me.proton.jobforandroid.fitliroutegps

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewTrackMapLayoutTest {
    @Test fun mapFillsItsConstrainedParent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_view_track, null, false) as ConstraintLayout
        val map = root.findViewById<ViewGroup>(R.id.map)
        val params = map.layoutParams as ConstraintLayout.LayoutParams

        assertEquals(0, params.width)
        assertEquals(0, params.height)
        assertEquals(0, params.startToStart)
        assertEquals(0, params.endToEnd)
        assertEquals(0, params.topToTop)
        assertEquals(0, params.bottomToBottom)
    }
}
