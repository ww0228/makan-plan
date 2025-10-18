package makanplan.model

// BMI categories used by the app
enum BMIStatus:
  case Underweight, Normal, Overweight, Obese

// Stored BMI record tied to a user
final case class BMI(
                      id: Long,           // DB id
                      userId: Long,       // owner
                      bmiValue: Double,   // numeric BMI (e.g., 22.4)
                      healthStatus: String// human-readable status (optional persistence)
                    )

// BMI utilities
object BMI:
  // compute BMI from height (cm) and weight (kg); returns (value, status)
  def compute(heightCm: Double, weightKg: Double): (Double, BMIStatus) =
    val h = heightCm / 100.0            // cm -> m
    if h <= 0 || weightKg <= 0 then     // simple guard
      (0.0, BMIStatus.Underweight)
    else
      val raw = weightKg / (h * h)      // kg / m^2
      val value = BigDecimal(raw).setScale(1, BigDecimal.RoundingMode.HALF_UP).toDouble // 1 d.p.
      val status =
        if value < 18.5 then BMIStatus.Underweight
        else if value < 25.0 then BMIStatus.Normal
        else if value < 30.0 then BMIStatus.Overweight
        else BMIStatus.Obese
      // return rounded value + category
      (BigDecimal(value).setScale(1, BigDecimal.RoundingMode.HALF_UP).toDouble, status)
