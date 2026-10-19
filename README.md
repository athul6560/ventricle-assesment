# Ventricle Assessment

Android app (Kotlin, Jetpack Compose, MVVM, Hilt, Room) for the
**Complex Bi-Ventricular Feasibility Assessment** proforma used in congenital
cardiac surgery planning.

## Workflow

1. **Patients** — the start screen lists every patient (name, MRD no, category
   chip, last updated). Tap `+` to add a new patient, tap a row to open their
   form, trash icon to delete (with confirm).
2. **Assessment form** — 5 steps per patient:
   - *Patient* — name, MRD no, gender, age, diagnosis, procedure/discharge
     dates, category (Possible / Difficult-Doubtful / Not Possible)
   - *Primary (1°)* — echocardiography / CT-MRI planning questions
   - *Secondary (2°)* — VR/3DP planning questions
   - *Post-Op* — post-surgical discussion & validation
   - *Review* — read-only summary of everything entered
3. **Export PDF** — the Review step exports the completed proforma (patient
   info + all three levels) as an A4 PDF and shares it via the system chooser.
   File name: `VentricleAssessment_<MRD or name>_<yyyyMMdd>.pdf`.

Every question uses the proforma's dual format: trichotomous Yes / No /
Uncertain plus a 5-point Likert scale. All data is stored locally in Room
(`ventricle.db`) — no network calls anywhere.

## Stack

- Kotlin 2.0.21, Compose BOM 2024.12.01 (Material 3), AGP 8.7.3, Gradle 8.9
- Hilt 2.56.2 (DI), Room 2.6.1 (local DB), navigation-compose 2.8.4
- PDF export via framework `android.graphics.pdf.PdfDocument` (no extra deps)

## Build

```bash
gradle wrapper          # once, to generate gradle-wrapper.jar (gitignored)
./gradlew assembleDebug  # or open in Android Studio and press Run
```

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) builds a debug
APK on manual dispatch.
