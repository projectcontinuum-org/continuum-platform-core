package com.continuum.core.api.server.model
data class Page<T>(
    val data: List<T>,             // List of elements in the page
    val currentPage: Int,          // Current page number
    val currentPageSize: Int,      // Number of elements in a page
    val totalPages: Int,           // Total number of pages
    val totalElements: Long,       // Total number of elements
    val hasNext: Boolean,          // Flag to indicate if there is a next page
    val hasPrevious: Boolean       // Flag to indicate if there is a previous page
)