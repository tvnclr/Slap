package dev.tvnclr.slap.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.tvnclr.ui.TvMotion

private object Routes {
  const val HOME = "home"
  const val SHARE = "share"
  const val PACK = "pack/{id}"
  const val EDITOR = "editor/{pack}?uri={uri}&edit={edit}"
  const val SETTINGS = "settings"
  const val ABOUT = "about"

  fun pack(id: String) = "pack/$id"

  fun editor(pack: String, uri: Uri, edit: String? = null) = "editor/$pack?uri=${Uri.encode(uri.toString())}&edit=${edit.orEmpty()}"
}

/**
 * Whole app. Started from the launcher it opens on the pack list; started from another app's share
 * menu ([sharedUri]) it asks which pack the file goes into, and closes once the sticker is saved.
 */
@Composable
fun SlapApp(sharedUri: Uri?, onFinish: () -> Unit) {
  val nav = rememberNavController()
  val fromShare = sharedUri != null
  NavHost(
      navController = nav,
      startDestination = if (fromShare) Routes.SHARE else Routes.HOME,
      enterTransition = { fadeIn(tween(TvMotion.normal)) },
      exitTransition = { fadeOut(tween(TvMotion.fast)) },
      popEnterTransition = { fadeIn(tween(TvMotion.normal)) },
      popExitTransition = { fadeOut(tween(TvMotion.fast)) },
  ) {
    composable(Routes.HOME) {
      HomeScreen(
          onOpenPack = { nav.navigate(Routes.pack(it)) },
          onSettings = { nav.navigate(Routes.SETTINGS) },
          onAbout = { nav.navigate(Routes.ABOUT) },
      )
    }
    composable(Routes.SHARE) {
      ShareScreen(
          uri = sharedUri!!,
          onPick = { pack -> nav.navigate(Routes.editor(pack, sharedUri)) },
          onClose = onFinish,
      )
    }
    composable(Routes.PACK, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
      PackScreen(
          packId = entry.arguments?.getString("id").orEmpty(),
          onBack = { nav.popBackStack() },
          onEdit = { pack, uri -> nav.navigate(Routes.editor(pack, uri)) },
          onEditSticker = { pack, file, uri -> nav.navigate(Routes.editor(pack, uri, file)) },
      )
    }
    composable(
        Routes.EDITOR,
        arguments =
            listOf(
                navArgument("pack") { type = NavType.StringType },
                navArgument("uri") { type = NavType.StringType },
                navArgument("edit") {
                  type = NavType.StringType
                  defaultValue = ""
                },
            ),
    ) { entry ->
      val uri = Uri.parse(Uri.decode(entry.arguments?.getString("uri").orEmpty()))
      EditorScreen(
          packId = entry.arguments?.getString("pack").orEmpty(),
          uri = uri,
          editFile = entry.arguments?.getString("edit")?.takeIf { it.isNotEmpty() },
          onDone = { saved ->
            if (fromShare && saved) onFinish() else nav.popBackStack()
          },
      )
    }
    composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
  }
}
