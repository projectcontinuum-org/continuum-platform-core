package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema

/** One page of a node output table. Only used as `Page<List<CellDto>>`, documented as [NodeOutputPage]. */
data class Page<T>(
  val data: List<T>,             // List of elements in the page
  val currentPage: Int,          // Current page number
  val currentPageSize: Int,      // Number of elements in a page
  val totalPages: Int,           // Total number of pages
  val totalElements: Long,       // Total number of elements
  val hasNext: Boolean,          // Flag to indicate if there is a next page
  val hasPrevious: Boolean       // Flag to indicate if there is a previous page
)

/** Documentation type of `Page<List<CellDto>>`, so the generic page gets a precise, uniquely named schema. */
@Schema(name = "NodeOutputPage", description = "One page of rows of a node output table.")
abstract class NodeOutputPage {
  @get:Schema(description = "The rows of this page; each row is the list of its cells in column order.")
  abstract val data: List<List<CellDto>>
  @get:Schema(description = "Zero-based number of this page.", example = "0")
  abstract val currentPage: Int
  @get:Schema(description = "Requested page size (rows per page).", example = "10")
  abstract val currentPageSize: Int
  @get:Schema(description = "Number of pages.", example = "5")
  abstract val totalPages: Int
  @get:Schema(description = "Number of rows in the table.", example = "42")
  abstract val totalElements: Long
  @get:Schema(description = "Whether a later page exists.", example = "true")
  abstract val hasNext: Boolean
  @get:Schema(description = "Whether an earlier page exists.", example = "false")
  abstract val hasPrevious: Boolean
}
