package org.example;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.bytedeco.opencv.global.opencv_core.CV_32SC1;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * FaceRecognitionService encapsulates OpenCV-based face and body detection
 * and LBPH recognition along with training data management.
 *
 * Responsibilities:
 * - Load cascade classifiers and the LBPH recognizer
 * - Train from `training-data` filesystem and save/load model file
 * - Detect faces and bodies in frames and return recognition results
 * - Persist new training images and delete a user's training set
 */
public class FaceRecognitionService {

    private final DatabaseService databaseService;
    private final CascadeClassifier faceDetector;
    private final CascadeClassifier bodyDetector;
    private final LBPHFaceRecognizer faceRecognizer;
    private final Map<Integer, String> labelNameMap;
    private volatile boolean isTrained = false;

    public static class RecognitionResult {
        private final int label; private final String name; private final double confidence; private final Rect faceRect;
        public RecognitionResult(int l, String n, double c, Rect r) { label=l; name=n; confidence=c; faceRect=r; }
        public int getLabel() { return label; } public String getName() { return name; } public double getConfidence() { return confidence; } public Rect getFaceRect() { return faceRect; }
    }

    public FaceRecognitionService(DatabaseService dbService) {
        this.databaseService = dbService;
        this.labelNameMap = dbService.getLabelNameMap();

        try {
            this.faceDetector = new CascadeClassifier(loadCascadeFile("haarcascade_frontalface_default.xml"));
            this.bodyDetector = new CascadeClassifier(loadCascadeFile("haarcascade_fullbody.xml"));
        } catch (IOException e) { throw new RuntimeException("CRITICAL ERROR: Could not load cascade files.", e); }
        this.faceRecognizer = LBPHFaceRecognizer.create();
    }

    private String loadCascadeFile(String cascadeFileName) throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(cascadeFileName);
        if (is == null) { throw new IOException("Cascade file not found in resources: " + cascadeFileName); }
        File tempFile = File.createTempFile("cascade-", ".xml");
        tempFile.deleteOnExit();
        try (FileOutputStream os = new FileOutputStream(tempFile)) { byte[] buffer = new byte[1024]; int bytesRead; while ((bytesRead = is.read(buffer)) != -1) { os.write(buffer, 0, bytesRead); } }
        is.close();
        return tempFile.getAbsolutePath();
    }

    public void deleteTrainingData(String role, String name) {
        Path userDirectory = Paths.get("training-data", role, name);
        if (Files.exists(userDirectory)) {
            try {
                Files.walk(userDirectory)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("Successfully deleted training data for: " + name);
            } catch (IOException e) {
                System.err.println("Error deleting training data directory for " + name + ": " + e.getMessage());
            }
        }
    }

    public RectVector detectBodies(Mat frame) {
        Mat grayFrame = new Mat();
        cvtColor(frame, grayFrame, COLOR_BGR2GRAY);
        equalizeHist(grayFrame, grayFrame);
        RectVector bodies = new RectVector();
        bodyDetector.detectMultiScale(grayFrame, bodies, 1.1, 3, 0, new Size(50, 100), new Size());
        grayFrame.release();
        return bodies;
    }

    public boolean isTrained() {
        return this.isTrained;
    }

    public void rebuildLabelNameMap() {
        System.out.println("Rebuilding label-to-name map from database...");
        Map<Integer, String> dbLabelMap = databaseService.getLabelNameMap();
        synchronized (labelNameMap) {
            labelNameMap.clear();
            labelNameMap.putAll(dbLabelMap);
        }
        System.out.println("Label-to-name map rebuilt. Found " + labelNameMap.size() + " users.");
    }

    public void trainModel(String trainingDataPath) {
        rebuildLabelNameMap();

        if (labelNameMap.isEmpty()) {
            System.err.println("Info: No users found in the database. Model will not be trained.");
            this.isTrained = false;
            return;
        }

        MatVector images = new MatVector();
        List<Integer> labelsList = new ArrayList<>();

        System.out.println("Starting model training based on database users...");
        CascadeClassifier trainingDetector;
        try {
            trainingDetector = new CascadeClassifier(loadCascadeFile("haarcascade_frontalface_default.xml"));
        } catch (IOException e) {
            System.err.println("FATAL: Could not load cascade for training thread. Aborting retrain.");
            return;
        }

        for (Map.Entry<Integer, String> entry : this.labelNameMap.entrySet()) {
            int currentLabel = entry.getKey();
            String displayName = entry.getValue();
            String[] parts = displayName.split(": ");
            if (parts.length != 2) continue;
            String role = parts[0];
            String name = parts[1];

            Path personDirPath = Paths.get(trainingDataPath, role, name);

            if (!Files.isDirectory(personDirPath)) {
                System.out.println("Warning: No training photo folder found for user '" + name + "'. Skipping.");
                continue;
            }

            try (Stream<Path> paths = Files.walk(personDirPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase().endsWith(".jpg") || path.toString().toLowerCase().endsWith(".png"))
                        .forEach(imageFile -> {
                            try {
                                Mat image = imdecode(new Mat(Files.readAllBytes(imageFile)), 1);
                                if (!image.empty()) {
                                    addFaceToTrainingSet(image, images, labelsList, currentLabel, imageFile.toFile(), trainingDetector);
                                }
                                image.release();
                            } catch (IOException e) {
                                System.err.println("Error reading file: " + imageFile);
                            }
                        });
            } catch (IOException e) {
                System.err.println("Error walking directory: " + personDirPath);
            }
        }

        if (images.size() > 0) {
            Mat labelsMat = new Mat(labelsList.size(), 1, CV_32SC1);
            org.bytedeco.javacpp.indexer.IntIndexer indexer = labelsMat.createIndexer();
            for (int i = 0; i < labelsList.size(); i++) {
                indexer.put(i, labelsList.get(i));
            }

            faceRecognizer.train(images, labelsMat);
            this.isTrained = true;
            System.out.println("Model training complete. Trained on " + images.size() + " images for " + labelNameMap.size() + " users.");
            labelsMat.release();
        } else {
            System.err.println("Error: Could not find any valid training photos for the users in the database.");
            this.isTrained = false;
        }

        images.close();
        trainingDetector.close();
    }

    private void addFaceToTrainingSet(Mat image, MatVector images, List<Integer> labelsList, int label, File imageFile, CascadeClassifier detectorToUse) {
        Mat grayImage = new Mat();
        cvtColor(image, grayImage, COLOR_BGR2GRAY);
        equalizeHist(grayImage, grayImage);
        RectVector detectedFaces = new RectVector();
        detectorToUse.detectMultiScale(grayImage, detectedFaces, 1.1, 3, 0, new Size(30, 30), new Size());

        if (detectedFaces.size() == 1) {
            Mat face = new Mat(grayImage, detectedFaces.get(0));
            Mat resizedFace = new Mat();
            resize(face, resizedFace, new Size(200, 200));
            images.push_back(resizedFace);
            labelsList.add(label);
            face.release();
        } else {
            System.out.println("Warning: Skipping image '" + imageFile.getName() + "' (found " + detectedFaces.size() + " faces). DELETING FILE.");
            try {
                Files.delete(imageFile.toPath());
            } catch (IOException e) {
                System.err.println("Error deleting skipped image: " + e.getMessage());
            }
        }
        grayImage.release();
        detectedFaces.releaseReference();
    }

    public List<RecognitionResult> recognizeFaces(Mat frame) {
        List<RecognitionResult> results = new ArrayList<>();
        if (!isTrained) return results;

        Mat grayFrame = new Mat();
        cvtColor(frame, grayFrame, COLOR_BGR2GRAY);
        equalizeHist(grayFrame, grayFrame);
        RectVector detectedFaces = new RectVector();
        this.faceDetector.detectMultiScale(grayFrame, detectedFaces, 1.1, 6, 0, new Size(100, 100), new Size());

        for (long i = 0; i < detectedFaces.size(); i++) {
            Rect faceRect = detectedFaces.get(i);
            Mat face = new Mat(grayFrame, faceRect);
            Mat resizedFace = new Mat();
            resize(face, resizedFace, new Size(200, 200));

            int[] predictedLabel = new int[1];
            double[] confidence = new double[1];
            faceRecognizer.predict(resizedFace, predictedLabel, confidence);

            String name = getLabelName(predictedLabel[0]);
            results.add(new RecognitionResult(predictedLabel[0], name, confidence[0], faceRect));

            face.release();
            resizedFace.release();
        }
        grayFrame.release();
        detectedFaces.releaseReference();
        return results;
    }

    public RectVector detectFacesOnly(Mat frame) {
        Mat grayFrame = new Mat();
        cvtColor(frame, grayFrame, COLOR_BGR2GRAY);
        equalizeHist(grayFrame, grayFrame);
        RectVector detectedFaces = new RectVector();
        this.faceDetector.detectMultiScale(grayFrame, detectedFaces, 1.1, 6, 0, new Size(100, 100), new Size());
        grayFrame.release();
        return detectedFaces;
    }

    public void saveTrainingImage(Mat faceImage, String personName, String personRole) {
        Path dirPath = Paths.get("training-data", personRole, personName);
        try {
            Files.createDirectories(dirPath);
            String fileName = personName + "_" + System.currentTimeMillis() + ".jpg";
            imwrite(dirPath.resolve(fileName).toString(), faceImage);
            System.out.println("Saved new training image: " + fileName + " for Role: " + personRole);
        } catch (IOException e) { System.err.println("Error saving new training image: " + e.getMessage()); }
    }

    public String getLabelName(int label) {
        synchronized (labelNameMap) {
            return labelNameMap.getOrDefault(label, "Unknown");
        }
    }

    public void saveModel(String filePath) { faceRecognizer.save(filePath); }

    public void loadModel(String filePath) {
        faceRecognizer.read(filePath);
        this.isTrained = true;
    }
}