package com.blockquest.domain.repository

import com.blockquest.domain.model.Piece
import com.blockquest.domain.model.PieceShape
import kotlinx.coroutines.flow.Flow

interface PieceRepository {
    fun getPiece(id: String): Piece?
    fun getAllPieces(): List<Piece>
    fun getPoolForWorld(worldIndex: Int): List<Piece>
}
