package com.visualjava.preview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;

/**
 * Sidecar JavaFX renderer.
 *
 * Listens on a localhost-bound TCP port. Prints "PORT=<n>\n" to stdout once
 * bound so the parent plugin can discover the port. Accepts newline-delimited
 * JSON commands per the protocol in the plan; one connection at a time is fine
 * for v1 (the plugin opens a single persistent socket per project).
 */
public final class PreviewRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        // Start the JavaFX runtime up-front so the first render isn't a cold start.
        CountDownLatch fxReady = new CountDownLatch(1);
        Platform.startup(fxReady::countDown);
        fxReady.await();
        Platform.setImplicitExit(false);

        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            System.out.println("PORT=" + server.getLocalPort());
            System.out.flush();

            while (true) {
                Socket client = server.accept();
                handleClient(client);
            }
        }
    }

    private static void handleClient(Socket client) {
        Thread t = new Thread(() -> {
            try (client;
                 BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    String response = dispatch(line);
                    out.write(response);
                    out.write('\n');
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        }, "preview-client");
        t.setDaemon(true);
        t.start();
    }

    private static String dispatch(String json) {
        try {
            JsonNode req = MAPPER.readTree(json);
            String op = req.path("op").asText("");
            return switch (op) {
                case "ping" -> reply("pong");
                case "render" -> render(req);
                case "shutdown" -> {
                    Platform.exit();
                    System.exit(0);
                    yield reply("bye");
                }
                default -> error("unknown op: " + op);
            };
        } catch (Exception e) {
            return error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String render(JsonNode req) throws Exception {
        String fxml = req.path("fxml").asText();
        int width = req.path("width").asInt(800);
        int height = req.path("height").asInt(600);

        java.util.List<String> stylesheets = new java.util.ArrayList<>();
        JsonNode stylesNode = req.path("stylesheets");
        if (stylesNode.isArray()) {
            for (JsonNode s : stylesNode) stylesheets.add(s.asText());
        }

        String sanitised = sanitiseForPreview(fxml);

        FxResult result = onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader();
            loader.setControllerFactory(c -> new Object());
            Parent root = loader.load(new ByteArrayInputStream(sanitised.getBytes(StandardCharsets.UTF_8)));

            populateSampleData(root);

            Scene scene = new Scene(root, width, height);
            // Live CSS: apply project stylesheets in order. The plugin sends
            // file:/// or file: URLs; bad ones are silently ignored by JavaFX.
            for (String url : stylesheets) {
                try { scene.getStylesheets().add(url); } catch (Exception ignored) {}
            }
            root.applyCss();
            root.layout();

            SnapshotParameters params = new SnapshotParameters();
            WritableImage image = new WritableImage(width, height);
            scene.snapshot(image);

            BufferedImage awt = SwingFXUtils.fromFXImage(image, null);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(awt, "png", png);

            ArrayNode nodes = MAPPER.createArrayNode();
            walk(root, nodes);

            return new FxResult(Base64.getEncoder().encodeToString(png.toByteArray()), nodes);
        });

        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("op", "frame");
        resp.put("pngBase64", result.pngBase64);
        resp.set("nodes", result.nodes);
        return MAPPER.writeValueAsString(resp);
    }

    /**
     * Fill any TableView in the scene graph with a few placeholder rows so the
     * canvas shows column headers AND realistic-looking content. The user's
     * real PropertyValueFactory / cellValueFactory bindings don't run in the
     * preview (we don't have user beans), so we install a preview cellFactory
     * that just shows "<column-text> <row-number>". Rows are wiped + replaced
     * each render so user edits to columns reflect immediately.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void populateSampleData(Node root) {
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            if (n instanceof TableView<?> tv) {
                ObservableList<Object> rows = FXCollections.observableArrayList();
                for (int i = 1; i <= 5; i++) rows.add(new Object());
                ((TableView<Object>) tv).setItems(rows);
                int row = 0;
                for (TableColumn<?, ?> col : tv.getColumns()) {
                    final int rowMarker = row++;
                    String header = col.getText() == null || col.getText().isBlank() ? "Col" : col.getText();
                    ((TableColumn) col).setCellFactory(c -> new TableCell<Object, Object>() {
                        @Override protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || getTableRow() == null || getTableRow().getIndex() < 0) {
                                setText(null);
                            } else {
                                setText(header + " " + (getTableRow().getIndex() + 1));
                            }
                        }
                    });
                }
            }
            if (n instanceof Parent p) {
                for (Node child : p.getChildrenUnmodifiable()) stack.push(child);
            }
        }
    }

    private static void walk(Node node, ArrayNode out) {
        // DFS that also tracks the nearest ancestor with an fx:id, so each
        // emitted bounds row carries its parent fx:id for Alt-click parent
        // selection and other hierarchy-aware operations on the canvas.
        record Frame(Node node, String parentFxId) {}
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(node, ""));
        while (!stack.isEmpty()) {
            Frame f = stack.pop();
            Node n = f.node;
            String myFxId = (n.getId() != null && !n.getId().isEmpty()) ? n.getId() : null;
            if (myFxId != null) {
                var b = n.localToScene(n.getBoundsInLocal());
                ObjectNode o = MAPPER.createObjectNode();
                o.put("fxId", myFxId);
                o.put("tagName", n.getClass().getSimpleName());
                o.put("parentFxId", f.parentFxId);
                o.put("x", b.getMinX());
                o.put("y", b.getMinY());
                o.put("w", b.getWidth());
                o.put("h", b.getHeight());
                out.add(o);
            }
            String parentForChildren = myFxId != null ? myFxId : f.parentFxId;
            if (n instanceof Parent p) {
                for (Node child : p.getChildrenUnmodifiable()) {
                    stack.push(new Frame(child, parentForChildren));
                }
            }
        }
    }

    /**
     * Strip everything in the FXML that would require user controller code to resolve:
     *  - {@code fx:controller="..."} — we don't load user classes here.
     *  - Any {@code on<Event>="#methodName"} attribute — FXMLLoader rejects these without a controller.
     * Inline scripts (no leading {@code #}) are left intact since they don't require a controller.
     */
    private static String sanitiseForPreview(String fxml) {
        String s = fxml.replaceAll("\\s+fx:controller=\"[^\"]*\"", "");
        s = s.replaceAll("\\s+on[A-Z][A-Za-z]*=\"#[^\"]*\"", "");
        return s;
    }

    private static <T> T onFxThread(FxCallable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return work.call();
        }
        final Object[] result = new Object[1];
        final Exception[] error = new Exception[1];
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result[0] = work.call();
            } catch (Exception e) {
                error[0] = e;
            } finally {
                done.countDown();
            }
        });
        done.await();
        if (error[0] != null) throw error[0];
        @SuppressWarnings("unchecked")
        T t = (T) result[0];
        return t;
    }

    @FunctionalInterface
    private interface FxCallable<T> {
        T call() throws Exception;
    }

    private record FxResult(String pngBase64, ArrayNode nodes) {}

    private static String reply(String message) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("op", "ok");
        n.put("message", message);
        return n.toString();
    }

    private static String error(String message) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("op", "error");
        n.put("message", message);
        return n.toString();
    }
}
