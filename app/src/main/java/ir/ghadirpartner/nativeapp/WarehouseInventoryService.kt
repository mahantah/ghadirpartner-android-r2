package ir.ghadirpartner.nativeapp

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** One serial row displayed by the warehouse screen. */
internal data class WarehouseSerial(
    val id: Int,
    val serial: String,
    val product: String,
    val cartonCode: String,
    val orderId: Int,
    val orderNumber: String,
)

/** A bounded result page with global ownership counts independent of the search. */
internal data class WarehouseInventoryPage(
    val rows: List<WarehouseSerial>,
    val total: Int,
    val free: Int,
    val assigned: Int,
    val page: Int,
    val limit: Int,
) {
    init {
        require(total >= 0 && free >= 0 && assigned >= 0)
        require(page > 0 && limit > 0 && rows.size <= limit)
        require(total.toLong() <= free.toLong() + assigned)
        require(rows.map { it.id }.distinct().size == rows.size)
        require(rows.all { it.id > 0 && it.serial.isNotBlank() })
    }

    /** Decide whether another server page can contain results. */
    fun hasMore(): Boolean = page.toLong() * limit < total
}

/** The network boundary for the warehouse screen; authentication stays in ApiClient. */
internal class WarehouseInventoryRepository(
    private val api: ApiClient,
) {
    /** Read and validate one page instead of downloading the complete inventory array. */
    suspend fun page(
        query: String,
        page: Int,
        limit: Int,
    ): WarehouseInventoryPage {
        require(page > 0 && limit > 0)
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val response = api.get("/api/inventory/search?q=$encoded&page=$page&limit=$limit") as JSONObject
        val values = response.getJSONArray("rows")
        val rows =
            (0 until values.length()).map { index ->
                val value = values.getJSONObject(index)
                WarehouseSerial(
                    id = value.getInt("id"),
                    serial = value.getString("serial"),
                    product = value.optString("product"),
                    cartonCode = value.optString("carton_code"),
                    orderId = value.optInt("order_id"),
                    orderNumber = value.optString("order_number"),
                )
            }
        val result =
            WarehouseInventoryPage(
                rows = rows,
                total = response.getInt("total"),
                free = response.getInt("free"),
                assigned = response.getInt("assigned"),
                page = response.getInt("page"),
                limit = response.getInt("limit"),
            )
        require(result.page == page && result.limit == limit)
        return result
    }
}

/** The only warehouse data facade used by the Compose screen. */
internal class WarehouseInventoryService(
    private val repository: WarehouseInventoryRepository,
    private val pageSize: Int,
) {
    init {
        require(pageSize > 0)
    }

    /** Fetch one requested page with the validated deployment policy. */
    suspend fun load(
        query: String,
        page: Int,
    ): WarehouseInventoryPage = repository.page(query, page, pageSize)
}
