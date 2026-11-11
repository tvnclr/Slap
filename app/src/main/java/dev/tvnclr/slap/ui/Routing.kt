package dev.tvnclr.slap.ui

import java.util.Base64

/**
 * A URI carried inside a navigation route. Base64url has no '%' or reserved characters, so the
 * Navigation library's own decoding cannot change it: an encoded document URI such as
 * `.../document/image%3A17428` arrives exactly as it was granted (decoding it twice turned it into
 * `image:17428`, a different URI the picker's permission does not cover).
 */
internal object RouteArg {
  private val enc = Base64.getUrlEncoder().withoutPadding()
  private val dec = Base64.getUrlDecoder()

  fun encode(value: String): String = enc.encodeToString(value.toByteArray(Charsets.UTF_8))

  fun decode(arg: String): String = runCatching { String(dec.decode(arg), Charsets.UTF_8) }.getOrDefault("")
}

/** What a file shared from another app turned out to be. */
internal enum class ShareCheck {
  STILL,
  MOVING,
  /** The sharing app did not grant read access, or the file cannot be read at all. */
  UNREADABLE,
}

/** Runs the kind check and turns a missing permission (SecurityException) or any failure into [ShareCheck.UNREADABLE]. */
internal fun checkShared(isMoving: () -> Boolean): ShareCheck =
    try {
      if (isMoving()) ShareCheck.MOVING else ShareCheck.STILL
    } catch (e: SecurityException) {
      ShareCheck.UNREADABLE
    } catch (e: Exception) {
      ShareCheck.UNREADABLE
    }
