package makanplan.view

import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.geometry.Insets
import javafx.collections.FXCollections
import javafx.beans.property.{ReadOnlyObjectWrapper, ReadOnlyStringWrapper}
import scalikejdbc.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import makanplan.model.FoodItem
import makanplan.dao.FoodItemDao

class InventoryController:

  // FXML table + columns
  @FXML private var table: TableView[FoodItem] = _
  @FXML private var nameCol:   TableColumn[FoodItem, String] = _
  @FXML private var catCol:    TableColumn[FoodItem, String] = _
  @FXML private var qtyCol:    TableColumn[FoodItem, java.lang.Double] = _
  @FXML private var unitCol:   TableColumn[FoodItem, String] = _
  @FXML private var expiryCol: TableColumn[FoodItem, String] = _
  @FXML private var costCol:   TableColumn[FoodItem, java.lang.Double] = _
  @FXML private var messageLabel: Label = _

  // in-memory rows for the table
  private val items = FXCollections.observableArrayList[FoodItem]()

  // setup columns, row styling, and load data
  @FXML def initialize(): Unit =
    nameCol.setCellValueFactory(cd => new ReadOnlyStringWrapper(cd.getValue.name))
    catCol.setCellValueFactory(cd => new ReadOnlyStringWrapper(cd.getValue.category))
    qtyCol.setCellValueFactory(cd => new ReadOnlyObjectWrapper[java.lang.Double](cd.getValue.quantity))
    unitCol.setCellValueFactory(cd => new ReadOnlyStringWrapper(cd.getValue.unit))
    expiryCol.setCellValueFactory(cd => new ReadOnlyStringWrapper(cd.getValue.expiry.toString))
    costCol.setCellValueFactory(cd => new ReadOnlyObjectWrapper[java.lang.Double](cd.getValue.cost))

    table.setItems(items)

    // row CSS: expired = red, expiring soon (≤7 days) = yellow
    table.setRowFactory(_ => new TableRow[FoodItem]():
      override def updateItem(item: FoodItem, empty: Boolean): Unit =
        super.updateItem(item, empty)
        getStyleClass.removeAll("expiring-soon", "expired")
        if !empty && item != null then
          val days = ChronoUnit.DAYS.between(LocalDate.now, item.expiry)
          if days < 0 then getStyleClass.add("expired")
          else if days <= 7 then getStyleClass.add("expiring-soon")
    )

    refresh()

  // load items for current user and update summary
  private def refresh(): Unit =
    val uid = makanplan.MainApp.currentUserId.getOrElse(0L)
    if uid <= 0 then { setMsg("Please log in to view inventory."); return }
    given DBSession = AutoSession
    val rows = FoodItemDao.findAllByUser(uid)
    items.setAll(rows*)
    val soon    = rows.count(r => { val d = ChronoUnit.DAYS.between(LocalDate.now, r.expiry); d >= 0 && d <= 7 })
    val expired = rows.count(_.expiry.isBefore(LocalDate.now))
    setMsg(s"${rows.size} items • $soon expiring ≤ 7 days • $expired expired")

  // Add / Edit / Delete
  // open form to add a new item
  @FXML def handleAdd(): Unit =
    val uid = makanplan.MainApp.currentUserId.getOrElse(0L); if uid <= 0 then return
    showItemForm(None).foreach { draft =>
      given DBSession = AutoSession
      FoodItemDao.create(draft.copy(id = 0L, userId = uid))
      refresh(); setMsg("Item added.")
    }

  // open form to edit the selected item
  @FXML def handleEdit(): Unit =
    val sel = table.getSelectionModel.getSelectedItem
    if sel == null then { setMsg("Select an item to edit."); return }
    showItemForm(Some(sel)).foreach { updated =>
      given DBSession = AutoSession
      FoodItemDao.update(updated.copy(id = sel.id, userId = sel.userId))
      refresh(); setMsg("Item updated.")
    }

  // delete the selected item (with confirm)
  @FXML def handleDelete(): Unit =
    val sel = table.getSelectionModel.getSelectedItem
    if sel == null then { setMsg("Select an item to delete."); return }
    val alert = new Alert(Alert.AlertType.CONFIRMATION, s"Delete '${sel.name}'?", ButtonType.OK, ButtonType.CANCEL)
    val res = alert.showAndWait()
    if res.isPresent && res.get() == ButtonType.OK then
      given DBSession = AutoSession
      FoodItemDao.delete(sel.id)
      refresh(); setMsg("Item deleted.")

  // Add/Edit dialog with simple validation 

  // builds the dialog; returns a FoodItem draft on OK
  private def showItemForm(existing: Option[FoodItem]): Option[FoodItem] =
    val dialog = new Dialog[FoodItem]()
    dialog.setTitle(if existing.isDefined then "Edit Item" else "Add Item")
    dialog.getDialogPane.getButtonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

    val nameF = new TextField();  nameF.setPromptText("Name")
    val catF  = new TextField();  catF.setPromptText("Category")
    val qtyF  = new TextField();  qtyF.setPromptText("Qty (e.g. 1.5)")
    val unitF = new TextField();  unitF.setPromptText("Unit (kg, pcs...)")
    val dateF = new DatePicker(); dateF.setPromptText("Expiry")
    val costF = new TextField();  costF.setPromptText("Cost (e.g. 4.50)")

    existing.foreach { it =>
      nameF.setText(it.name)
      catF.setText(it.category)
      qtyF.setText(it.quantity.toString)
      unitF.setText(it.unit)
      dateF.setValue(it.expiry)
      costF.setText(it.cost.toString)
    }

    val grid = new GridPane()
    grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10, 10, 10, 10))
    grid.add(new Label("Name"),    0, 0); grid.add(nameF, 1, 0)
    grid.add(new Label("Category"),0, 1); grid.add(catF, 1, 1)
    grid.add(new Label("Quantity"),0, 2); grid.add(qtyF, 1, 2)
    grid.add(new Label("Unit"),    0, 3); grid.add(unitF, 1, 3)
    grid.add(new Label("Expiry"),  0, 4); grid.add(dateF, 1, 4)
    grid.add(new Label("Cost"),    0, 5); grid.add(costF, 1, 5)

    // inline validation message (styled by CSS)
    val hint = new Label()
    hint.getStyleClass.add("form-hint-warning")
    grid.add(hint, 1, 6)

    dialog.getDialogPane.setContent(grid)

    // disable OK until valid
    val okBtn = dialog.getDialogPane.lookupButton(ButtonType.OK).asInstanceOf[Button]
    okBtn.setDisable(true)

    def validDouble(s: String): Boolean =
      val t = s.trim
      t.nonEmpty && t.toDoubleOption.exists(_ >= 0.0)

    def validate(): Unit =
      val nameOk = nameF.getText.trim.nonEmpty
      val catOk  = catF.getText.trim.nonEmpty
      val unitOk = unitF.getText.trim.nonEmpty
      val qtyOk  = validDouble(qtyF.getText)
      val costOk = validDouble(costF.getText)
      val dpOk   = dateF.getValue != null && !dateF.getValue.isBefore(LocalDate.now)
      okBtn.setDisable(!(nameOk && catOk && unitOk && qtyOk && costOk && dpOk))
      if !nameOk then hint.setText("Name is required.")
      else if !catOk then hint.setText("Category is required.")
      else if !unitOk then hint.setText("Unit is required.")
      else if !qtyOk then hint.setText("Quantity must be a number ≥ 0.")
      else if !costOk then hint.setText("Cost must be a number ≥ 0.")
      else if !dpOk then hint.setText("Expiry must be today or later.")
      else hint.setText("")

    // watch all fields for changes
    nameF.textProperty.addListener((_,_,_) => validate())
    catF.textProperty.addListener((_,_,_) => validate())
    unitF.textProperty.addListener((_,_,_) => validate())
    qtyF.textProperty.addListener((_,_,_) => validate())
    costF.textProperty.addListener((_,_,_) => validate())
    dateF.valueProperty.addListener((_,_,_) => validate())
    validate()

    dialog.setResultConverter {
      case ButtonType.OK =>
        val base = existing.getOrElse(FoodItem(0L, 0L, "", "", 0.0, "", LocalDate.now, 0.0))
        base.copy(
          name     = nameF.getText.trim,
          category = catF.getText.trim,
          quantity = qtyF.getText.trim.toDouble,
          unit     = unitF.getText.trim,
          expiry   = dateF.getValue,
          cost     = costF.getText.trim.toDouble
        )
      case _ => null
    }

    val res = dialog.showAndWait()
    if res.isPresent then Option(res.get) else None

  // helper to show a message at the bottom
  private def setMsg(s: String): Unit =
    if messageLabel != null then messageLabel.setText(s)

  // FXML compatibility aliases
  @FXML def handleAddItem(): Unit = handleAdd()
  @FXML def handleEditItem(): Unit = handleEdit()
  @FXML def handleDeleteItem(): Unit = handleDelete()
