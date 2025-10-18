package makanplan.view

import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.stage.Stage

class AboutController:

  // window handle (set by caller after loading FXML)
  var stage: Option[Stage] = None

  // flag so caller knows user closed the dialog
  var okClicked: Boolean = false

  // Close button handler
  @FXML
  def handleClose(action: ActionEvent): Unit =
    okClicked = true
    stage.foreach(_.close())
