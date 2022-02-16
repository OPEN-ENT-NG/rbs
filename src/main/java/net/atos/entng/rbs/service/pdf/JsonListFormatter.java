package net.atos.entng.rbs.service.pdf;

import fr.wseduc.webutils.I18n;
import net.atos.entng.rbs.model.ExportBooking;
import org.joda.time.DateTime;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class JsonListFormatter extends JsonFormatter {

    private class Booking {
        private String ownerName;
        private DateTime startDate;
        private DateTime endDate;
        private String quantity;

        private Booking(JsonFormatter thisFormatter, String ownerName, String startDate, String endDate, String quantity) {
            this.ownerName = ownerName;
            this.startDate = thisFormatter.toUserTimeZone(startDate);
            this.endDate= thisFormatter.toUserTimeZone(endDate);
            this.quantity= quantity;
        }

        private String getOwnerName() {
            return ownerName;
        }

        private DateTime getStartDate() {
            return startDate;
        }

        private DateTime getEndDate() {
            return endDate;
        }

        private String getQuantity() {
            return quantity;
        }
    }

    private class BookingComparator implements Comparator<Booking> {

        @Override
        public int compare(Booking booking, Booking t1) {
            return booking.getStartDate().compareTo(t1.getStartDate());
        }
    }

	private final int CALENDAR_WIDTH = 680;
    private final int NUMBER_OF_BOOKINGS_BY_PAGE = 25;

	public JsonListFormatter(JsonObject jsonFileObject, String host, Locale locale, String userTimeZone) {
        super(jsonFileObject, host, locale, userTimeZone);
	}

	public JsonObject format(){

        JsonObject convertedObject = new JsonObject();
        convertedObject.put(EDITION_DATE_FIELD_NAME, DateTime.now().toString("dd/MM/YYYY"));

        // General calendar settings
        convertedObject.put(CALENDAR_WIDTH_FIELD_NAME, CALENDAR_WIDTH);
        convertedObject.put(CALENDAR_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
        convertedObject.put(I18N_TITLE, I18n.getInstance().translate(MAP_I18N.get(I18N_TITLE), this.host, this.locale));
        JsonArray exportBookingList = exportObject.getJsonArray(BOOKING_LIST_FIELD_NAME);

        // Building resource list
        ArrayList<Long> performedResourceId = new ArrayList<Long>();
        JsonArray resourceList = new fr.wseduc.webutils.collections.JsonArray();

        final String i18nTo = I18n.getInstance().translate(MAP_I18N.get(I18N_TO), this.host, this.locale);

        for (int i = 0; i < exportBookingList.size(); i++) {
            JsonObject bookingIterator = exportBookingList.getJsonObject(i);
            Long currentResourceId = bookingIterator.getLong(ExportBooking.RESOURCE_ID);

            if(!performedResourceId.contains(currentResourceId)){ // New resource found
                performedResourceId.add(bookingIterator.getLong(ExportBooking.RESOURCE_ID));

                // Adding resource bookings
                ArrayList<Booking> bookingList = new ArrayList<>();
                for (int j = 0; j < exportBookingList.size(); j++) {
                    JsonObject exportBooking = exportBookingList.getJsonObject(j);

                    if(exportBooking.getLong(ExportBooking.RESOURCE_ID).equals(currentResourceId)){ // This booking belongs to the current resource


                        String ownerName = exportBooking.getString(ExportBooking.BOOKING_OWNER_NAME);
                        String startDate = exportBooking.getString(ExportBooking.BOOKING_START_DATE);
                        String endDate = exportBooking.getString(ExportBooking.BOOKING_END_DATE);
                        String quantity = exportBooking.getLong(ExportBooking.QUANTITY).toString();

                        bookingList.add(new Booking(this, ownerName, startDate, endDate, quantity));
                    }
                }

                // Sort bookings by start date
                Collections.sort(bookingList, new BookingComparator());

                int cpt = 0;
                JsonArray jsonBookingList = new fr.wseduc.webutils.collections.JsonArray();
                for (Booking b: bookingList) {
                    JsonObject jsonBooking = new JsonObject();

                    jsonBooking.put(ExportBooking.BOOKING_OWNER_NAME, b.getOwnerName());
                    jsonBooking.put(ExportBooking.BOOKING_START_DATE, b.getStartDate().toString("dd/MM/YYYY à kk:mm").replace("à", i18nTo).replace(':', 'h'));
                    jsonBooking.put(ExportBooking.BOOKING_END_DATE, b.getEndDate().toString("dd/MM/YYYY à kk:mm").replace("à", i18nTo).replace(':', 'h'));
                    jsonBooking.put(ExportBooking.QUANTITY, b.getQuantity());

                    jsonBookingList.add(jsonBooking);
                    cpt++;

                    if(cpt % NUMBER_OF_BOOKINGS_BY_PAGE == 0) {
                        JsonObject resource = buildResource(currentResourceId,
                                bookingIterator.getString(ExportBooking.RESOURCE_NAME),
                                bookingIterator.getString(ExportBooking.RESOURCE_COLOR),
                                bookingIterator.getString(ExportBooking.SCHOOL_NAME),
                                jsonBookingList);

                        resourceList.add(resource);
                        jsonBookingList = new fr.wseduc.webutils.collections.JsonArray();
                    }
                }

                if(cpt % NUMBER_OF_BOOKINGS_BY_PAGE != 0) {
                    JsonObject resource = buildResource(currentResourceId,
                            bookingIterator.getString(ExportBooking.RESOURCE_NAME),
                            bookingIterator.getString(ExportBooking.RESOURCE_COLOR),
                            bookingIterator.getString(ExportBooking.SCHOOL_NAME),
                            jsonBookingList);

                    resourceList.add(resource);
                }
            }
        }

        convertedObject.put(RESOURCES_FIELD_NAME, resourceList);

        return convertedObject;
	}

	private JsonObject buildResource(Long id, String name, String color, String schoolName, JsonArray bookingList) {
        JsonObject resource = new JsonObject();
        resource.put(ExportBooking.RESOURCE_ID, id);
        resource.put(ExportBooking.RESOURCE_NAME, name);
        resource.put(ExportBooking.RESOURCE_COLOR, color);
        resource.put(ExportBooking.SCHOOL_NAME, schoolName);
        resource.put(BOOKING_LIST_FIELD_NAME, bookingList);
        resource.put(I18N_HEADER_OWNER, I18n.getInstance().translate(MAP_I18N.get(I18N_HEADER_OWNER), this.host, this.locale));
        resource.put(I18N_HEADER_START, I18n.getInstance().translate(MAP_I18N.get(I18N_HEADER_START), this.host, this.locale));
        resource.put(I18N_HEADER_END, I18n.getInstance().translate(MAP_I18N.get(I18N_HEADER_END), this.host, this.locale));
        resource.put(I18N_QUANTITY, I18n.getInstance().translate(MAP_I18N.get(I18N_QUANTITY), this.host, this.locale));
        resource.put(I18N_FOOTER, I18n.getInstance().translate(MAP_I18N.get(I18N_FOOTER), this.host, this.locale));

        return resource;
    }
}
