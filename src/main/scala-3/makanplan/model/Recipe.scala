package makanplan.model

final case class Recipe(
                         id: Long,
                         title: String,
                         ingredientsText: String,
                         instructionsText: String,
                         imageName: Option[String]
                       )
