# 🍽️ Calorie Detector App

An Android application that uses computer vision to detect food items from uploaded images and estimates their nutritional values, including **calories, carbohydrates, proteins, fats**, and more. Ideal for individuals tracking their diet or practicing mindful eating.

---

## 📱 Features

- 📷 **Upload or Capture Image**: Users can upload a food image from their gallery or use the phone's camera to capture a new one.
- 🧠 **Food Detection using YOLO**: The app uses a custom-trained YOLO (You Only Look Once) object detection model to identify food items in the image.
- 📊 **Nutritional Estimation**: After detection, it provides nutritional facts like:
  - Calories
  - Carbohydrates
  - Protein
  - Fats
  - Fiber
- 🔍 **Ingredient Lookup**: Access information from a local JSON database for fast response and offline capabilities.
- ⚡ **Lightweight**: Designed with optimized model (TensorFlow Lite) for fast and efficient inference on mobile devices.

---

## 🧑‍💻 How It Works

1. **User uploads an image** from gallery or camera.
2. The app processes the image and runs the YOLO model to detect the food items.
3. For each identified food item, the app looks up its nutritional values from a pre-loaded dataset (`nutrition_data.json`).
4. Displays a breakdown of nutrients for the user.

---

