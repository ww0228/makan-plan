package makanplan.view

import javafx.fxml.{FXML, FXMLLoader}
import javafx.scene.{Node, Parent}
import javafx.scene.control.{Label, Tab, TabPane}
import makanplan.MainApp.logoutToLogin
import scalikejdbc.*
import makanplan.dao.UserDao

class PlannerTabsController:

  // FXML controls
  @FXML private var userLabel: Label = _
  @FXML private var tabs: TabPane = _
  @FXML private var inventoryTab: Tab = _
  @FXML private var mealPlannerTab: Tab = _
  @FXML private var profileTab: Tab = _
  @FXML private var bmiTab: Tab = _
  @FXML private var recipeBookTab: Tab = _

  // Tab navigation
  @FXML def navInventory(): Unit   = if tabs != null then tabs.getSelectionModel.select(inventoryTab)
  @FXML def navMealPlanner(): Unit = if tabs != null then tabs.getSelectionModel.select(mealPlannerTab)
  @FXML def navProfile(): Unit     = if tabs != null then tabs.getSelectionModel.select(profileTab)
  @FXML def navBMI(): Unit         = if tabs != null then tabs.getSelectionModel.select(bmiTab)
  @FXML def navRecipeBook(): Unit  = if tabs != null then tabs.getSelectionModel.select(recipeBookTab)

  // Controller setup
  @FXML def initialize(): Unit =
    // Ensure a logged-in user exists
    val uid = sessionUserId().getOrElse {
      safeShowLogin(); return
    }

    // Get username from session or DB
    val uname = sessionUsername().orElse {
      given DBSession = AutoSession
      UserDao.findById(uid).map(_.username)
    }.getOrElse {
      safeShowLogin(); return
    }
    userLabel.setText(s"Signed in as $uname")

    // Load tab contents
    inventoryTab.setContent(load("/makanplan/view/InventoryView.fxml"))
    mealPlannerTab.setContent(load("/makanplan/view/MealPlannerView.fxml"))
    profileTab.setContent(load("/makanplan/view/ProfileView.fxml"))
    bmiTab.setContent(load("/makanplan/view/BMIVIew.fxml")) // verify FXML path
    recipeBookTab.setContent(load("/makanplan/view/RecipeBookView.fxml"))

  // Load FXML; show an inline error label if it fails
  private def load(resource: String): Node =
    try new FXMLLoader(getClass.getResource(resource)).load[Parent]()
    catch
      case e: Throwable =>
        val l = new Label(s"Failed to load $resource: ${e.getMessage}")
        l.setStyle("-fx-text-fill: firebrick;")
        l

  // Logout button handler
  @FXML def handleLogout(): Unit =
    logoutToLogin()

  // Session helpers
  // Uses reflection to read session fields from MainApp
  private def sessionUserId(): Option[Long] =
    try
      val f = makanplan.MainApp.getClass.getDeclaredField("currentUserId")
      f.setAccessible(true)
      f.get(makanplan.MainApp).asInstanceOf[Option[Long]]
    catch
      case _ => None

  private def sessionUsername(): Option[String] =
    try
      val f = makanplan.MainApp.getClass.getDeclaredField("currentUsername")
      f.setAccessible(true)
      f.get(makanplan.MainApp).asInstanceOf[Option[String]]
    catch
      case _ => None

  // Clear session values in MainApp
  private def clearSession(): Unit =
    try
      val f1 = makanplan.MainApp.getClass.getDeclaredField("currentUserId")
      f1.setAccessible(true); f1.set(makanplan.MainApp, None)
    catch case _ => ()
    try
      val f2 = makanplan.MainApp.getClass.getDeclaredField("currentUsername")
      f2.setAccessible(true); f2.set(makanplan.MainApp, None)
    catch case _ => ()

  // Show Login safely
  private def safeShowLogin(): Unit =
    try makanplan.MainApp.showLoginInCenter()
    catch case _ => ()
