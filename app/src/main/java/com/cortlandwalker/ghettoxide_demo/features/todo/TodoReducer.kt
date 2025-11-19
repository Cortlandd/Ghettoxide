package com.cortlandwalker.ghettoxide_demo.features.todo

import com.cortlandwalker.ghettoxide.Reducer

class TodoReducer : Reducer<TodoState, TodoAction, TodoEffect>() {
    override fun onLoadAction(): TodoAction? {
        return null
    }

    override suspend fun process(action: TodoAction) {
        when (action) {
            is TodoAction.DeleteTodo -> {
                state {
                    val newItems = it.items.filter { it != action.todoName }
                    it.copy(items = newItems)
                }
            }
            is TodoAction.Save -> {
                state {
                    it.copy(items = it.items + action.todoName)
                }
            }
            is TodoAction.TappedTodo -> {
                emit(TodoEffect.ToastTodo(action.todoName))
            }
        }
    }
}