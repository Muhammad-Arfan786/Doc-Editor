package com.docreader.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.docreader.R;
import com.docreader.databinding.ActivityPdfViewerBinding;
import com.docreader.models.Note;
import com.docreader.utils.NotesManager;
import com.docreader.utils.PdfEditManager;
import com.docreader.views.DrawingView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity for viewing and editing PDF documents.
 * Uses Android's native PdfRenderer API for viewing and iText for editing.
 */
public class PdfViewerActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private ActivityPdfViewerBinding binding;
    private String filePath;
    private String fileName;
    private int totalPages = 0;
    private int currentPage = 0;
    private float currentZoom = 1.0f;
    private NotesManager notesManager;

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private List<ImageView> pageViews = new ArrayList<>();

    // Edit mode
    private boolean isEditMode = false;
    private PdfEditManager pdfEditManager;
    private int currentColor = Color.RED;
    private DrawingView.Tool currentTool = DrawingView.Tool.NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPdfViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        filePath = getIntent().getStringExtra("file_path");
        fileName = getIntent().getStringExtra("file_name");

        if (filePath == null) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        notesManager = new NotesManager(this);
        pdfEditManager = new PdfEditManager(this, filePath);

        setupToolbar();
        setupControls();
        setupSearch();
        setupEditToolbar();
        loadPdf();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(fileName != null ? fileName : "PDF Viewer");
        }

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupControls() {
        // Zoom controls
        binding.btnZoomIn.setOnClickListener(v -> zoomIn());
        binding.btnZoomOut.setOnClickListener(v -> zoomOut());

        // Page navigation
        binding.btnPrevPage.setOnClickListener(v -> goToPreviousPage());
        binding.btnNextPage.setOnClickListener(v -> goToNextPage());

        // Save edit FAB
        binding.fabSaveEdit.setOnClickListener(v -> saveEditedPdf());

        updateZoomLabel();
    }

    private void setupSearch() {
        binding.btnCloseSearch.setOnClickListener(v -> hideSearch());

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void setupEditToolbar() {
        // Tool buttons
        binding.btnPen.setOnClickListener(v -> selectTool(DrawingView.Tool.PEN, binding.btnPen));
        binding.btnHighlighter.setOnClickListener(v -> selectTool(DrawingView.Tool.HIGHLIGHTER, binding.btnHighlighter));
        binding.btnText.setOnClickListener(v -> selectTool(DrawingView.Tool.TEXT, binding.btnText));
        binding.btnEraser.setOnClickListener(v -> selectTool(DrawingView.Tool.ERASER, binding.btnEraser));

        // Color buttons
        binding.btnColorRed.setOnClickListener(v -> selectColor(Color.RED, binding.btnColorRed));
        binding.btnColorBlue.setOnClickListener(v -> selectColor(Color.parseColor("#2196F3"), binding.btnColorBlue));
        binding.btnColorGreen.setOnClickListener(v -> selectColor(Color.parseColor("#4CAF50"), binding.btnColorGreen));
        binding.btnColorYellow.setOnClickListener(v -> selectColor(Color.parseColor("#FFEB3B"), binding.btnColorYellow));

        // Undo/Redo/Clear
        binding.btnUndo.setOnClickListener(v -> binding.drawingView.undo());
        binding.btnRedo.setOnClickListener(v -> binding.drawingView.redo());
        binding.btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear All")
                    .setMessage("Clear all drawings on this page?")
                    .setPositiveButton("Clear", (dialog, which) -> binding.drawingView.clearAll())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Text placement listener
        binding.drawingView.setOnTextPlacementListener((x, y) -> showAddTextDialog(x, y));

        // Default color selection
        binding.btnColorRed.setSelected(true);
    }

    private void selectTool(DrawingView.Tool tool, ImageButton button) {
        currentTool = tool;

        // Clear all tool selections
        binding.btnPen.setSelected(false);
        binding.btnHighlighter.setSelected(false);
        binding.btnText.setSelected(false);
        binding.btnEraser.setSelected(false);

        // Select current tool
        button.setSelected(true);

        // Update drawing view
        binding.drawingView.setTool(tool);
    }

    private void selectColor(int color, ImageButton button) {
        currentColor = color;

        // Clear all color selections
        binding.btnColorRed.setSelected(false);
        binding.btnColorBlue.setSelected(false);
        binding.btnColorGreen.setSelected(false);
        binding.btnColorYellow.setSelected(false);

        // Select current color
        button.setSelected(true);

        // Update drawing view color
        binding.drawingView.setColor(color);
    }

    private void loadPdf() {
        binding.progressBar.setVisibility(View.VISIBLE);

        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);
            totalPages = pdfRenderer.getPageCount();

            renderAllPages();

            binding.progressBar.setVisibility(View.GONE);
            updatePageInfo();

        } catch (IOException e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void renderAllPages() {
        binding.pagesContainer.removeAllViews();
        pageViews.clear();

        for (int i = 0; i < totalPages; i++) {
            ImageView imageView = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            imageView.setLayoutParams(params);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            final int pageIndex = i;
            imageView.setOnClickListener(v -> {
                currentPage = pageIndex;
                updatePageInfo();
            });

            binding.pagesContainer.addView(imageView);
            pageViews.add(imageView);

            renderPage(i);
        }
    }

    private void renderPage(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= totalPages) {
            return;
        }

        PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);

        // Calculate scaled dimensions based on zoom
        int baseWidth = getResources().getDisplayMetrics().widthPixels - 32;
        float aspectRatio = (float) page.getHeight() / page.getWidth();

        int width = (int) (baseWidth * currentZoom);
        int height = (int) (width * aspectRatio);

        // Cap maximum size to prevent out of memory
        int maxSize = 4096;
        if (width > maxSize) {
            width = maxSize;
            height = (int) (width * aspectRatio);
        }
        if (height > maxSize) {
            height = maxSize;
            width = (int) (height / aspectRatio);
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();

        if (pageIndex < pageViews.size()) {
            pageViews.get(pageIndex).setImageBitmap(bitmap);
        }
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;

        if (isEditMode) {
            enterEditMode();
        } else {
            exitEditMode();
        }
    }

    private void enterEditMode() {
        isEditMode = true;

        // Show edit toolbar
        binding.editToolbar.setVisibility(View.VISIBLE);
        binding.drawingView.setVisibility(View.VISIBLE);
        binding.fabSaveEdit.setVisibility(View.VISIBLE);

        // Enable drawing view
        binding.drawingView.setEditEnabled(true);

        // Disable scrolling while editing (keep on single page)
        binding.scrollView.setNestedScrollingEnabled(false);

        // Select pen tool by default
        selectTool(DrawingView.Tool.PEN, binding.btnPen);

        // Update toolbar title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit Mode - Page " + (currentPage + 1));
        }

        Toast.makeText(this, "Edit mode enabled. Draw on the PDF!", Toast.LENGTH_SHORT).show();
    }

    private void exitEditMode() {
        // Check for unsaved changes
        if (binding.drawingView.hasDrawings() || pdfEditManager.hasChanges()) {
            new AlertDialog.Builder(this)
                    .setTitle("Unsaved Changes")
                    .setMessage("You have unsaved changes. Do you want to save them?")
                    .setPositiveButton("Save", (dialog, which) -> {
                        saveEditedPdf();
                        finishExitEditMode();
                    })
                    .setNegativeButton("Discard", (dialog, which) -> {
                        binding.drawingView.clearAll();
                        pdfEditManager.clearAllAnnotations();
                        finishExitEditMode();
                    })
                    .setNeutralButton("Cancel", null)
                    .show();
        } else {
            finishExitEditMode();
        }
    }

    private void finishExitEditMode() {
        isEditMode = false;

        // Hide edit toolbar
        binding.editToolbar.setVisibility(View.GONE);
        binding.drawingView.setVisibility(View.GONE);
        binding.fabSaveEdit.setVisibility(View.GONE);

        // Disable drawing view
        binding.drawingView.setEditEnabled(false);
        binding.drawingView.setTool(DrawingView.Tool.NONE);

        // Re-enable scrolling
        binding.scrollView.setNestedScrollingEnabled(true);

        // Reset toolbar title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(fileName != null ? fileName : "PDF Viewer");
        }
    }

    private void showAddTextDialog(float x, float y) {
        EditText input = new EditText(this);
        input.setHint("Enter text...");
        input.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(this)
                .setTitle("Add Text")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        // Normalize coordinates
                        float normalizedX = x / binding.drawingView.getWidth();
                        float normalizedY = y / binding.drawingView.getHeight();

                        pdfEditManager.addTextAnnotation(currentPage, normalizedX, normalizedY, text, currentColor, 14f);
                        Toast.makeText(this, "Text added", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveEditedPdf() {
        // Check permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            return;
        }

        performSave();
    }

    private void performSave() {
        binding.progressBar.setVisibility(View.VISIBLE);

        // Save drawing overlay for current page
        if (binding.drawingView.hasDrawings()) {
            Bitmap drawingBitmap = binding.drawingView.getDrawingBitmap();
            if (drawingBitmap != null) {
                pdfEditManager.setDrawingOverlay(currentPage, drawingBitmap);
            }
        }

        new Thread(() -> {
            try {
                String savedPath = pdfEditManager.saveEditedPdf();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    new AlertDialog.Builder(this)
                            .setTitle("PDF Saved")
                            .setMessage("Edited PDF saved to:\n" + savedPath)
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Open", (dialog, which) -> openSavedPdf(savedPath))
                            .show();

                    // Clear drawings after save
                    binding.drawingView.clearAll();
                    pdfEditManager.clearAllAnnotations();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void openSavedPdf(String path) {
        try {
            File file = new File(path);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No PDF viewer available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                performSave();
            } else {
                Toast.makeText(this, "Permission required to save PDF", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updatePageInfo() {
        String pageInfo = String.format("%d / %d", currentPage + 1, totalPages);

        if (notesManager.pageHasNotes(filePath, currentPage)) {
            pageInfo += " *";
        }

        binding.tvPageInfo.setText(pageInfo);

        // Update edit mode title if in edit mode
        if (isEditMode && getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit Mode - Page " + (currentPage + 1));
        }
    }

    private void updateZoomLabel() {
        binding.tvZoom.setText(String.format("%d%%", (int) (currentZoom * 100)));
    }

    private void zoomIn() {
        if (currentZoom < 3.0f) {
            currentZoom += 0.25f;
            updateZoomLabel();
            rerenderPages();
        }
    }

    private void zoomOut() {
        if (currentZoom > 0.5f) {
            currentZoom -= 0.25f;
            updateZoomLabel();
            rerenderPages();
        }
    }

    private void rerenderPages() {
        binding.progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            for (int i = 0; i < totalPages; i++) {
                final int pageIndex = i;
                runOnUiThread(() -> renderPage(pageIndex));
            }
            runOnUiThread(() -> binding.progressBar.setVisibility(View.GONE));
        }).start();
    }

    private void goToPreviousPage() {
        if (currentPage > 0) {
            // Save current page drawings before changing page
            if (isEditMode && binding.drawingView.hasDrawings()) {
                Bitmap drawingBitmap = binding.drawingView.getDrawingBitmap();
                if (drawingBitmap != null) {
                    pdfEditManager.setDrawingOverlay(currentPage, drawingBitmap);
                }
                binding.drawingView.clearAll();
            }

            currentPage--;
            scrollToPage(currentPage);
            updatePageInfo();
        }
    }

    private void goToNextPage() {
        if (currentPage < totalPages - 1) {
            // Save current page drawings before changing page
            if (isEditMode && binding.drawingView.hasDrawings()) {
                Bitmap drawingBitmap = binding.drawingView.getDrawingBitmap();
                if (drawingBitmap != null) {
                    pdfEditManager.setDrawingOverlay(currentPage, drawingBitmap);
                }
                binding.drawingView.clearAll();
            }

            currentPage++;
            scrollToPage(currentPage);
            updatePageInfo();
        }
    }

    private void scrollToPage(int pageIndex) {
        if (pageIndex >= 0 && pageIndex < pageViews.size()) {
            ImageView pageView = pageViews.get(pageIndex);
            binding.scrollView.smoothScrollTo(0, pageView.getTop());
        }
    }

    private void showSearch() {
        binding.searchBar.setVisibility(View.VISIBLE);
        binding.etSearch.requestFocus();
    }

    private void hideSearch() {
        binding.searchBar.setVisibility(View.GONE);
        binding.etSearch.setText("");
    }

    private void performSearch() {
        String query = binding.etSearch.getText().toString();
        if (query.isEmpty()) return;

        Toast.makeText(this, "Text search not available in native PDF viewer", Toast.LENGTH_SHORT).show();
    }

    private void showAddNoteDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter your note...");
        input.setMinLines(3);
        input.setMaxLines(10);
        input.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(this)
                .setTitle("Add Note - Page " + (currentPage + 1))
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String noteText = input.getText().toString().trim();
                    if (!noteText.isEmpty()) {
                        Note note = new Note(filePath, currentPage, noteText);
                        notesManager.addNote(note);
                        Toast.makeText(this, "Note added", Toast.LENGTH_SHORT).show();
                        updatePageInfo();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNotesDialog() {
        List<Note> notes = notesManager.getNotesForDocument(filePath);

        if (notes.isEmpty()) {
            Toast.makeText(this, "No notes for this document", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder notesList = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

        for (Note note : notes) {
            notesList.append("Page ").append(note.getPageNumber() + 1).append(":\n");
            notesList.append(note.getContent()).append("\n");
            notesList.append("(").append(dateFormat.format(new Date(note.getCreatedAt()))).append(")\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Notes (" + notes.size() + ")")
                .setMessage(notesList.toString().trim())
                .setPositiveButton("OK", null)
                .setNeutralButton("Clear All", (dialog, which) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Clear All Notes?")
                            .setMessage("This will delete all notes for this document.")
                            .setPositiveButton("Delete", (d, w) -> {
                                notesManager.deleteNotesForDocument(filePath);
                                Toast.makeText(this, "All notes deleted", Toast.LENGTH_SHORT).show();
                                updatePageInfo();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .show();
    }

    private void showPageNotesDialog() {
        List<Note> pageNotes = notesManager.getNotesForPage(filePath, currentPage);

        if (pageNotes.isEmpty()) {
            showAddNoteDialog();
            return;
        }

        StringBuilder notesList = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

        for (Note note : pageNotes) {
            notesList.append(note.getContent()).append("\n");
            notesList.append("(").append(dateFormat.format(new Date(note.getCreatedAt()))).append(")\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Notes - Page " + (currentPage + 1))
                .setMessage(notesList.toString().trim())
                .setPositiveButton("Add More", (dialog, which) -> showAddNoteDialog())
                .setNegativeButton("Close", null)
                .setNeutralButton("Delete All", (dialog, which) -> {
                    for (Note note : pageNotes) {
                        notesManager.deleteNote(note.getId());
                    }
                    Toast.makeText(this, "Page notes deleted", Toast.LENGTH_SHORT).show();
                    updatePageInfo();
                })
                .show();
    }

    private void shareDocument() {
        try {
            File file = new File(filePath);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (isEditMode) {
            exitEditMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_pdf_viewer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_search) {
            if (binding.searchBar.getVisibility() == View.VISIBLE) {
                hideSearch();
            } else {
                showSearch();
            }
            return true;
        } else if (id == R.id.action_edit) {
            toggleEditMode();
            return true;
        } else if (id == R.id.action_add_note) {
            showPageNotesDialog();
            return true;
        } else if (id == R.id.action_view_notes) {
            showNotesDialog();
            return true;
        } else if (id == R.id.action_share) {
            shareDocument();
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (pdfRenderer != null) {
            pdfRenderer.close();
        }
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (ImageView imageView : pageViews) {
            imageView.setImageBitmap(null);
        }
        pageViews.clear();
    }
}
