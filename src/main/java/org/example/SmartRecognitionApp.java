package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.example.camera.CameraManager;
import org.example.model.RecognitionLog;
import org.example.network.WebServer;
import org.example.profile.HeightProfileStore;
import org.example.ui.MainControls;
import org.example.ui.SettingsWindow;
import org.example.ui.UserManagementWindow;
import org.example.video.VideoRecorder;

import javafx.embed.swing.SwingFXUtils;

// --- FIX: Corrected the import paths ---
import org.bytedeco.javacpp.BytePointer; // CORRECTED PATH
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.example.network.SharedFrameHolder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

public class SmartRecognitionApp extends Application {

    private enum SystemState { INITIALIZING, ENROLLMENT_ONLY, RECOGNIZING }
    private volatile SystemState currentState = SystemState.INITIALIZING;

    private ImageView imageView;
    private Label statusLabel;
    private MainControls controls;
    private Stage primaryStage;

    private TableView<RecognitionLog> historyTable;
    private ObservableList<RecognitionLog> recognitionLogList;

    private final CameraManager cameraManager = new CameraManager();
    private final DatabaseService databaseService = new DatabaseService();
    private final FaceRecognitionService recognitionService = new FaceRecognitionService(databaseService);
    private final OpenCVFrameConverter.ToMat toMatConverter = new OpenCVFrameConverter.ToMat();
    private final Java2DFrameConverter java2DConverter = new Java2DFrameConverter();
    private final AppConfig appConfig = new AppConfig();
    private WebServer webServer;

    private final AtomicBoolean isRetraining = new AtomicBoolean(false);
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private volatile Mat currentFrame;

    private final Map<Integer, org.example.model.CaptureState> lastCaptureState = new HashMap<>();
    private int newPhotosCaptured = 0;
    private static final int RETRAIN_THRESHOLD = 10;
    private static final String MODEL_FILE = "my_trained_model.yml";
    private static final String TRAINING_DIR = "training-data";
    private static final double POSE_CHANGE_THRESHOLD = 0.15;
    private static final long COOLDOWN_PERIOD_MS = 5000;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 50.0;

    private Rect lastSeenUnknownFace = null;
    private long unknownFaceLastSeenTime = 0;
    private static final long UNKNOWN_FACE_STABLE_MS = 2000;

    private final Deque<Mat> frameBuffer = new LinkedList<>();
    private static final int VIDEO_BUFFER_FRAMES = 450;
    private static final String RECORDINGS_DIR = "recordings";

    private static final int MIN_HEIGHT_SAMPLES = 10;
    private static final double HEIGHT_MATCH_TOLERANCE = 0.15;
    private static final String HEIGHT_PROFILES_FILE = "height_profiles.json";
    private final HeightProfileStore heightProfileStore = new HeightProfileStore(HEIGHT_PROFILES_FILE);

    public static void main(String[] args) {
        try { Files.createDirectories(Paths.get(RECORDINGS_DIR)); }
        catch (IOException e) { System.err.println("Could not create recordings directory: " + e.getMessage()); }
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.webServer = new WebServer(databaseService, recognitionService);

        this.recognitionLogList = FXCollections.observableArrayList();

        this.imageView = new ImageView();
        this.statusLabel = new Label("Status: Initializing...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-padding: 10;");

        this.controls = new MainControls(
                this::manualCapture,
                this::recordLast15Seconds,
                this::enrollManually,
                () -> {},
                this::showUserManagementWindow,
                this::showSettingsWindow,
                this::cleanShutdown
        );
        controls.viewHistoryButton.setDisable(true);
        controls.viewHistoryButton.setVisible(false);

        this.historyTable = createHistoryTable();

        VBox root = new VBox(10, imageView, statusLabel, historyTable, controls);
        root.setAlignment(Pos.CENTER);
        VBox.setVgrow(historyTable, Priority.ALWAYS);

        Scene scene = new Scene(root, 1050, 850);
        primaryStage.setTitle("Smart Security Camera Dashboard");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> cleanShutdown());

        webServer.start();

        primaryStage.show();
        startBackgroundServices();
    }

    private TableView<RecognitionLog> createHistoryTable() {
        TableView<RecognitionLog> table = new TableView<>();
        table.setPlaceholder(new Label("No recognition events yet..."));

        TableColumn<RecognitionLog, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<RecognitionLog, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));

        TableColumn<RecognitionLog, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setPrefWidth(200);

        TableColumn<RecognitionLog, Double> confidenceCol = new TableColumn<>("Confidence");
        confidenceCol.setCellValueFactory(new PropertyValueFactory<>("confidence"));

        table.getColumns().addAll(nameCol, roleCol, timeCol, confidenceCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.setItems(recognitionLogList);
        return table;
    }

    private void showUserManagementWindow() {
        Runnable retrain = () -> {
            updateStatus("User deleted. Retraining AI model...");
            recognitionService.trainModel(TRAINING_DIR);
            recognitionService.saveModel(MODEL_FILE);
            Platform.runLater(() -> updateStatus("Model retrained successfully."));
        };
        new UserManagementWindow(databaseService, recognitionService, retrain).show(primaryStage);
    }

    private void showSettingsWindow() {
        new SettingsWindow(appConfig, () -> updateStatus("Settings saved. Restart application for changes to take full effect.")).show(primaryStage);
    }

    private void startBackgroundServices() {
        heightProfileStore.load();
        new Thread(() -> {
            File modelFile = new File(MODEL_FILE);
            if (modelFile.exists() && !modelFile.isDirectory()) {
                updateStatus("Status: Loading AI model...");
                recognitionService.loadModel(MODEL_FILE);
                recognitionService.rebuildLabelNameMap();
            }

            if (!recognitionService.isTrained()) {
                updateStatus("Status: No model found or data is empty. Training from disk...");
                recognitionService.trainModel(TRAINING_DIR);
            }

            ObservableList<RecognitionLog> initialHistory = databaseService.getHistory();
            Platform.runLater(() -> recognitionLogList.setAll(initialHistory));

            if (recognitionService.isTrained()) {
                currentState = SystemState.RECOGNIZING;
                updateStatus("Status: Live recognition started.");
            } else {
                currentState = SystemState.ENROLLMENT_ONLY;
                updateStatus("Status: No trained data. Please enroll a new person.");
            }

            CameraManager.GrabberFactory factory = () -> {
                String cameraType = appConfig.getCameraType();
                if (cameraType.equals("WEBCAM")) {
                    System.out.println("Starting default webcam...");
                    return new OpenCVFrameGrabber(0);
                } else {
                    String ipCameraUrl = appConfig.getIpCameraUrl();
                    if (ipCameraUrl.isEmpty()) {
                        Platform.runLater(() -> {
                            updateStatus("ERROR: IP Camera selected, but URL is empty. Please configure in settings.");
                            showAlert("Configuration Error", "IP Camera selected, but URL is empty. Please configure in settings.");
                        });
                        throw new IllegalStateException("IP Camera URL is empty");
                    }
                    System.out.println("Starting IP camera from URL: " + ipCameraUrl);
                    return new OpenCVFrameGrabber(ipCameraUrl);
                }
            };

            cameraManager.startAsync(
                    factory,
                    frame -> {
                        Mat grabbedMat = toMatConverter.convert(frame);
                        if (grabbedMat == null || grabbedMat.empty()) return;
                        synchronized (frameBuffer) {
                            frameBuffer.addLast(grabbedMat.clone());
                            if (frameBuffer.size() > VIDEO_BUFFER_FRAMES) {
                                frameBuffer.removeFirst().release();
                            }
                        }
                        synchronized (this) {
                            if (this.currentFrame != null && !this.currentFrame.isNull()) {
                                this.currentFrame.release();
                            }
                            this.currentFrame = grabbedMat.clone();
                        }
                        processAndDisplayFrame(grabbedMat);
                    },
                    e -> Platform.runLater(() -> {
                        updateStatus("ERROR: Could not start camera! " + e.getMessage());
                        showAlert("Camera Error", "Could not start camera. Please check your settings or camera connection.\nDetails: " + e.getMessage());
                    })
            );
        }).start();
    }

    private void processAndDisplayFrame(Mat frame) {
        Mat frameToProcess = frame.clone();
        switch (currentState) {
            case RECOGNIZING:
                processFrameForRecognition(frameToProcess);
                break;
            case ENROLLMENT_ONLY:
                processFrameForEnrollment(frameToProcess);
                break;
            case INITIALIZING:
                break;
        }

        // --- Feed the MJPEG Stream ---
        BytePointer jpegBytes = new BytePointer();
        opencv_imgcodecs.imencode(".jpg", frameToProcess, jpegBytes);
        byte[] byteArray = new byte[(int) jpegBytes.limit()];
        jpegBytes.get(byteArray);
        SharedFrameHolder.updateFrame(byteArray);
        jpegBytes.close();

        Image imageToShow = matToImage(frameToProcess);
        Platform.runLater(() -> imageView.setImage(imageToShow));

        frameToProcess.release();
        frame.release();
    }


    private void processFrameForRecognition(Mat frame) {
        RectVector detectedBodies = recognitionService.detectBodies(frame);
        List<FaceRecognitionService.RecognitionResult> faceResults = recognitionService.recognizeFaces(frame);
        boolean unknownFaceFoundThisFrame = false;
        Set<Rect> matchedBodyRects = new HashSet<>();

        for (FaceRecognitionService.RecognitionResult result : faceResults) {
            Rect faceRect = result.getFaceRect();
            String nameLabelText;
            Scalar color;

            if (result.getLabel() != -1 && result.getConfidence() < 80) {
                nameLabelText = result.getName() + " (" + String.format("%.2f", result.getConfidence()) + ")";
                if (result.getName().startsWith("Owner:")) color = new Scalar(0, 255, 0, 0);
                else if (result.getName().startsWith("Employee:")) color = new Scalar(255, 255, 0, 0);
                else if (result.getName().equalsIgnoreCase("Unknown")) {
                    nameLabelText = "Unknown";
                    color = new Scalar(0, 0, 255, 0);
                    unknownFaceFoundThisFrame = true;
                    handleUnknownFace(faceRect);
                } else {
                    color = new Scalar(0, 255, 0, 0);
                }

                if (!nameLabelText.equals("Unknown")) {
                    for (long i = 0; i < detectedBodies.size(); i++) {
                        Rect bodyRect = detectedBodies.get(i);
                        if (bodyRect.contains(new Point(faceRect.x() + faceRect.width() / 2, faceRect.y() + faceRect.height() / 2))) {
                            heightProfileStore.addSample(result.getLabel(), bodyRect.height());
                            matchedBodyRects.add(bodyRect);
                            break;
                        }
                    }

                    if (result.getConfidence() < HIGH_CONFIDENCE_THRESHOLD && shouldCaptureNewPose(result.getLabel(), faceRect)) {
                        String[] parts = result.getName().split(": ");
                        if (parts.length == 2) {
                            Mat frameForSaving;
                            synchronized(this) {
                                if(this.currentFrame != null && !this.currentFrame.empty()){
                                    frameForSaving = this.currentFrame.clone();
                                } else {
                                    frameForSaving = frame.clone();
                                }
                            }
                            recognitionService.saveTrainingImage(frameForSaving.apply(faceRect), parts[1], parts[0]);
                            frameForSaving.release();

                            databaseService.logRecognition(result.getLabel(), result.getConfidence());
                            addNewLogToTable(result.getName(), new Timestamp(System.currentTimeMillis()).toString(), result.getConfidence());

                            lastCaptureState.put(result.getLabel(), new org.example.model.CaptureState(System.currentTimeMillis(), faceRect));
                            updateStatus("Status: Captured new photo for: " + parts[1]);
                            newPhotosCaptured++;
                            if (newPhotosCaptured >= RETRAIN_THRESHOLD) triggerBackgroundRetraining();
                            try {
                                String json = String.format(java.util.Locale.ROOT,
                                        "{\"type\":\"recognition\",\"name\":\"%s\",\"confidence\":%.2f,\"box\":{\"x\":%d,\"y\":%d,\"width\":%d,\"height\":%d}}",
                                        parts[1], result.getConfidence(), faceRect.x(), faceRect.y(), faceRect.width(), faceRect.height());
                                org.example.network.EventBus.broadcast(json);
                            } catch (Throwable ignored) {}
                        }
                    } else if (shouldLogRecognition(result.getLabel())) {
                        databaseService.logRecognition(result.getLabel(), result.getConfidence());
                        addNewLogToTable(result.getName(), new Timestamp(System.currentTimeMillis()).toString(), result.getConfidence());

                        lastCaptureState.put(result.getLabel(), new org.example.model.CaptureState(System.currentTimeMillis(), result.getFaceRect()));

                        System.out.println("Logged recognition for user " + result.getLabel() + " to database.");
                        try {
                            String json = String.format(java.util.Locale.ROOT,
                                    "{\"type\":\"recognition\",\"name\":\"%s\",\"confidence\":%.2f,\"box\":{\"x\":%d,\"y\":%d,\"width\":%d,\"height\":%d}}",
                                    result.getName(), result.getConfidence(), result.getFaceRect().x(), result.getFaceRect().y(), result.getFaceRect().width(), result.getFaceRect().height());
                            org.example.network.EventBus.broadcast(json);
                        } catch (Throwable ignored) {}
                    }
                }
            } else {
                nameLabelText = "Unknown";
                color = new Scalar(0, 0, 255, 0);
                unknownFaceFoundThisFrame = true;
                handleUnknownFace(faceRect);
                try {
                    String json = String.format(java.util.Locale.ROOT,
                            "{\"type\":\"unknown\",\"box\":{\"x\":%d,\"y\":%d,\"width\":%d,\"height\":%d}}",
                            faceRect.x(), faceRect.y(), faceRect.width(), faceRect.height());
                    org.example.network.EventBus.broadcast(json);
                } catch (Throwable ignored) {}
            }
            rectangle(frame, faceRect, color, 2, LINE_8, 0);
            putText(frame, nameLabelText, new Point(faceRect.x(), faceRect.y() - 10), FONT_HERSHEY_SIMPLEX, 0.7, color, 2, LINE_8, false);
        }

        if (!unknownFaceFoundThisFrame) {
            resetUnknownFaceTracking();
        }

        for (long i = 0; i < detectedBodies.size(); i++) {
            Rect bodyRect = detectedBodies.get(i);
            if (matchedBodyRects.contains(bodyRect)) continue;
            int bodyHeight = bodyRect.height();
            String bestMatchName = null;
            Optional<Integer> bestLabel = heightProfileStore.bestMatchByHeight(bodyHeight, MIN_HEIGHT_SAMPLES, HEIGHT_MATCH_TOLERANCE);
            if (bestLabel.isPresent()) {
                bestMatchName = recognitionService.getLabelName(bestLabel.get());
            }
            if (bestMatchName != null && !bestMatchName.equalsIgnoreCase("Unknown")) {
                Scalar color = new Scalar(0, 255, 255, 0);
                String label = "Possible: " + bestMatchName + " (by height)";
                rectangle(frame, bodyRect, color, 2, LINE_8, 0);
                putText(frame, label, new Point(bodyRect.x(), bodyRect.y() - 10), FONT_HERSHEY_SIMPLEX, 0.7, color, 2, LINE_8, false);
            } else {
                Scalar color = new Scalar(255, 0, 0, 0);
                rectangle(frame, bodyRect, color, 2, LINE_8, 0);
                putText(frame, "Body", new Point(bodyRect.x(), bodyRect.y() - 10), FONT_HERSHEY_SIMPLEX, 0.7, color, 2, LINE_8, false);
            }
        }
        detectedBodies.releaseReference();
    }

    private void addNewLogToTable(String displayName, String timestamp, double confidence) {
        String[] parts = displayName.split(": ");
        String role = parts.length > 1 ? parts[0] : "Unknown";
        String name = parts.length > 1 ? parts[1] : parts[0];

        RecognitionLog newLog = new RecognitionLog(name, role, timestamp, confidence);

        Platform.runLater(() -> {
            recognitionLogList.add(0, newLog);
            if (recognitionLogList.size() > 500) {
                recognitionLogList.remove(recognitionLogList.size() - 1);
            }
        });
    }

    private void processFrameForEnrollment(Mat frame) {
        RectVector detectedFaces = recognitionService.detectFacesOnly(frame);
        boolean unknownFaceFoundThisFrame = false;
        if (detectedFaces.size() > 0) {
            Rect faceRect = detectedFaces.get(0);
            rectangle(frame, faceRect, new Scalar(0, 0, 255, 0), 2, LINE_8, 0);
            Point namePos = new Point(faceRect.x(), faceRect.y() - 10);
            putText(frame, "Unknown (Enrollment Mode)", namePos, FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 0, 255, 0), 2, LINE_8, false);
            unknownFaceFoundThisFrame = true;
            handleUnknownFace(faceRect);
        }
        if (!unknownFaceFoundThisFrame) {
            resetUnknownFaceTracking();
        }
        detectedFaces.releaseReference();
    }

    private void enrollManually() {
        if (lastSeenUnknownFace != null) {
            Mat faceToSave;
            synchronized(this){
                if (this.currentFrame == null || this.currentFrame.empty()) return;
                faceToSave = this.currentFrame.apply(lastSeenUnknownFace).clone();
            }

            TextInputDialog nameDialog = new TextInputDialog();
            nameDialog.setTitle("New Person Enrollment");
            nameDialog.setHeaderText("Enter the name for the new person.");
            nameDialog.setContentText("Name:");
            Optional<String> nameResult = nameDialog.showAndWait();

            nameResult.ifPresent(name -> {
                if (name != null && !name.trim().isEmpty()) {
                    List<String> roles = Arrays.asList("Owner", "Employee", "Visitor");
                    ChoiceDialog<String> roleDialog = new ChoiceDialog<>("Employee", roles);
                    roleDialog.setTitle("Role Selection");
                    roleDialog.setHeaderText("Select a role for " + name);
                    roleDialog.setContentText("Role:");
                    Optional<String> roleResult = roleDialog.showAndWait();

                    roleResult.ifPresent(role -> {
                        int newUserId = databaseService.addUser(name, role);
                        if (newUserId != -1) {
                            updateStatus("Status: Enrolling '" + name + "' as " + role + " with ID " + newUserId);
                            recognitionService.saveTrainingImage(faceToSave, name, role);
                            newPhotosCaptured++;
                            triggerBackgroundRetraining();
                        } else {
                            updateStatus("Error: Could not enroll " + name + ". They may already exist.");
                        }
                    });
                }
            });
            faceToSave.release();
            resetUnknownFaceTracking();
        }
    }


    private void handleUnknownFace(Rect faceRect) {
        if (isRetraining.get()) return;
        lastSeenUnknownFace = faceRect;
        long currentTime = System.currentTimeMillis();
        if (unknownFaceLastSeenTime == 0) {
            unknownFaceLastSeenTime = currentTime;
        }
        if (currentTime - unknownFaceLastSeenTime > UNKNOWN_FACE_STABLE_MS) {
            Platform.runLater(() -> {
                controls.enrollButton.setDisable(false);
                if (!isRecording.get() && !isRetraining.get()) {
                    updateStatus("Status: Stable unknown face detected. Ready to enroll.");
                }
            });
        }
    }

    private void resetUnknownFaceTracking() {
        if (lastSeenUnknownFace != null) {
            lastSeenUnknownFace = null;
            unknownFaceLastSeenTime = 0;
            Platform.runLater(() -> controls.enrollButton.setDisable(true));
        }
    }

    private void triggerBackgroundRetraining() {
        if (isRetraining.compareAndSet(false, true)) {
            updateStatus("Status: Retraining model with " + newPhotosCaptured + " new photos...");
            final int photosForThisTraining = newPhotosCaptured;
            newPhotosCaptured = 0;
            new Thread(() -> {
                recognitionService.trainModel(TRAINING_DIR);
                recognitionService.saveModel(MODEL_FILE);
                if (currentState == SystemState.ENROLLMENT_ONLY && recognitionService.isTrained()) {
                    currentState = SystemState.RECOGNIZING;
                }
                updateStatus("Status: Model update complete! (" + photosForThisTraining + " photos added)");
                try { Thread.sleep(5000); } catch (InterruptedException e) {}
                if (!isRecording.get()) { updateStatus("Status: Live recognition started."); }
                isRetraining.set(false);
            }).start();
        }
    }

    private void manualCapture() {
        if (currentState != SystemState.RECOGNIZING) { updateStatus("Status: Please wait until the model is trained."); return; }
        Mat frameToProcess;
        synchronized (this) {
            if (currentFrame == null || currentFrame.empty()) { updateStatus("Error: No frame available to capture."); return; }
            frameToProcess = currentFrame.clone();
        }
        List<FaceRecognitionService.RecognitionResult> results = recognitionService.recognizeFaces(frameToProcess);
        FaceRecognitionService.RecognitionResult bestCandidate = null;
        int maxArea = 0;
        for (FaceRecognitionService.RecognitionResult result : results) {
            if (result.getLabel() != -1 && result.getConfidence() < 80) {
                Rect faceRect = result.getFaceRect();
                int area = faceRect.width() * faceRect.height();
                if (area > maxArea) { maxArea = area; bestCandidate = result; }
            }
        }
        if (bestCandidate != null) {
            String[] parts = bestCandidate.getName().split(": ");
            if (parts.length == 2) {
                String role = parts[0];
                String name = parts[1];
                recognitionService.saveTrainingImage(frameToProcess.apply(bestCandidate.getFaceRect()), name, role);
                updateStatus("Status: Manually captured photo for: " + name);
                newPhotosCaptured++;
                if (newPhotosCaptured >= RETRAIN_THRESHOLD) { triggerBackgroundRetraining(); }
            }
        } else {
            updateStatus("Status: No recognized person found to capture.");
        }
        frameToProcess.release();
    }

    private void recordLast15Seconds() {
        if (isRecording.compareAndSet(false, true)) {
            updateStatus("Status: Saving last 15 seconds...");
            new Thread(() -> {
                List<Mat> matsToRecord;
                synchronized (frameBuffer) {
                    matsToRecord = new ArrayList<>(frameBuffer.size());
                    for (Mat mat : frameBuffer) {
                        matsToRecord.add(mat.clone());
                    }
                }
                if (matsToRecord.isEmpty()) {
                    updateStatus("Error: Frame buffer is empty. Nothing to record.");
                    isRecording.set(false);
                    return;
                }
                OpenCVFrameGrabber activeGrabber = cameraManager.getGrabber();
                if (activeGrabber == null) {
                    updateStatus("Error: Camera not initialized.");
                    isRecording.set(false);
                    return;
                }
                VideoRecorder recorder = new VideoRecorder(isRecording, RECORDINGS_DIR);
                recorder.recordBufferedMats(
                        matsToRecord,
                        activeGrabber,
                        () -> { try { Thread.sleep(5000); } catch (InterruptedException ignored) {} if (!isRetraining.get()) { updateStatus("Status: Live recognition started."); } },
                        this::updateStatus
                );
            }).start();
        } else {
            updateStatus("Status: Recording already in progress.");
        }
    }

    private void cleanShutdown() {
        if (webServer != null) {
            webServer.stop();
        }

        heightProfileStore.save();
        System.out.println("Starting clean shutdown...");
        cameraManager.stop();
        synchronized (this) {
            if (this.currentFrame != null && !this.currentFrame.isNull()) {
                this.currentFrame.release();
                this.currentFrame = null;
                System.out.println("Final currentFrame Mat released.");
            }
        }
        synchronized (frameBuffer) {
            while (!frameBuffer.isEmpty()) {
                frameBuffer.removeFirst().release();
            }
            System.out.println("All Mats in frame buffer released.");
        }
        System.out.println("Shutdown complete. Exiting.");
        Platform.exit();
        System.exit(0);
    }

    private void updateStatus(String message) { Platform.runLater(() -> statusLabel.setText(message)); }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private boolean shouldCaptureNewPose(int label, Rect currentFaceRect) {
        long currentTime = System.currentTimeMillis();
        org.example.model.CaptureState state = lastCaptureState.get(label);
        if (state == null) return true;
        if (currentTime - state.lastCaptureTime < COOLDOWN_PERIOD_MS) return false;
        return hasPoseChanged(state.lastFaceRect, currentFaceRect);
    }

    private boolean shouldLogRecognition(int label) {
        org.example.model.CaptureState state = lastCaptureState.get(label);
        if (state == null) {
            return true;
        }
        long currentTime = System.currentTimeMillis();
        return currentTime - state.lastCaptureTime > COOLDOWN_PERIOD_MS;
    }

    private boolean hasPoseChanged(Rect lastRect, Rect currentRect) {
        int lastCenterX = lastRect.x() + lastRect.width() / 2;
        int lastCenterY = lastRect.y() + lastRect.height() / 2;
        int currentCenterX = currentRect.x() + currentRect.width() / 2;
        int currentCenterY = currentRect.y() + currentRect.height() / 2;
        double dx = currentCenterX - lastCenterX;
        double dy = currentCenterY - lastCenterY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double positionChange = distance / (double) lastRect.width();
        double lastArea = (double) lastRect.width() * lastRect.height();
        double currentArea = (double) currentRect.width() * currentRect.height();
        double areaChange = Math.abs(currentArea - lastArea) / lastArea;
        return positionChange > POSE_CHANGE_THRESHOLD || areaChange > POSE_CHANGE_THRESHOLD;
    }

    private Image matToImage(Mat mat) {
        Frame frame = toMatConverter.convert(mat);
        BufferedImage bufferedImage = java2DConverter.getBufferedImage(frame, 1.0, false, null);
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    private void saveHeightProfiles() { heightProfileStore.save(); }

    @Override public void stop() { cleanShutdown(); }
}