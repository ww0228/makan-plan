package makanplan.view

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout.{GridPane, VBox, HBox}
import javafx.geometry.Insets
import javafx.collections.FXCollections
import javafx.beans.property.{ReadOnlyObjectWrapper, ReadOnlyStringWrapper}
import scalikejdbc._
import java.time.LocalDate
import makanplan.dao.{MealPlanDao, PlannedMealDao, FoodItemDao}
import makanplan.model.{MealPlan, PlannedMeal}
import makanplan.model.FoodItem
import scala.jdk.CollectionConverters._
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory
import javafx.animation.{KeyFrame, Timeline, Animation}
import javafx.util.Duration
import javafx.application.Platform
import javafx.scene.input.KeyCode

class MealPlannerController:

  @FXML private var table: TableView[PlannedMeal] = _
  @FXML private var nameCol:     TableColumn[PlannedMeal, String] = _
  @FXML private var kindCol:     TableColumn[PlannedMeal, String] = _
  @FXML private var servingsCol: TableColumn[PlannedMeal, java.lang.Integer] = _
  @FXML private var costCol:     TableColumn[PlannedMeal, java.lang.Double] = _
  @FXML private var bannerLabel: Label = _
  @FXML private var messageLabel: Label = _
  @FXML private var weekTotalLabel: Label = _
  @FXML private var calendarGrid: GridPane = _

  // state
  private val meals = FXCollections.observableArrayList[PlannedMeal]() // table data
  private var activePlan: Option[MealPlan] = None                      // current user's plan

  // day tag lives at end of name, e.g. "Chicken Rice [2025-08-22]"
  private val DayTagRegex = """\s*\[(\d{4}-\d{2}-\d{2})]\s*$""".r

  // refreshes calendar so "today" border moves
  private var ticker: Timeline = _

  // simple row model for ingredient list in dialog
  final case class IngredientSel(item: FoodItem, qty: Double)

  // init 
  @FXML def initialize(): Unit =
    // table columns
    nameCol.setCellValueFactory(cd => new ReadOnlyStringWrapper(stripDayTag(cd.getValue.name)))
    kindCol.setCellValueFactory(cd => new ReadOnlyStringWrapper(cd.getValue.kind))
    servingsCol.setCellValueFactory(cd => new ReadOnlyObjectWrapper[java.lang.Integer](cd.getValue.servings))
    costCol.setCellValueFactory(cd => new ReadOnlyObjectWrapper[java.lang.Double](cd.getValue.cost))

    table.getSelectionModel.setSelectionMode(SelectionMode.SINGLE)
    table.setItems(meals)

    // double-click edit, Delete to remove, context menu
    table.setRowFactory(tv => {
      val row = new TableRow[PlannedMeal]()
      row.setOnMouseClicked(ev => if (ev.getClickCount == 2 && !row.isEmpty) editMeal(row.getItem))
      row
    })
    table.setOnKeyPressed(ev => if ev.getCode == KeyCode.DELETE then handleRemoveMeal())
    val cm = new ContextMenu(
      new MenuItem("Edit"){ setOnAction(_ => handleEditMeal()) },
      new MenuItem("Remove"){ setOnAction(_ => handleRemoveMeal()) }
    )
    table.setContextMenu(cm)

    ensureActivePlan()
    refreshMeals()
    startRealtimeTicker()

  // ensure user has an active plan for the current week
  private def ensureActivePlan(): Unit =
    val uid = makanplan.MainApp.currentUserId.getOrElse(0L)
    if uid <= 0 then { setBanner("Please log in"); return }
    given DBSession = AutoSession
    activePlan = MealPlanDao.findActiveForUser(uid) match
      case some @ Some(mp) => some
      case None =>
        val (start, end) = MealPlanDao.currentWeekRange()
        val id = MealPlanDao.createForWeek(uid, start, end)
        Some(MealPlan(id, uid, start, end))
    activePlan.foreach(mp => setBanner(s"Meal Plan: ${mp.startDate} → ${mp.endDate}"))

  // reload meals for active plan and redraw
  private def refreshMeals(): Unit =
    given DBSession = AutoSession
    activePlan match
      case Some(mp) =>
        val rows = PlannedMealDao.findByPlan(mp.id)
        meals.setAll(rows*)
        setMsg(s"${rows.size} meals planned.")
        setWeekTotal()
        renderCalendar()
      case None =>
        meals.clear()
        setWeekTotal()
        renderCalendar()
        setMsg("No active plan.")

  // select helper for syncing ListView and TableView
  private def selectMealInTable(m: PlannedMeal): Unit =
    val idx = meals.asScala.indexWhere(_.id == m.id)
    if idx >= 0 then table.getSelectionModel.select(idx)

  // guard for actions that need a selection
  private def requireSelection(action: String): Option[PlannedMeal] =
    val sel = table.getSelectionModel.getSelectedItem
    if sel == null then
      new Alert(Alert.AlertType.INFORMATION, s"Please select a meal to $action (double-click a row, or click an item in the Calendar).").showAndWait()
      None
    else Some(sel)

  // actions on toolbar/context menu 
  @FXML def handleEditMeal(): Unit =
    requireSelection("edit").foreach(editMeal)

  // open edit dialog and persist changes
  private def editMeal(m: PlannedMeal): Unit =
    showMealForm(Some(m)).foreach { updated =>
      given DBSession = AutoSession
      PlannedMealDao.update(updated.copy(id = m.id, mealPlanId = m.mealPlanId))
      refreshMeals(); setMsg("Meal updated.")
    }

  // delete selected meal
  @FXML def handleRemoveMeal(): Unit =
    requireSelection("remove").foreach { sel =>
      val alert = new Alert(Alert.AlertType.CONFIRMATION, s"Remove '${stripDayTag(sel.name)}'?", ButtonType.OK, ButtonType.CANCEL)
      val res = alert.showAndWait()
      if res.isPresent && res.get() == ButtonType.OK then
        given DBSession = AutoSession
        PlannedMealDao.delete(sel.id)
        refreshMeals(); setMsg("Meal removed.")
    }

  // wipe all meals in current week
  @FXML def handleClearWeek(): Unit =
    activePlan match
      case None => setMsg("No active plan to clear.");
      case Some(mp) =>
        val alert = new Alert(Alert.AlertType.CONFIRMATION, s"Clear all meals for ${mp.startDate} → ${mp.endDate}?", ButtonType.OK, ButtonType.CANCEL)
        val res = alert.showAndWait()
        if res.isPresent && res.get() == ButtonType.OK then
          given DBSession = AutoSession
          PlannedMealDao.deleteByPlan(mp.id)
          refreshMeals(); setMsg("Week cleared.")

  // add meal by picking items from inventory (cost is total of picks)
  @FXML def handleAddFromInventory(): Unit =
    activePlan match
      case None => setMsg("No active plan. Please log in.");
      case Some(mp) =>
        given DBSession = AutoSession

        val inv = FoodItemDao.findAllByUser(mp.userId)
        if inv.isEmpty then {
          setMsg("Your inventory is empty."); return
        }

        val dlg = new Dialog[PlannedMeal]()
        dlg.setTitle("Add from Inventory to Create Meal")
        dlg.getDialogPane.getButtonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        val nameF = new TextField()                      // meal name
        nameF.setPromptText("Meal name")
        val kindF = new ComboBox[String](FXCollections.observableArrayList("Breakfast", "Lunch", "Dinner", "Snack", "Dessert", "Other"))
        kindF.setPromptText("Select")
        val servF = new Spinner[Integer](1, 20, 1)       // servings
        val dayF = new DatePicker(mp.startDate)          // day within plan
        val min = mp.startDate
        val max = mp.endDate
        dayF.setDayCellFactory(_ => new DateCell() {
          override def updateItem(date: LocalDate, empty: Boolean): Unit =
            super.updateItem(date, empty)
            val dis = empty || date.isBefore(min) || date.isAfter(max)
            setDisable(dis)
            if dis then setStyle("-fx-opacity: 0.5; -fx-background-color: #f5f5f5;")
        })
        val totalLbl = new Label("Total cost: RM 0.00")  // running total
        val availLbl = new Label("")                     // stock/cost info
        val noteLbl = new Label("Note: ingredient breakdown is not stored yet; only total cost is saved.")

        // inventory dropdown
        val invCombo = new ComboBox[FoodItem](FXCollections.observableArrayList(inv *))
        invCombo.setPrefWidth(320)
        invCombo.setPromptText("Pick an item")
        invCombo.setCellFactory(_ => new ListCell[FoodItem]():
          override def updateItem(item: FoodItem, empty: Boolean): Unit =
            super.updateItem(item, empty)
            if empty || item == null then setText(null)
            else setText(s"${item.name} (${item.unit}) — RM ${"%.2f".format(item.cost)} total")
        )
        invCombo.setButtonCell(new ListCell[FoodItem]():
          override def updateItem(item: FoodItem, empty: Boolean): Unit =
            super.updateItem(item, empty)
            if empty || item == null then setText(null)
            else setText(s"${item.name} (${item.unit}) — RM ${"%.2f".format(item.cost)} total")
        )

        val qtySpin = new Spinner[java.lang.Double]()     // quantity picker
        qtySpin.setValueFactory(new DoubleSpinnerValueFactory(0.0, 999999.0, 1.0, 1.0))
        qtySpin.setEditable(true)

        val addBtn = new Button("Add Item")               // add selection
        val rmBtn = new Button("Remove Selected")         // remove selection

        // picked items (dialog-local table)
        final case class IngredientSel(item: FoodItem, qty: Double)
        val itemsObs = FXCollections.observableArrayList[IngredientSel]()
        val tbl = new TableView[IngredientSel](itemsObs)
        tbl.setPrefHeight(220)

        val colItem = new TableColumn[IngredientSel, String]("Item")
        colItem.setPrefWidth(220)
        val colQty = new TableColumn[IngredientSel, String]("Qty")
        colQty.setPrefWidth(80)
        val colUnit = new TableColumn[IngredientSel, String]("Unit")
        colUnit.setPrefWidth(80)
        val colUnitCost = new TableColumn[IngredientSel, String]("Cost/Unit")
        colUnitCost.setPrefWidth(100)
        val colSub = new TableColumn[IngredientSel, String]("Subtotal")
        colSub.setPrefWidth(110)

        colItem.setCellValueFactory(cd => new ReadOnlyStringWrapper(cd.getValue.item.name))
        colQty.setCellValueFactory(cd => new ReadOnlyStringWrapper(f"${cd.getValue.qty}%.2f"))
        colUnit.setCellValueFactory(cd => new ReadOnlyStringWrapper(cd.getValue.item.unit))

        // try to read an "available/qty/stock" field if present
        def qtyAvailableOf(it: FoodItem): Option[Double] =
          it match
            case p: Product =>
              val names = p.productElementNames.iterator.zipWithIndex.toMap
              val idxOpt: Option[Int] =
                List("available", "quantity", "qty", "stock")
                  .find(names.contains)
                  .map(names.apply)
              idxOpt.flatMap { i =>
                scala.util.Try(p.productElement(i) match
                  case d: java.lang.Double => d.doubleValue()
                  case d: Double => d
                  case i: java.lang.Integer => i.toDouble
                  case l: java.lang.Long => l.toDouble
                  case s: String => s.toDoubleOption.getOrElse(Double.NaN)
                  case _ => Double.NaN
                ).toOption.filter(!_.isNaN)
              }

        // cost per unit with fallback if stock is unknown
        def costPerUnit(it: FoodItem): Double =
          val q = qtyAvailableOf(it).getOrElse(0.0)
          val per = if q > 0.0 then it.cost / q else it.cost
          if per.isNaN || per.isInfinite then 0.0 else per

        // spinner step by unit
        def stepForUnit(u: String): Double =
          u.toLowerCase match
            case "g" | "ml" => 10.0
            case "kg" | "l" | "liter" | "litre" => 0.1
            case "pcs" | "pc" | "pack" | "bottle" | "can" | "unit" => 1.0
            case _ => 1.0

        // how much of this item already used in picks
        def usedSoFarFor(name: String): Double =
          itemsObs.asScala.filter(_.item.name == name).map(_.qty).sum

        // remaining stock after current picks
        def remainingFor(it: FoodItem): Double =
          qtyAvailableOf(it).map(av => math.max(0.0, av - usedSoFarFor(it.name))).getOrElse(Double.PositiveInfinity)

        // fill cost/row totals
        colUnitCost.setCellValueFactory(cd => new ReadOnlyStringWrapper(f"RM ${costPerUnit(cd.getValue.item)}%.2f"))
        colSub.setCellValueFactory(cd => new ReadOnlyStringWrapper(f"RM ${costPerUnit(cd.getValue.item) * cd.getValue.qty}%.2f"))
        tbl.getColumns.addAll(colItem, colQty, colUnit, colUnitCost, colSub)

        // configure qty spinner and info labels
        def configureSpinnerFor(it: FoodItem): Unit =
          val step = stepForUnit(Option(it.unit).getOrElse(""))
          val baseAvail = qtyAvailableOf(it)
          val remaining = remainingFor(it)
          val max = if (java.lang.Double.isInfinite(remaining)) 999999.0 else math.min(remaining, 999999.0)
          val init = if max <= 0.0 then 0.0 else math.min(step, max)
          qtySpin.setValueFactory(new DoubleSpinnerValueFactory(0.0, max, init, step))
          qtySpin.setDisable(max <= 0.0)
          addBtn.setDisable(max <= 0.0)
          val baseTxt = baseAvail.map(av => f"$av%.2f").getOrElse("—")
          val remTxt = if java.lang.Double.isInfinite(remaining) then "∞" else f"$remaining%.2f"
          val perTxt = f"${costPerUnit(it)}%.2f"
          availLbl.setText(
            s"Available: $baseTxt ${Option(it.unit).getOrElse("")}   Remaining: $remTxt   Cost/Unit: RM $perTxt"
          )

        invCombo.valueProperty().addListener((_, _, it) => if it != null then configureSpinnerFor(it))

        // live validation on qty typing
        qtySpin.getEditor.textProperty.addListener((_, _, _) =>
          val it = invCombo.getValue
          if it != null then
            val rem = remainingFor(it)
            val raw = qtySpin.getEditor.getText.trim
            val q = raw.toDoubleOption.getOrElse(0.0)
            val tooMuch = !java.lang.Double.isInfinite(rem) && q > rem + 1e-9
            addBtn.setDisable(tooMuch || q <= 0.0)
            qtySpin.getEditor.setStyle(if tooMuch then "-fx-background-color: #FEF2F2;" else "")
        )

        // recalc total and refresh info
        def recalc(): Unit =
          val sum = itemsObs.asScala.map(x => costPerUnit(x.item) * x.qty).sum
          totalLbl.setText(f"Total cost: RM $sum%.2f")
          val cur = invCombo.getValue
          if cur != null then configureSpinnerFor(cur)

        // add/remove picked ingredient rows
        addBtn.setOnAction(_ =>
          val it = invCombo.getValue
          val q = Option(qtySpin.getValue).map(_.doubleValue()).getOrElse(0.0)
          if it == null || q <= 0.0 then ()
          else
            val remaining = remainingFor(it)
            if !java.lang.Double.isInfinite(remaining) && q > remaining + 1e-9 then
              new Alert(
                Alert.AlertType.ERROR,
                f"Requested $q%.2f ${Option(it.unit).getOrElse("")} exceeds available stock. " +
                  f"Remaining: $remaining%.2f ${Option(it.unit).getOrElse("")}."
              ).showAndWait()
            else
              itemsObs.add(IngredientSel(it, q))
              qtySpin.getEditor.setStyle("")
              recalc()
        )

        rmBtn.setOnAction(_ =>
          val sel = tbl.getSelectionModel.getSelectedItem
          if sel != null then {
            itemsObs.remove(sel); recalc()
          }
        )

        val chooserRow = new HBox(8.0, new Label("Item"), invCombo, new Label("Qty"), qtySpin, addBtn, rmBtn)
        chooserRow.setPadding(new Insets(0, 0, 0, 0))

        // dialog layout
        val grid = new GridPane()
        grid.setHgap(10)
        grid.setVgap(10)
        grid.setPadding(new Insets(10, 10, 10, 10))
        grid.add(new Label("Name"), 0, 0)
        grid.add(nameF, 1, 0)
        grid.add(new Label("Type"), 0, 1)
        grid.add(kindF, 1, 1)
        grid.add(new Label("Servings"), 0, 2)
        grid.add(servF, 1, 2)
        grid.add(new Label("Day"), 0, 3)
        grid.add(dayF, 1, 3)
        grid.add(new Label("Ingredients"), 0, 4)
        grid.add(chooserRow, 1, 4)
        grid.add(availLbl, 1, 5)
        grid.add(tbl, 1, 6)
        grid.add(totalLbl, 1, 7)
        grid.add(noteLbl, 1, 8)

        dlg.getDialogPane.setContent(grid)

        if inv.nonEmpty then invCombo.getSelectionModel.select(0)

        // build PlannedMeal from inputs
        dlg.setResultConverter {
          case ButtonType.OK =>
            val nm = nameF.getText.trim
            val kd = Option(kindF.getValue).map(_.trim).getOrElse("")
            val sv = servF.getValue.intValue
            val day = Option(dayF.getValue).getOrElse(mp.startDate)
            val total = itemsObs.asScala.map(x => costPerUnit(x.item) * x.qty).sum
            if nm.isEmpty || kd.isEmpty || sv <= 0 || total <= 0.0 then null
            else
              PlannedMeal(0L, mp.id, withDayTag(nm = nm, day = day), kd, sv, total)
          case _ => null
        }

        val res = dlg.showAndWait()
        if res.isPresent then
          given DBSession = AutoSession
          PlannedMealDao.create(res.get)
          refreshMeals()
          setMsg("Meal created from inventory.")

  // matches any [YYYY-MM-DD] inside the name
  private val AnyDayTagRegex = """\[(\d{4}-\d{2}-\d{2})]""".r

  // pull date from name if present
  private def extractDayTag(name: String): Option[LocalDate] =
    AnyDayTagRegex.findFirstMatchIn(name).flatMap(m =>
      scala.util.Try(LocalDate.parse(m.group(1))).toOption
    )

  // attach/replace day tag
  private def withDayTag(nm: String, day: LocalDate): String =
    val baseMax = 190 // avoid huge names
    val base = nm.trim.take(baseMax)
    s"$base [${day.toString}]"

  // resolve which day a meal belongs to
  private def mealDay(m: PlannedMeal, default: LocalDate): LocalDate =
    extractDayTag(m.name).getOrElse(default)

  // show clean name without tag
  private def stripDayTag(n: String): String =
    DayTagRegex.replaceFirstIn(n, "").trim

  // generic add/edit dialog for a meal
  private def showMealForm(existing: Option[PlannedMeal]): Option[PlannedMeal] =
    activePlan match
      case None =>
        new Alert(Alert.AlertType.INFORMATION, "No active plan is loaded.").showAndWait()
        None

      case Some(mp) =>
        val dialog = new Dialog[PlannedMeal]()
        dialog.setTitle(if existing.isDefined then "Edit Meal" else "Add Meal")
        dialog.getDialogPane.getButtonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        val nameF = new TextField(); nameF.setPromptText("Name")
        val kindF = new ComboBox[String](FXCollections.observableArrayList("Breakfast", "Lunch", "Dinner", "Snack", "Dessert", "Other"))
        kindF.setPromptText("Select")
        val servF = new Spinner[Integer](1, 20, 1)
        val costF = new TextField(); costF.setPromptText("Cost (e.g. 4.50)")

        // initialize day within current week
        val initDay = existing.flatMap(m => extractDayTag(m.name)).getOrElse(mp.startDate)
        val dayF = new DatePicker(initDay)
        val min = mp.startDate
        val max = mp.endDate
        dayF.setDayCellFactory(_ => new DateCell() {
          override def updateItem(date: LocalDate, empty: Boolean): Unit =
            super.updateItem(date, empty)
            val dis = empty || date.isBefore(min) || date.isAfter(max)
            setDisable(dis)
            if dis then setStyle("-fx-opacity: 0.5; -fx-background-color: #f5f5f5;")
        })

        // prefill when editing
        existing.foreach { m =>
          nameF.setText(stripDayTag(m.name))
          kindF.getSelectionModel.select(m.kind)
          servF.getValueFactory.setValue(m.servings)
          costF.setText(m.cost.toString)
        }

        // layout
        val grid = new GridPane()
        grid.setHgap(10)
        grid.setVgap(10)
        grid.setPadding(new Insets(10, 10, 10, 10))
        grid.add(new Label("Name"), 0, 0); grid.add(nameF, 1, 0)
        grid.add(new Label("Type"), 0, 1); grid.add(kindF, 1, 1)
        grid.add(new Label("Servings"), 0, 2); grid.add(servF, 1, 2)
        grid.add(new Label("Cost"), 0, 3); grid.add(costF, 1, 3)
        grid.add(new Label("Day"), 0, 4); grid.add(dayF, 1, 4)

        dialog.getDialogPane.setContent(grid)

        // result
        dialog.setResultConverter {
          case ButtonType.OK =>
            try
              val nm = nameF.getText.trim
              val kd = Option(kindF.getValue).map(_.trim).getOrElse("")
              val sv = servF.getValue.intValue
              val cs = costF.getText.trim.toDouble
              val day = Option(dayF.getValue).getOrElse(mp.startDate)
              if nm.isEmpty || kd.isEmpty || sv <= 0 then null
              else
                existing match
                  case Some(base) =>
                    base.copy( // overwrite values
                      name = withDayTag(nm, day),
                      kind = kd,
                      servings = sv,
                      cost = cs
                    )
                  case None =>
                    PlannedMeal(0L, mp.id, withDayTag(nm, day), kd, sv, cs)
            catch case _: NumberFormatException => null
          case _ => null
        }

        val res = dialog.showAndWait()
        if res.isPresent then Option(res.get) else None

  // update banner/message labels
  private def setBanner(s: String): Unit = if bannerLabel != null then bannerLabel.setText(s)
  private def setMsg(s: String): Unit = if messageLabel != null then messageLabel.setText(s)

  // compute week total and render label
  private def setWeekTotal(): Unit =
    val sum = meals.asScala.map(_.cost).sum
    if weekTotalLabel != null then weekTotalLabel.setText(f"Week Total: RM $sum%.2f")

  // draw Mon..Sun × Breakfast/Lunch/Dinner/Snacks grid
  private def renderCalendar(): Unit =
    if calendarGrid == null then return
    calendarGrid.getChildren.clear()

    activePlan match
      case None => ()
      case Some(mp) =>
        val days = (0 until 7).map(i => mp.startDate.plusDays(i.toLong)) // Mon..Sun
        val today = LocalDate.now
        val cols = Vector("Breakfast","Lunch","Dinner","Snacks")

        // normalize kind into one of 4 buckets
        def colKey(kind: String): String =
          kind.toLowerCase match
            case "breakfast" => "Breakfast"
            case "lunch"     => "Lunch"
            case "dinner"    => "Dinner"
            case "snack" | "snacks" | "dessert" | "other" => "Snacks"
            case _           => "Snacks"

        // pastel header colors
        val headerColor = Map(
          "Breakfast" -> "#f8c8c8",
          "Lunch"     -> "#f9e5b2",
          "Dinner"    -> "#f4e3a1",
          "Snacks"    -> "#cfe7cf"
        )
        def headerStyle(bg: String) =
          s"-fx-background-color: $bg; -fx-font-weight: bold; -fx-alignment: center; -fx-padding: 8; -fx-background-radius: 10;"

        // corner cell
        val corner = new Label("")
        GridPane.setColumnIndex(corner, 0); GridPane.setRowIndex(corner, 0)
        calendarGrid.getChildren.add(corner)

        // column headers
        for ((ck, cIdx) <- cols.zipWithIndex) {
          val lbl = new Label(ck)
          lbl.setStyle(headerStyle(headerColor(ck)))
          GridPane.setColumnIndex(lbl, cIdx + 1); GridPane.setRowIndex(lbl, 0)
          calendarGrid.getChildren.add(lbl)
        }

        // row headers
        for ((d, rIdx) <- days.zipWithIndex) {
          val rowHdr = new Label(s"${d.getDayOfWeek.toString.substring(0,3)}\n${d.toString}")
          val base = "-fx-font-weight: bold; -fx-alignment: center; -fx-padding: 8; -fx-background-radius: 10; -fx-background-color: #e9ecef;"
          val todayExtra = if d.isEqual(today) then " -fx-border-color: -fx-accent; -fx-border-width: 2;" else ""
          rowHdr.setStyle(base + todayExtra)
          GridPane.setColumnIndex(rowHdr, 0); GridPane.setRowIndex(rowHdr, rIdx + 1)
          calendarGrid.getChildren.add(rowHdr)
        }

        // data cells
        for ((d, rIdx) <- days.zipWithIndex) {
          for ((ck, cIdx) <- cols.zipWithIndex) {
            val cellBox = new VBox(4.0)
            cellBox.setPadding(new Insets(6,6,6,6))
            val baseStyle = "-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-box-border; -fx-border-radius: 8; -fx-background-radius: 8;"
            val todayBorder = if d.isEqual(today) then " -fx-border-color: -fx-accent; -fx-border-width: 2;" else ""
            cellBox.setStyle(baseStyle + todayBorder)

            val list = new ListView[PlannedMeal]()
            list.setPrefHeight(120)

            // filter meals for this day+column
            val ms = meals.asScala.filter(m => mealDay(m, mp.startDate).isEqual(d) && colKey(m.kind) == ck).toList
            val items = FXCollections.observableArrayList[PlannedMeal]()
            items.addAll(ms*)
            list.setItems(items)

            // render list rows
            list.setCellFactory(_ => new ListCell[PlannedMeal](){
              override def updateItem(item: PlannedMeal, empty: Boolean): Unit =
                super.updateItem(item, empty)
                if empty || item == null then setText(null)
                else setText(s"• ${stripDayTag(item.name)} (x${item.servings})  RM ${"%.2f".format(item.cost)}")
            })

            // sync selection; double-click edit; context menu
            list.getSelectionModel.selectedItemProperty.addListener((_,_,sel) => if sel != null then selectMealInTable(sel))
            list.setOnMouseClicked(ev => if ev.getClickCount == 2 && !list.getSelectionModel.isEmpty then editMeal(list.getSelectionModel.getSelectedItem))

            val cm = new ContextMenu(
              new MenuItem("Edit"){ setOnAction(_ => if (!list.getSelectionModel.isEmpty) editMeal(list.getSelectionModel.getSelectedItem)) },
              new MenuItem("Remove"){ setOnAction(_ => { selectMealInTable(list.getSelectionModel.getSelectedItem); handleRemoveMeal() }) }
            )
            list.setContextMenu(cm)

            // subtotal for this cell
            val total = ms.map(_.cost).sum
            val totalLbl = new Label(f"RM $total%.2f")

            cellBox.getChildren.addAll(totalLbl, list)
            GridPane.setColumnIndex(cellBox, cIdx + 1); GridPane.setRowIndex(cellBox, rIdx + 1)
            calendarGrid.getChildren.add(cellBox)
          }
        }

  // realtime ticker to keep totals/today fresh
  private def startRealtimeTicker(): Unit =
    val frame = new KeyFrame(Duration.seconds(60), _ => Platform.runLater(() => {
      setWeekTotal()
      renderCalendar()
    }))
    ticker = new Timeline(frame)
    ticker.setCycleCount(Animation.INDEFINITE)
    ticker.play()
