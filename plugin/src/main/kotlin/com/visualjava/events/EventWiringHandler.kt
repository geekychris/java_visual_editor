package com.visualjava.events

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.xml.XmlFile
import com.visualjava.codegen.ControllerCodeGenerator
import com.visualjava.codegen.FxmlControllerResolver
import com.visualjava.preview.PreviewClient
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * Orchestrates "wire this event" across the FXML, the controller class, and
 * the editor caret. One entrypoint per UI gesture.
 */
class EventWiringHandler(
    private val project: Project,
    private val fxmlFile: XmlFile,
) {

    private val log = thisLogger()
    private val codeGen = ControllerCodeGenerator(project)
    private val controllerResolver = FxmlControllerResolver(project)

    /** The VB6 "double-click → default event" path. */
    fun wireDefault(node: PreviewClient.NodeBounds) {
        val event = defaultEventFor(node)
        wireEvent(node, event)
    }

    /** A specific event (e.g., from the right-click menu). */
    fun wireEvent(node: PreviewClient.NodeBounds, event: EventDescriptor) {
        val fxmlTagName = codeGen.findTagByFxId(fxmlFile, node.fxId)?.localName ?: return
        val methodName = event.methodName(node.fxId)

        val generatedMethod = mutableListOf<PsiMethod>()
        try {
            WriteCommandAction.runWriteCommandAction(
                project,
                "Add ${event.property} handler",
                null,
                {
                    val controller = controllerResolver.findOrCreateController(fxmlFile)
                    codeGen.ensureField(controller, node.fxId, fxmlTagName)
                    val example = EventHandlerExamples.exampleFor(fxmlTagName, event.property, node.fxId)
                    val method = codeGen.ensureHandlerWithBody(
                        controller, methodName, event.eventClassFqn, example,
                    )
                    codeGen.wireFxmlEvent(fxmlFile, node.fxId, event.property, methodName)
                    generatedMethod += method
                },
            )
        } catch (e: FxmlControllerResolver.NoSuitableTargetException) {
            log.warn("Cannot wire event ${event.property}: ${e.message}")
            notify("Cannot wire ${event.property}", e.message ?: "No suitable controller location.", NotificationType.WARNING)
            return
        } catch (e: Throwable) {
            log.error("Unexpected failure wiring event ${event.property}", e)
            notify("Failed to wire ${event.property}", "${e.javaClass.simpleName}: ${e.message}", NotificationType.ERROR)
            return
        }

        generatedMethod.firstOrNull()?.let { navigateInto(it) }
    }

    private fun notify(title: String, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Visual Java")
            .createNotification(title, message, type)
            .notify(project)
    }

    fun buildPopupMenu(node: PreviewClient.NodeBounds): JPopupMenu {
        val menu = JPopupMenu()
        val default = defaultEventFor(node)
        val all = allEventsFor(node)
        all.forEach { event ->
            val suffix = if (event == default) "   (default)" else ""
            val item = JMenuItem("Add ${event.property}$suffix")
            item.addActionListener { wireEvent(node, event) }
            menu.add(item)
        }
        return menu
    }

    fun defaultEventFor(node: PreviewClient.NodeBounds): EventDescriptor {
        val tagName = codeGen.findTagByFxId(fxmlFile, node.fxId)?.localName ?: "Node"
        return EventCatalog.defaultFor(tagName)
    }

    fun allEventsFor(node: PreviewClient.NodeBounds): List<EventDescriptor> {
        val tagName = codeGen.findTagByFxId(fxmlFile, node.fxId)?.localName ?: "Node"
        return EventCatalog.allFor(tagName)
    }

    private fun navigateInto(method: PsiMethod) {
        val body = method.body
        val virtualFile = method.containingFile?.virtualFile ?: return
        val offset = body?.lBrace?.let { it.textOffset + 1 }
            ?: body?.textRange?.startOffset?.plus(1)
            ?: method.textRange.startOffset
        OpenFileDescriptor(project, virtualFile, offset).navigate(true)
    }

    /**
     * Find every fxId-bearing widget in the form whose canonical event is
     * NOT yet wired (no on*= attribute). Returns the (node, event) pairs
     * the user could batch-wire.
     */
    fun findUnwired(nodes: List<PreviewClient.NodeBounds>): List<Pair<PreviewClient.NodeBounds, EventDescriptor>> {
        return nodes.mapNotNull { node ->
            val tag = codeGen.findTagByFxId(fxmlFile, node.fxId) ?: return@mapNotNull null
            val event = EventCatalog.defaultFor(tag.localName)
            val already = tag.getAttributeValue(event.property)
            if (already.isNullOrBlank()) node to event else null
        }
    }

    /** Wire all of the given (node, event) pairs in a single undo step. */
    fun wireBulk(pairs: List<Pair<PreviewClient.NodeBounds, EventDescriptor>>) {
        if (pairs.isEmpty()) return
        try {
            WriteCommandAction.runWriteCommandAction(
                project,
                "Wire ${pairs.size} events",
                null,
                {
                    val controller = controllerResolver.findOrCreateController(fxmlFile)
                    for ((node, event) in pairs) {
                        val tagName = codeGen.findTagByFxId(fxmlFile, node.fxId)?.localName ?: continue
                        val methodName = event.methodName(node.fxId)
                        codeGen.ensureField(controller, node.fxId, tagName)
                        val body = EventHandlerExamples.exampleFor(tagName, event.property, node.fxId)
                        codeGen.ensureHandlerWithBody(controller, methodName, event.eventClassFqn, body)
                        codeGen.wireFxmlEvent(fxmlFile, node.fxId, event.property, methodName)
                    }
                },
            )
        } catch (e: FxmlControllerResolver.NoSuitableTargetException) {
            log.warn("Cannot bulk-wire: ${e.message}")
            notify("Cannot bulk-wire", e.message ?: "No suitable controller location.", NotificationType.WARNING)
        } catch (e: Throwable) {
            log.error("Unexpected failure during bulk wire", e)
            notify("Bulk wire failed", "${e.javaClass.simpleName}: ${e.message}", NotificationType.ERROR)
        }
    }
}
