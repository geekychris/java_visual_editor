package com.visualjava.palette

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/** Wraps a [ComponentDescriptor] for Swing drag-and-drop. */
class ComponentTransferable(val descriptor: ComponentDescriptor) : Transferable {

    companion object {
        val FLAVOR: DataFlavor = DataFlavor(
            ComponentDescriptor::class.java,
            "Visual Java component",
        )
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(FLAVOR)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == FLAVOR
    override fun getTransferData(flavor: DataFlavor): Any {
        if (flavor != FLAVOR) throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
        return descriptor
    }
}
