package com.visualjava.palette

/** Either a section header or a draggable widget row in the palette list. */
sealed class PaletteEntry {
    data class Header(val category: ComponentDescriptor.Category) : PaletteEntry()
    data class Item(val descriptor: ComponentDescriptor) : PaletteEntry()
}

object PaletteEntries {
    val all: List<PaletteEntry> = buildList {
        var last: ComponentDescriptor.Category? = null
        for (d in PaletteCatalog.all) {
            if (d.category != last) {
                add(PaletteEntry.Header(d.category))
                last = d.category
            }
            add(PaletteEntry.Item(d))
        }
    }
}
