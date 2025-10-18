package makanplan.view

import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.{Alert, MenuItem}
import javafx.scene.control.Alert.AlertType
import makanplan.MainApp

class RootLayoutController {

  // Top menu items wired from FXML
  @FXML private var Inventory: MenuItem   = _
  @FXML private var MealPlanner: MenuItem = _
  @FXML private var RecipeBook: MenuItem  = _
  @FXML private var BMI: MenuItem         = _
  @FXML private var Profile: MenuItem     = _
  @FXML private var Close: MenuItem       = _
  @FXML private var About: MenuItem       = _

  // Runs an action only if a user is logged in
  private def requireLogin(action: => Unit): Unit =
    if (MainApp.currentUserId.isDefined) action
    else showError("Please sign in first.")

  // Shows an error alert; uses custom Message util if available, else falls back
  private def showError(msg: String): Unit =
    try
      val clazz = Class.forName("makanplan.util.Message")
      val m = clazz.getMethod("showAlertError", classOf[javafx.stage.Stage], classOf[String])
      m.invoke(null, MainApp.stage, msg)
      ()
    catch
      case _: Throwable =>
        val a = new Alert(AlertType.ERROR)
        a.setTitle("Error")
        a.setHeaderText(null)
        a.setContentText(msg)
        a.showAndWait()
        ()

  // Menu navigation handlers (guarded by login)
  @FXML def goInventory(e: ActionEvent): Unit =
    requireLogin { MainApp.tabsController.foreach(_.navInventory()) }

  @FXML def goMealPlanner(e: ActionEvent): Unit =
    requireLogin { MainApp.tabsController.foreach(_.navMealPlanner()) }

  @FXML def goRecipeBook(e: ActionEvent): Unit =
    requireLogin { MainApp.tabsController.foreach(_.navRecipeBook()) }

  @FXML def goBMI(e: ActionEvent): Unit =
    requireLogin { MainApp.tabsController.foreach(_.navBMI()) }

  @FXML def goProfile(e: ActionEvent): Unit =
    requireLogin { MainApp.tabsController.foreach(_.navProfile()) }

  // Static dialogs / app commands
  @FXML def handleAbout(e: ActionEvent): Unit =
    MainApp.showAbout()

  @FXML def handleClose(e: ActionEvent): Unit =
    System.exit(0)
}
