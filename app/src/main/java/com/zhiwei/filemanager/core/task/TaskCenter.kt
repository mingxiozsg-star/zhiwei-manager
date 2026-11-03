package com.zhiwei.filemanager.core.task

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class TaskInfo(
    val id: Long,
    val title: String,
    val progress: Float = 0f,
    val detail: String = "",
    val finished: Boolean = false,
    val success: Boolean = true,
    val error: String? = null
)

/** 全局后台任务看板：所有耗时操作（复制/解压/转码/合并）都往这里报进度。 */
object TaskCenter {

    private var seq = 0L
    private val _tasks = MutableStateFlow<List<TaskInfo>>(emptyList())
    val tasks: StateFlow<List<TaskInfo>> = _tasks

    @Synchronized
    fun start(title: String, detail: String = ""): Long {
        val id = ++seq
        _tasks.update { it + TaskInfo(id = id, title = title, detail = detail) }
        return id
    }

    fun update(id: Long, progress: Float, detail: String = "") {
        _tasks.update { list ->
            list.map {
                if (it.id == id) it.copy(progress = progress.coerceIn(0f, 1f), detail = detail) else it
            }
        }
    }

    fun finish(id: Long, success: Boolean = true, error: String? = null) {
        _tasks.update { list ->
            list.map {
                if (it.id == id) it.copy(progress = 1f, finished = true, success = success, error = error) else it
            }
        }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filterNot { it.finished } }
    }

    fun clearAll() {
        _tasks.update { emptyList() }
    }
}
