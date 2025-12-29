package org.example;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class Main extends Application {

    // ====== Данни: контролни точки ======
    private final List<Point2D> controlPoints = new ArrayList<>();

    // ====== UI ======
    private Canvas canvas;

    private Label degreeLabel;
    private Slider dtSlider;

    private Slider tSlider;
    private Label tLabel;
    private Label btLabel;

    private CheckBox showBtCheck;
    private CheckBox showLevelsCheck;

    private Button playPauseBtn;

    // Examples (само това оставяме като “професионално”)
    private Button ex1Btn, ex2Btn, ex3Btn;

    // ====== Drag ======
    private int draggedIndex = -1;
    private static final double PICK_RADIUS = 10;

    // ====== Анимация “стъпка напред” ======
    private boolean playing = false;
    private double drawT = 0.0; // 0..1

    @Override
    public void start(Stage stage) {
        canvas = new Canvas(900, 600);

        degreeLabel = new Label("Degree (n): 0 | Points: 0");

        dtSlider = new Slider(0.001, 0.02, 0.004);
        dtSlider.setShowTickLabels(true);
        dtSlider.setShowTickMarks(true);

        tSlider = new Slider(0.0, 1.0, 0.5);
        tSlider.setShowTickLabels(true);
        tSlider.setShowTickMarks(true);

        tLabel = new Label("t = 0.500");
        btLabel = new Label("B(t) = -");

        showBtCheck = new CheckBox("Show point B(t)");
        showBtCheck.setSelected(true);

        showLevelsCheck = new CheckBox("Show de Casteljau levels");
        showLevelsCheck.setSelected(false);

        playPauseBtn = new Button("Play");
        Button clearBtn = new Button("Clear");

        ex1Btn = new Button("Example 1");
        ex2Btn = new Button("Example 2");
        ex3Btn = new Button("Example 3");

        // listeners
        dtSlider.valueProperty().addListener((obs, oldV, newV) -> redraw());

        tSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (!playing) redraw();
        });

        showBtCheck.selectedProperty().addListener((obs, oldV, newV) -> redraw());
        showLevelsCheck.selectedProperty().addListener((obs, oldV, newV) -> redraw());

        // buttons
        playPauseBtn.setOnAction(e -> {
            playing = !playing;
            playPauseBtn.setText(playing ? "Pause" : "Play");
            tSlider.setDisable(playing);

            if (playing) drawT = 0.0;
            redraw();
        });

        clearBtn.setOnAction(e -> {
            controlPoints.clear();
            stopPlaying();
            redraw();
        });

        ex1Btn.setOnAction(e -> loadExample1());
        ex2Btn.setOnAction(e -> loadExample2());
        ex3Btn.setOnAction(e -> loadExample3());

        VBox right = new VBox(
                10,
                degreeLabel,

                new Label("Step dt (по-малко = по-гладко)"),
                dtSlider,

                new Label("Parameter t (0..1)"),
                tSlider,
                tLabel,
                btLabel,

                showBtCheck,
                showLevelsCheck,

                new Separator(),
                new Label("Presets (Examples)"),
                ex1Btn,
                ex2Btn,
                ex3Btn,

                new Separator(),
                playPauseBtn,
                clearBtn,

                new Label("Ляв клик: добавя точка\nDrag: мести точка\nДесен клик върху точка: трие")
        );
        right.setStyle("-fx-padding: 12; -fx-background-color: #f3f3f3;");

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setRight(right);

        Scene scene = new Scene(root);
        stage.setTitle("Polar/Bezier (de Casteljau) - arbitrary degree");
        stage.setScene(scene);
        stage.show();

        // mouse events
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> draggedIndex = -1);

        // step-forward timer
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!playing) return;

                double dt = dtSlider.getValue();
                drawT = Math.min(drawT + dt, 1.0);

                if (drawT >= 1.0 - 1e-9) stopPlaying();
                redraw();
            }
        };
        timer.start();

        redraw();
    }

    private void stopPlaying() {
        playing = false;
        playPauseBtn.setText("Play");
        tSlider.setDisable(false);
        drawT = 0.0;
    }

    // ====== Mouse handlers ======
    private void onMousePressed(MouseEvent e) {
        if (playing) stopPlaying();

        double x = e.getX(), y = e.getY();

        if (e.getButton() == MouseButton.PRIMARY) {
            int idx = findPointIndexNear(x, y);
            if (idx >= 0) {
                draggedIndex = idx;
                return;
            }
            controlPoints.add(new Point2D(x, y));
            redraw();
        } else if (e.getButton() == MouseButton.SECONDARY) {
            int idx = findPointIndexNear(x, y);
            if (idx >= 0) {
                controlPoints.remove(idx);
                redraw();
            }
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (playing) stopPlaying();
        if (draggedIndex < 0) return;

        controlPoints.set(draggedIndex, new Point2D(e.getX(), e.getY()));
        redraw();
    }

    private int findPointIndexNear(double x, double y) {
        for (int i = 0; i < controlPoints.size(); i++) {
            if (controlPoints.get(i).distance(x, y) <= PICK_RADIUS) return i;
        }
        return -1;
    }

    // ====== Drawing ======
    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int points = controlPoints.size();
        int degree = Math.max(points - 1, 0);
        degreeLabel.setText("Degree (n): " + degree + " | Points: " + points);

        drawControlPolygon(g);
        drawControlPoints(g);

        if (controlPoints.size() < 2) {
            double t = tSlider.getValue();
            tLabel.setText(String.format("t = %.3f", t));
            btLabel.setText("B(t) = -");
            return;
        }

        double tForViz = playing ? drawT : tSlider.getValue();
        tLabel.setText(String.format("t = %.3f", tForViz));

        double dt = dtSlider.getValue();
        double tEnd = playing ? drawT : 1.0;
        drawBezierUpTo(g, dt, tEnd);

        Point2D bt = deCasteljau(controlPoints, tForViz);
        btLabel.setText(String.format("B(t) = (%.1f, %.1f)", bt.getX(), bt.getY()));

        if (showBtCheck.isSelected()) {
            g.setFill(Color.RED);
            g.fillOval(bt.getX() - 5, bt.getY() - 5, 10, 10);
        }

        if (showLevelsCheck.isSelected()) {
            drawCasteljauLevels(g, tForViz);
        }
    }

    private void drawControlPoints(GraphicsContext g) {
        g.setFill(Color.BLACK);
        for (Point2D p : controlPoints) {
            g.fillOval(p.getX() - 4, p.getY() - 4, 8, 8);
        }
    }

    private void drawControlPolygon(GraphicsContext g) {
        g.setStroke(Color.GRAY);
        g.setLineWidth(1.2);
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            Point2D a = controlPoints.get(i);
            Point2D b = controlPoints.get(i + 1);
            g.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
        }
    }

    private void drawBezierUpTo(GraphicsContext g, double dt, double tEnd) {
        g.setStroke(Color.DODGERBLUE);
        g.setLineWidth(2.2);

        Point2D prev = deCasteljau(controlPoints, 0.0);

        for (double t = dt; t <= tEnd + 1e-9; t += dt) {
            double tt = Math.min(t, tEnd);
            Point2D cur = deCasteljau(controlPoints, tt);
            g.strokeLine(prev.getX(), prev.getY(), cur.getX(), cur.getY());
            prev = cur;
        }
    }

    // ====== de Casteljau levels ======
    private void drawCasteljauLevels(GraphicsContext g, double t) {
        List<List<Point2D>> levels = computeCasteljauLevels(controlPoints, t);

        for (int k = 1; k < levels.size(); k++) {
            List<Point2D> pts = levels.get(k);
            if (pts.size() < 2) continue;

            double alpha = Math.max(0.15, 0.60 - k * 0.08);
            g.setStroke(Color.color(1.0, 0.55, 0.0, alpha));
            g.setLineWidth(1.2);

            for (int i = 0; i < pts.size() - 1; i++) {
                Point2D a = pts.get(i);
                Point2D b = pts.get(i + 1);
                g.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
            }

            g.setFill(Color.color(1.0, 0.55, 0.0, alpha));
            for (Point2D p : pts) {
                g.fillOval(p.getX() - 3, p.getY() - 3, 6, 6);
            }
        }
    }

    private static List<List<Point2D>> computeCasteljauLevels(List<Point2D> control, double t) {
        List<List<Point2D>> levels = new ArrayList<>();
        List<Point2D> current = new ArrayList<>(control);
        levels.add(current);

        while (current.size() > 1) {
            List<Point2D> next = new ArrayList<>();
            for (int i = 0; i < current.size() - 1; i++) {
                Point2D a = current.get(i);
                Point2D b = current.get(i + 1);
                next.add(a.multiply(1 - t).add(b.multiply(t)));
            }
            levels.add(next);
            current = next;
        }
        return levels;
    }

    // ====== de Casteljau (произволен ред) ======
    private static Point2D deCasteljau(List<Point2D> control, double t) {
        List<Point2D> tmp = new ArrayList<>(control);
        int n = tmp.size() - 1;

        for (int k = 1; k <= n; k++) {
            for (int i = 0; i <= n - k; i++) {
                Point2D a = tmp.get(i);
                Point2D b = tmp.get(i + 1);
                tmp.set(i, a.multiply(1 - t).add(b.multiply(t)));
            }
        }
        return tmp.get(0);
    }

    // ====== Examples ======
    private void loadExample1() {
        stopPlaying();
        controlPoints.clear();

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        controlPoints.add(new Point2D(w * 0.10, h * 0.80));
        controlPoints.add(new Point2D(w * 0.25, h * 0.20));
        controlPoints.add(new Point2D(w * 0.75, h * 0.20));
        controlPoints.add(new Point2D(w * 0.90, h * 0.80));

        redraw();
    }

    private void loadExample2() {
        stopPlaying();
        controlPoints.clear();

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        controlPoints.add(new Point2D(w * 0.10, h * 0.50));
        controlPoints.add(new Point2D(w * 0.25, h * 0.15));
        controlPoints.add(new Point2D(w * 0.50, h * 0.85));
        controlPoints.add(new Point2D(w * 0.75, h * 0.15));
        controlPoints.add(new Point2D(w * 0.90, h * 0.50));

        redraw();
    }

    private void loadExample3() {
        stopPlaying();
        controlPoints.clear();

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        controlPoints.add(new Point2D(w * 0.12, h * 0.70));
        controlPoints.add(new Point2D(w * 0.25, h * 0.25));
        controlPoints.add(new Point2D(w * 0.42, h * 0.80));
        controlPoints.add(new Point2D(w * 0.58, h * 0.20));
        controlPoints.add(new Point2D(w * 0.75, h * 0.75));
        controlPoints.add(new Point2D(w * 0.88, h * 0.30));

        redraw();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
