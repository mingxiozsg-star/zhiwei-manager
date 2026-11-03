package com.zhiwei.filemanager.core.model

enum class SortMode(val label: String) {
    NAME("名称"),
    SIZE("大小"),
    DATE("日期"),
    TYPE("类型")
}

enum class SortOrder(val label: String) {
    ASC("升序"),
    DESC("降序")
}
