package com.ft.ftchinese.database

import android.provider.BaseColumns

object HistoryTable {

    object Cols : BaseColumns {
        const val ID = "id"
        const val TYPE = "type"
        const val TITLE = "title"
        const val STANDFIRST = "standfirst"
    }

    const val NAME = "reading_history"

    val SQL_CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS ${NAME} (
                ${BaseColumns._ID} INTEGER PRIMARY KEY,
                ${Cols.ID} TEXT,
                ${Cols.TYPE} TEXT,
                ${Cols.TITLE} TEXT,
                ${Cols.STANDFIRST} TEXT,
                read_at TEXT DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (id, type) ON CONFLICT REPLACE)
        """.trimIndent()

    const val SQL_DELETE_TABLE = "DROP TABLE IF EXISTS $NAME"
}