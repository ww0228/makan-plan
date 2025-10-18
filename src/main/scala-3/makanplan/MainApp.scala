package makanplan

import javafx.fxml.FXMLLoader
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.Scene
import scalafx.Includes.*
import scalafx.stage.Modality.ApplicationModal
import scalafx.stage.Stage
import makanplan.util.Database
import makanplan.view.PlannerTabsController

object MainApp extends JFXApp3:
  // Global UI root kept for swapping center content
  var rootPane: Option[javafx.scene.layout.BorderPane] = None
  // Logged-in user id (None means not signed in)
  var currentUserId: Option[Long] = None
  // Reference to the tabs controller for menu navigation
  var tabsController: Option[PlannerTabsController] = None
  // Convenience flag for login state
  def isSignedIn: Boolean = currentUserId.isDefined

  override def start(): Unit =
    // Initialize database and tables
    Database.init()

    // Load root layout (menu bar + center placeholder)
    val rootLayoutResource = getClass.getResource("/makanplan/view/RootLayout.fxml")
    val loader = new FXMLLoader(rootLayoutResource)
    val rootLayout = loader.load[javafx.scene.layout.BorderPane]()
    // Cache the root BorderPane so we can swap center panes later
    rootPane = Option(loader.getRoot[javafx.scene.layout.BorderPane]())

    // Create primary stage and attach stylesheet
    stage = new PrimaryStage():
      title = "MakanPlan"
      scene = new Scene():
        val css = getClass.getResource("/makanplan/view/MakanPlan.css")
        if css != null then stylesheets = Seq(css.toExternalForm)
        root = rootLayout

    // Show login screen in the center area
    showLoginInCenter()

  // Replace center with Login view and wire success callback
  def showLoginInCenter(): Unit =
    val loginUrl = getClass.getResource("/makanplan/view/LoginView.fxml")
    val loader = new FXMLLoader(loginUrl)
    val pane = loader.load[javafx.scene.Parent]()
    rootPane.foreach(_.setCenter(pane))

    val ctrl = loader.getController[makanplan.view.AuthController]
    ctrl.onSuccess = () => showTabsInCenter()

  // Replace center with the main Tabs view and keep controller ref
  def showTabsInCenter(): Unit =
    val tabsUrl = getClass.getResource("/makanplan/view/PlannerTabs.fxml")
    val loader = new FXMLLoader(tabsUrl)
    val pane = loader.load[javafx.scene.Parent]()

    tabsController = Option(loader.getController[PlannerTabsController]())
    rootPane.foreach(_.setCenter(pane))

  // Show the About dialog as a modal window
  def showAbout(): Boolean =
    val about = getClass.getResource("/makanplan/view/About.fxml")
    val loader = new FXMLLoader(about)
    loader.load()
    val pane = loader.getRoot[javafx.scene.Parent]()
    val mywindow = new Stage():
      initOwner(stage)
      initModality(ApplicationModal)
      title = "About"
      scene = new Scene():
        root = pane
    val ctrl = loader.getController[makanplan.view.AboutController]()
    ctrl.stage = Option(mywindow)
    mywindow.showAndWait()
    ctrl.okClicked

  // Clear session and return to Login
  def logoutToLogin(): Unit =
    currentUserId = None
    tabsController = None
    rootPane.foreach(_.setCenter(new javafx.scene.layout.StackPane()))
    showLoginInCenter()

  // Replace center with Sign Up view
  def showSignupInCenter(): Unit =
    val url = getClass.getResource("/makanplan/view/SignUpView.fxml")
    val pane = new javafx.fxml.FXMLLoader(url).load[javafx.scene.Parent]()
    rootPane.foreach(_.setCenter(pane))
