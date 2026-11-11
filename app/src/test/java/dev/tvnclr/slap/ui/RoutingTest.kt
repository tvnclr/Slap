package dev.tvnclr.slap.ui

import java.net.URLDecoder
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoutingTest {

  // URI from Files > Recent: the "%3A" is part of the document id the permission was granted for
  private val docUri = "content://com.android.providers.media.documents/document/image%3A17428"

  /** What the Navigation library does to a route argument: one percent-decoding pass. */
  private fun navDecode(s: String) = URLDecoder.decode(s, "UTF-8")

  @Test
  fun oldEncodingChangedTheUri() {
    // Uri.encode + Navigation's decoding + the extra Uri.decode of 1.1.0 dev 9
    val arrived = navDecode(navDecode(URLEncoder.encode(docUri, "UTF-8")))
    assertNotEquals(docUri, arrived)
    assertEquals("content://com.android.providers.media.documents/document/image:17428", arrived)
  }

  @Test
  fun routeArgSurvivesNavigationDecoding() {
    for (u in listOf(docUri, "content://media/picker/0/com.android.providers.media.photopicker/media/1000", "file:///data/user/0/dev.tvnclr.slap/files/packs/a b/src/s1.webp.src", "content://x/é%25+?&=#")) {
      val arg = RouteArg.encode(u)
      assertEquals(u, RouteArg.decode(navDecode(arg)))
    }
  }

  @Test
  fun badArgumentGivesEmpty() {
    assertEquals("", RouteArg.decode("%%%not base64%%%"))
  }

  @Test
  fun sharedFileWithoutPermissionIsUnreadable() {
    assertEquals(ShareCheck.UNREADABLE, checkShared { throw SecurityException("dev.tvnclr.slap has no access to content://media/external/images/media/1") })
    assertEquals(ShareCheck.UNREADABLE, checkShared { throw IllegalStateException("gone") })
    assertEquals(ShareCheck.MOVING, checkShared { true })
    assertEquals(ShareCheck.STILL, checkShared { false })
  }
}
