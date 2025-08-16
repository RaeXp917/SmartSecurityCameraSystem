# Sample Training Data Structure

This directory shows you how to organize your training data for the Smart Security Camera system.

## Example Structure

```
training-data/
├── Owner/
│   └── John/
│       ├── John_001.jpg
│       ├── John_002.jpg
│       └── John_003.jpg
├── Family/
│   ├── Sarah/
│   │   ├── Sarah_001.jpg
│   │   └── Sarah_002.jpg
│   └── Mike/
│       ├── Mike_001.jpg
│       └── Mike_002.jpg
└── Employee/
    └── Jane/
        ├── Jane_001.jpg
        └── Jane_002.jpg
```

## How to Use This Sample

1. **Copy the structure**: Use this as a template for your own training data
2. **Replace photos**: Add your own photos following the naming convention
3. **Organize by role**: Group people by their role in your system
4. **Use descriptive names**: Make folder names clear and meaningful

## Photo Requirements

- **Format**: JPG, JPEG, PNG, or BMP
- **Quality**: High resolution, clear images
- **Lighting**: Good lighting conditions
- **Angles**: Front-facing photos work best
- **Quantity**: 10-20 photos per person recommended
- **Content**: Single face per photo (no group shots)

## Naming Convention

- **Folder names**: Use descriptive names (e.g., "John", "Sarah", "Mike")
- **Photo names**: Include person name and sequence number
- **Examples**: `John_001.jpg`, `Sarah_001.jpg`, `Mike_001.jpg`

## Important Notes

- The system will automatically detect faces in your photos
- Photos with multiple faces will be rejected
- Photos with no faces will be rejected
- Use consistent naming for better organization
- Keep backup copies of your training data
