# Training Data Directory

This directory is where you store face recognition training images for the Smart Security Camera system.

## Structure
```
training-data/
├── [Role]/
│   └── [PersonName]/
│       ├── person1_photo1.jpg
│       ├── person1_photo2.jpg
│       └── ...
└── README.md
```

## How to Add Training Data

1. **Create a role directory** (e.g., `Owner`, `Family`, `Employee`)
2. **Create a person directory** with their name (e.g., `John`, `Jane`)
3. **Add multiple photos** of the person (recommended: 10-20 photos)
   - Use clear, front-facing photos
   - Include different lighting conditions
   - Include different expressions
   - Use JPG format
   - Recommended size: 200x200 pixels or larger

## Example
```
training-data/
├── Owner/
│   └── John/
│       ├── John_001.jpg
│       ├── John_002.jpg
│       └── John_003.jpg
└── Family/
    └── Sarah/
        ├── Sarah_001.jpg
        └── Sarah_002.jpg
```

## Important Notes
- The system will automatically detect faces in your photos
- Photos with multiple faces will be rejected
- Photos with no faces will be rejected
- Use descriptive filenames for easier management
