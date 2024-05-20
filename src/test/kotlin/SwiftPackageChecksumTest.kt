import store.kmpd.utils.swiftPackageChecksum
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SwiftPackageChecksumTest {

    @Test
    fun test() {
        assertEquals(
            "96fa8f226d3801741e807533552bc4b177ac4544d834073b6a5298934d34b40b",
            swiftPackageChecksum(
                File(assertNotNull(javaClass.classLoader.getResource("checkChecksum.zip")).file),
            ),
        )
    }
}