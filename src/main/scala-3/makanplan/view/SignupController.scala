package makanplan.view

import javafx.fxml.FXML
import javafx.scene.control.{Label, PasswordField, TextField}
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType
import scalikejdbc.*
import makanplan.MainApp
import makanplan.dao.UserDao
import makanplan.util.Password
import javafx.scene.layout.StackPane

class SignupController:

  @FXML private var usernameField: TextField = _
  @FXML private var passwordField: PasswordField = _
  @FXML private var confirmField:  PasswordField = _
  @FXML private var messageLabel:  Label = _
  @FXML private var rightPane: StackPane = _

  // Navigate back to the Login view
  @FXML private def handleBackToLogin(): Unit =
    MainApp.showLoginInCenter()

  // Handle the Sign Up action
  @FXML private def handleSignup(): Unit =
    // Read inputs safely
    val u = Option(usernameField).map(_.getText.trim).getOrElse("")
    val p = Option(passwordField).map(_.getText).getOrElse("")
    val c = Option(confirmField).map(_.getText).getOrElse("")

    // Show an inline label + alert, then stop the flow
    def fail(msg: String): Unit =
      if messageLabel != null then messageLabel.setText(msg)
      new Alert(AlertType.Error) {
        title = "Invalid sign up"
        headerText = "Please correct the fields"
        contentText = msg
      }.showAndWait()

    // Simple validations
    if u.length < 3 then return fail("Username must be at least 3 characters.")
    if p.length < 6 then return fail("Password must be at least 6 characters.")
    if p != c then return fail("Passwords do not match.")

    // Database session
    given DBSession = AutoSession

    // Ensure username is unique, then create the user
    UserDao.findByUsername(u) match
      case Some(_) =>
        fail("Username already exists. Choose another.")
      case None =>
        val id = UserDao.create(u, Password.sha256(p))
        // Optional: insert default preferences/settings for the new user

        // Success feedback
        new Alert(AlertType.Information) {
          title = "Account created"
          headerText = "Sign-up successful"
          contentText = s"Welcome, $u! You can now log in."
        }.showAndWait()

        // Go back to login screen
        MainApp.showLoginInCenter()
