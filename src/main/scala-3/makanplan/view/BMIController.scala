package makanplan.view

import javafx.fxml.FXML
import javafx.scene.control.{Label, TextField}
import javafx.animation.PauseTransition
import javafx.util.Duration
import makanplan.model.BMIStatus

class BMIController:

  @FXML private var heightField: TextField = _
  @FXML private var weightField: TextField = _
  @FXML private var bmiLabel: Label = _
  @FXML private var statusLabel: Label = _
  @FXML private var messageLabel: Label = _

  // remember last calculated value (optional)
  private var lastValue: Option[(Double, BMIStatus)] = None

  // small timer to auto-hide error messages
  private var hideTimer: PauseTransition = _

  // init: hide message by default; clear it whenever the user types
  @FXML def initialize(): Unit =
    hideMsg()
    hideTimer = new PauseTransition(Duration.seconds(3))
    hideTimer.setOnFinished(_ => hideMsg())

    if heightField != null then
      heightField.textProperty.addListener((_, _, _) => hideOnInput())
    if weightField != null then
      weightField.textProperty.addListener((_, _, _) => hideOnInput())

  @FXML def handleCalculate(): Unit =
    val h = parseDouble(heightField)
    val w = parseDouble(weightField)

    if h <= 0 || w <= 0 then
      showMsg("Enter valid height/weight.")
      return

    val (v, s) = makanplan.model.BMI.compute(h, w)
    lastValue = Some((v, s))

    if bmiLabel != null then bmiLabel.setText(f"$v%.1f")
    if statusLabel != null then statusLabel.setText(s.toString)

    // successful compute -> hide any previous error
    hideMsg()

  private def parseDouble(tf: TextField): Double =
    Option(tf).flatMap(t => Option(t.getText)).map(_.trim).flatMap(_.toDoubleOption).getOrElse(0.0)

  // show an error and auto-hide after a short delay
  private def showMsg(s: String, seconds: Double = 3.0): Unit =
    if messageLabel != null then
      messageLabel.setText(s)
      messageLabel.setVisible(true)
      messageLabel.setManaged(true) // remove layout gap when hidden
      if hideTimer == null then hideTimer = new PauseTransition(Duration.seconds(seconds))
      hideTimer.stop()
      hideTimer.setDuration(Duration.seconds(seconds))
      hideTimer.playFromStart()

  // hide the message immediately
  private def hideMsg(): Unit =
    if messageLabel != null then
      messageLabel.setText("")
      messageLabel.setVisible(false)
      messageLabel.setManaged(false)

  // whenever the user changes input, clear any old error
  private def hideOnInput(): Unit =
    if hideTimer != null then hideTimer.stop()
    hideMsg()
