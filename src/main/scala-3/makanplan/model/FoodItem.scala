package makanplan.model

import java.time.LocalDate

// Inventory item owned by a user
final case class FoodItem(
                           id: Long,            // DB id
                           userId: Long,        // owner
                           name: String,        // item name (e.g., "Chicken Breast")
                           category: String,    // category (e.g., "Meat", "Dairy")
                           quantity: Double,    // available amount
                           unit: String,        // unit for quantity (e.g., "g", "ml", "pcs")
                           expiry: LocalDate,   // expiry/best-before date
                           cost: Double         // total cost for the current quantity
                         )
