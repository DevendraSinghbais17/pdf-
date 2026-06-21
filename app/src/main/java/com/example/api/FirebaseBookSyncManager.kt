package com.example.api

import android.content.Context
import android.util.Log
import com.example.model.ReadBook
import com.example.model.SavedMedia
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FirebaseBookSyncManager(context: Context) {

    private val sharedPrefs = context.getSharedPreferences("media_seeker_prefs", Context.MODE_PRIVATE)
    
    // Generate or retrieve a unique, persistent client ID to keep user data private and separate
    val userId: String = sharedPrefs.getString("user_rtdb_id", null) ?: run {
        val newId = "user_" + UUID.randomUUID().toString().substring(0, 10)
        sharedPrefs.edit().putString("user_rtdb_id", newId).apply()
        newId
    }

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }

    private val booksRef by lazy {
        database.getReference("users").child(userId).child("books_read")
    }

    private val bookmarksRef by lazy {
        database.getReference("users").child(userId).child("bookmarks")
    }

    private val _readBooks = MutableStateFlow<List<ReadBook>>(emptyList())
    val readBooks: StateFlow<List<ReadBook>> = _readBooks.asStateFlow()

    private val _firebaseLoaded = MutableStateFlow(false)
    val firebaseLoaded: StateFlow<Boolean> = _firebaseLoaded.asStateFlow()

    private var booksListener: ValueEventListener? = null

    init {
        startObservingReadBooks()
    }

    fun startObservingReadBooks() {
        if (booksListener != null) return

        booksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ReadBook>()
                for (child in snapshot.children) {
                    try {
                        val book = child.getValue(ReadBook::class.java)
                        if (book != null) {
                            list.add(book)
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseSync", "Error parsing read book", e)
                    }
                }
                _readBooks.value = list.sortedByDescending { it.timestamp }
                _firebaseLoaded.value = true
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseSync", "Firebase DB Cancelled: ${error.message}")
            }
        }
        booksRef.addValueEventListener(booksListener!!)
    }

    fun saveReadBook(title: String, author: String, rating: Int, notes: String, dateRead: String) {
        val id = UUID.randomUUID().toString()
        val book = ReadBook(
            id = id,
            title = title,
            author = author,
            rating = rating,
            notes = notes,
            dateRead = dateRead,
            timestamp = System.currentTimeMillis()
        )
        booksRef.child(id).setValue(book)
            .addOnSuccessListener {
                Log.d("FirebaseSync", "Successfully saved read book: $title")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseSync", "Error saving read book", e)
            }
    }

    fun deleteReadBook(bookId: String) {
        booksRef.child(bookId).removeValue()
            .addOnSuccessListener {
                Log.d("FirebaseSync", "Successfully deleted read book: $bookId")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseSync", "Error deleting read book", e)
            }
    }

    // Backup bookmarks to Firebase for easy cloud-sync
    fun syncBookmarksToFirebase(bookmarks: List<SavedMedia>) {
        bookmarksRef.setValue(bookmarks)
            .addOnSuccessListener {
                Log.d("FirebaseSync", "Successfully synced all bookmarks to cloud.")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseSync", "Error syncing bookmarks", e)
            }
    }

    fun cleanup() {
        booksListener?.let {
            booksRef.removeEventListener(it)
        }
    }
}
