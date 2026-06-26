package com.blockquest.data.local

import com.blockquest.domain.model.Piece
import com.blockquest.domain.model.PieceShape
import com.blockquest.domain.repository.PieceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPieceRepository @Inject constructor() : PieceRepository {
    
    private val allPieces = PieceShape.Library.values.map { shape ->
        Piece(
            id = shape.id,
            shape = shape,
            isBossPiece = shape.id.contains("boss") || shape.id.contains("scythe")
        )
    }

    override fun getPiece(id: String): Piece? = allPieces.find { it.id == id }

    override fun getAllPieces(): List<Piece> = allPieces

    override fun getPoolForWorld(worldIndex: Int): List<Piece> {
        return when (worldIndex) {
            0 -> allPieces.filter { !it.isBossPiece && it.shape.size <= 12 }
            1 -> allPieces.filter { !it.isBossPiece }
            else -> allPieces
        }
    }
}
