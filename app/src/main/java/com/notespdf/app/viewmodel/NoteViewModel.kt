package com.notespdf.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.notespdf.app.data.Note
import com.notespdf.app.data.NoteDatabase
import com.notespdf.app.data.NoteRepository
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository
    val allNotes: LiveData<List<Note>>

    private val _searchQuery = MutableLiveData<String>("")
    val searchResults: LiveData<List<Note>> = _searchQuery.switchMap { query ->
        if (query.isBlank()) repository.allNotes
        else repository.searchNotes(query)
    }

    init {
        val dao = NoteDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(dao)
        allNotes = repository.allNotes
    }

    fun insert(note: Note) = viewModelScope.launch {
        repository.insert(note)
    }

    fun update(note: Note) = viewModelScope.launch {
        repository.update(note)
    }

    fun delete(note: Note) = viewModelScope.launch {
        repository.delete(note)
    }

    fun deleteById(id: Int) = viewModelScope.launch {
        repository.deleteById(id)
    }

    suspend fun getNoteById(id: Int): Note? {
        return repository.getNoteById(id)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
