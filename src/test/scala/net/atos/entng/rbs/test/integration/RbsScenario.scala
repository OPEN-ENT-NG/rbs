package net.atos.entng.rbs.test.integration

import io.gatling.core.Predef._
import io.gatling.core.body.{Body, StringBody}
import io.gatling.http.Predef._

import org.entcore.test.appregistry.Role
import net.minidev.json.{ JSONValue, JSONObject }
import scala.collection.JavaConverters._
import java.util.concurrent.TimeUnit

object RbsScenario {

  def getSlotAsStringBody(slot: Tuple2[Long, Long]): Body = {
    return StringBody("""{"start_date" : """ + slot._1 + """, "end_date" : """ + slot._2 + ""","iana":"Europe/Paris"}""")
  }

  // Slots to create concurrent bookings
  val startDate = 1914298200L // Unix timestamp in seconds, corresponding to 2030-08-30 05:30:00

  val firstSlot = (startDate, startDate + TimeUnit.SECONDS.convert(3, TimeUnit.HOURS))
  val secondSlot = (firstSlot._2, firstSlot._2 + TimeUnit.SECONDS.convert(3, TimeUnit.HOURS))
  val thirdSlot = (firstSlot._1 - TimeUnit.SECONDS.convert(2, TimeUnit.HOURS),
    secondSlot._2 + TimeUnit.SECONDS.convert(1, TimeUnit.HOURS))

  val concurrentSlot = (firstSlot._1 + TimeUnit.SECONDS.convert(10, TimeUnit.MINUTES),
    firstSlot._2 + TimeUnit.SECONDS.convert(1, TimeUnit.HOURS))
  val nonConcurrentSlot = (concurrentSlot._2, concurrentSlot._2 + TimeUnit.SECONDS.convert(1, TimeUnit.HOURS))
  val secondNonConcurrentSlot = (concurrentSlot._1 - TimeUnit.SECONDS.convert(1, TimeUnit.HOURS), concurrentSlot._1)

  // Dates to create a periodic booking
  val pSlotStartDate = 1919755800L // 2030-11-01 (friday) 10:30:00
  val pSlotEndDate = pSlotStartDate + TimeUnit.SECONDS.convert(7, TimeUnit.HOURS)
  val pLastSlotEndDate = 1923409800L // 2030-12-13 17:30:00

  val scnCreateTeachers = exec(http("Login - admin user")
    .post("""/auth/login""")
    .formParam("""callBack""", """http%3A%2F%2Flocalhost%3A8080%2Fadmin""")
    .formParam("""email""", """tom.mate""")
    .formParam("""password""", """password""")
    .check(status.is(302)))
    
  val scnCreateBookings =
    Role.createAndSetRole("Réservation de ressources")
      .exec(http("Login - teacher")
        .post("""/auth/login""")
        .formParam("""email""", """${teacherDrouillacLogin}""")
        .formParam("""password""", """blipblop"""))
      
  val scnAdml = exec(http("Login - ADML")
        .post("""/auth/login""")
        .formParam("""email""", """${teacherDaudierLogin}""")
        .formParam("""password""", """blipblop"""))

}