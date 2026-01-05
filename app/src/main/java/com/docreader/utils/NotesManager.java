package com.docreader.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.docreader.models.Note;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages document notes/annotations storage.
 */
public class NotesManager {

    private static final String PREFS_NAME = "document_notes";
    private static final String KEY_NOTES = "notes";

    private final SharedPreferences prefs;
    private final Gson gson;

    public NotesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    /**
     * Get all notes for a specific document.
     */
    public List<Note> getNotesForDocument(String documentPath) {
        List<Note> allNotes = getAllNotes();
        return allNotes.stream()
                .filter(note -> note.getDocumentPath().equals(documentPath))
                .collect(Collectors.toList());
    }

    /**
     * Get notes for a specific page.
     */
    public List<Note> getNotesForPage(String documentPath, int pageNumber) {
        List<Note> allNotes = getAllNotes();
        return allNotes.stream()
                .filter(note -> note.getDocumentPath().equals(documentPath)
                        && note.getPageNumber() == pageNumber)
                .collect(Collectors.toList());
    }

    /**
     * Add a new note.
     */
    public void addNote(Note note) {
        List<Note> notes = getAllNotes();
        notes.add(note);
        saveNotes(notes);
    }

    /**
     * Update an existing note.
     */
    public void updateNote(Note updatedNote) {
        List<Note> notes = getAllNotes();
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).getId().equals(updatedNote.getId())) {
                notes.set(i, updatedNote);
                break;
            }
        }
        saveNotes(notes);
    }

    /**
     * Delete a note.
     */
    public void deleteNote(String noteId) {
        List<Note> notes = getAllNotes();
        notes.removeIf(note -> note.getId().equals(noteId));
        saveNotes(notes);
    }

    /**
     * Delete all notes for a document.
     */
    public void deleteNotesForDocument(String documentPath) {
        List<Note> notes = getAllNotes();
        notes.removeIf(note -> note.getDocumentPath().equals(documentPath));
        saveNotes(notes);
    }

    /**
     * Get count of notes for a document.
     */
    public int getNoteCount(String documentPath) {
        return getNotesForDocument(documentPath).size();
    }

    /**
     * Check if a page has notes.
     */
    public boolean pageHasNotes(String documentPath, int pageNumber) {
        return !getNotesForPage(documentPath, pageNumber).isEmpty();
    }

    private List<Note> getAllNotes() {
        String json = prefs.getString(KEY_NOTES, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<ArrayList<Note>>() {}.getType();
        List<Note> notes = gson.fromJson(json, type);
        return notes != null ? notes : new ArrayList<>();
    }

    private void saveNotes(List<Note> notes) {
        String json = gson.toJson(notes);
        prefs.edit().putString(KEY_NOTES, json).apply();
    }
}
