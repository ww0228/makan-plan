package makanplan.view

import javafx.fxml.FXML
import javafx.scene.control.{Label, TableColumn, TableView, TextArea, TextField}
import javafx.scene.image.{Image, ImageView}
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections

import scalikejdbc.{AutoSession, DBSession}

import makanplan.dao.RecipeDao
import makanplan.model.Recipe

class RecipeBookController {

  // FXML-wired controls
  @FXML private var table: TableView[Recipe] = _
  @FXML private var titleCol: TableColumn[Recipe, String] = _
  @FXML private var searchField: TextField = _
  @FXML private var ingredientsArea: TextArea = _
  @FXML private var instructionsArea: TextArea = _
  @FXML private var recipeImage: ImageView = _
  @FXML private var messageLabel: Label = _

  // Backing list for the table
  private val items = FXCollections.observableArrayList[Recipe]()

  // Setup on load
  @FXML def initialize(): Unit = {
    given DBSession = AutoSession
    RecipeDao.ensureTable()

    table.setItems(items)
    titleCol.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.title))
    titleCol.setSortable(false)
    table.getSortOrder.clear()
    table.getSelectionModel.selectedItemProperty.addListener((_, _, row) => showRow(row))

    // Hide the optional status label if present
    if (messageLabel != null) {
      messageLabel.setText("")
      messageLabel.setVisible(false)
      messageLabel.setManaged(false)
    }

    reload("")
  }

  // Search handler
  @FXML def handleSearch(): Unit = {
    val q = Option(searchField).map(_.getText).getOrElse("").trim

    if (q.isEmpty) {
      reload("")
      return
    }

    given DBSession = AutoSession
    val raw: Seq[Recipe] = RecipeDao.search(q, limit = 500)

    // De-duplicate by (title,image) pair
    val byPair = dedupeByTitleAndImage(raw)

    // Extra de-duplication by normalized title for searches
    val dataForView = dedupeByTitleSearch(byPair)

    items.setAll(dataForView*)
    if (dataForView.nonEmpty) table.getSelectionModel.select(0) else showRow(null)
  }

  // Load data for table (with optional query)
  private def reload(q: String): Unit = {
    given DBSession = AutoSession

    val raw: Seq[Recipe] =
      if (q.nonEmpty) RecipeDao.search(q, limit = 500)
      else            RecipeDao.latest(limit = 500)

    // De-duplicate before showing
    val data = dedupeByTitleAndImage(raw)

    items.setAll(data*)
    if (data.nonEmpty) table.getSelectionModel.select(0) else showRow(null)
  }

  // Show selected recipe details
  private def showRow(row: Recipe | Null): Unit = {
    if (row == null) {
      ingredientsArea.setText("")
      instructionsArea.setText("")
      if (recipeImage != null) recipeImage.setImage(null)
      return
    }

    // Pretty formatting
    ingredientsArea.setText(prettyIngredients(Option(row.ingredientsText).getOrElse("")))
    instructionsArea.setText(prettyInstructions(Option(row.instructionsText).getOrElse("")))

    // Load image by imageName or slugified title
    val imgOpt = row.imageName.filter(_.trim.nonEmpty)
    loadImage(imgOpt, row.title)
  }

  // formatting helpers
  // Turn "['a', 'b', 'c']" or comma text into bulleted lines
  private def prettyIngredients(raw: String): String = {
    val normalized = raw.replace('’', '\'').trim
    val inside =
      if (normalized.startsWith("[") && normalized.endsWith("]"))
        normalized.substring(1, normalized.length - 1)
      else normalized

    // Prefer chunks wrapped in single quotes
    val quoted = "'([^']+)'".r.findAllMatchIn(inside).map(_.group(1).trim).filter(_.nonEmpty).toList
    val items =
      if (quoted.nonEmpty) quoted
      else inside.split("""\s*,\s*""").toList.map(_.trim).filter(_.nonEmpty)

    items.map(i => s"• $i").mkString("\n")
  }

  // Number each instruction line and add a blank line between steps
  private def prettyInstructions(raw: String): String = {
    val lines = raw
      .replace("\r\n", "\n").replace("\r", "\n")
      .split("\n").toList
      .map(_.trim).filter(_.nonEmpty)

    lines.zipWithIndex
      .map { case (l, i) => s"${i + 1}. $l" }
      .mkString("\n\n")
  }

  // de-dup helpers
  // Keep only the first recipe for each (title, imageName) pair (case-insensitive)
  private def dedupeByTitleAndImage(list: Seq[Recipe]): Seq[Recipe] = {
    val seen = scala.collection.mutable.HashSet[(String, String)]()
    list.filter { r =>
      val key = (
        Option(r.title).getOrElse("").trim.toLowerCase,
        r.imageName.map(_.trim.toLowerCase).getOrElse("")
      )
      if (seen(key)) false else { seen += key; true }
    }
  }

  // Normalize a title: de-accent, lowercase, trim, collapse whitespace
  private def normTitle(s: String): String =
    java.text.Normalizer.normalize(Option(s).getOrElse(""), java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
      .toLowerCase
      .replaceAll("\\s+", " ")
      .trim

  // Extra search-only de-dup by normalized title (order-preserving)
  private def dedupeByTitleSearch(list: Seq[Recipe]): Seq[Recipe] = {
    val seen = scala.collection.mutable.HashSet[String]()
    list.filter { r =>
      val key = normTitle(r.title)
      if (seen(key)) false else {
        seen += key; true
      }
    }
  }

  // Make a filesystem-friendly slug from a title
  private def slugify(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
      .toLowerCase
      .replaceAll("[^a-z0-9]+", "-")
      .replaceAll("(^-|-$)", "")

  // Try classpath images under /images/meals and legacy /images/meal
  private def loadImage(imageNameOpt: Option[String], title: String): Unit = {
    val base = imageNameOpt.map(_.trim).filter(_.nonEmpty).getOrElse(slugify(title))
    val file =
      if (base.toLowerCase.endsWith(".jpg") || base.toLowerCase.endsWith(".jpeg")) base
      else s"$base.jpg"

    val paths = Seq(
      s"/images/meals/$file",
      s"/images/meal/$file"
    )

    val streamOpt = paths.view.map(getClass.getResourceAsStream).find(_ != null)
    if (streamOpt.isDefined && recipeImage != null) {
      val is = streamOpt.get
      try recipeImage.setImage(new Image(is))
      finally is.close()
    } else if (recipeImage != null) {
      recipeImage.setImage(null)
    }
  }
}
