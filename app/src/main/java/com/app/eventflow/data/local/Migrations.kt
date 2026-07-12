package com.app.eventflow.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1→v2 (Módulo 2): cache de favoritos + cola offline de favoritos (api/09). */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS favorite_event (" +
                "id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, venueName TEXT NOT NULL, " +
                "startsAt TEXT NOT NULL, endsAt TEXT NOT NULL, timezone TEXT NOT NULL, " +
                "status TEXT NOT NULL, coverUrl TEXT, categoryId INTEGER NOT NULL, " +
                "categoryName TEXT NOT NULL, categoryIcon TEXT, priceAmount TEXT, " +
                "priceCurrency TEXT, savedAt INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS pending_favorite_op (" +
                "eventId TEXT NOT NULL PRIMARY KEY, `add` INTEGER NOT NULL, createdAt INTEGER NOT NULL)",
        )
    }
}

/** v2→v3 (Módulo 3): cache de órdenes y boletos para lectura offline (api/09 §3). */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS order_cache (" +
                "id TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, totalAmount TEXT NOT NULL, " +
                "totalCurrency TEXT NOT NULL, expiresAt TEXT NOT NULL, createdAt TEXT NOT NULL, " +
                "itemsJson TEXT NOT NULL, paymentJson TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ticket_cache (" +
                "id TEXT NOT NULL PRIMARY KEY, eventId TEXT, eventTitle TEXT, eventVenue TEXT, " +
                "eventStartsAt TEXT, eventTimezone TEXT, ticketTypeName TEXT NOT NULL, zoneName TEXT, " +
                "status TEXT NOT NULL, acquiredVia TEXT NOT NULL, purchasedAt TEXT NOT NULL, " +
                "qrAvailableAt TEXT, canRecover INTEGER NOT NULL)",
        )
    }
}
