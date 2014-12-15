package net.atos.entng.rbs.test.integration

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import org.entcore.test.appregistry.Role
import net.minidev.json.{ JSONValue, JSONObject }
import scala.collection.JavaConverters._
import java.util.concurrent.TimeUnit
import io.gatling.http.request.StringBody

object RbsScenario {

  def getSlotAsStringBody(slot: Tuple2[Long, Long]): StringBody = {
    return StringBody("""{"start_date" : """ + slot._1 + """, "end_date" : """ + slot._2 + "}")
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
  val periodicStartDate = 1919755800L // 2030-11-01 (friday) 09:30:00
  val periodicEndDate = periodicStartDate + TimeUnit.SECONDS.convert(7, TimeUnit.HOURS)

  val scnCreateTeachers = exec(http("Login - admin user")
    .post("""/auth/login""")
    .formParam("""callBack""", """http%3A%2F%2Flocalhost%3A8080%2Fadmin""")
    .formParam("""email""", """tom.mate""")
    .formParam("""password""", """password""")
    .check(status.is(302)))

    .exec(http("Create manual teacher")
      .post("""/directory/api/user""")
      .formParam("""classId""", """${classId}""")
      .formParam("""lastname""", "PIRES")
      .formParam("""firstname""", """Rachelle""")
      .formParam("""type""", """Teacher""")
      .check(status.is(200)))
    .exec(http("Create manual teacher")
      .post("""/directory/api/user""")
      .formParam("""classId""", """${classId}""")
      .formParam("""lastname""", "BAILLY")
      .formParam("""firstname""", """Catherine""")
      .formParam("""type""", """Teacher""")
      .check(status.is(200)))

    .exec(http("List teachers in class")
      .get("""/directory/api/personnes?id=${classId}&type=Teacher""")
      .check(status.is(200), jsonPath("$.status").is("ok"),
        jsonPath("$.result").find.transformOption(_.map(res => {
          val json = JSONValue.parse(res).asInstanceOf[JSONObject]
          json.values.asScala.foldLeft[List[(String, String)]](Nil) { (acc, c) =>
            val user = c.asInstanceOf[JSONObject]
            user.get("lastName").asInstanceOf[String] match {
              case "PIRES" | "BAILLY" if user.get("code") != null =>
                (user.get("lastName").asInstanceOf[String], user.get("userId").asInstanceOf[String]) :: acc
              case _ => acc
            }
          }.toMap
        })).saveAs("createdTeacherIds")))

    .exec { session =>
      val uIds = session("createdTeacherIds").as[Map[String, String]]
      session.set("teacherPiresId", uIds.get("PIRES").get).set("teacherBaillyId", uIds.get("BAILLY").get)
        .set("now", System.currentTimeMillis())
    }
    .exec(http("Teacher details")
      .get("""/directory/api/details?id=${teacherPiresId}""")
      .check(status.is(200), jsonPath("$.status").is("ok"),
        jsonPath("$.result.*.login").find.saveAs("teacherPiresLogin"),
        jsonPath("$.result.*.code").find.saveAs("teacherPiresCode")))
    .exec(http("Teacher details")
      .get("""/directory/api/details?id=${teacherBaillyId}""")
      .check(status.is(200), jsonPath("$.status").is("ok"),
        jsonPath("$.result.*.login").find.saveAs("teacherBaillyLogin"),
        jsonPath("$.result.*.code").find.saveAs("teacherBaillyCode")))

    .exec(http("Activate teacher account")
      .post("""/auth/activation""")
      .formParam("""login""", """${teacherPiresLogin}""")
      .formParam("""activationCode""", """${teacherPiresCode}""")
      .formParam("""password""", """blipblop""")
      .formParam("""confirmPassword""", """blipblop""")
      .formParam("""acceptCGU""", """true""")
      .check(status.is(302)))
    .exec(http("Activate teacher account")
      .post("""/auth/activation""")
      .formParam("""login""", """${teacherBaillyLogin}""")
      .formParam("""activationCode""", """${teacherBaillyCode}""")
      .formParam("""password""", """blipblop""")
      .formParam("""confirmPassword""", """blipblop""")
      .formParam("""acceptCGU""", """true""")
      .check(status.is(302)))

  val scnCreateBookings =
    Role.createAndSetRole("Réservation de ressources")
      .exec(http("Login - teacher")
        .post("""/auth/login""")
        .formParam("""email""", """${teacherLogin}""")
        .formParam("""password""", """blipblop""")
        .check(status.is(302)))
      // ResourceType
      .exec(http("Create type")
        .post("/rbs/type")
        .body(StringBody("""{"name" : "type created", "validation" : true, "school_id" : "${schoolId}"}"""))
        .check(status.is(200),
          jsonPath("$.id").find.saveAs("typeId")))
      // Resource
      .exec(http("Create resource")
        .post("/rbs/resources")
        .body(StringBody("""{"name" : "resource created",
    					"description" : "resource created desc",
    					"periodic_booking" : true,
    					"is_available" : true,
    					"type_id" : ${typeId}}"""))
        .check(status.is(200),
          jsonPath("$.id").find.saveAs("resourceId")))
      .exec(http("Get resource")
        .get("/rbs/resource/${resourceId}")
        .check(status.is(200),
          jsonPath("$.id").find.is("${resourceId}"),
          jsonPath("$.name").find.is("resource created"),
          jsonPath("$.description").find.is("resource created desc"),
          jsonPath("$.periodic_booking").find.is("true"),
          jsonPath("$.is_available").find.is("true"),
          jsonPath("$.type_id").find.is("${typeId}")))
      .exec(http("Update resource")
        .put("/rbs/resource/${resourceId}")
        .body(StringBody("""{"name" : "resource updated",
        					"description" : "resource updated desc",
        					"type_id" : ${typeId},
            				"is_available" : true,
        					"was_available" : true }"""))
        .check(status.is(200)))
      .exec(http("Get updated resource")
        .get("/rbs/resource/${resourceId}")
        .check(status.is(200),
          jsonPath("$.id").find.is("${resourceId}"),
          jsonPath("$.name").find.is("resource updated"),
          jsonPath("$.description").find.is("resource updated desc"),
          jsonPath("$.periodic_booking").find.is("true"),
          jsonPath("$.is_available").find.is("true"),
          jsonPath("$.type_id").find.is("${typeId}")))
      .exec(http("List resources")
        .get("/rbs/resources")
        .check(status.is(200),
          jsonPath("$[0].id").find.is("${resourceId}")))
      .exec(http("Share rights 'rbs.read' and 'rbs.contrib' for created type")
        .put("/rbs/share/json/${typeId}")
        .bodyPart(StringBodyPart("userId", "${teacherPiresId}"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|updateBooking"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|updatePeriodicBooking"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|createPeriodicBooking"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|createBooking"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-ResourceController|get"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-ResourceTypeController|getResourceType"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|listBookingsByResource"))
        .check(status.is(200)))
      .exec(http("Share rights 'rbs.read' and 'rbs.contrib' for created type")
        .put("/rbs/share/json/${typeId}")
        .bodyPart(StringBodyPart("userId", "${teacherBaillyId}"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|updateBooking"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|updatePeriodicBooking"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|createPeriodicBooking"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|createBooking"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-ResourceController|get"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-ResourceTypeController|getResourceType"))
        .bodyPart(StringBodyPart("actions", "net-atos-entng-rbs-controllers-BookingController|listBookingsByResource"))
        .check(status.is(200)))
      .exec(http("Logout - teacher")
        .get("""/auth/logout""")
        .check(status.is(302)))

      // 1. Concurrent bookings
      // Other teachers create bookings that overlap each other
      .exec(http("Login - teacher 2")
        .post("""/auth/login""")
        .formParam("""email""", """${teacherPiresLogin}""")
        .formParam("""password""", """blipblop""")
        .check(status.is(302)))
      .exec(http("Create booking")
        .post("/rbs/resource/${resourceId}/booking")
        .body(getSlotAsStringBody(firstSlot))
        .check(status.is(200),
          jsonPath("$.id").find.saveAs("firstBookingId")))
      .exec(http("Create booking")
        .post("/rbs/resource/${resourceId}/booking")
        .body(getSlotAsStringBody(secondSlot))
        .check(status.is(200),
          jsonPath("$.id").find.saveAs("secondBookingId")))
      .exec(http("Create booking")
        .post("/rbs/resource/${resourceId}/booking")
        .body(getSlotAsStringBody(thirdSlot))
        .check(status.is(200),
          jsonPath("$.id").find.saveAs("thirdBookingId")))
      .exec(http("Logout - teacher 2")
        .get("""/auth/logout""")
        .check(status.is(302)))

      .exec(http("Login - teacher 3")
        .post("""/auth/login""")
        .formParam("""email""", """${teacherBaillyLogin}""")
        .formParam("""password""", """blipblop""")
        .check(status.is(302)))
      .exec(http("Create booking")
        .post("/rbs/resource/${resourceId}/booking")
        .body(getSlotAsStringBody(concurrentSlot))
        .check(status.is(200),
          jsonPath("$.id").find.saveAs("concurrentBookingId")))
      // Create 2 bookings that do not overlap with the previous bookings
      .exec(http("Create booking")
        .post("/rbs/resource/${resourceId}/booking")
        .body(getSlotAsStringBody(nonConcurrentSlot))
        .check(status.is(200),
          jsonPath("$.id").find.saveAs("nonConcurrentBookingId")))
      .exec(http("Create booking")
        .post("/rbs/resource/${resourceId}/booking")
        .body(getSlotAsStringBody(secondNonConcurrentSlot))
        .check(status.is(200),
          jsonPath("$.id").find.saveAs("secondNonConcurrentBookingId")))
      .exec(http("Logout - teacher 3")
        .get("""/auth/logout""")
        .check(status.is(302)))

      .exec(http("Login 1 - teacher")
        .post("""/auth/login""")
        .formParam("""email""", """${teacherLogin}""")
        .formParam("""password""", """blipblop""")
        .check(status.is(302)))
      /* Validate a booking, and check that :
       * concurrents bookings have been refused
       * and non concurrent bookings still have status "created"
       */
      .exec(http("Validate booking")
        .put("/rbs/resource/${resourceId}/booking/${concurrentBookingId}/process")
        .body(StringBody("""{"status": 2}"""))
        .check(status.is(200),
          jsonPath("$.id").is("${concurrentBookingId}"),
          jsonPath("$.status").is("2")))
      .exec(http("List bookings and check their status")
        .get("/rbs/resource/${resourceId}/bookings")
        .check(status.is(200),
          jsonPath("$[?(@.id == ${concurrentBookingId})].status").is("2"),
          jsonPath("$[?(@.id == ${firstBookingId})].status").is("3"),
          jsonPath("$[?(@.id == ${secondBookingId})].status").is("3"),
          jsonPath("$[?(@.id == ${thirdBookingId})].status").is("3"),
          jsonPath("$[?(@.id == ${nonConcurrentBookingId})].status").is("1"),
          jsonPath("$[?(@.id == ${secondNonConcurrentBookingId})].status").is("1")))
      .exec(http("Try creating a conflicting booking")
        .post("/rbs/resource/${resourceId}/booking")
        .body(getSlotAsStringBody(firstSlot))
        .check(status.is(409)))

      // 2. Create a periodic booking and check that slots' start and end dates are correct
      .exec(http("Create periodic booking")
        .post("/rbs/resource/${resourceId}/booking/periodic")
        .body(StringBody("""{"booking_reason":"Résa périodique",
            "start_date" : """ + periodicStartDate + """,
            "end_date" : """ + periodicEndDate + """,
            "days":[false, true, false, false, true, true, false],
            "periodicity":2,
            "occurrences":10
            }"""))
        .check(status.is(200),
          jsonPath("$[?(@.status != 1)]").notExists,
          jsonPath("$..id").findAll.saveAs("slotsIds")))
      .exec(http("List bookings and check their dates")
        .get("/rbs/resource/${resourceId}/bookings")
        .check(status.is(200),
          jsonPath("$[?(@.id == ${slotsIds(0)})].start_date").is("2030-11-01T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(0)})].end_date").is("2030-11-01T16:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(1)})].start_date").is("2030-11-11T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(1)})].end_date").is("2030-11-11T16:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(2)})].start_date").is("2030-11-14T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(2)})].end_date").is("2030-11-14T16:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(3)})].start_date").is("2030-11-15T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(3)})].end_date").is("2030-11-15T16:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(4)})].start_date").is("2030-11-25T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(4)})].end_date").is("2030-11-25T16:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(5)})].start_date").is("2030-11-28T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(5)})].end_date").is("2030-11-28T16:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(6)})].start_date").is("2030-11-29T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(6)})].end_date").is("2030-11-29T16:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(7)})].start_date").is("2030-12-09T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(7)})].end_date").is("2030-12-09T16:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(8)})].start_date").is("2030-12-12T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(8)})].end_date").is("2030-12-12T16:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(9)})].start_date").is("2030-12-13T09:30:00.000"),
          jsonPath("$[?(@.id == ${slotsIds(9)})].end_date").is("2030-12-13T16:30:00.000")))

      // Deletes
      .exec(http("Delete Resource")
        .delete("/rbs/resource/${resourceId}")
        .check(status.is(200)))
      .exec(http("Get Resource deleted")
        .get("/rbs/resource/${resourceId}")
        .check(status.is(401)))
//      .exec(http("Delete Type")
//        .delete("/rbs/type/${typeId}")
//        .check(status.is(204)))
//      .exec(http("Get Type deleted")
//        .get("/rbs/type/${typeId}")
//        .check(status.is(401)))
}