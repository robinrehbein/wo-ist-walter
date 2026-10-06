package de.wuselburg

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.wuselburg.core.Levels
import de.wuselburg.core.SceneBuilder
import de.wuselburg.render.recordScenePicture
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecorderTest {
    @Test fun recordsAllLevels() {
        for (l in Levels.all) {
            val p = recordScenePicture(SceneBuilder.build(l))
            assertEquals(2180, p.width)
        }
    }
}
