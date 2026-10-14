# Folder Cleaner

Kotlin + Jetpack Compose Android app for mass-selecting and deleting folders.

## Build
1. Open this folder in Android Studio (Koala or newer, JDK 17). Let Gradle sync.
2. Run on a device, or Build > Build APK(s). Release builds are signed with the debug key so they install directly.

## Use
1. Grant "All files access" on first launch (required to see/delete empty folders on Android 11+).
2. Tap the **Empty** chip to list only folders with no real files (hidden files like `.nomedia` are ignored).
3. **Long-press** a folder and drag across the grid to select a range. Drag near the top/bottom edge to auto-scroll.
   Starting the drag on an already selected folder deselects instead. Tap toggles a single folder.
4. Tap **Select all** to grab everything in the current filter, then **Delete** and confirm once.

## Safety
- The confirmation dialog warns when selected folders contain files.
- DCIM, Pictures, Download, Movies, Music, Documents, Android and similar top-level folders are never deleted.
- Symbolic links are never followed.
- Hidden folders (starting with ".") are not listed, but their contents count toward whether a parent is empty.
