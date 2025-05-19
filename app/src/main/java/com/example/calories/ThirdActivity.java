package com.example.calories;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ThirdActivity extends AppCompatActivity {

    private ImageView imageView;
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.5f;
    private static final float IOU_THRESHOLD = 0.5f;
    private Map<String, Nutrients> nutritionData;

    private TextView proteinTextView;
    private TextView fiberTextView;
    private TextView carbsTextView;
    private TextView fatTextView;
    private TextView caloriesTextView;

    private final String[] CLASS_NAMES = {
            "Bitter melon", "Brinjal", "Cabbage", "Calabash", "Capsicum", "Cauliflower",
            "Cherry", "Garlic", "Ginger", "Green Chili", "Kiwi", "Lady finger", "Onion",
            "Potato", "Sponge Gourd", "Tomato", "apple", "avocado", "banana", "cucumber",
            "dragon fruit", "egg", "guava", "mango", "orange", "oren", "peach", "pear",
            "pineapple", "strawberry", "sugar apple", "watermelon"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_third);

        imageView = findViewById(R.id.selectedImageView);

        // Initialize nutrient TextViews
        proteinTextView = findViewById(R.id.proteinTextView);
        fiberTextView = findViewById(R.id.fiberTextView);
        carbsTextView = findViewById(R.id.carbsTextView);
        fatTextView = findViewById(R.id.fatTextView);
        caloriesTextView = findViewById(R.id.caloriesTextView);

        nutritionData = loadNutritionData();

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("imageUri")) {
            Uri imageUri = Uri.parse(intent.getStringExtra("imageUri"));
            displayImageAndRunModel(imageUri);
        }
    }

    private Map<String, Nutrients> loadNutritionData() {
        Map<String, Nutrients> data = new HashMap<>();
        try {
            InputStream inputStream = getAssets().open("nutrition_data.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();

            JSONObject jsonObject = new JSONObject(jsonString.toString());
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String fruit = keys.next();
                JSONObject nutrients = jsonObject.getJSONObject(fruit);
                float calories = (float) nutrients.getDouble("calories");
                float protein = (float) nutrients.getDouble("protein");
                float carbs = (float) nutrients.getDouble("carbs");
                float fat = (float) nutrients.getDouble("fat");
                float fiber = (float) nutrients.getDouble("fiber");
                data.put(fruit.toLowerCase(), new Nutrients(calories, protein, carbs, fat, fiber));
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Handle error silently or log, no UI update since no resultTextView
        }
        return data;
    }

    private void displayImageAndRunModel(Uri imageUri) {
        try {
            InputStream imageStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
            imageView.setImageBitmap(bitmap);

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
            ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);

            Interpreter interpreter = new Interpreter(loadModelFile("best_model.tflite"));
            float[][][] output = new float[1][36][8400]; // adjust shape if needed

            interpreter.run(inputBuffer, output);
            List<Detection> result = postprocessOutput(output[0]);

            displayResults(result);

        } catch (Exception e) {
            e.printStackTrace();
            // Handle error silently or log
        }
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        buffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixelValue : intValues) {
            buffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f); // R
            buffer.putFloat(((pixelValue >> 8) & 0xFF) / 255.0f);  // G
            buffer.putFloat((pixelValue & 0xFF) / 255.0f);         // B
        }

        return buffer;
    }

    private MappedByteBuffer loadModelFile(String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<Detection> postprocessOutput(float[][] outputs) {
        List<float[]> boxes = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        List<Integer> classes = new ArrayList<>();

        float[][] transposedOutputs = new float[outputs[0].length][outputs.length];
        for (int i = 0; i < outputs.length; i++) {
            for (int j = 0; j < outputs[0].length; j++) {
                transposedOutputs[j][i] = outputs[i][j];
            }
        }

        for (float[] output : transposedOutputs) {
            float xCenter = output[0];
            float yCenter = output[1];
            float w = output[2];
            float h = output[3];
            float[] classProbs = new float[CLASS_NAMES.length];
            System.arraycopy(output, 4, classProbs, 0, CLASS_NAMES.length);
            float score = max(classProbs);
            if (score > CONFIDENCE_THRESHOLD) {
                float xMin = (xCenter - w / 2) * INPUT_SIZE;
                float yMin = (yCenter - h / 2) * INPUT_SIZE;
                float xMax = (xCenter + w / 2) * INPUT_SIZE;
                float yMax = (yCenter + h / 2) * INPUT_SIZE;
                boxes.add(new float[]{xMin, yMin, xMax, yMax});
                scores.add(score);
                int classId = argMax(classProbs);
                if (classId >= 0 && classId < CLASS_NAMES.length) {
                    classes.add(classId);
                }
            }
        }

        List<Integer> indices = nms(boxes, scores, IOU_THRESHOLD);

        Map<String, Integer> counts = new HashMap<>();
        for (int idx : indices) {
            int clsId = classes.get(idx);
            String clsName = CLASS_NAMES[clsId];
            counts.put(clsName, counts.containsKey(clsName) ? counts.get(clsName) + 1 : 1);
        }

        List<Detection> detections = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String name = entry.getKey();
            int count = entry.getValue();
            Nutrients nutrients = nutritionData.get(name.toLowerCase());
            if (nutrients != null) {
                nutrients = new Nutrients(
                        nutrients.calories * count,
                        nutrients.protein * count,
                        nutrients.carbs * count,
                        nutrients.fat * count,
                        nutrients.fiber * count
                );
            } else {
                nutrients = new Nutrients(0, 0, 0, 0, 0);
            }
            detections.add(new Detection(name, count, nutrients));
        }

        Collections.sort(detections, new Comparator<Detection>() {
            @Override
            public int compare(Detection d1, Detection d2) {
                return Integer.compare(d2.count, d1.count);
            }
        });

        return detections;
    }

    private float max(float[] array) {
        float max = array[0];
        for (float value : array) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private int argMax(float[] array) {
        int maxIndex = 0;
        float max = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private List<Integer> nms(List<float[]> boxes, List<Float> scores, float iouThreshold) {
        List<Integer> indices = new ArrayList<>();
        List<Integer> sortedIndices = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            sortedIndices.add(i);
        }
        Collections.sort(sortedIndices, new Comparator<Integer>() {
            @Override
            public int compare(Integer i1, Integer i2) {
                return Float.compare(scores.get(i2), scores.get(i1));
            }
        });

        boolean[] used = new boolean[boxes.size()];
        for (int i : sortedIndices) {
            if (!used[i]) {
                indices.add(i);
                used[i] = true;
                float[] box1 = boxes.get(i);
                for (int j : sortedIndices) {
                    if (!used[j]) {
                        float[] box2 = boxes.get(j);
                        if (calculateIoU(box1, box2) > iouThreshold) {
                            used[j] = true;
                        }
                    }
                }
            }
        }
        return indices;
    }

    private float calculateIoU(float[] box1, float[] box2) {
        float x1 = Math.max(box1[0], box2[0]);
        float y1 = Math.max(box1[1], box2[1]);
        float x2 = Math.min(box1[2], box2[2]);
        float y2 = Math.min(box1[3], box2[3]);
        float intersection = Math.max(0f, x2 - x1) * Math.max(0f, y2 - y1);
        float area1 = (box1[2] - box1[0]) * (box1[3] - box1[1]);
        float area2 = (box2[2] - box2[0]) * (box2[3] - box2[1]);
        return intersection / (area1 + area2 - intersection);
    }

    private void displayResults(List<Detection> detections) {
        if (detections.isEmpty()) {
            // No objects detected - show zeros in nutrient views
            proteinTextView.setText("Protein: 0 g");
            fiberTextView.setText("Fiber: 0 g");
            carbsTextView.setText("Carbs: 0 g");
            fatTextView.setText("Fat: 0 g");
            caloriesTextView.setText("Calories: 0 kcal");
            return;
        }

        float totalProtein = 0f, totalFiber = 0f, totalCarbs = 0f, totalFat = 0f, totalCalories = 0f;

        for (Detection detection : detections) {
            totalProtein += detection.nutrients.protein;
            totalFiber += detection.nutrients.fiber;
            totalCarbs += detection.nutrients.carbs;
            totalFat += detection.nutrients.fat;
            totalCalories += detection.nutrients.calories;
        }

        proteinTextView.setText(String.format("Protein: %.1f g", totalProtein));
        fiberTextView.setText(String.format("Fiber: %.1f g", totalFiber));
        carbsTextView.setText(String.format("Carbs: %.1f g", totalCarbs));
        fatTextView.setText(String.format("Fat: %.1f g", totalFat));
        caloriesTextView.setText(String.format("Calories: %.1f kcal", totalCalories));
    }

    private static class Nutrients {
        float calories;
        float protein;
        float carbs;
        float fat;
        float fiber;

        public Nutrients(float calories, float protein, float carbs, float fat, float fiber) {
            this.calories = calories;
            this.protein = protein;
            this.carbs = carbs;
            this.fat = fat;
            this.fiber = fiber;
        }
    }

    private static class Detection {
        String name;
        int count;
        Nutrients nutrients;

        public Detection(String name, int count, Nutrients nutrients) {
            this.name = name;
            this.count = count;
            this.nutrients = nutrients;
        }
    }
}
