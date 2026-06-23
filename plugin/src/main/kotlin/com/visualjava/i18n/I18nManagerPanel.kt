package com.visualjava.i18n

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Properties
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JOptionPane
import javax.swing.table.AbstractTableModel

/**
 * Authoring view for ResourceBundle .properties files.
 *
 * Picks one *base* bundle name (e.g. `strings`) and shows every key across the
 * detected locales as a single editable grid:
 *
 *   key            | strings (default) | strings_de | strings_fr
 *   app.title      | My App            | Meine App  |
 *   button.save    | Save              |            | Sauver
 *
 * Locale columns auto-detected from sibling files (`strings_xx[_YY].properties`).
 * Empty cells = missing translation (highlight in dim grey).
 *
 * Save commits all locales atomically. New rows added at bottom; new locales
 * via "+ Locale".
 *
 * Out of scope: machine translation, pluralisation rules, lint for unused keys.
 */
class I18nManagerPanel(private val project: Project) :
    JBPanel<I18nManagerPanel>(BorderLayout()) {

    private val bundleCombo = JComboBox<String>()
    private val model = I18nTableModel()
    private val table = JBTable(model).apply {
        rowHeight = JBUI.scale(22)
        showHorizontalLines = true
    }
    private var currentBundleDir: VirtualFile? = null

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        bundleCombo.addActionListener { onBundlePicked(bundleCombo.selectedItem as? String) }
        detectBundles()
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // Double-click on a cell to open the .properties file for that locale.
                if (e.clickCount != 2) return
                val col = table.columnAtPoint(e.point)
                if (col <= 0) return
                val locale = model.locales[col - 1]
                val dir = currentBundleDir ?: return
                val base = bundleCombo.selectedItem as? String ?: return
                val name = if (locale.isEmpty()) "$base.properties" else "${base}_$locale.properties"
                dir.findChild(name)?.let { FileEditorManager.getInstance(project).openFile(it, true) }
            }
        })
    }

    private fun buildToolbar(): JBPanel<*> {
        val bar = JBPanel<JBPanel<*>>(BorderLayout()).apply { border = JBUI.Borders.empty(4) }
        val left = JBPanel<JBPanel<*>>().apply {
            add(JBLabel("Bundle:")); add(bundleCombo)
        }
        val right = JBPanel<JBPanel<*>>().apply {
            add(JButton("+ Key").apply { addActionListener { addKey() } })
            add(JButton("+ Locale").apply { addActionListener { addLocale() } })
            add(JButton("Save").apply { addActionListener { save() } })
            add(JButton("Refresh").apply { addActionListener { detectBundles() } })
        }
        bar.add(left, BorderLayout.WEST)
        bar.add(right, BorderLayout.EAST)
        return bar
    }

    private fun detectBundles() {
        bundleCombo.removeAllItems()
        for (root in resourceRoots()) {
            walkBundles(root) { dir, base ->
                bundleCombo.addItem(base)  // simplest UX: pick by base name, first one wins
                if (currentBundleDir == null) currentBundleDir = dir
            }
        }
        if (bundleCombo.itemCount == 0) {
            model.replace(emptyList(), emptyList(), emptyMap())
        }
    }

    private fun walkBundles(dir: VirtualFile, visit: (VirtualFile, String) -> Unit) {
        val seenBases = mutableSetOf<String>()
        for (child in dir.children) {
            if (child.isDirectory) {
                walkBundles(child, visit)
                continue
            }
            if (child.extension != "properties") continue
            val base = child.nameWithoutExtension.substringBefore('_')
            if (base !in seenBases) {
                seenBases += base
                visit(dir, base)
            }
        }
    }

    private fun onBundlePicked(base: String?) {
        val baseName = base ?: return
        val dir = currentBundleDir ?: return
        val locales = mutableListOf<String>()
        val perLocaleProps = linkedMapOf<String, Properties>()
        for (child in dir.children) {
            if (child.extension != "properties") continue
            val name = child.nameWithoutExtension
            if (name == baseName) {
                locales.add(""); perLocaleProps[""] = loadProps(child)
            } else if (name.startsWith("${baseName}_")) {
                val loc = name.removePrefix("${baseName}_")
                locales.add(loc); perLocaleProps[loc] = loadProps(child)
            }
        }
        val allKeys = perLocaleProps.values.flatMap { it.stringPropertyNames() }.distinct().sorted()
        model.replace(allKeys, locales, perLocaleProps.mapValues { (_, p) -> p.stringPropertyNames().associateWith { p.getProperty(it).orEmpty() } })
    }

    private fun loadProps(vf: VirtualFile): Properties {
        val p = Properties()
        ReadAction.compute<Unit, RuntimeException> { vf.inputStream.use { p.load(it) } }
        return p
    }

    private fun addKey() {
        val k = Messages.showInputDialog(project, "Key:", "Add key", null) ?: return
        if (k.isBlank()) return
        model.addKey(k.trim())
    }

    private fun addLocale() {
        val loc = Messages.showInputDialog(project, "Locale code (e.g. de, fr_CA):", "Add locale", null) ?: return
        if (loc.isBlank()) return
        model.addLocale(loc.trim())
    }

    private fun save() {
        val dir = currentBundleDir ?: return
        val base = bundleCombo.selectedItem as? String ?: return
        ApplicationManager.getApplication().runWriteAction {
            for (locale in model.locales) {
                val fileName = if (locale.isEmpty()) "$base.properties" else "${base}_$locale.properties"
                val text = buildString {
                    for (k in model.keys) {
                        val v = model.valueOf(k, locale)
                        if (v.isEmpty()) continue
                        append(k).append("=").append(v.replace("\n", "\\n")).append("\n")
                    }
                }
                val existing = dir.findChild(fileName)
                val target = existing ?: dir.createChildData(this, fileName)
                target.setBinaryContent(text.toByteArray(Charsets.UTF_8))
            }
        }
        JOptionPane.showMessageDialog(this, "Saved ${model.locales.size} locale file(s).")
    }

    private fun resourceRoots(): List<VirtualFile> = ReadAction.compute<List<VirtualFile>, RuntimeException> {
        ModuleManager.getInstance(project).modules
            .flatMap { ModuleRootManager.getInstance(it).getSourceRoots(true).toList() }
            .filter { it.path.contains("/resources/") || it.path.endsWith("/resources") }
            .distinct()
    }
}

class I18nTableModel : AbstractTableModel() {
    /** Keys in row order. */
    var keys: MutableList<String> = mutableListOf()
        private set

    /** Locales in column order (column 0 is the key, columns 1..N are locales). */
    var locales: MutableList<String> = mutableListOf()
        private set

    private val data = linkedMapOf<String, MutableMap<String, String>>()  // locale -> (key -> value)

    fun replace(allKeys: List<String>, locs: List<String>, props: Map<String, Map<String, String>>) {
        keys = allKeys.toMutableList()
        locales = locs.toMutableList()
        data.clear()
        for (loc in locs) data[loc] = props[loc].orEmpty().toMutableMap()
        fireTableStructureChanged()
    }

    fun addKey(k: String) {
        if (keys.contains(k)) return
        keys.add(k)
        fireTableRowsInserted(keys.size - 1, keys.size - 1)
    }

    fun addLocale(loc: String) {
        if (locales.contains(loc)) return
        locales.add(loc)
        data[loc] = mutableMapOf()
        fireTableStructureChanged()
    }

    fun valueOf(key: String, locale: String): String = data[locale]?.get(key).orEmpty()

    override fun getRowCount(): Int = keys.size
    override fun getColumnCount(): Int = 1 + locales.size
    override fun getColumnName(column: Int): String =
        if (column == 0) "Key" else locales[column - 1].ifEmpty { "(default)" }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val key = keys[rowIndex]
        return if (columnIndex == 0) key else valueOf(key, locales[columnIndex - 1])
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val newText = aValue?.toString().orEmpty()
        val key = keys[rowIndex]
        if (columnIndex == 0) {
            if (newText.isNotBlank() && newText != key) {
                // Rename: copy values onto the new key in every locale.
                for (loc in locales) {
                    val map = data.getOrPut(loc) { mutableMapOf() }
                    val v = map.remove(key)
                    if (v != null) map[newText] = v
                }
                keys[rowIndex] = newText
            }
        } else {
            val loc = locales[columnIndex - 1]
            data.getOrPut(loc) { mutableMapOf() }[key] = newText
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }
}
