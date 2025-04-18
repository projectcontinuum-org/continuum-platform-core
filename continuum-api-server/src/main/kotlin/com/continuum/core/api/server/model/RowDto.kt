package com.continuum.core.api.server.model

data class RowDto(
    val rowNumber: Long,
    val cells: List<CellDto>
)