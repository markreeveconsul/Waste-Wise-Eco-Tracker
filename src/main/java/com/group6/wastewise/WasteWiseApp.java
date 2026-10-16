package com.group6.wastewise;

import com.group6.wastewise.model.*;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.*;
import javafx.util.Duration;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Native JavaFX controls and JavaFX CSS; no browser or WebView. */
public class WasteWiseApp extends Application {
    private final WasteTracker tracker = new WasteTracker();
    private final EnumMap<Category, Label> weights = new EnumMap<>(Category.class);
    private final EnumMap<Category, Label> percentages = new EnumMap<>(Category.class);
    private final EnumMap<Category, Label> counts = new EnumMap<>(Category.class);
    private final Map<Category, ToggleButton> filterButtons = new HashMap<>();
    private final TableView<Waste> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label total = label("0.0", "total-number");
    private final Label centerWeight = label("0.0", "chart-number");
    private final Label footer = label("", "muted mono");
    private final Label recordCount = label("0", "nav-badge");
    private final Label heapText = label("", "heap-text mono");
    private final ProgressBar heapBar = new ProgressBar();
    private final Canvas donut = new Canvas(176, 176);
    private final StackPane root = new StackPane();
    private final VBox toastArea = new VBox(8);
    private final VBox body = new VBox(28);
    private final ScrollPane scroll = new ScrollPane();
    private final Button undo = button("Undo last delete", "quiet", this::undoDelete);
    private Category selectedCategory;
    private Stage stage;
    private StackPane overlay;
    private Node focusBeforeModal;
    private BorderPane shell;
    private Timeline memoryTimer;
    private double dragX, dragY;
    private boolean smoke;

    public static void main(String[] args) { launch(args); }
    @Override public void start(Stage primaryStage) {
        for (String family : List.of("Inter", "GeistMono")) for (int weight : List.of(400,500,600,700)) {
            var font = getClass().getResourceAsStream("/com/group6/wastewise/fonts/" + family + "-" + weight + ".ttf");
            if (font != null) javafx.scene.text.Font.loadFont(font,12);
        }
        stage = primaryStage;
        smoke = getParameters().getRaw().contains("--smoke-test");
        if (getParameters().getRaw().contains("--demo") || smoke) tracker.loadDemoRecords();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("WasteWise Desktop | Group 6");
        shell = new BorderPane(); shell.getStyleClass().add("shell");
        shell.setTop(titleBar()); shell.setLeft(sidebar()); shell.setCenter(workspace());
        root.getStyleClass().add("desktop"); root.getChildren().add(shell);
        toastArea.setMaxSize(400, Region.USE_PREF_SIZE); toastArea.setPickOnBounds(false);
        StackPane.setAlignment(toastArea, Pos.BOTTOM_RIGHT); StackPane.setMargin(toastArea, new Insets(0, 22, 22, 0));
        root.getChildren().add(toastArea);
        Label resize = label("◢", "resize-grip"); resize.setCursor(Cursor.SE_RESIZE);
        resize.setOnMouseDragged(e -> { if (!stage.isMaximized()) { stage.setWidth(Math.max(stage.getMinWidth(), e.getScreenX() - stage.getX() + 8)); stage.setHeight(Math.max(stage.getMinHeight(), e.getScreenY() - stage.getY() + 8)); } });
        StackPane.setAlignment(resize, Pos.BOTTOM_RIGHT); root.getChildren().add(resize);
        Scene scene = new Scene(root, 1450, 900);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/group6/wastewise/theme.css")).toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE && overlay != null) { closeModal(); e.consume(); }
            else if (e.isShortcutDown() && overlay == null) {
                if (e.getCode() == KeyCode.K) { focusSearch(); e.consume(); }
                else if (e.getCode() == KeyCode.N) { openAddModal(); e.consume(); }
                else if (e.getCode() == KeyCode.Z) { undoDelete(); e.consume(); }
            }
        });
        stage.setScene(scene); stage.setMinWidth(1120); stage.setMinHeight(740);
        stage.setOnCloseRequest(e -> { e.consume(); openExitModal(); });
        refresh(); stage.show();
        memoryTimer = new Timeline(new KeyFrame(Duration.seconds(2), e -> updateHeap()));
        memoryTimer.setCycleCount(Animation.INDEFINITE); memoryTimer.play();
        if (smoke) Platform.runLater(this::runSmokeTest);
    }
    @Override public void stop() { if (memoryTimer != null) memoryTimer.stop(); }

    private HBox titleBar() {
        Button close = button("", "window-dot red-dot", this::openExitModal);
        Button minimize = button("", "window-dot yellow-dot", () -> stage.setIconified(true));
        Button maximize = button("", "window-dot green-dot", () -> stage.setMaximized(!stage.isMaximized()));
        close.setTooltip(new Tooltip("Close application")); minimize.setTooltip(new Tooltip("Minimize")); maximize.setTooltip(new Tooltip("Maximize / restore"));
        HBox dots = row(8, close, minimize, maximize);
        HBox bar = row(12, dots, label("│", "title-muted"), label("●", "green-text"), label("WasteWise Desktop", "title-brand"), label("/", "title-muted"), label("JavaFX Runtime (ArrayList Mode)", "title-muted mono"));
        bar.getStyleClass().add("titlebar");
        bar.getChildren().add(spacer());
        bar.getChildren().add(button("Quick Search   Ctrl K", "title-search", this::focusSearch));
        heapBar.setPrefSize(64, 8); heapBar.setMaxHeight(8);
        HBox heap = row(8, icon("cpu", "#34d399", 14), label("JVM Heap:", "title-muted mono"), heapBar, heapText); heap.getStyleClass().add("heap");
        bar.getChildren().add(heap);
        bar.setOnMousePressed(e -> { dragX = e.getScreenX() - stage.getX(); dragY = e.getScreenY() - stage.getY(); });
        bar.setOnMouseDragged(e -> { if (!(e.getTarget() instanceof Button) && !stage.isMaximized()) { stage.setX(e.getScreenX() - dragX); stage.setY(e.getScreenY() - dragY); } });
        bar.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && e.getTarget() instanceof HBox) stage.setMaximized(!stage.isMaximized()); });
        return bar;
    }
    private VBox sidebar() {
        VBox side = new VBox(22); side.getStyleClass().add("sidebar"); side.setPrefWidth(256); side.setMinWidth(256);
        StackPane brandIcon = new StackPane(icon("leaf", "#ffffff", 22)); brandIcon.getStyleClass().add("brand-icon");
        VBox brandText = column(5, row(6, label("EcoTracker", "brand-name"), label("PRO", "pro")), label("ArrayList Session", "side-muted mono"));
        HBox brand = row(12, brandIcon, brandText); brand.getStyleClass().add("brand");
        VBox nav = new VBox(8); nav.getStyleClass().add("nav-section");
        nav.getChildren().add(label("MAIN WORKSPACE", "nav-heading mono"));
        Button overview = navButton("Overview Dashboard", "grid", () -> { scroll.setVvalue(0); search.clear(); setFilter(null); });
        overview.getStyleClass().add("nav-active");
        HBox overviewGraphic = (HBox) overview.getGraphic(); overviewGraphic.getChildren().addAll(spacer(), recordCount);
        nav.getChildren().addAll(overview, navButton("Segregation Logs", "layers", this::focusSearch), navButton("Log New Waste", "plus", this::openAddModal));
        VBox categories = new VBox(8); categories.getStyleClass().add("nav-section"); categories.getChildren().add(label("CATEGORIES FOCUS", "nav-heading mono"));
        for (Category c : Category.values()) {
            Label count = label("0", "side-muted mono"); counts.put(c, count);
            HBox graphic = row(10, dot(c), label(c.toString(), "side-label"), spacer(), count);
            Button b = button("", "category-nav", () -> { setFilter(c); scroll.setVvalue(1); }); b.setGraphic(graphic); b.setMaxWidth(Double.MAX_VALUE); graphic.setPrefWidth(190);
            categories.getChildren().add(b);
        }
        VBox note = column(7, row(8, label("Engine Runtime", "side-label"), label("In-Memory", "green-text mono")), label("Data resets when application is\nclosed or exited.", "side-muted")); note.getStyleClass().add("runtime-note");
        Button exit = button("Exit Java Program", "exit-button", this::openExitModal); exit.setGraphic(icon("power", "#fca5a5", 16)); exit.setMaxWidth(Double.MAX_VALUE);
        VBox bottom = column(12, note, exit); bottom.getStyleClass().add("side-bottom");
        Region space = new Region(); VBox.setVgrow(space, Priority.ALWAYS);
        side.getChildren().addAll(brand, nav, categories, space, bottom); return side;
    }
    private BorderPane workspace() {
        HBox heading = row(10, label("Waste Segregation & Collection Tracker", "page-title"), label("GROUP 6", "group-badge mono"));
        VBox titles = column(6, heading, label("Track organic waste, recyclables, and residual items in real-time memory.", "muted"));
        Button add = button("Log Waste Item", "primary", this::openAddModal); add.setId("add-button"); add.setGraphic(icon("plus", "#a7f3d0", 16));
        HBox header = row(18, titles, spacer(), add); header.getStyleClass().add("page-header");
        body.getStyleClass().add("body-content"); body.getChildren().addAll(metrics(), ledger());
        scroll.setContent(body); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("workspace-scroll");
        BorderPane main = new BorderPane(scroll); main.setTop(header); main.getStyleClass().add("workspace"); return main;
    }
    private HBox metrics() {
        VBox heroContent = new VBox(24);
        StackPane hero = new StackPane(); hero.getStyleClass().add("hero"); hero.setMinWidth(400); hero.setPrefWidth(620); HBox.setHgrow(hero, Priority.ALWAYS);
        Label active = label("◉   Active Session Records", "session-pill mono");
        Label heroTitle = label("Ecosystem Weight Ledger", "hero-heading"); heroTitle.setWrapText(true);
        VBox intro = column(7, active, heroTitle, label("Calculated live across all active ArrayList memory instances in Java runtime.", "hero-description")); intro.setMinWidth(0); HBox.setHgrow(intro,Priority.ALWAYS);
        HBox totalRow = row(10, total, label("kg", "total-unit")); totalRow.setAlignment(Pos.BASELINE_LEFT);
        VBox totalBox = column(5,label("TOTAL COLLECTION WEIGHT", "hero-caption mono"),totalRow); totalBox.getStyleClass().add("total-box"); totalBox.setMinWidth(175); totalBox.setMaxHeight(Region.USE_PREF_SIZE); totalRow.setAlignment(Pos.BASELINE_RIGHT);
        HBox top = row(16,intro,totalBox); top.setAlignment(Pos.TOP_LEFT); heroContent.getChildren().add(top);
        HBox categories = new HBox(12);
        for (Category c : Category.values()) {
            Label weight = label("0.0", "category-weight mono"); weights.put(c, weight);
            Label pct = label("0.0% of total", "category-pct mono"); percentages.put(c, pct);
            Label name = label(c.toString(), "category-name " + c.style() + "-text");
            VBox card = column(6, row(6, dot(c), name), row(5, weight, label("kg", "category-pct mono")), pct);
            card.getStyleClass().add("category-card"); card.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(card, Priority.ALWAYS); categories.getChildren().add(card);
        }
        Region heroSpace = new Region(); VBox.setVgrow(heroSpace,Priority.ALWAYS); heroContent.getChildren().addAll(heroSpace,categories);
        Node watermark = icon("leaf","#34d399",230); watermark.setOpacity(.08); watermark.setMouseTransparent(true); StackPane.setAlignment(watermark,Pos.BOTTOM_RIGHT);
        hero.getChildren().addAll(watermark,heroContent);
        VBox chart = new VBox(12); chart.getStyleClass().add("chart-card"); chart.setPrefWidth(340); chart.setMinWidth(270);
        Label chartTitle = label("CATEGORY RATIO", "section-eyebrow"); chartTitle.setMinWidth(Region.USE_PREF_SIZE);
        HBox chartHeading = row(8, chartTitle, spacer(), label("By Weight (kg)", "muted tiny mono"));
        VBox center = column(2, centerWeight, label("TOTAL KG", "muted tiny mono")); center.setAlignment(Pos.CENTER); center.setMouseTransparent(true);
        StackPane ring = new StackPane(donut, center); VBox.setVgrow(ring, Priority.ALWAYS); ring.setMinHeight(190);
        HBox legend = row(16, row(5, dot(Category.BIODEGRADABLE), label("Bio", "legend")), row(5, dot(Category.RECYCLABLE), label("Rec", "legend")), row(5, dot(Category.RESIDUAL), label("Res", "legend"))); legend.setAlignment(Pos.CENTER); legend.getStyleClass().add("chart-legend");
        chart.getChildren().addAll(chartHeading, ring, legend);
        HBox metricRow = new HBox(24, hero, chart); chart.prefWidthProperty().bind(metricRow.widthProperty().subtract(48).divide(3)); return metricRow;
    }
    private VBox ledger() {
        VBox ledger = new VBox(); ledger.getStyleClass().add("ledger");
        HBox title = row(10, icon("layers", "#2d6a4f", 22), column(4, label("Segregated Waste Ledger", "section-title"), label("Inspect, search, and manage records stored in memory.", "muted")));
        search.setPromptText("Search item or location..."); search.setId("search-input"); search.setPrefWidth(225); search.textProperty().addListener((o,a,b) -> refreshTable());
        ToggleGroup group = new ToggleGroup();
        ToggleButton all = filter("All", null, group); all.setSelected(true);
        HBox filters = row(2, all); filters.getStyleClass().add("filters");
        for (Category c : Category.values()) filters.getChildren().add(filter(c.toString(), c, group));
        HBox tools = row(12, search, filters); tools.setAlignment(Pos.CENTER_RIGHT);
        FlowPane toolbar = new FlowPane(18, 14); toolbar.setAlignment(Pos.CENTER_LEFT); toolbar.getChildren().addAll(title, tools); toolbar.getStyleClass().add("ledger-toolbar");
        table.setId("waste-table"); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); table.setFixedCellSize(52); table.setPrefHeight(304);
        TableColumn<Waste,String> id = textColumn("#ID", w -> "#" + w.getId(), 65); id.setMaxWidth(80);
        id.setComparator(Comparator.comparingInt(value -> Integer.parseInt(value.substring(1))));
        TableColumn<Waste,String> name = textColumn("WASTE NAME / ITEM", Waste::getWasteName, 245);
        TableColumn<Waste,String> category = textColumn("CATEGORY", w -> w.getCategory().toString(), 155);
        category.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(null); setGraphic(null);
                if (!empty && getTableRow().getItem() != null) {
                    Category cat = getTableRow().getItem().getCategory(); setGraphic(label("●  " + value, "category-badge " + cat.style() + "-badge"));
                }
            }
        });
        TableColumn<Waste,String> weight = textColumn("WEIGHT", w -> format(w.getWeight()) + " kg", 95); weight.setStyle("-fx-alignment: CENTER-RIGHT;");
        weight.setComparator(Comparator.comparingDouble(value -> Double.parseDouble(value.replace(" kg", ""))));
        TableColumn<Waste,String> location = textColumn("COLLECTION LOCATION", Waste::getLocation, 175);
        TableColumn<Waste,Void> actions = new TableColumn<>("ACTIONS"); actions.setPrefWidth(75); actions.setMaxWidth(85); actions.setSortable(false);
        actions.setCellFactory(c -> new TableCell<>() {
            private final Button remove = button("", "delete-button", () -> { Waste w = getTableRow().getItem(); if (w != null) delete(w); });
            { remove.setGraphic(icon("trash", "#40916c", 16)); remove.setTooltip(new Tooltip("Remove record")); remove.setAccessibleText("Remove record"); }
            @Override protected void updateItem(Void value, boolean empty) { super.updateItem(value, empty); setGraphic(empty ? null : remove); setAlignment(Pos.CENTER); }
        });
        table.getColumns().addAll(id, name, category, weight, location, actions);
        VBox empty = column(10, icon("layers","#40916c",28),label("No waste records match your search", "section-title"), label("Clear filters or log a new waste item.", "muted"), button("Log New Waste Item", "primary", this::openAddModal)); empty.setAlignment(Pos.CENTER); empty.setPadding(new Insets(24)); table.setPlaceholder(empty);
        table.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.DELETE && table.getSelectionModel().getSelectedItem() != null) delete(table.getSelectionModel().getSelectedItem()); });
        undo.setId("undo-button");
        HBox foot = row(10, footer, spacer(), undo, label("◇  Volatile Memory Session", "muted tiny mono")); foot.getStyleClass().add("ledger-footer");
        ledger.getChildren().addAll(toolbar, table, foot); return ledger;
    }
    private TableColumn<Waste,String> textColumn(String title, java.util.function.Function<Waste,String> value, double width) {
        TableColumn<Waste,String> c = new TableColumn<>(title); c.setPrefWidth(width); c.setCellValueFactory(v -> new ReadOnlyStringWrapper(value.apply(v.getValue()))); return c;
    }
    private ToggleButton filter(String text, Category category, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text); b.getStyleClass().add("filter"); b.setToggleGroup(group); b.setOnAction(e -> setFilter(category)); filterButtons.put(category, b); return b;
    }
    private void setFilter(Category category) { selectedCategory = category; filterButtons.get(category).setSelected(true); refreshTable(); }
    private void focusSearch() { scroll.setVvalue(1); search.requestFocus(); }
    private void refreshTable() {
        List<Waste> results = tracker.search(search.getText(), selectedCategory);
        table.setItems(FXCollections.observableArrayList(results)); table.sort();
        footer.setText("Showing " + results.size() + " of " + tracker.viewWaste().size() + " records in Java memory");
        undo.setDisable(!tracker.canUndo());
    }
    private void refresh() {
        double sum = tracker.calculateTotalWeight(); total.setText(format(sum)); centerWeight.setText(format(sum)); recordCount.setText("" + tracker.viewWaste().size());
        for (Category c : Category.values()) {
            double n = tracker.calculateCategoryWeight(c); weights.get(c).setText(format(n)); percentages.get(c).setText(String.format(Locale.US, "%.1f%% of total", sum == 0 ? 0 : n / sum * 100)); counts.get(c).setText("" + tracker.count(c));
        }
        GraphicsContext gc = donut.getGraphicsContext2D(); gc.clearRect(0,0,176,176); gc.setLineWidth(21); gc.setStroke(Color.web("#f0f4f1")); gc.strokeOval(21,21,134,134);
        double start = 90;
        for (Category c : Category.values()) {
            double extent = sum == 0 ? 0 : tracker.calculateCategoryWeight(c) / sum * 360;
            gc.setStroke(Color.web(color(c))); gc.strokeArc(21,21,134,134,start,-extent,ArcType.OPEN); start -= extent;
        }
        refreshTable(); updateHeap();
    }
    private void updateHeap() {
        Runtime r = Runtime.getRuntime(); long used = r.totalMemory() - r.freeMemory();
        heapText.setText(String.format(Locale.US, "%.1f MB", used / 1048576.0)); heapBar.setProgress((double) used / r.maxMemory());
    }
    private void delete(Waste w) { if (tracker.removeWaste(w.getId())) { refresh(); toast("Record Deleted", "Removed \"" + w.getWasteName() + "\". Use Undo last delete to restore."); } }
    private void undoDelete() { if (tracker.undoLastDelete()) { refresh(); toast("Restored", "Record restored to ArrayList memory."); } }

    private void openAddModal() {
        if (overlay != null) return;
        TextField name = new TextField(); name.setPromptText("e.g., Apple Core, Cardboard Sheet"); name.setId("input-name");
        ComboBox<Category> category = new ComboBox<>(FXCollections.observableArrayList(Category.values())); category.setPromptText("Choose category..."); category.setMaxWidth(Double.MAX_VALUE); category.setId("input-category");
        TextField weight = new TextField(); weight.setPromptText("0.5"); weight.setId("input-weight");
        TextField location = new TextField(); location.setPromptText("Cafeteria Bin A"); location.setId("input-location");
        Label error = label("", "validation-error"); error.setId("form-error"); error.setWrapText(true);
        VBox left = field("WEIGHT (kg)", weight), right = field("LOCATION / BIN", location); HBox.setHgrow(left,Priority.ALWAYS); HBox.setHgrow(right,Priority.ALWAYS);
        Button save = button("Append to ArrayList", "primary", () -> {
            try {
                double value;
                try { value = Double.parseDouble(weight.getText().strip()); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a valid numeric weight in kilograms."); }
                Waste added = tracker.addWaste(name.getText(), category.getValue(), value, location.getText());
                refresh(); closeModal(); toast("Record Added", "Inserted \"" + added.getWasteName() + "\" into ArrayList.");
            } catch (IllegalArgumentException ex) { error.setText(ex.getMessage()); }
        }); save.setId("save-record"); save.setDefaultButton(true);
        VBox card = column(18, modalHeader("Add Waste Item", "Insert new entry into ArrayList", "plus"), field("WASTE ITEM NAME", name), field("CATEGORY SELECT", category), new HBox(12,left,right), error, row(10, spacer(), button("Cancel", "quiet", this::closeModal), save));
        showModal(card); Platform.runLater(name::requestFocus);
    }
    private void openExitModal() {
        if (overlay != null) return;
        Label warning = label("All logged waste records will be permanently lost because data is kept strictly inside Java ArrayList memory during runtime.", "warning-copy"); warning.setWrapText(true);
        VBox note = column(10, label("IN-MEMORY STORAGE NOTICE", "warning-heading mono"), warning); note.getStyleClass().add("warning-note");
        Button exit = button("Exit & Flush Memory", "danger", () -> { tracker.clear(); stage.hide(); Platform.exit(); }); exit.setId("confirm-exit");
        VBox card = column(20, modalHeader("Exit Program Confirmation", "Termination Warning", "power"), label("Are you sure you want to end the Java application session?", "muted"), note, row(10, spacer(), button("Cancel & Keep Running", "quiet", this::closeModal), exit));
        showModal(card);
    }
    private HBox modalHeader(String title, String subtitle, String icon) { HBox h = row(12, icon(icon,"#40916c",24), column(4,label(title,"modal-title"),label(subtitle,"muted")), spacer(), button("×","quiet",this::closeModal)); h.getStyleClass().add("modal-header"); return h; }
    private VBox field(String caption, Control control) { return column(7, label(caption,"field-label mono"), control); }
    private void showModal(VBox card) {
        focusBeforeModal = root.getScene().getFocusOwner(); shell.setDisable(true); toastArea.setDisable(true);
        card.getStyleClass().add("modal-card"); card.setMaxSize(470,Region.USE_PREF_SIZE);
        overlay = new StackPane(card); overlay.getStyleClass().add("modal-overlay"); overlay.setId("modal-overlay");
        root.getChildren().add(overlay); StackPane.setMargin(overlay, new Insets(-24));
        FadeTransition fade = new FadeTransition(Duration.millis(160),overlay); fade.setFromValue(0); fade.setToValue(1); fade.play();
        Platform.runLater(card::requestFocus);
    }
    private void closeModal() { if (overlay != null) { root.getChildren().remove(overlay); overlay = null; shell.setDisable(false); toastArea.setDisable(false); if (focusBeforeModal != null) focusBeforeModal.requestFocus(); } }
    private void toast(String title, String message) {
        Label copy = label(message,"muted"); copy.setWrapText(true);
        VBox notice = column(5,label(title,"section-title"),copy); notice.getStyleClass().add("toast"); toastArea.getChildren().setAll(notice);
        PauseTransition timeout = new PauseTransition(Duration.seconds(5)); timeout.setOnFinished(e -> toastArea.getChildren().remove(notice)); timeout.play();
    }
    private Button navButton(String text, String glyph, Runnable action) { Button b = button("","nav-button", action); HBox content = row(10,icon(glyph,"#74c69d",16),label(text,"side-label")); content.setPrefWidth(190); b.setGraphic(content); b.setMaxWidth(Double.MAX_VALUE); return b; }
    private static String format(double n) { return String.format(Locale.US, n != 0 && Math.abs(n) < .1 ? "%.3f" : "%.1f",n); }
    private static Label label(String text, String classes) { Label l = new Label(text); l.getStyleClass().addAll(classes.split(" ")); return l; }
    private static Button button(String text,String classes,Runnable action) { Button b = new Button(text); b.getStyleClass().addAll(classes.split(" ")); b.setOnAction(e -> action.run()); b.setCursor(Cursor.HAND); return b; }
    private static HBox row(double gap, Node... nodes) { HBox box = new HBox(gap,nodes); box.setAlignment(Pos.CENTER_LEFT); return box; }
    private static VBox column(double gap, Node... nodes) { return new VBox(gap,nodes); }
    private static Region spacer() { Region r = new Region(); HBox.setHgrow(r,Priority.ALWAYS); return r; }
    private static String color(Category c) { return switch(c) { case BIODEGRADABLE -> "#10b981"; case RECYCLABLE -> "#0ea5e9"; case RESIDUAL -> "#f59e0b"; }; }
    private static Circle dot(Category c) { return new Circle(4,Color.web(color(c))); }
    private static Node icon(String name,String color,double size) {
        String d = switch(name) {
            case "plus" -> "M12 5 L12 19 M5 12 L19 12";
            case "trash" -> "M3 6 L21 6 M9 6 L9 3 L15 3 L15 6 M5 6 L6 21 L18 21 L19 6 M10 10 L10 17 M14 10 L14 17";
            case "grid" -> "M3 3 H9 V9 H3 Z M15 3 H21 V9 H15 Z M3 15 H9 V21 H3 Z M15 15 H21 V21 H15 Z";
            case "layers" -> "M12 3 L22 8 L12 13 L2 8 Z M2 12 L12 17 L22 12 M2 16 L12 21 L22 16";
            case "power" -> "M12 2 V12 M6 5 A9 9 0 1 0 18 5";
            case "cpu" -> "M6 6 H18 V18 H6 Z M9 9 H15 V15 H9 Z M9 2 V6 M15 2 V6 M9 18 V22 M15 18 V22 M2 9 H6 M2 15 H6 M18 9 H22 M18 15 H22";
            default -> "M20 3 C5 1 1 10 7 17 C13 23 23 15 20 3 Z M3 22 L16 9";
        };
        SVGPath path = new SVGPath(); path.setContent(d); path.setFill(Color.TRANSPARENT); path.setStroke(Color.web(color)); path.setStrokeWidth(1.7); path.setStrokeLineCap(StrokeLineCap.ROUND); path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        Group group = new Group(path); group.setScaleX(size/24); group.setScaleY(size/24); StackPane box = new StackPane(group); box.setMinSize(size,size); box.setPrefSize(size,size); box.setMaxSize(size,size); return box;
    }

    /** Deterministic interaction checks against actual JavaFX controls; snapshots use the scene, not the desktop. */
    private void runSmokeTest() {
        try {
            Path dir = Path.of(System.getProperty("wastewise.snapshots", "work")); Files.createDirectories(dir);
            snapshot(dir.resolve("dashboard.png"));
            require(table.getItems().size() == 5,"Demo records"); require(Math.abs(tracker.calculateTotalWeight()-2.7)<1e-9,"Demo total");
            search.setText("cafeteria"); require(table.getItems().size()==1,"Search location"); search.clear();
            setFilter(Category.RECYCLABLE); require(table.getItems().size()==2,"Category filter"); setFilter(null);
            openAddModal(); root.applyCss(); root.layout(); snapshot(dir.resolve("add-dialog.png"));
            ((TextField)root.lookup("#input-name")).setText("Test food");
            ((ComboBox<Category>)root.lookup("#input-category")).setValue(Category.BIODEGRADABLE);
            ((TextField)root.lookup("#input-weight")).setText("-2");
            ((TextField)root.lookup("#input-location")).setText("Test bin");
            ((Button)root.lookup("#save-record")).fire(); require(tracker.viewWaste().size()==5 && !((Label)root.lookup("#form-error")).getText().isEmpty(),"Invalid weight rejected");
            ((TextField)root.lookup("#input-weight")).setText("2"); ((Button)root.lookup("#save-record")).fire(); require(tracker.viewWaste().size()==6 && overlay==null,"Add via form");
            Waste last = tracker.viewWaste().get(5); delete(last); require(tracker.viewWaste().size()==5,"Delete"); undo.fire(); require(tracker.viewWaste().size()==6,"Undo");
            tracker.clear(); refresh(); toastArea.getChildren().clear(); snapshot(dir.resolve("empty.png")); require(table.getItems().isEmpty() && tracker.calculateTotalWeight()==0,"Empty state");
            openExitModal(); root.applyCss(); root.layout(); snapshot(dir.resolve("exit-dialog.png")); closeModal(); require(stage.isShowing(),"Exit cancellation");
            stage.setWidth(1120); stage.setHeight(740); tracker.loadDemoRecords(); refresh();
            PauseTransition resizeWait = new PauseTransition(Duration.millis(400));
            resizeWait.setOnFinished(event -> {
                try {
                    require(root.getWidth() == 1120,"Compact viewport resized"); snapshot(dir.resolve("compact.png"));
                    System.out.println("PASS: JavaFX form, validation, search, filters, delete, undo, empty state, exit cancellation and compact resize");
                    Files.writeString(dir.resolve("ui-test-result.txt"),"PASS: JavaFX interactions and compact resize; 5 scene snapshots.\n");
                    stage.hide(); Platform.exit();
                } catch (Throwable e) { e.printStackTrace(); Platform.exit(); System.exit(1); }
            }); resizeWait.play();
        } catch(Throwable e) { e.printStackTrace(); Platform.exit(); System.exit(1); }
    }
    private void snapshot(Path target) throws Exception {
        root.applyCss(); root.layout();
        javafx.scene.image.WritableImage image = root.snapshot(null,null);
        int w=(int)image.getWidth(),h=(int)image.getHeight(); BufferedImage buffered = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        int[] pixels=new int[w*h]; image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w); buffered.setRGB(0,0,w,h,pixels,0,w); ImageIO.write(buffered,"png",target.toFile());
    }
    private static void require(boolean condition,String description) { if(!condition) throw new AssertionError(description); }
}
