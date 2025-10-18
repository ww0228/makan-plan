package makanplan.model

// Link table: which FoodItem (by id) a Recipe uses, and how much
final case class RecipeIngredient(
                                   id: Long,        // DB id
                                   recipeId: Long,  // parent recipe (FK)
                                   foodItemId: Long,// referenced food item (FK)
                                   quantity: Double,// amount needed
                                   unit: String     // unit (e.g., "g", "ml", "pcs")
                                 )
