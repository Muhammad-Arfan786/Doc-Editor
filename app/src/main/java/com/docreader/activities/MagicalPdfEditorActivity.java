package com.docreader.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.docreader.R;
import com.docreader.databinding.ActivityMagicalPdfEditorBinding;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.listener.OnTapListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import ir.vasl.magicalpec.viewModel.MagicalPECViewModel;

/**
 * Activity for direct PDF editing using MagicalPdfEditor library.
 * Supports adding images and annotations directly on PDF pages.
 */
public class MagicalPdfEditorActivity extends AppCompatActivity implements
        OnLoadCompleteListener, OnPageChangeListener, OnPageErrorListener, OnTapListener {

    private static final int REQUEST_PICK_IMAGE = 100;

    private ActivityMagicalPdfEditorBinding binding;
    private String filePath;
    private String fileName;
    private Uri fileUri;
    private int totalPages = 0;
    private int currentPage = 0;
    private MagicalPECViewModel viewModel;
    private MotionEvent lastTapEvent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMagicalPdfEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        filePath = getIntent().getStringExtra("file_path");
        fileName = getIntent().getStringExtra("file_name");

        if (filePath == null) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(MagicalPECViewModel.class);

        setupToolbar();
        setupButtons();
        loadPdf();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(fileName != null ? fileName : "PDF Editor");
        }
    }

    private void setupButtons() {
        // Edit actions
        binding.btnAddText.setOnClickListener(v -> showAddTextDialog());
        binding.btnAddImage.setOnClickListener(v -> pickImage());
        binding.btnAddSignature.setOnClickListener(v -> showSignatureInfo());
        binding.btnUndo.setOnClickListener(v -> undoLastAction());
        binding.btnSave.setOnClickListener(v -> showSaveInfo());

        // Page navigation
        binding.btnPrevPage.setOnClickListener(v -> goToPreviousPage());
        binding.btnNextPage.setOnClickListener(v -> goToNextPage());
    }

    private void loadPdf() {
        binding.progressBar.setVisibility(View.VISIBLE);

        try {
            File pdfFile = new File(filePath);
            if (!pdfFile.exists()) {
                Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            fileUri = Uri.fromFile(pdfFile);

            // Configure the viewer
            binding.magicalPdfViewer.fromFile(pdfFile)
                    .defaultPage(0)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .enableAntialiasing(true)
                    .spacing(10)
                    .onLoad(this)
                    .onPageChange(this)
                    .onPageError(this)
                    .onTap(this)
                    .load();

        } catch (Exception e) {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showAddTextDialog() {
        Toast.makeText(this, "Tap on PDF where you want to add content, then use Add Image", Toast.LENGTH_LONG).show();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    private void addImageToPdf(Uri imageUri) {
        try {
            binding.progressBar.setVisibility(View.VISIBLE);

            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();
            }

            if (bitmap != null && viewModel != null) {
                // Convert bitmap to byte array
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] imageBytes = baos.toByteArray();

                // Get PDF coordinates from last tap or use default position
                PointF pdfPoint;
                if (lastTapEvent != null) {
                    pdfPoint = binding.magicalPdfViewer.convertScreenPintsToPdfCoordinates(lastTapEvent);
                } else {
                    pdfPoint = new PointF(100, 100);
                }

                // Generate unique reference hash
                String referenceHash = UUID.randomUUID().toString();

                // Add image as OCG (Optional Content Group) item
                viewModel.addOCG(pdfPoint, fileUri, currentPage, referenceHash, imageBytes);

                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Image added to page " + (currentPage + 1), Toast.LENGTH_SHORT).show();

                // Reload to show changes
                reloadPdf();
            }
        } catch (Exception e) {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Error adding image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void reloadPdf() {
        try {
            File pdfFile = new File(filePath);
            binding.magicalPdfViewer.fromFile(pdfFile)
                    .defaultPage(currentPage)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .enableAntialiasing(true)
                    .spacing(10)
                    .onLoad(this)
                    .onPageChange(this)
                    .onPageError(this)
                    .onTap(this)
                    .load();
        } catch (Exception e) {
            Toast.makeText(this, "Error reloading PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSignatureInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Add Signature")
                .setMessage("To add a signature:\n\n1. Tap on PDF where you want the signature\n2. Click 'Add Image'\n3. Select your signature image")
                .setPositiveButton("Add Image", (d, w) -> pickImage())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void undoLastAction() {
        Toast.makeText(this, "Undo not available - changes are saved directly", Toast.LENGTH_SHORT).show();
    }

    private void showSaveInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Save PDF")
                .setMessage("Changes are automatically saved to the original PDF file.\n\nFile: " + filePath)
                .setPositiveButton("OK", null)
                .show();
    }

    private void goToPreviousPage() {
        if (currentPage > 0) {
            currentPage--;
            binding.magicalPdfViewer.jumpTo(currentPage, true);
            updatePageInfo();
        }
    }

    private void goToNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            binding.magicalPdfViewer.jumpTo(currentPage, true);
            updatePageInfo();
        }
    }

    private void updatePageInfo() {
        binding.tvPageInfo.setText(String.format("%d / %d", currentPage + 1, totalPages));
    }

    // ==================== LISTENERS ====================

    @Override
    public void loadComplete(int nbPages) {
        binding.progressBar.setVisibility(View.GONE);
        totalPages = nbPages;
        updatePageInfo();
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        currentPage = page;
        totalPages = pageCount;
        updatePageInfo();
    }

    @Override
    public void onPageError(int page, Throwable t) {
        Toast.makeText(this, "Error loading page " + (page + 1), Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onTap(MotionEvent e) {
        // Store tap event for adding content
        lastTapEvent = MotionEvent.obtain(e);
        return false;
    }

    // ==================== ACTIVITY CALLBACKS ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_PICK_IMAGE) {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    addImageToPdf(imageUri);
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Editor")
                .setMessage("Changes have been saved to the PDF.")
                .setPositiveButton("OK", (d, w) -> super.onBackPressed())
                .show();
    }
}
