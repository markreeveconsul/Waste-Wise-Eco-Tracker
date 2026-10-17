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
    private final Label pageTitle = label("Overview Dashboard", "page-title");
    private final Label pageSubtitle = label("Your collection at a glance. Every category, every kilogram.", "muted");
    private final Label visibleWeight = label("0.0 kg", "visible-weight mono");
    private final Label logsTotal = label("0.0 kg", "stat-value mono");
    private final Label logsCount = label("0 records logged", "muted");
    private final EnumMap<Category, Label> logWeights = new EnumMap<>(Category.class);
    private final EnumMap<Category, Label> logCounts = new EnumMap<>(Category.class);
    private final VBox recentRows = new VBox();
    private final Label recentCaption = label("", "muted");
    private final Label emptyTitle = label("No waste records yet", "section-title");
    private final Label emptyCopy = label("Add your first waste item to start tracking.", "muted");
    private final Button clearFilters = button("Clear filters", "quiet", this::clearFilters);
    private BorderPane workspacePane;
    private VBox logsPage;
    private final ScrollPane logsScroll = new ScrollPane();
    private final Canvas distribution = new Canvas(600, 10);
    private final EnumMap<Category, Label> ratioLabels = new EnumMap<>(Category.class);
    private final EnumMap<Category, Label> statShares = new EnumMap<>(Category.class);
    private Button overviewNav;
    private Button logsNav;
    private boolean showingLogs;
    private Category selectedCategory;
    private Stage stage;
    private StackPane overlay;
    private Node focusBeforeModal;
    private BorderPane shell;
    private Timeline memoryTimer;
    private double dragX, dragY;
    private boolean smoke;
    private boolean expanded;
    private Rectangle2D restoredBounds;
    private Button maximizeControl;
    private Rectangle2D screenBounds() {
        var screens = Screen.getScreensForRectangle(stage.getX(),stage.getY(),stage.getWidth(),stage.getHeight());
        return (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
    }
    private void toggleMaximize() {
        if (stage.isFullScreen()) stage.setFullScreen(false);
        if (expanded) {
            stage.setX(restoredBounds.getMinX()); stage.setY(restoredBounds.getMinY());
            stage.setWidth(restoredBounds.getWidth()); stage.setHeight(restoredBounds.getHeight());
            expanded=false; maximizeControl.setText("□");
        } else {
            restoredBounds=new Rectangle2D(stage.getX(),stage.getY(),stage.getWidth(),stage.getHeight());
            Rectangle2D bounds=screenBounds();
            stage.setX(bounds.getMinX()); stage.setY(bounds.getMinY()); stage.setWidth(bounds.getWidth()); stage.setHeight(bounds.getHeight());
            expanded=true; maximizeControl.setText("❐");
        }
    }
    private void toggleFullScreen() { stage.setFullScreen(!stage.isFullScreen()); }
    private boolean titleControl(Object target) {
        if (!(target instanceof Node)) return false;
        for (Node node=(Node)target; node!=null; node=node.getParent()) if (node instanceof ButtonBase) return true;
        return false;
    }


    public static void main(String[] args) { launch(args); }
    @Override public void start(Stage primaryStage) {
        for (String family : List.of("Inter", "GeistMono")) for (int weight : List.of(400,500,600,700)) {
            var font = getClass().getResourceAsStream("/com/group6/wastewise/fonts/" + family + "-" + weight + ".ttf");
            if (font != null) javafx.scene.text.Font.loadFont(font,12);
        }
        stage = primaryStage;
        smoke = getParameters().getRaw().contains("--smoke-test");
        if (getParameters().getRaw().contains("--demo") || smoke) tracker.loadDemoRecords();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("WasteWise Desktop | Group 6");
        shell = new BorderPane(); shell.getStyleClass().add("shell");
        shell.setTop(titleBar()); shell.setLeft(sidebar()); shell.setCenter(workspace());
        root.getStyleClass().add("desktop"); root.getChildren().add(shell);
        Rectangle windowClip = new Rectangle();
        windowClip.widthProperty().bind(root.widthProperty());
        windowClip.heightProperty().bind(root.heightProperty());
        windowClip.setArcWidth(32); windowClip.setArcHeight(32);
        root.setClip(windowClip);
        toastArea.setMaxSize(400, Region.USE_PREF_SIZE); toastArea.setPickOnBounds(false);
        StackPane.setAlignment(toastArea, Pos.BOTTOM_RIGHT); StackPane.setMargin(toastArea, new Insets(0, 22, 22, 0));
        root.getChildren().add(toastArea);
        Label resize = label("◢", "resize-grip"); resize.setCursor(Cursor.SE_RESIZE);
        resize.setOnMouseDragged(e -> { if (!expanded && !stage.isFullScreen()) { stage.setWidth(Math.max(stage.getMinWidth(), e.getScreenX() - stage.getX() + 8)); stage.setHeight(Math.max(stage.getMinHeight(), e.getScreenY() - stage.getY() + 8)); } });
        StackPane.setAlignment(resize, Pos.BOTTOM_RIGHT); StackPane.setMargin(resize, new Insets(0, 6, 6, 0)); root.getChildren().add(resize);
        Scene scene = new Scene(root, 1450, 900);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/group6/wastewise/theme.css")).toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F11) { toggleFullScreen(); e.consume(); }
            else if (e.getCode() == KeyCode.ESCAPE && overlay != null) { closeModal(); e.consume(); }
            else if (e.isShortcutDown() && overlay == null) {
                if (e.getCode() == KeyCode.K) { focusSearch(); e.consume(); }
                else if (e.getCode() == KeyCode.N) { openAddModal(); e.consume(); }
                else if (e.getCode() == KeyCode.Z) { undoDelete(); e.consume(); }
            }
        });
        stage.setScene(scene); stage.setResizable(true);
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setMinWidth(Math.min(1120,screen.getWidth())); stage.setMinHeight(Math.min(640,screen.getHeight()));
        stage.setWidth(Math.min(1450,Math.max(stage.getMinWidth(),screen.getWidth()*.88)));
        stage.setHeight(Math.min(900,Math.max(stage.getMinHeight(),screen.getHeight()*.85)));
        stage.setX(screen.getMinX()+(screen.getWidth()-stage.getWidth())/2);
        stage.setY(screen.getMinY()+(screen.getHeight()-stage.getHeight())/2);
        stage.setFullScreenExitHint("Press Esc or F11 to leave full screen");
        stage.setOnCloseRequest(e -> { e.consume(); openExitModal(); });
        refresh(); stage.show();
        memoryTimer = new Timeline(new KeyFrame(Duration.seconds(2), e -> updateHeap()));
        memoryTimer.setCycleCount(Animation.INDEFINITE); memoryTimer.play();
        if (smoke) Platform.runLater(this::runSmokeTest);
    }
    @Override public void stop() { if (memoryTimer != null) memoryTimer.stop(); }

    private HBox titleBar() {
        HBox bar = row(12, label("●", "green-text"), label("WasteWise Desktop", "title-brand"));
        bar.getStyleClass().add("titlebar");
        bar.getChildren().add(spacer());
        bar.getChildren().add(button("Quick Search   Ctrl K", "title-search", this::focusSearch));
        Button about = button("About", "title-search", this::openAboutModal); about.setId("about-button");
        bar.getChildren().add(about);
        Button minimize = button("—", "window-control", () -> stage.setIconified(true)); minimize.setId("window-minimize");
        maximizeControl = button("□", "window-control", this::toggleMaximize); maximizeControl.setId("window-maximize");
        Button full = button("⛶", "window-control", this::toggleFullScreen); full.setId("window-fullscreen");
        Button close = button("×", "window-control window-close", this::openExitModal);
        minimize.setTooltip(new Tooltip("Minimize to taskbar")); maximizeControl.setTooltip(new Tooltip("Maximize / restore window"));
        full.setTooltip(new Tooltip("Full screen / exit full screen (F11)")); close.setTooltip(new Tooltip("Close application"));
        minimize.setAccessibleText("Minimize"); maximizeControl.setAccessibleText("Maximize or restore"); full.setAccessibleText("Toggle full screen"); close.setAccessibleText("Close");
        bar.getChildren().addAll(minimize,maximizeControl,full,close);
        bar.setOnMousePressed(e -> { if (titleControl(e.getTarget())) return; dragX = e.getScreenX() - stage.getX(); dragY = e.getScreenY() - stage.getY(); });
        bar.setOnMouseDragged(e -> { if (!titleControl(e.getTarget()) && !expanded && !stage.isFullScreen()) { stage.setX(e.getScreenX() - dragX); stage.setY(e.getScreenY() - dragY); } });
        bar.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !titleControl(e.getTarget())) toggleMaximize(); });
        return bar;
    }
    private VBox sidebar() {
        VBox side = new VBox(22); side.getStyleClass().add("sidebar"); side.setPrefWidth(256); side.setMinWidth(256);
        StackPane brandIcon = new StackPane(icon("leaf", "#ffffff", 22)); brandIcon.getStyleClass().add("brand-icon");
        VBox brandText = column(5, row(6, label("WasteWise", "brand-name"), label("G6", "pro")), label("Waste collection tracker", "side-muted"));
        HBox brand = row(12, brandIcon, brandText); brand.getStyleClass().add("brand");
        VBox nav = new VBox(8); nav.getStyleClass().add("nav-section");
        nav.getChildren().add(label("MAIN WORKSPACE", "nav-heading mono"));
        overviewNav = navButton("Overview Dashboard", "grid", () -> showView(false));
        overviewNav.setId("nav-overview"); overviewNav.getStyleClass().add("nav-active");

        logsNav = navButton("Segregation Logs", "layers", () -> showView(true)); logsNav.setId("nav-logs");
        ((HBox)logsNav.getGraphic()).getChildren().addAll(spacer(),recordCount);
        nav.getChildren().addAll(overviewNav, logsNav, navButton("Log New Waste", "plus", this::openAddModal));
        VBox categories = new VBox(8); categories.getStyleClass().add("nav-section"); categories.getChildren().add(label("CATEGORIES FOCUS", "nav-heading mono"));
        for (Category c : Category.values()) {
            Label count = label("0", "side-muted mono"); counts.put(c, count);
            HBox graphic = row(10, dot(c), label(c.toString(), "side-label"), spacer(), count);
            Button b = button("", "category-nav", () -> { showView(true); search.clear(); setFilter(c); }); b.setGraphic(graphic); b.setMaxWidth(Double.MAX_VALUE); graphic.setPrefWidth(190);
            categories.getChildren().add(b);
        }
        VBox note = column(7, row(8, label("Current session", "side-label")), label("Data resets when application is\nclosed or exited.", "side-muted")); note.getStyleClass().add("runtime-note");
        Button exit = button("Exit application", "exit-button", this::openExitModal); exit.setGraphic(icon("power", "#fca5a5", 16)); exit.setMaxWidth(Double.MAX_VALUE);
        VBox bottom = column(12, note, exit); bottom.getStyleClass().add("side-bottom");
        Region space = new Region(); VBox.setVgrow(space, Priority.ALWAYS);
        side.getChildren().addAll(brand, nav, categories, space, bottom); return side;
    }
    private BorderPane workspace() {
        HBox heading = row(10, pageTitle, label("GROUP 6", "group-badge mono"));
        VBox titles = column(6, heading, pageSubtitle);
        Button add = button("Add Waste Record", "primary", this::openAddModal); add.setId("add-button"); add.setGraphic(icon("plus", "#ffffff", 16));
        HBox header = row(18, titles, spacer(), add); header.getStyleClass().add("page-header");
        body.getStyleClass().add("body-content"); body.setId("overview-page"); body.getChildren().addAll(metrics(), recentActivity());
        scroll.setContent(body); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("workspace-scroll");
        logsPage = ledger(); logsPage.setId("logs-page");
        logsPage.setMinHeight(590);
        logsScroll.setContent(logsPage); logsScroll.setFitToWidth(true); logsScroll.setFitToHeight(true);
        logsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); logsScroll.getStyleClass().add("workspace-scroll");
        workspacePane = new BorderPane(scroll); workspacePane.setTop(header); workspacePane.getStyleClass().add("workspace"); return workspacePane;
    }
    private void showView(boolean logs) {
        showingLogs = logs;
        workspacePane.setCenter(logs ? logsScroll : scroll);
        workspacePane.getStyleClass().remove("dark-workspace");
        if (logs) workspacePane.getStyleClass().add("dark-workspace");
        pageTitle.setText(logs ? "Segregation Logs" : "Overview Dashboard");
        pageSubtitle.setText(logs ? "Search, filter, and manage your collection records." : "Your collection at a glance. Every category, every kilogram.");
        overviewNav.getStyleClass().remove("nav-active"); logsNav.getStyleClass().remove("nav-active");
        (logs ? logsNav : overviewNav).getStyleClass().add("nav-active");
        if (!logs) scroll.setVvalue(0);
    }
    private VBox recentActivity() {
        Button all = button("View all records  →", "quiet", () -> { clearFilters(); showView(true); }); all.setId("view-all-records");
        HBox heading = row(12, icon("layers", "#2d6a4f", 22), column(4,label("Recently logged waste", "section-title"),recentCaption),spacer(),all); heading.getStyleClass().add("recent-heading");
        VBox card = column(0,heading,recentRows); card.getStyleClass().add("ledger");
        return card;
    }
    private void refreshRecent() {
        List<Waste> records = tracker.viewWaste(); recentRows.getChildren().clear();
        recentCaption.setText(records.isEmpty() ? "Your collection starts with the first record." : "Latest " + Math.min(5,records.size()) + " of " + records.size() + " records this session");
        if (records.isEmpty()) {
            VBox empty = column(12,icon("leaf","#40916c",30),label("Ready for your first collection?", "section-title"),label("Log a waste item to see your totals and category breakdown.","muted"),button("Add your first record","primary",this::openAddModal));
            empty.setAlignment(Pos.CENTER); empty.setPadding(new Insets(28)); recentRows.getChildren().add(empty); return;
        }
        for (int i=records.size()-1; i>=Math.max(0,records.size()-5); i--) {
            Waste w = records.get(i);
            Label name = label(w.getWasteName(),"recent-name"); name.setMaxWidth(Double.MAX_VALUE); name.setMinWidth(0); name.setTooltip(new Tooltip(w.getWasteName()));
            Label location = label(w.getLocation(),"muted"); location.setMinWidth(0); location.setTooltip(new Tooltip(w.getLocation()));
            Label badge = label("●  " + w.getCategory(),"category-badge " + w.getCategory().style() + "-badge");
            Label value = label(format(w.getWeight())+" kg","recent-weight mono"); value.setMaxWidth(Double.MAX_VALUE); value.setAlignment(Pos.CENTER_RIGHT);
            GridPane line = new GridPane(); line.setHgap(16); line.getStyleClass().add("recent-row");
            for (double percent : new double[]{40,22,24,14}) { ColumnConstraints cc = new ColumnConstraints(); cc.setPercentWidth(percent); line.getColumnConstraints().add(cc); }
            line.add(name,0,0); line.add(badge,1,0); line.add(location,2,0); line.add(value,3,0); recentRows.getChildren().add(line);
        }
    }
    private HBox inventoryStats() {
        HBox stats = new HBox(14);
        VBox all = column(10,label("TOTAL COLLECTION","stat-caption"),logsTotal,statFoot(logsCount,label("All waste","muted"))); all.getStyleClass().addAll("inventory-stat","total-stat");
        stats.getChildren().add(categoryLink(all,null,"logs-all"));
        for (Category c : Category.values()) {
            Label value = label("0.0 kg","stat-value mono"), count = label("0 records","muted"); logWeights.put(c,value); logCounts.put(c,count);
            Label share = label("0.0%",c.style()+"-text mono"); statShares.put(c,share);
            VBox card = column(10,row(7,dot(c),label(c.toString(),"stat-caption")),value,statFoot(count,share)); card.getStyleClass().addAll("inventory-stat",c.style()+"-stat"); stats.getChildren().add(categoryLink(card,c,"logs-"+c.style()));
        }
        for (Node n : stats.getChildren()) { Region card=(Region)n; card.setMinWidth(0); card.setPrefWidth(250); card.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(card,Priority.ALWAYS); }
        return stats;
    }
    private HBox statFoot(Label count, Label share) {
        HBox foot = row(4,count,spacer(),share); foot.getStyleClass().add("stat-foot"); return foot;
    }
    private VBox distributionCard() {
        FlowPane legend = new FlowPane(16,6);
        for (Category c : Category.values()) {
            Label value = label("",c.style()+"-text mono"); ratioLabels.put(c,value);
            legend.getChildren().add(row(6,dot(c),value));
        }
        StackPane track = new StackPane(distribution); track.getStyleClass().add("distribution-track");
        distribution.widthProperty().bind(track.widthProperty().subtract(4));
        distribution.widthProperty().addListener((o,a,b) -> drawDistribution());
        VBox card = column(10,label("STREAM WEIGHT DISTRIBUTION","distribution-title mono"),legend,track);
        card.getStyleClass().add("distribution-card"); return card;
    }
    private void drawDistribution() {
        double sum=tracker.calculateTotalWeight(), x=0;
        GraphicsContext gc=distribution.getGraphicsContext2D(); gc.clearRect(0,0,distribution.getWidth(),10);
        for (Category c : Category.values()) {
            double share=sum==0?0:tracker.calculateCategoryWeight(c)/sum;
            double width=share*distribution.getWidth(); gc.setFill(Color.web(color(c))); gc.fillRect(x,0,width,10); x+=width;
            if (ratioLabels.containsKey(c)) ratioLabels.get(c).setText(c+String.format(Locale.US," (%.1f%%)",share*100));
            if (statShares.containsKey(c)) statShares.get(c).setText(String.format(Locale.US,"%.1f%%",share*100));
        }
    }
    private Button categoryLink(VBox content, Category category, String id) {
        Button link = button("", "card-link", () -> { showView(true); search.clear(); setFilter(category); });
        link.setId(id); link.setGraphic(content); link.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE); link.setMinWidth(0);
        content.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);
        content.prefWidthProperty().bind(link.widthProperty().subtract(4));
        content.prefHeightProperty().bind(link.heightProperty().subtract(4));
        link.setAccessibleText(category == null ? "View all records" : "View " + category + " records");
        link.setTooltip(new Tooltip(link.getAccessibleText()));
        return link;
    }
    private void openAboutModal() {
        if (overlay != null) return;
        updateHeap(); heapBar.setPrefWidth(100);
        Label description = label("WasteWise helps Group 6 record, classify, and track waste collection. Records are cleared when the application closes.","muted"); description.setWrapText(true);
        VBox details = column(12,
            label("APPLICATION DETAILS", "field-label"),
            label("Interface: JavaFX + CSS", "muted"),
            label("Storage: Java ArrayList (session only)", "muted"),
            label("Java: " + System.getProperty("java.version"), "muted"),
            row(12,label("JVM memory in use", "muted"),heapBar,heapText));
        details.getStyleClass().add("about-details");
        Button done = button("Done", "primary", this::closeModal); done.setId("about-done");
        showModal(column(20,modalHeader("About WasteWise","Waste Segregation & Collection Tracker","leaf"),description,details,row(10,spacer(),done)));
    }
    private HBox metrics() {
        VBox heroContent = new VBox(24);
        StackPane hero = new StackPane(); hero.getStyleClass().add("hero"); hero.setMinWidth(400); hero.setPrefWidth(620); HBox.setHgrow(hero, Priority.ALWAYS);
        Label active = label("Current session", "session-pill mono");
        Label heroTitle = label("Collection Summary", "hero-heading"); heroTitle.setWrapText(true);
        VBox intro = column(7, active, heroTitle, label("Track your collected waste and see how each category contributes.", "hero-description")); intro.setMinWidth(0); HBox.setHgrow(intro,Priority.ALWAYS);
        HBox totalRow = row(10, total, label("kg", "total-unit")); totalRow.setAlignment(Pos.BASELINE_LEFT);
        VBox totalBox = column(5,label("TOTAL WEIGHT", "hero-caption mono"),totalRow); totalBox.getStyleClass().add("total-box"); totalBox.setMinWidth(175); totalBox.setMaxHeight(Region.USE_PREF_SIZE); totalRow.setAlignment(Pos.BASELINE_RIGHT);
        HBox top = row(16,intro,totalBox); top.setAlignment(Pos.TOP_LEFT); heroContent.getChildren().add(top);
        HBox categories = new HBox(12);
        for (Category c : Category.values()) {
            Label weight = label("0.0", "category-weight mono"); weights.put(c, weight);
            Label pct = label("0.0% of total", "category-pct mono"); percentages.put(c, pct);
            Label name = label(c.toString(), "category-name " + c.style() + "-text");
            VBox card = column(6, row(6, dot(c), name), row(5, weight, label("kg", "category-pct mono")), pct);
            card.getStyleClass().add("category-card"); card.setMaxWidth(Double.MAX_VALUE); Button link = categoryLink(card,c,"overview-"+c.style()); link.setMinHeight(124); HBox.setHgrow(link, Priority.ALWAYS); categories.getChildren().add(link);
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
        search.setPromptText("Search item or location..."); search.setId("search-input"); search.setPrefWidth(225); search.textProperty().addListener((o,a,b) -> refreshTable());
        ToggleGroup group = new ToggleGroup();
        ToggleButton all = filter("All Records", null, group); all.setSelected(true);
        HBox filters = row(2, all); filters.getStyleClass().add("filters");
        for (Category c : Category.values()) filters.getChildren().add(filter(c.toString(), c, group));
        HBox tools = row(12, filters, spacer(), search); tools.getStyleClass().add("records-toolbar");
        search.setMinWidth(155); search.setPrefWidth(230); HBox.setHgrow(search,Priority.SOMETIMES);
        clearFilters.setId("clear-filters");
        HBox tableHeading = row(12,label("Collection records","section-title"),spacer(),clearFilters,undo); tableHeading.getStyleClass().add("records-heading");
        table.setId("waste-table"); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); table.setFixedCellSize(52); table.setMinHeight(160); table.setPrefHeight(420); VBox.setVgrow(table,Priority.ALWAYS);
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
        TableColumn<Waste,Double> weight = new TableColumn<>("WEIGHT (kg)"); weight.setPrefWidth(110); weight.setMinWidth(95);
        weight.setCellValueFactory(v -> new javafx.beans.property.ReadOnlyObjectWrapper<>(v.getValue().getWeight()));
        weight.setCellFactory(c -> new TableCell<>() {
            { setStyle("-fx-alignment: CENTER-RIGHT;"); }
            @Override protected void updateItem(Double value, boolean empty) { super.updateItem(value,empty); setText(empty || value == null ? null : format(value)); }
        });
        TableColumn<Waste,String> location = textColumn("COLLECTION LOCATION", Waste::getLocation, 175);
        TableColumn<Waste,Void> actions = new TableColumn<>("REMOVE"); actions.setMinWidth(72); actions.setPrefWidth(75); actions.setMaxWidth(85); actions.setSortable(false);
        actions.setCellFactory(c -> new TableCell<>() {
            private final Button remove = button("", "delete-button", () -> { Waste w = getTableRow().getItem(); if (w != null) delete(w); });
            { remove.setGraphic(icon("trash", "#40916c", 16)); remove.setTooltip(new Tooltip("Remove record")); remove.setAccessibleText("Remove record"); }
            @Override protected void updateItem(Void value, boolean empty) { super.updateItem(value, empty); setGraphic(empty ? null : remove); setAlignment(Pos.CENTER); }
        });
        table.getColumns().addAll(id, name, category, weight, location, actions);
        VBox empty = column(10, icon("layers","#40916c",28),emptyTitle, emptyCopy, button("Log New Waste Item", "primary", this::openAddModal)); empty.setAlignment(Pos.CENTER); empty.setPadding(new Insets(24)); table.setPlaceholder(empty);
        table.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.DELETE && table.getSelectionModel().getSelectedItem() != null) delete(table.getSelectionModel().getSelectedItem()); });
        undo.setId("undo-button");
        HBox foot = row(10, footer, spacer(),label("Visible records weight", "muted"),visibleWeight); foot.getStyleClass().add("ledger-footer");
        ledger.getChildren().addAll(tableHeading, table, foot); VBox.setVgrow(ledger,Priority.ALWAYS);
        VBox page = column(16, inventoryStats(),distributionCard(),tools,ledger); page.getStyleClass().add("logs-content");
        return page;
    }
    private TableColumn<Waste,String> textColumn(String title, java.util.function.Function<Waste,String> value, double width) {
        TableColumn<Waste,String> c = new TableColumn<>(title); c.setPrefWidth(width); c.setCellValueFactory(v -> new ReadOnlyStringWrapper(value.apply(v.getValue()))); return c;
    }
    private ToggleButton filter(String text, Category category, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text); b.getStyleClass().add("filter"); b.setToggleGroup(group); b.setOnAction(e -> setFilter(category)); filterButtons.put(category, b); return b;
    }
    private void setFilter(Category category) { selectedCategory = category; filterButtons.get(category).setSelected(true); refreshTable(); }
    private void focusSearch() { showView(true); search.requestFocus(); }
    private void clearFilters() { search.clear(); setFilter(null); }
    private void refreshTable() {
        List<Waste> results = tracker.search(search.getText(), selectedCategory);
        Waste selected = table.getSelectionModel().getSelectedItem();
        table.getItems().setAll(results); table.sort();
        if (selected != null && results.contains(selected)) table.getSelectionModel().select(selected);
        footer.setText("Showing " + results.size() + " of " + tracker.viewWaste().size() + " records");
        visibleWeight.setText(format(results.stream().mapToDouble(Waste::getWeight).sum()) + " kg");
        clearFilters.setDisable(search.getText().isBlank() && selectedCategory == null);
        boolean emptySession = tracker.viewWaste().isEmpty();
        emptyTitle.setText(emptySession ? "No waste records yet" : "No matching records");
        emptyCopy.setText(emptySession ? "Add your first waste item to start tracking." : "Try another search or clear the filters above.");
        undo.setDisable(!tracker.canUndo());
    }
    private void refresh() {
        double sum = tracker.calculateTotalWeight(); total.setText(format(sum)); centerWeight.setText(format(sum)); recordCount.setText("" + tracker.viewWaste().size());
        for (Category c : Category.values()) {
            double n = tracker.calculateCategoryWeight(c); weights.get(c).setText(format(n)); percentages.get(c).setText(String.format(Locale.US, "%.1f%% share", sum == 0 ? 0 : n / sum * 100)); counts.get(c).setText("" + tracker.count(c));
        }
        GraphicsContext gc = donut.getGraphicsContext2D(); gc.clearRect(0,0,176,176); gc.setLineWidth(21); gc.setStroke(Color.web("#f0f4f1")); gc.strokeOval(21,21,134,134);
        double start = 90;
        for (Category c : Category.values()) {
            double extent = sum == 0 ? 0 : tracker.calculateCategoryWeight(c) / sum * 360;
            gc.setStroke(Color.web(color(c))); gc.strokeArc(21,21,134,134,start,-extent,ArcType.OPEN); start -= extent;
        }
        logsTotal.setText(format(sum) + " kg"); logsCount.setText(tracker.viewWaste().size() + " records logged");
        for (Category c : Category.values()) {
            logWeights.get(c).setText(format(tracker.calculateCategoryWeight(c)) + " kg");
            String countText = tracker.count(c) + (tracker.count(c) == 1 ? " record" : " records");
            logCounts.get(c).setText(countText);
            percentages.get(c).setText(percentages.get(c).getText() + "\n" + countText);
        }
        drawDistribution(); refreshRecent(); refreshTable(); updateHeap();
    }
    private void updateHeap() {
        Runtime r = Runtime.getRuntime(); long used = r.totalMemory() - r.freeMemory();
        heapText.setText(String.format(Locale.US, "%.1f MB", used / 1048576.0)); heapBar.setProgress((double) used / r.maxMemory());
    }
    private void delete(Waste w) { if (tracker.removeWaste(w.getId())) { refresh(); toast("Record Deleted", "Removed \"" + w.getWasteName() + "\". Use Undo last delete to restore."); } }
    private void undoDelete() { if (tracker.undoLastDelete()) { refresh(); toast("Restored", "Record restored to your collection."); } }

    private void openAddModal() {
        if (overlay != null) return;
        TextField name = new TextField(); name.setPromptText("e.g., Apple Core, Cardboard Sheet"); name.setId("input-name");
        ComboBox<Category> category = new ComboBox<>(FXCollections.observableArrayList(Category.values())); category.setPromptText("Choose category..."); category.setMaxWidth(Double.MAX_VALUE); category.setId("input-category");
        TextField weight = new TextField(); weight.setPromptText("0.5"); weight.setId("input-weight");
        TextField location = new TextField(); location.setPromptText("Cafeteria Bin A"); location.setId("input-location");
        Label helper = label("Choose a category to see example items.", "category-helper"); helper.setWrapText(true); helper.setId("category-helper");
        category.valueProperty().addListener((o,a,c) -> helper.setText(c == null ? "Choose a category to see example items." : switch(c) {
            case BIODEGRADABLE -> "Examples: fruit peels, food scraps, leaves and garden waste.";
            case RECYCLABLE -> "Examples: clean paper, cardboard, bottles and metal cans. Follow local collection rules.";
            case RESIDUAL -> "Examples: used tissues, diapers and non-recyclable wrappers.";
        }));
        Label error = label("", "validation-error"); error.setId("form-error"); error.setWrapText(true);
        VBox left = field("WEIGHT (kg)", weight), right = field("LOCATION / BIN", location); HBox.setHgrow(left,Priority.ALWAYS); HBox.setHgrow(right,Priority.ALWAYS);
        Button save = button("Add Waste Record", "primary", () -> {
            try {
                double value;
                try { value = Double.parseDouble(weight.getText().strip()); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a valid numeric weight in kilograms."); }
                Waste added = tracker.addWaste(name.getText(), category.getValue(), value, location.getText());
                refresh(); closeModal(); toast("Record Added", "Logged \"" + added.getWasteName() + "\" successfully.");
            } catch (IllegalArgumentException ex) { error.setText(ex.getMessage()); }
        }); save.setId("save-record"); save.setDefaultButton(true);
        VBox card = column(18, modalHeader("Add Waste Item", "Record a collection for this session", "plus"), field("WASTE ITEM NAME", name), column(8,field("WASTE CATEGORY", category),helper), new HBox(12,left,right), error, row(10, spacer(), button("Cancel", "quiet", this::closeModal), save));
        showModal(card); Platform.runLater(name::requestFocus);
    }
    private void openExitModal() {
        if (overlay != null) return;
        Label warning = label("Your records are stored only for this session. Closing the application will remove all logged waste records.", "warning-copy"); warning.setWrapText(true);
        VBox note = column(10, label("SESSION RECORDS WILL BE LOST", "warning-heading mono"), warning); note.getStyleClass().add("warning-note");
        Button exit = button("Exit application", "danger", () -> { tracker.clear(); stage.hide(); Platform.exit(); }); exit.setId("confirm-exit");
        VBox card = column(20, modalHeader("Exit Program Confirmation", "Your session has not been saved", "power"), label("Are you sure you want to end this tracking session?", "muted"), note, row(10, spacer(), button("Resume Tracking", "quiet", this::closeModal), exit));
        showModal(card);
    }
    private HBox modalHeader(String title, String subtitle, String icon) { HBox h = row(12, icon(icon,"#40916c",24), column(4,label(title,"modal-title"),label(subtitle,"muted")), spacer(), button("×","quiet",this::closeModal)); h.getStyleClass().add("modal-header"); return h; }
    private VBox field(String caption, Control control) { return column(7, label(caption,"field-label mono"), control); }
    private void showModal(VBox card) {
        focusBeforeModal = root.getScene().getFocusOwner(); shell.setDisable(true); toastArea.setDisable(true);
        card.getStyleClass().add("modal-card"); card.setMaxSize(470,Region.USE_PREF_SIZE);
        overlay = new StackPane(card); overlay.getStyleClass().add("modal-overlay"); overlay.setId("modal-overlay");
        root.getChildren().add(overlay);
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
    private static String color(Category c) { return switch(c) { case BIODEGRADABLE -> "#10b981"; case RECYCLABLE -> "#06b6d4"; case RESIDUAL -> "#f59e0b"; }; }
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
            double initialWidth=stage.getWidth(), initialHeight=stage.getHeight();
            maximizeControl.fire(); require(expanded && Math.abs(stage.getWidth()-screenBounds().getWidth())<2,"Maximize fills work area");
            maximizeControl.fire(); require(!expanded && Math.abs(stage.getWidth()-initialWidth)<2 && Math.abs(stage.getHeight()-initialHeight)<2,"Restore returns to windowed size");
            snapshot(dir.resolve("dashboard.png"));
            ((Button)root.lookup("#about-button")).fire(); require(overlay!=null,"About opens");
            snapshot(dir.resolve("about.png")); ((Button)root.lookup("#about-done")).fire(); require(overlay==null,"About closes");
            search.setText("unmatched"); ((Button)root.lookup("#overview-rec")).fire();
            require(showingLogs && selectedCategory==Category.RECYCLABLE && search.getText().isEmpty() && table.getItems().size()==2,"Overview card filters and clears stale search");
            ((Button)logsPage.lookup("#logs-bio")).fire(); require(selectedCategory==Category.BIODEGRADABLE && table.getItems().size()==2,"Inventory category card filters");
            ((Button)logsPage.lookup("#logs-all")).fire(); require(selectedCategory==null && table.getItems().size()==5,"Total card clears filters");
            overviewNav.fire();
            require(!showingLogs && workspacePane.getCenter()==scroll,"Overview is initial screen");
            logsNav.fire(); require(showingLogs && workspacePane.getCenter()==logsScroll,"Dedicated logs navigation");
            snapshot(dir.resolve("logs.png"));
            require(statShares.get(Category.RECYCLABLE).getText().equals("74.1%"),"Distribution uses global weights");
            require(table.getItems().size() == 5,"Demo records"); require(Math.abs(tracker.calculateTotalWeight()-2.7)<1e-9,"Demo total");
            search.setText("cafeteria"); require(table.getItems().size()==1,"Search location"); require(visibleWeight.getText().equals("0.4 kg"),"Filtered total");
            overviewNav.fire(); require(!showingLogs && search.getText().equals("cafeteria"),"Navigation preserves filters");
            logsNav.fire(); require(table.getItems().size()==1,"Records view restores search"); clearFilters.fire(); require(table.getItems().size()==5,"Clear filters");
            setFilter(Category.RECYCLABLE); require(table.getItems().size()==2,"Category filter"); require(visibleWeight.getText().equals("2.0 kg") && logsTotal.getText().equals("2.7 kg"),"Global and visible totals independent");
            snapshot(dir.resolve("filtered-logs.png")); setFilter(null);
            search.setText("no matching item"); require(table.getItems().isEmpty() && visibleWeight.getText().equals("0.0 kg"),"No matches"); snapshot(dir.resolve("no-matches.png")); clearFilters.fire();
            TableColumn<Waste,?> weightColumn = table.getColumns().get(3);
            weightColumn.setSortType(TableColumn.SortType.DESCENDING); table.getSortOrder().setAll(weightColumn); table.sort();
            Waste heaviest = table.getItems().get(0); require(heaviest.getWeight()==1.2,"Weight sorts numerically");
            delete(heaviest); require(tracker.viewWaste().stream().noneMatch(w -> w.getId()==heaviest.getId()),"Sorted deletion uses ID");
            undo.fire(); require(table.getItems().get(0).getId()==heaviest.getId(),"Undo preserves sort"); table.getSortOrder().clear();
            openAddModal(); root.applyCss(); root.layout(); snapshot(dir.resolve("add-dialog.png"));
            ((TextField)root.lookup("#input-name")).setText("Test food");
            ((ComboBox<Category>)root.lookup("#input-category")).setValue(Category.BIODEGRADABLE);
            require(((Label)root.lookup("#category-helper")).getText().contains("fruit peels"),"Category examples update");
            ((TextField)root.lookup("#input-weight")).setText("-2");
            ((TextField)root.lookup("#input-location")).setText("Test bin");
            ((Button)root.lookup("#save-record")).fire(); require(tracker.viewWaste().size()==5 && !((Label)root.lookup("#form-error")).getText().isEmpty(),"Invalid weight rejected");
            ((TextField)root.lookup("#input-weight")).setText("2"); ((Button)root.lookup("#save-record")).fire(); require(tracker.viewWaste().size()==6 && overlay==null,"Add via form");
            Waste last = tracker.viewWaste().get(5); delete(last); require(tracker.viewWaste().size()==5,"Delete"); undo.fire(); require(tracker.viewWaste().size()==6,"Undo");
            tracker.clear(); refresh(); toastArea.getChildren().clear(); snapshot(dir.resolve("empty.png")); require(table.getItems().isEmpty() && tracker.calculateTotalWeight()==0,"Empty state");
            require(statShares.values().stream().allMatch(l -> l.getText().equals("0.0%")),"Empty distribution resets");
            openExitModal(); root.applyCss(); root.layout(); snapshot(dir.resolve("exit-dialog.png")); closeModal(); require(stage.isShowing(),"Exit cancellation");
            stage.setWidth(1120); stage.setHeight(740); tracker.loadDemoRecords(); refresh();
            PauseTransition resizeWait = new PauseTransition(Duration.millis(400));
            resizeWait.setOnFinished(event -> {
                try {
                    require(root.getWidth() == 1120,"Compact viewport resized"); snapshot(dir.resolve("compact-logs.png"));
                    overviewNav.fire(); snapshot(dir.resolve("compact.png"));
                    require(overviewNav.getStyleClass().contains("nav-active") && !logsNav.getStyleClass().contains("nav-active"),"Active navigation styling");
                    System.out.println("PASS: JavaFX form, validation, search, filters, delete, undo, empty state, exit cancellation and compact resize");
                    Files.writeString(dir.resolve("ui-test-result.txt"),"PASS: two-screen navigation, retained filters, visible/global totals, clear filters, numeric sorting, sorted deletion and undo, form validation, add, delete, undo, empty states, exit cancellation, compact layouts, About panel and category card navigation; 10 scene snapshots.\n");
                    testWindowStates();
                } catch (Throwable e) { e.printStackTrace(); Platform.exit(); System.exit(1); }
            }); resizeWait.play();
        } catch(Throwable e) { e.printStackTrace(); Platform.exit(); System.exit(1); }
    }
    private void testWindowStates() {
        ((Button)root.lookup("#window-fullscreen")).fire();
        windowCheckLater(() -> {
            require(stage.isFullScreen(),"Full screen enters");
            toggleFullScreen();
            windowCheckLater(() -> {
                require(!stage.isFullScreen(),"Full screen exits");
                ((Button)root.lookup("#window-minimize")).fire();
                windowCheckLater(() -> {
                    require(stage.isIconified(),"Window minimizes");
                    stage.setIconified(false);
                    windowCheckLater(() -> {
                        require(!stage.isIconified(),"Window restores from taskbar state");
                        System.out.println("PASS: maximize, restore, full screen, exit full screen, minimize, restore from minimized");
                        stage.hide(); Platform.exit();
                    });
                });
            });
        });
    }
    private void windowCheckLater(Runnable check) {
        PauseTransition wait = new PauseTransition(Duration.millis(350));
        wait.setOnFinished(e -> { try { check.run(); } catch(Throwable error) { error.printStackTrace(); Platform.exit(); System.exit(1); } });
        wait.play();
    }
    private void snapshot(Path target) throws Exception {
        root.applyCss(); root.layout();
        javafx.scene.image.WritableImage image = root.snapshot(null,null);
        int w=(int)image.getWidth(),h=(int)image.getHeight(); BufferedImage buffered = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        int[] pixels=new int[w*h]; image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w); buffered.setRGB(0,0,w,h,pixels,0,w); ImageIO.write(buffered,"png",target.toFile());
    }
    private static void require(boolean condition,String description) { if(!condition) throw new AssertionError(description); }
}
