package com.visualjava.inspector

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractCellEditor
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Renders a space-separated styleClass attribute as removable pill "chips" plus
 * a "+ Add" affordance. The cell value (the actual FXML attribute) is still a
 * single space-separated string — the renderer just makes it click-friendly.
 */
class StyleClassCellRenderer : TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
    ): Component {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        panel.background = if (isSelected) table.selectionBackground else table.background
        val classes = (value?.toString().orEmpty()).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (classes.isEmpty()) {
            panel.add(JLabel("(none)").apply { foreground = JBColor.GRAY })
        } else {
            for (c in classes) panel.add(chip(c))
        }
        return panel
    }
    private fun chip(text: String): JLabel = JLabel(text).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(120, 160, 220), Color(80, 110, 160)), 1, true),
            BorderFactory.createEmptyBorder(1, 6, 1, 6),
        )
        background = JBColor(Color(220, 230, 250), Color(40, 60, 90))
        isOpaque = true
    }
}

/**
 * Edit styleClass via a small dialog-like row: text field for the
 * space-separated value, plus a "× class" removable chip per existing entry
 * that strips itself out on click. Commits on Enter or focus loss.
 */
class StyleClassCellEditor : AbstractCellEditor(), TableCellEditor {
    private val field = JTextField()
    private val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))

    override fun getCellEditorValue(): Any = field.text.trim()

    override fun getTableCellEditorComponent(
        table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int,
    ): Component {
        panel.removeAll()
        field.text = value?.toString().orEmpty()
        field.columns = 12
        field.addActionListener { stopCellEditing() }
        panel.add(field)
        val existing = field.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (c in existing) {
            val removable = JLabel("× $c").apply {
                toolTipText = "Remove '$c'"
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JBColor.GRAY, 1, true),
                    BorderFactory.createEmptyBorder(1, 6, 1, 6),
                )
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        field.text = field.text
                            .split(Regex("\\s+"))
                            .filter { it.isNotBlank() && it != c }
                            .joinToString(" ")
                        stopCellEditing()
                    }
                })
            }
            panel.add(removable)
        }
        return panel
    }
}

/**
 * Edit ImageView.image: shows the current URL + a "Browse…" button that opens
 * a file chooser scoped to the project. On pick, copies (well, references) the
 * file by resource URL (`@image.png`) so JavaFX's FXML resource loader can find
 * it when the form is rendered.
 */
class ImageCellEditor(private val project: Project) : AbstractCellEditor(), TableCellEditor {
    private val field = JTextField()
    private val browse = JButton("Browse…")
    private val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).also {
        it.add(field); it.add(browse)
    }

    init {
        field.columns = 18
        field.addActionListener { stopCellEditing() }
        browse.addActionListener {
            val desc = FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withFileFilter { vf ->
                    val ext = vf.extension?.lowercase()
                    ext in setOf("png", "jpg", "jpeg", "gif", "bmp")
                }
                .withTitle("Pick an image")
            val picked = FileChooser.chooseFile(desc, project, null) ?: return@addActionListener
            val projectRoot = project.basePath?.let { java.io.File(it) }
            val rel = projectRoot?.let { VfsUtil.getRelativePath(picked, com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(it)!!, '/') }
            // FXML's @ prefix resolves relative to the FXML file's package; fall
            // back to an absolute file: URL when the file isn't inside the project.
            field.text = rel?.let { "@/$it" } ?: picked.url
            stopCellEditing()
        }
    }

    override fun getCellEditorValue(): Any = field.text.trim()
    override fun getTableCellEditorComponent(
        table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int,
    ): Component {
        field.text = value?.toString().orEmpty()
        return panel
    }
}
