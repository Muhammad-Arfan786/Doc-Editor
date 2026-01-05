package com.docreader.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.docreader.R;
import com.docreader.databinding.ActivityDocViewerBinding;
import com.docreader.utils.FileUtils;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Activity for viewing and editing DOC and DOCX documents.
 */
public class DocViewerActivity extends AppCompatActivity {

    private ActivityDocViewerBinding binding;
    private String filePath;
    private String fileName;
    private String documentText = "";
    private String originalText = "";
    private float fontSize = 16f;
    private boolean isEditMode = false;
    private boolean hasChanges = false;
    private List<Integer> searchPositions = new ArrayList<>();
    private int currentSearchIndex = -1;
    private String currentSearchQuery = "";
    private Menu optionsMenu;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDocViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        filePath = getIntent().getStringExtra("file_path");
        fileName = getIntent().getStringExtra("file_name");

        if (filePath == null) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupControls();
        setupSearch();
        setupBackHandler();
        loadDocument();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(fileName != null ? fileName : "Document Viewer");
        }

        binding.toolbar.setNavigationOnClickListener(v -> handleBackPress());
    }

    private void setupControls() {
        // Font size controls
        binding.btnFontSmaller.setOnClickListener(v -> decreaseFontSize());
        binding.btnFontLarger.setOnClickListener(v -> increaseFontSize());

        // Edit/Save button
        binding.btnEditSave.setOnClickListener(v -> {
            if (isEditMode) {
                saveDocument();
            } else {
                enterEditMode();
            }
        });
    }

    private void setupSearch() {
        binding.btnCloseSearch.setOnClickListener(v -> hideSearch());

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.etSearch.getText().toString());
                return true;
            }
            return false;
        });

        binding.btnSearchPrev.setOnClickListener(v -> navigateSearch(-1));
        binding.btnSearchNext.setOnClickListener(v -> navigateSearch(1));
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });
    }

    private void handleBackPress() {
        if (isEditMode && hasChanges) {
            showUnsavedChangesDialog();
        } else if (isEditMode) {
            exitEditMode();
        } else {
            finish();
        }
    }

    private void loadDocument() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvContent.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                String text;
                if (FileUtils.isDocx(fileName)) {
                    text = loadDocx(file);
                } else {
                    text = loadDoc(file);
                }

                documentText = text;
                originalText = text;

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvContent.setVisibility(View.VISIBLE);
                    binding.tvContent.setText(documentText);
                    binding.tvContent.setTextSize(fontSize);
                    binding.etContent.setTextSize(fontSize);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.error_loading + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private String loadDocx(File file) throws Exception {
        StringBuilder text = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n\n");
            }
        }
        return text.toString().trim();
    }

    private String loadDoc(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument document = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(document)) {

            return extractor.getText();
        }
    }

    private void enterEditMode() {
        isEditMode = true;
        binding.tvContent.setVisibility(View.GONE);
        binding.etContent.setVisibility(View.VISIBLE);
        binding.etContent.setText(documentText);
        binding.etContent.setTextSize(fontSize);
        binding.etContent.requestFocus();

        binding.btnEditSave.setText("Save");
        binding.btnEditSave.setIconResource(R.drawable.ic_save);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Editing: " + fileName);
        }

        updateMenuVisibility();

        // Track changes
        binding.etContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                hasChanges = !s.toString().equals(originalText);
            }
        });
    }

    private void exitEditMode() {
        isEditMode = false;
        hasChanges = false;
        binding.etContent.setVisibility(View.GONE);
        binding.tvContent.setVisibility(View.VISIBLE);
        binding.tvContent.setText(documentText);

        binding.btnEditSave.setText("Edit");
        binding.btnEditSave.setIconResource(R.drawable.ic_edit);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(fileName);
        }

        updateMenuVisibility();
    }

    private void saveDocument() {
        String newText = binding.etContent.getText().toString();

        binding.progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                File file = new File(filePath);

                if (FileUtils.isDocx(fileName)) {
                    saveDocx(file, newText);
                } else {
                    // For .doc files, save as new .docx
                    saveAsNewDocx(newText);
                    return;
                }

                documentText = newText;
                originalText = newText;

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    hasChanges = false;
                    Toast.makeText(this, "Document saved successfully", Toast.LENGTH_SHORT).show();
                    exitEditMode();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveDocx(File file, String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            String[] paragraphs = text.split("\n\n");

            for (String para : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();

                // Handle line breaks within paragraph
                String[] lines = para.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    run.setText(lines[i]);
                    if (i < lines.length - 1) {
                        run.addBreak();
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                document.write(fos);
            }
        }
    }

    private void saveAsNewDocx(String text) {
        try {
            // Create new file name
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            String newFileName = baseName + "_edited_" + timestamp + ".docx";

            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!documentsDir.exists()) {
                documentsDir.mkdirs();
            }

            File newFile = new File(getCacheDir(), newFileName);

            try (XWPFDocument document = new XWPFDocument()) {
                String[] paragraphs = text.split("\n\n");

                for (String para : paragraphs) {
                    XWPFParagraph paragraph = document.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(para);
                }

                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    document.write(fos);
                }
            }

            // Update current file path
            filePath = newFile.getAbsolutePath();
            fileName = newFileName;
            documentText = text;
            originalText = text;

            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                hasChanges = false;
                Toast.makeText(this, "Saved as: " + newFileName, Toast.LENGTH_LONG).show();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(fileName);
                }
                exitEditMode();
            });

        } catch (Exception e) {
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void saveAs() {
        String newText = isEditMode ? binding.etContent.getText().toString() : documentText;

        // Create dialog for file name
        android.widget.EditText input = new android.widget.EditText(this);
        String baseName = fileName.contains(".") ?
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        input.setText(baseName + "_copy");

        new AlertDialog.Builder(this)
                .setTitle("Save As")
                .setMessage("Enter file name (will be saved as .docx)")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        executor.execute(() -> {
                            try {
                                File newFile = new File(getCacheDir(), newName + ".docx");

                                try (XWPFDocument document = new XWPFDocument()) {
                                    String[] paragraphs = newText.split("\n\n");
                                    for (String para : paragraphs) {
                                        XWPFParagraph paragraph = document.createParagraph();
                                        XWPFRun run = paragraph.createRun();
                                        run.setText(para);
                                    }

                                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                                        document.write(fos);
                                    }
                                }

                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Saved: " + newFile.getName(), Toast.LENGTH_SHORT).show();
                                });

                            } catch (Exception e) {
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUnsavedChangesDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. What would you like to do?")
                .setPositiveButton("Save", (dialog, which) -> saveDocument())
                .setNegativeButton("Discard", (dialog, which) -> {
                    hasChanges = false;
                    finish();
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void updateMenuVisibility() {
        if (optionsMenu != null) {
            optionsMenu.findItem(R.id.action_undo).setVisible(isEditMode);
            optionsMenu.findItem(R.id.action_redo).setVisible(isEditMode);
            optionsMenu.findItem(R.id.action_save).setVisible(isEditMode);
        }
    }

    private void increaseFontSize() {
        if (fontSize < 32f) {
            fontSize += 2f;
            binding.tvContent.setTextSize(fontSize);
            binding.etContent.setTextSize(fontSize);
        }
    }

    private void decreaseFontSize() {
        if (fontSize > 10f) {
            fontSize -= 2f;
            binding.tvContent.setTextSize(fontSize);
            binding.etContent.setTextSize(fontSize);
        }
    }

    private void showSearch() {
        binding.searchBar.setVisibility(View.VISIBLE);
        binding.etSearch.requestFocus();
    }

    private void hideSearch() {
        binding.searchBar.setVisibility(View.GONE);
        binding.etSearch.setText("");
        if (!isEditMode) {
            binding.tvContent.setText(documentText);
        }
        searchPositions.clear();
        currentSearchIndex = -1;
    }

    private void performSearch(String query) {
        if (query == null || query.isEmpty() || documentText.isEmpty()) {
            binding.tvSearchCount.setText("");
            return;
        }

        currentSearchQuery = query;
        searchPositions.clear();
        currentSearchIndex = -1;

        String textToSearch = isEditMode ? binding.etContent.getText().toString() : documentText;

        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(textToSearch);

        while (matcher.find()) {
            searchPositions.add(matcher.start());
        }

        if (searchPositions.isEmpty()) {
            binding.tvSearchCount.setText(R.string.no_results);
            if (!isEditMode) {
                binding.tvContent.setText(documentText);
            }
        } else {
            currentSearchIndex = 0;
            if (!isEditMode) {
                highlightSearchResults();
            }
            updateSearchCount();
        }
    }

    private void highlightSearchResults() {
        if (searchPositions.isEmpty() || currentSearchQuery.isEmpty()) return;

        SpannableString spannableString = new SpannableString(documentText);

        for (int i = 0; i < searchPositions.size(); i++) {
            int start = searchPositions.get(i);
            int end = start + currentSearchQuery.length();

            int color = (i == currentSearchIndex) ?
                    Color.YELLOW : Color.parseColor("#FFEB3B");

            spannableString.setSpan(
                    new BackgroundColorSpan(color),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        binding.tvContent.setText(spannableString);

        if (currentSearchIndex >= 0 && currentSearchIndex < searchPositions.size()) {
            scrollToPosition(searchPositions.get(currentSearchIndex));
        }
    }

    private void navigateSearch(int direction) {
        if (searchPositions.isEmpty()) return;

        currentSearchIndex += direction;
        if (currentSearchIndex >= searchPositions.size()) {
            currentSearchIndex = 0;
        } else if (currentSearchIndex < 0) {
            currentSearchIndex = searchPositions.size() - 1;
        }

        if (!isEditMode) {
            highlightSearchResults();
        }
        updateSearchCount();
    }

    private void updateSearchCount() {
        binding.tvSearchCount.setText(String.format("%d/%d",
                currentSearchIndex + 1, searchPositions.size()));
    }

    private void scrollToPosition(int position) {
        if (binding.tvContent.getLayout() != null) {
            int line = binding.tvContent.getLayout().getLineForOffset(position);
            int y = binding.tvContent.getLayout().getLineTop(line);
            binding.scrollView.smoothScrollTo(0, y);
        }
    }

    private void shareDocument() {
        try {
            File file = new File(filePath);
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            if (FileUtils.isDocx(fileName)) {
                shareIntent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            } else {
                shareIntent.setType("application/msword");
            }
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Document"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_doc_editor, menu);
        optionsMenu = menu;
        updateMenuVisibility();
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
        } else if (id == R.id.action_save) {
            saveDocument();
            return true;
        } else if (id == R.id.action_save_as) {
            saveAs();
            return true;
        } else if (id == R.id.action_share) {
            shareDocument();
            return true;
        } else if (id == android.R.id.home) {
            handleBackPress();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
