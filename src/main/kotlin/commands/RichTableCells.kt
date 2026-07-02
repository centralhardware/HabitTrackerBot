package commands

import dev.inmo.tgbotapi.types.rich.RichBlockTableCell
import dev.inmo.tgbotapi.types.rich.RichText
import dev.inmo.tgbotapi.types.rich.RichTextPlain
import dev.inmo.tgbotapi.types.rich.buildRichText

/** Helpers for building left-aligned [RichBlockTableCell]s used by the /tracks and /log tables. */

internal fun cell(text: RichText) = RichBlockTableCell(text = text, align = "left", valign = "top")

internal fun plainCell(text: String) = cell(RichTextPlain(text))

internal fun boldCell(text: String) = cell(buildRichText { bold(text) })
