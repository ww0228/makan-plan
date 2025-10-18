package makanplan.view

import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.geometry.Insets
import scalikejdbc.*
import makanplan.model.{DietaryPreference, UserPreferences, User}
import makanplan.dao.{UserDao, UserPreferencesDao, DietaryPreferenceDao}

class ProfileController:

  // Labels and controls shown on the Profile tab
  @FXML private var usernameLabel: Label = _
  @FXML private var emailLabel: Label = _
  @FXML private var dietPrefLabel: Label = _
  @FXML private var budgetLabel: Label = _
  @FXML private var messageLabel: Label = _
  @FXML private var editButton: Button = _

  // Cached data from database for quick access
  private var cachedUser: Option[User] = None
  private var cachedDiet: Option[DietaryPreference] = None
  private var cachedSettings: Option[UserPreferences] = None

  // Runs when the FXML is loaded
  @FXML def initialize(): Unit =
    loadFromDB()

  // Loads current user's data and fills labels
  private def loadFromDB(): Unit =
    val uid = currentUserId()
    if uid <= 0 then { setMsg("Please log in."); return }
    given DBSession = AutoSession

    cachedUser     = UserDao.findById(uid)
    cachedDiet     = DietaryPreferenceDao.findByUser(uid)
    cachedSettings = UserPreferencesDao.findByUser(uid)

    usernameLabel.setText(cachedUser.map(_.username).getOrElse(""))
    emailLabel.setText(cachedUser.flatMap(u => Option(u.email)).getOrElse(""))
    dietPrefLabel.setText(cachedDiet.map(_.preference).getOrElse(""))
    budgetLabel.setText(cachedSettings.map(s => f"${s.defaultBudget}%.2f").getOrElse(""))

    setMsg("")

  // Opens the edit dialog and writes changes back to DB
  @FXML def handleEdit(): Unit =
    val uid = currentUserId()
    if uid <= 0 then { setMsg("Please log in."); return }

    // Seed dialog with current values
    val startUsername = usernameLabel.getText
    val startEmail    = emailLabel.getText
    val startDiet     = dietPrefLabel.getText
    val startBudget   = toDouble(budgetLabel.getText).getOrElse(0.0)

    // Show dialog and handle result if OK
    showEditDialog(startUsername, startEmail, startDiet, startBudget).foreach {
      (username, email, diet, budget) =>
        given DBSession = AutoSession
        val existingMode = cachedSettings.map(_.defaultMode).getOrElse("Household")

        // Update user profile; show message if username is taken
        val ok = UserDao.updateProfile(uid, username, email)
        if !ok then { setMsg("Username is already taken."); return }

        // Upsert diet and preferences
        DietaryPreferenceDao.upsert(DietaryPreference.forUser(uid, diet))
        UserPreferencesDao.upsert(UserPreferences(uid, existingMode, budget))

        // Update session username if field exists
        try {
          val f = makanplan.MainApp.getClass.getDeclaredField("currentUsername")
          f.setAccessible(true)
          f.set(makanplan.MainApp, Some(username))
        } catch
          case _ => ()

        setMsg("Profile updated.")
        loadFromDB()
    }

  // Builds and shows a modal dialog for editing profile
  private def showEditDialog(
                              username0: String,
                              email0: String,
                              diet0: String,
                              budget0: Double
                            ): Option[(String, String, String, Double)] =
    val dialog = new Dialog[(String, String, String, Double)]()
    dialog.setTitle("Edit Profile")
    dialog.getDialogPane.getButtonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

    // Fields
    val usernameF = new TextField(username0); usernameF.setPromptText("Username")

    // Simple 2FA toggle that controls whether email is required
    val twoFaCheck = new CheckBox("Enable")
    val initialTwoFa = email0 != null && email0.trim.nonEmpty
    twoFaCheck.setSelected(initialTwoFa)

    val emailLabelNode = new Label("Email")
    val emailF    = new TextField(email0);    emailF.setPromptText("Email (required when 2FA is ON)")

    val dietF     = new ComboBox[String]()
    dietF.getItems.setAll(
      "Vegan","Vegetarian","Pescatarian","Halal",
      "Non-Vegetarian","Gluten-Free","Keto","Other"
    )
    dietF.setPromptText("Select")
    if diet0 != null && diet0.nonEmpty then dietF.getSelectionModel.select(diet0)

    val budgetF   = new TextField(f"$budget0%.2f"); budgetF.setPromptText("0.00")

    val hint = new Label(); hint.getStyleClass.add("form-hint-warning")

    // Layout
    val grid = new GridPane()
    grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(10, 10, 10, 10))

    grid.add(new Label("Username"), 0, 0); grid.add(usernameF, 1, 0)
    grid.add(new Label("Two-Factor Auth"), 0, 1); grid.add(twoFaCheck, 1, 1)
    grid.add(emailLabelNode, 0, 2); grid.add(emailF, 1, 2)
    grid.add(new Label("Diet"), 0, 3); grid.add(dietF, 1, 3)
    grid.add(new Label("Budget"), 0, 4); grid.add(budgetF, 1, 4)
    grid.add(hint, 1, 5)

    dialog.getDialogPane.setContent(grid)

    // Disable OK until inputs are valid
    val okBtn = dialog.getDialogPane.lookupButton(ButtonType.OK).asInstanceOf[Button]
    okBtn.setDisable(true)

    // Simple validators
    def validUsername(s: String): Boolean =
      s.matches("^[A-Za-z0-9._-]{3,32}$")

    def validEmail(s: String): Boolean =
      s.trim.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    def validBudget(s: String): Boolean =
      val t = s.trim
      t.matches("^\\d+(?:\\.\\d{1,2})?$") && t.toDouble >= 0.0 && t.toDouble <= 100000.0

    // Show/hide email field based on 2FA checkbox
    def applyTwoFaState(): Unit =
      val on = twoFaCheck.isSelected
      emailLabelNode.setVisible(on); emailLabelNode.setManaged(on)
      emailF.setVisible(on); emailF.setManaged(on)
      emailF.setDisable(!on)

    // Recheck all fields and update hint and OK state
    def validate(): Unit =
      val unameOk  = validUsername(usernameF.getText.trim)
      val budgetOk = validBudget(budgetF.getText)
      val dietOk   = Option(dietF.getValue).exists(_.nonEmpty)

      val emailRequired = twoFaCheck.isSelected
      val emailText = emailF.getText
      val emailOk =
        if !emailRequired then true
        else validEmail(emailText)

      okBtn.setDisable(!(unameOk && emailOk && budgetOk && dietOk))
      if !unameOk then hint.setText("Username: 3–32 chars, letters/digits/._- only.")
      else if !emailOk then hint.setText("Email is required and must be valid when 2FA is ON.")
      else if !budgetOk then hint.setText("Budget must be a number ≥ 0 with up to 2 decimals.")
      else if !dietOk then hint.setText("Please select a dietary preference.")
      else hint.setText("")

    // Hook up listeners to validate in real time
    usernameF.textProperty.addListener((_,_,_) => validate())
    budgetF.textProperty.addListener((_,_,_) => validate())
    dietF.valueProperty.addListener((_,_,_) => validate())
    twoFaCheck.selectedProperty.addListener((_,_,_) => { applyTwoFaState(); validate() })
    emailF.textProperty.addListener((_,_,_) => validate())

    // Apply initial toggle state and run first validation
    applyTwoFaState()
    validate()

    // Convert dialog result to a tuple when OK is pressed
    dialog.setResultConverter {
      case ButtonType.OK =>
        val uname  = usernameF.getText.trim
        // If 2FA is OFF, keep the original email
        val emailOut =
          if twoFaCheck.isSelected then emailF.getText.trim
          else email0
        val diet   = Option(dietF.getValue).getOrElse("")
        val budget = budgetF.getText.trim.toDouble
        (uname, emailOut, diet, budget)
      case _ => null
    }

    // Return the result as Option
    val res = dialog.showAndWait()
    if res.isPresent then Option(res.get) else None

  // Safe string-to-double parse
  private def toDouble(s: String): Option[Double] =
    try Some(s.trim.toDouble) catch case _: Throwable => None

  // Shows a message in the profile tab
  private def setMsg(s: String): Unit =
    if messageLabel != null then messageLabel.setText(s)

  // Reads current user id from MainApp session
  private def currentUserId(): Long =
    try
      val f = makanplan.MainApp.getClass.getDeclaredField("currentUserId")
      f.setAccessible(true)
      f.get(makanplan.MainApp).asInstanceOf[Option[Long]].getOrElse(0L)
    catch
      case _ => 0L
