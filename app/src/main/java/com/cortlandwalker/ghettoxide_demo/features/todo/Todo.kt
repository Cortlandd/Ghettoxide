package com.cortlandwalker.ghettoxide_demo.features.todo

data class TodoState(
    var items: List<String?> = emptyList()
)

sealed interface TodoAction {
    data class Save(val todoName: String) : TodoAction
    data class DeleteTodo(val todoName: String) : TodoAction
    data class TappedTodo(val todoName: String) : TodoAction
}

sealed interface TodoEffect {
    data class ToastTodo(val todoName: String) : TodoEffect
}