package makanplan.view

import javafx.fxml.FXML
import javafx.scene.control.{Label, PasswordField, TextField}
import scalikejdbc.*
import makanplan.dao.UserDao
import makanplan.util.Password
import javafx.scene.layout.StackPane

class AuthController:

  // callback to run after successful login (default: show tabs)
  var onSuccess: () => Unit = () =>
    try makanplan.MainApp.showTabsInCenter()
    catch case _ => ()

  // FXML refs
  @FXML private var usernameField: TextField = _
  @FXML private var passwordField: PasswordField = _
  @FXML private var messageLabel: Label = _
  @FXML private var rightPane: StackPane = _

  // login button
  @FXML def handleLogin(): Unit =
    val login = Option(usernameField.getText).map(_.trim).getOrElse("")
    val pass  = Option(passwordField.getText).getOrElse("")
    if login.isEmpty || pass.isEmpty then
      if messageLabel != null then messageLabel.setText("Enter username and password.")
      return

    given DBSession = AutoSession // DB session for this action
    UserDao.findByLogin(login) match
      case Some(u) if Password.verify(pass, u.passwordHash) =>
        // set global user info (using reflection to reach MainApp fields)
        try {
          val f = makanplan.MainApp.getClass.getDeclaredField("currentUserId")
          f.setAccessible(true)
          f.set(makanplan.MainApp, Some(u.id))
        } catch case _ => ()

        try {
          val f2 = makanplan.MainApp.getClass.getDeclaredField("currentUsername")
          f2.setAccessible(true)
          f2.set(makanplan.MainApp, Some(u.username))
        } catch case _ => ()

        if messageLabel != null then messageLabel.setText("") // clear any error
        onSuccess() // proceed to main UI

      case _ =>
        if messageLabel != null then messageLabel.setText("Invalid login.")

  // show signup screen
  @FXML def handleShowSignup(): Unit =
    makanplan.MainApp.showSignupInCenter()
