private lateinit var repository: NoteRepository
lateinit var allNotes: LiveData<List<Note>>

private val _searchQuery = MutableLiveData<String>("")
lateinit var searchResults: LiveData<List<Note>>

init {
    val dao = NoteDatabase.getDatabase(application).noteDao()
    repository = NoteRepository(dao)
    allNotes = repository.allNotes
    searchResults = _searchQuery.switchMap { query ->
        if (query.isBlank()) repository.allNotes
        else repository.searchNotes(query)
    }
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
